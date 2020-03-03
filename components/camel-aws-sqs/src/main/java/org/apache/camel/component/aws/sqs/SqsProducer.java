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
package org.apache.camel.component.aws.sqs;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.DeleteMessageResult;
import com.amazonaws.services.sqs.model.ListQueuesRequest;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.services.sqs.model.SendMessageBatchRequest;
import com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry;
import com.amazonaws.services.sqs.model.SendMessageBatchResult;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Producer which sends messages to the Amazon Web Service Simple Queue
 * Service <a href="http://aws.amazon.com/sqs/">AWS SQS</a>
 */
public class SqsProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(SqsProducer.class);

    private transient String sqsProducerToString;

    public SqsProducer(SqsEndpoint endpoint) throws NoFactoryAvailableException {
        super(endpoint);
        if (endpoint.getConfiguration().isFifoQueue() && ObjectHelper.isEmpty(getEndpoint().getConfiguration().getMessageGroupIdStrategy())) {
            throw new IllegalArgumentException("messageGroupIdStrategy must be set for FIFO queues.");
        }
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        SqsOperations operation = determineOperation(exchange);
        if (ObjectHelper.isEmpty(operation)) {
            processSingleMessage(exchange);
        } else {
            switch (operation) {
                case sendBatchMessage:
                    sendBatchMessage(getClient(), exchange);
                    break;
                case deleteMessage:
                    deleteMessage(getClient(), exchange);
                    break;
                case listQueues:
                    listQueues(getClient(), exchange);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported operation");
            }
        }
    }

    public void processSingleMessage(final Exchange exchange) {
        String body = exchange.getIn().getBody(String.class);
        SendMessageRequest request = new SendMessageRequest(getQueueUrl(), body);
        request.setMessageAttributes(translateAttributes(exchange.getIn().getHeaders(), exchange));
        addDelay(request, exchange);
        configureFifoAttributes(request, exchange);

        LOG.trace("Sending request [{}] from exchange [{}]...", request, exchange);

        SendMessageResult result = getClient().sendMessage(request);

        LOG.trace("Received result [{}]", result);

        Message message = getMessageForResponse(exchange);
        message.setHeader(SqsConstants.MESSAGE_ID, result.getMessageId());
        message.setHeader(SqsConstants.MD5_OF_BODY, result.getMD5OfMessageBody());
    }

    private void sendBatchMessage(AmazonSQS amazonSQS, Exchange exchange) {
        SendMessageBatchRequest request = new SendMessageBatchRequest(getQueueUrl());
        Collection<SendMessageBatchRequestEntry> entries = new ArrayList<>();
        if (exchange.getIn().getBody() instanceof Iterable) {
            Iterable c = exchange.getIn().getBody(Iterable.class);
            for (Object o : c) {
                String object = (String)o;
                SendMessageBatchRequestEntry entry = new SendMessageBatchRequestEntry();
                entry.setId(UUID.randomUUID().toString());
                entry.setMessageAttributes(translateAttributes(exchange.getIn().getHeaders(), exchange));
                entry.setMessageBody(object);
                addDelay(entry, exchange);
                configureFifoAttributes(entry, exchange);
                entries.add(entry);
            }
            request.setEntries(entries);
            SendMessageBatchResult result = amazonSQS.sendMessageBatch(request);
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        } else {
            request = exchange.getIn().getBody(SendMessageBatchRequest.class);
            SendMessageBatchResult result = amazonSQS.sendMessageBatch(request);
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void deleteMessage(AmazonSQS amazonSQS, Exchange exchange) {
        String receiptHandle = exchange.getIn().getHeader(SqsConstants.RECEIPT_HANDLE, String.class);
        DeleteMessageRequest request = new DeleteMessageRequest();
        request.setQueueUrl(getQueueUrl());
        if (ObjectHelper.isEmpty(receiptHandle)) {
            throw new IllegalArgumentException("Receipt Handle must be specified for the operation deleteMessage");
        }
        request.setReceiptHandle(receiptHandle);
        DeleteMessageResult result = amazonSQS.deleteMessage(request);
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void listQueues(AmazonSQS amazonSQS, Exchange exchange) {
        ListQueuesRequest request = new ListQueuesRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(SqsConstants.SQS_QUEUE_PREFIX))) {
            request.setQueueNamePrefix(exchange.getIn().getHeader(SqsConstants.SQS_QUEUE_PREFIX, String.class));
        }
        ListQueuesResult result = amazonSQS.listQueues(request);
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void configureFifoAttributes(SendMessageRequest request, Exchange exchange) {
        if (getEndpoint().getConfiguration().isFifoQueue()) {
            // use strategies
            MessageGroupIdStrategy messageGroupIdStrategy = getEndpoint().getConfiguration().getMessageGroupIdStrategy();
            String messageGroupId = messageGroupIdStrategy.getMessageGroupId(exchange);
            request.setMessageGroupId(messageGroupId);

            MessageDeduplicationIdStrategy messageDeduplicationIdStrategy = getEndpoint().getConfiguration().getMessageDeduplicationIdStrategy();
            String messageDeduplicationId = messageDeduplicationIdStrategy.getMessageDeduplicationId(exchange);
            request.setMessageDeduplicationId(messageDeduplicationId);

        }
    }

    private void configureFifoAttributes(SendMessageBatchRequestEntry request, Exchange exchange) {
        if (getEndpoint().getConfiguration().isFifoQueue()) {
            // use strategies
            MessageGroupIdStrategy messageGroupIdStrategy = getEndpoint().getConfiguration().getMessageGroupIdStrategy();
            String messageGroupId = messageGroupIdStrategy.getMessageGroupId(exchange);
            request.setMessageGroupId(messageGroupId);

            MessageDeduplicationIdStrategy messageDeduplicationIdStrategy = getEndpoint().getConfiguration().getMessageDeduplicationIdStrategy();
            String messageDeduplicationId = messageDeduplicationIdStrategy.getMessageDeduplicationId(exchange);
            request.setMessageDeduplicationId(messageDeduplicationId);

        }
    }

    private void addDelay(SendMessageRequest request, Exchange exchange) {
        Integer headerValue = exchange.getIn().getHeader(SqsConstants.DELAY_HEADER, Integer.class);
        Integer delayValue;
        if (headerValue == null) {
            LOG.trace("Using the config delay");
            delayValue = getEndpoint().getConfiguration().getDelaySeconds();
        } else {
            LOG.trace("Using the header delay");
            delayValue = headerValue;
        }
        LOG.trace("found delay: {}", delayValue);
        request.setDelaySeconds(delayValue == null ? Integer.valueOf(0) : delayValue);
    }

    private void addDelay(SendMessageBatchRequestEntry request, Exchange exchange) {
        Integer headerValue = exchange.getIn().getHeader(SqsConstants.DELAY_HEADER, Integer.class);
        Integer delayValue;
        if (headerValue == null) {
            LOG.trace("Using the config delay");
            delayValue = getEndpoint().getConfiguration().getDelaySeconds();
        } else {
            LOG.trace("Using the header delay");
            delayValue = headerValue;
        }
        LOG.trace("found delay: {}", delayValue);
        request.setDelaySeconds(delayValue == null ? Integer.valueOf(0) : delayValue);
    }

    protected AmazonSQS getClient() {
        return getEndpoint().getClient();
    }

    protected String getQueueUrl() {
        return getEndpoint().getQueueUrl();
    }

    protected SqsConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public SqsEndpoint getEndpoint() {
        return (SqsEndpoint)super.getEndpoint();
    }

    @Override
    public String toString() {
        if (sqsProducerToString == null) {
            sqsProducerToString = "SqsProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return sqsProducerToString;
    }

    Map<String, MessageAttributeValue> translateAttributes(Map<String, Object> headers, Exchange exchange) {
        Map<String, MessageAttributeValue> result = new HashMap<>();
        HeaderFilterStrategy headerFilterStrategy = getEndpoint().getHeaderFilterStrategy();
        for (Entry<String, Object> entry : headers.entrySet()) {
            // only put the message header which is not filtered into the
            // message attribute
            if (!headerFilterStrategy.applyFilterToCamelHeaders(entry.getKey(), entry.getValue(), exchange)) {
                Object value = entry.getValue();
                if (value instanceof String && !((String)value).isEmpty()) {
                    MessageAttributeValue mav = new MessageAttributeValue();
                    mav.setDataType("String");
                    mav.withStringValue((String)value);
                    result.put(entry.getKey(), mav);
                } else if (value instanceof ByteBuffer) {
                    MessageAttributeValue mav = new MessageAttributeValue();
                    mav.setDataType("Binary");
                    mav.withBinaryValue((ByteBuffer)value);
                    result.put(entry.getKey(), mav);
                } else if (value instanceof Boolean) {
                    MessageAttributeValue mav = new MessageAttributeValue();
                    mav.setDataType("Number.Boolean");
                    mav.withStringValue(((Boolean)value) ? "1" : "0");
                    result.put(entry.getKey(), mav);
                } else if (value instanceof Number) {
                    MessageAttributeValue mav = new MessageAttributeValue();
                    final String dataType;
                    if (value instanceof Integer) {
                        dataType = "Number.int";
                    } else if (value instanceof Byte) {
                        dataType = "Number.byte";
                    } else if (value instanceof Double) {
                        dataType = "Number.double";
                    } else if (value instanceof Float) {
                        dataType = "Number.float";
                    } else if (value instanceof Long) {
                        dataType = "Number.long";
                    } else if (value instanceof Short) {
                        dataType = "Number.short";
                    } else {
                        dataType = "Number";
                    }
                    mav.setDataType(dataType);
                    mav.withStringValue(value.toString());
                    result.put(entry.getKey(), mav);
                } else if (value instanceof Date) {
                    MessageAttributeValue mav = new MessageAttributeValue();
                    mav.setDataType("String");
                    mav.withStringValue(value.toString());
                    result.put(entry.getKey(), mav);
                } else {
                    // cannot translate the message header to message attribute
                    // value
                    LOG.warn("Cannot put the message header key={}, value={} into Sqs MessageAttribute", entry.getKey(), entry.getValue());
                }
            }
        }
        return result;
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

    private SqsOperations determineOperation(Exchange exchange) {
        SqsOperations operation = exchange.getIn().getHeader(SqsConstants.SQS_OPERATION, SqsOperations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }
}
