/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.aws2.eventbridge;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.EmptyAsyncCallback;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutTargetsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutTargetsResponse;
import software.amazon.awssdk.services.eventbridge.model.RemoveTargetsRequest;
import software.amazon.awssdk.services.eventbridge.model.Target;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;

/**
 * Consumes events from an AWS EventBridge rule by using an SQS queue as a target.
 *
 * <p>
 * AWS EventBridge does not support direct pull-based consumption. This consumer implements the standard AWS pattern:
 * configure an SQS queue as an EventBridge rule target, then poll the queue for events.
 * </p>
 *
 * <p>
 * The consumer can auto-create an SQS queue and wire it to the EventBridge rule, or use a user-provided queue. On
 * shutdown, auto-provisioned resources are cleaned up by default.
 * </p>
 */
public class EventbridgeConsumer extends ScheduledBatchPollingConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(EventbridgeConsumer.class);

    private SqsClient sqsClient;
    private String queueUrl;
    private String queueArn;
    private boolean queueAutoCreated;
    private String targetId;

    public EventbridgeConsumer(EventbridgeEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    public EventbridgeEndpoint getEndpoint() {
        return (EventbridgeEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        setupConsumerResources();
    }

    @Override
    protected void doStop() throws Exception {
        cleanupConsumerResources();
        super.doStop();
    }

    @Override
    protected int poll() throws Exception {
        ReceiveMessageResponse response = sqsClient.receiveMessage(
                ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(getEndpoint().getConfiguration().getMaxMessagesPerPoll())
                        .waitTimeSeconds(getEndpoint().getConfiguration().getWaitTimeSeconds())
                        .visibilityTimeout(getEndpoint().getConfiguration().getVisibilityTimeout())
                        .messageSystemAttributeNames(MessageSystemAttributeName.ALL)
                        .build());

        if (response.messages().isEmpty()) {
            return 0;
        }

        Queue<Object> exchanges = createExchanges(response.messages());
        return processBatch(CastUtils.cast(exchanges));
    }

    @Override
    public int processBatch(Queue<Object> exchanges) throws Exception {
        int total = exchanges.size();

        for (int i = 0; !exchanges.isEmpty(); i++) {
            final Exchange exchange = ObjectHelper.cast(Exchange.class, exchanges.poll());
            exchange.setProperty(ExchangePropertyKey.BATCH_INDEX, i);
            exchange.setProperty(ExchangePropertyKey.BATCH_SIZE, total);
            exchange.setProperty(ExchangePropertyKey.BATCH_COMPLETE, i == total - 1);

            final String receiptHandle
                    = exchange.getMessage().getHeader(EventbridgeConstants.RECEIPT_HANDLE, String.class);
            exchange.getExchangeExtension().addOnCompletion(new Synchronization() {
                @Override
                public void onComplete(Exchange exchange) {
                    deleteMessage(receiptHandle);
                }

                @Override
                public void onFailure(Exchange exchange) {
                    // Leave message in queue for retry
                    LOG.debug("Message processing failed, leaving in SQS queue for retry");
                }
            });

            getAsyncProcessor().process(exchange, EmptyAsyncCallback.get());
        }

        return total;
    }

    private void setupConsumerResources() throws Exception {
        EventbridgeConfiguration config = getEndpoint().getConfiguration();
        sqsClient = getEndpoint().getSqsClient();

        String ruleName = config.getRuleName();

        if (ObjectHelper.isNotEmpty(config.getQueueUrl())) {
            // User provided a queue URL
            queueUrl = config.getQueueUrl();
            queueAutoCreated = false;
            queueArn = getQueueArn(queueUrl);
        } else if (config.isAutoCreateQueue()) {
            // Auto-create queue
            String queueName
                    = "camel-eventbridge-" + ruleName + "-" + UUID.randomUUID().toString().substring(0, 8);

            CreateQueueResponse createResponse = sqsClient.createQueue(
                    CreateQueueRequest.builder()
                            .queueName(queueName)
                            .build());

            queueUrl = createResponse.queueUrl();
            queueAutoCreated = true;
            queueArn = getQueueArn(queueUrl);

            // Set queue policy to allow EventBridge to send messages
            String policy = buildQueuePolicy(queueArn);
            sqsClient.setQueueAttributes(
                    SetQueueAttributesRequest.builder()
                            .queueUrl(queueUrl)
                            .attributes(Map.of(QueueAttributeName.POLICY, policy))
                            .build());

            LOG.info("Auto-created SQS queue {} for EventBridge rule {}", queueName, ruleName);
        } else {
            throw new IllegalArgumentException(
                    "Either queueUrl must be specified or autoCreateQueue must be enabled for EventBridge consumer");
        }

        // Add queue as EventBridge target
        targetId = "camel-eb-" + UUID.randomUUID().toString().substring(0, 8);
        Target target = Target.builder()
                .id(targetId)
                .arn(queueArn)
                .build();

        EventBridgeClient ebClient = getEndpoint().getEventbridgeClient();
        PutTargetsResponse putTargetsResponse = ebClient.putTargets(
                PutTargetsRequest.builder()
                        .rule(ruleName)
                        .eventBusName(config.getEventbusName())
                        .targets(target)
                        .build());

        if (putTargetsResponse.failedEntryCount() > 0) {
            throw new IllegalStateException(
                    "Failed to add SQS target to EventBridge rule: " + putTargetsResponse.failedEntries());
        }

        LOG.info("Added SQS queue {} as target for EventBridge rule {}", queueArn, ruleName);
    }

    private void cleanupConsumerResources() {
        EventbridgeConfiguration config = getEndpoint().getConfiguration();

        try {
            // Remove target from EventBridge rule
            if (ObjectHelper.isNotEmpty(targetId)) {
                EventBridgeClient ebClient = getEndpoint().getEventbridgeClient();
                ebClient.removeTargets(
                        RemoveTargetsRequest.builder()
                                .rule(config.getRuleName())
                                .eventBusName(config.getEventbusName())
                                .ids(targetId)
                                .build());
                LOG.info("Removed target {} from EventBridge rule {}", targetId, config.getRuleName());
            }
        } catch (Exception e) {
            LOG.warn("Failed to remove EventBridge target: {}", e.getMessage());
        }

        try {
            // Delete auto-created queue
            if (queueAutoCreated && config.isDeleteQueueOnShutdown() && ObjectHelper.isNotEmpty(queueUrl)) {
                sqsClient.deleteQueue(
                        DeleteQueueRequest.builder()
                                .queueUrl(queueUrl)
                                .build());
                LOG.info("Deleted auto-created SQS queue {}", queueUrl);
            }
        } catch (Exception e) {
            LOG.warn("Failed to delete SQS queue: {}", e.getMessage());
        }
    }

    private Queue<Object> createExchanges(List<software.amazon.awssdk.services.sqs.model.Message> messages) {
        Queue<Object> exchanges = new LinkedList<>();
        for (software.amazon.awssdk.services.sqs.model.Message msg : messages) {
            Exchange exchange = createExchange(true);
            Message camelMessage = exchange.getMessage();
            camelMessage.setBody(msg.body());
            camelMessage.setHeader(EventbridgeConstants.MESSAGE_ID, msg.messageId());
            camelMessage.setHeader(EventbridgeConstants.RECEIPT_HANDLE, msg.receiptHandle());
            exchanges.add(exchange);
        }
        return exchanges;
    }

    private void deleteMessage(String receiptHandle) {
        try {
            sqsClient.deleteMessage(
                    DeleteMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .receiptHandle(receiptHandle)
                            .build());
        } catch (Exception e) {
            LOG.warn("Failed to delete message from SQS queue: {}", e.getMessage());
        }
    }

    private String getQueueArn(String queueUrl) {
        GetQueueAttributesResponse response = sqsClient.getQueueAttributes(
                GetQueueAttributesRequest.builder()
                        .queueUrl(queueUrl)
                        .attributeNames(QueueAttributeName.QUEUE_ARN)
                        .build());
        return response.attributes().get(QueueAttributeName.QUEUE_ARN);
    }

    private String buildQueuePolicy(String queueArn) {
        return "{\"Version\":\"2012-10-17\","
               + "\"Statement\":[{\"Sid\":\"AllowEventBridgeToSendMessage\","
               + "\"Effect\":\"Allow\","
               + "\"Principal\":{\"Service\":\"events.amazonaws.com\"},"
               + "\"Action\":\"sqs:SendMessage\","
               + "\"Resource\":\"" + queueArn + "\"}]}";
    }
}
