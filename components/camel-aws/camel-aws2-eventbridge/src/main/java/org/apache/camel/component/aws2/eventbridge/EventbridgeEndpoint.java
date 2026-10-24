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

import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.aws.common.AwsClientBuilderUtil;
import org.apache.camel.component.aws2.eventbridge.client.EventbridgeClientFactory;
import org.apache.camel.spi.*;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Manage AWS EventBridge cluster instances and consume events via SQS-backed polling.
 */
@UriEndpoint(firstVersion = "3.6.0", scheme = "aws2-eventbridge", title = "AWS Eventbridge",
             syntax = "aws2-eventbridge://eventbusNameOrArn", category = {
                     Category.CLOUD,
                     Category.MANAGEMENT },
             headersClass = EventbridgeConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.TEXT, javaType = "java.lang.String",
                                   description = "The event as JSON text, one message per event the rule sends to the SQS queue"
                                                 + " of the consumer: the envelope of EventBridge, with the data of the event"
                                                 + " in its detail field, and the id of the SQS message in a header"),
           producerIn = @BodyType(value = Carrier.ANY,
                                  byOption = @BodyType.When(option = "operation", value = "putEvent",
                                                            carriers = {
                                                                    Carrier.TEXT, Carrier.BYTES, Carrier.STREAM,
                                                                    Carrier.FILE }),
                                  description = "Not used by putRule, the default, and the other operations, which take their"
                                                + " input from headers and the eventPatternFile option. putEvent sends the body"
                                                + " as the detail of the event, which must be a JSON object as text: bytes, a"
                                                + " stream or a file are read as text, a Map or a POJO is sent as its"
                                                + " toString(), so marshal it first, and no body fails. With pojoRequest=true"
                                                + " the request of the AWS SDK, such as a PutEventsRequest"),
           producerOut = @BodyType(value = Carrier.POJO,
                                   description = "The response of the AWS SDK for the operation: a PutRuleResponse for putRule,"
                                                 + " the default, and a PutEventsResponse for putEvent. An event that AWS"
                                                 + " rejects, such as one whose detail is not a JSON object, does not fail the"
                                                 + " exchange: check the CamelAwsEventbridgeFailedEntryCount header"))
public class EventbridgeEndpoint extends ScheduledPollEndpoint implements EndpointServiceLocation {

    private final ReentrantLock lock = new ReentrantLock();
    private EventBridgeClient eventbridgeClient;
    private SqsClient sqsClient;

    @UriPath(description = "Event bus name or ARN")
    @Metadata(required = true)
    private String eventbusNameOrArn; // to support component docs
    @UriParam
    private EventbridgeConfiguration configuration;

    public EventbridgeEndpoint(String uri, Component component, EventbridgeConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public EventbridgeComponent getComponent() {
        return (EventbridgeComponent) super.getComponent();
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        if (ObjectHelper.isEmpty(configuration.getRuleName())) {
            throw new IllegalArgumentException("ruleName is required for EventBridge consumer");
        }
        EventbridgeConsumer consumer = new EventbridgeConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new EventbridgeProducer(this);
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        eventbridgeClient = ObjectHelper.isNotEmpty(configuration.getEventbridgeClient())
                ? configuration.getEventbridgeClient()
                : EventbridgeClientFactory.getEventbridgeClient(configuration);
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getEventbridgeClient())) {
            if (ObjectHelper.isNotEmpty(eventbridgeClient)) {
                eventbridgeClient.close();
            }
        }
        if (sqsClient != null) {
            sqsClient.close();
            sqsClient = null;
        }
        super.doStop();
    }

    public EventbridgeConfiguration getConfiguration() {
        return configuration;
    }

    public EventBridgeClient getEventbridgeClient() {
        return eventbridgeClient;
    }

    /**
     * Returns the SQS client used by the consumer, creating one if necessary. Uses the same credentials and region as
     * the EventBridge client.
     */
    public SqsClient getSqsClient() {
        lock.lock();
        try {
            if (sqsClient == null) {
                sqsClient = AwsClientBuilderUtil.buildClient(configuration, SqsClient::builder);
            }
            return sqsClient;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String getServiceUrl() {
        if (!configuration.isOverrideEndpoint()) {
            if (ObjectHelper.isNotEmpty(configuration.getRegion())) {
                return configuration.getRegion();
            }
        } else if (ObjectHelper.isNotEmpty(configuration.getUriEndpointOverride())) {
            return configuration.getUriEndpointOverride();
        }
        return null;
    }

    @Override
    public String getServiceProtocol() {
        return "eventbridge";
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        if (ObjectHelper.isNotEmpty(configuration.getEventbusName())) {
            return Map.of("eventbus", configuration.getEventbusName());
        }
        return null;
    }
}
