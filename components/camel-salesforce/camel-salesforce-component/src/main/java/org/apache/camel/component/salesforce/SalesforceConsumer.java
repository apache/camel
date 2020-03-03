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
package org.apache.camel.component.salesforce;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.PlatformEvent;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.apache.camel.component.salesforce.internal.client.RestClient;
import org.apache.camel.component.salesforce.internal.streaming.PushTopicHelper;
import org.apache.camel.component.salesforce.internal.streaming.SubscriptionHelper;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.cometd.bayeux.Message;
import org.cometd.bayeux.client.ClientSessionChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Salesforce consumer.
 */
public class SalesforceConsumer extends DefaultConsumer {

    private enum MessageKind {
        CHANGE_EVENT, PLATFORM_EVENT, PUSH_TOPIC;

        public static MessageKind fromTopicName(final String topicName) {
            if (topicName.startsWith("event/") || topicName.startsWith("/event/")) {
                return MessageKind.PLATFORM_EVENT;
            } else if (topicName.startsWith("data/") || topicName.startsWith("/data/")) {
                return MessageKind.CHANGE_EVENT;
            }

            return PUSH_TOPIC;
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(SalesforceConsumer.class);

    private static final String CREATED_DATE_PROPERTY = "createdDate";
    private static final String EVENT_PROPERTY = "event";
    private static final double MINIMUM_VERSION = 24.0;
    private static final ObjectMapper OBJECT_MAPPER = JsonUtils.createObjectMapper();
    private static final String PAYLOAD_PROPERTY = "payload";
    private static final String REPLAY_ID_PROPERTY = "replayId";
    private static final String SCHEMA_PROPERTY = "schema";
    private static final String SOBJECT_PROPERTY = "sobject";
    private static final String TYPE_PROPERTY = "type";

    private final SalesforceEndpoint endpoint;
    private final MessageKind messageKind;
    private final ObjectMapper objectMapper;

    private final boolean rawPayload;
    private final Class<?> sObjectClass;
    private boolean subscribed;
    private final SubscriptionHelper subscriptionHelper;
    private final String topicName;

    public SalesforceConsumer(final SalesforceEndpoint endpoint, final Processor processor, final SubscriptionHelper helper) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        final ObjectMapper configuredObjectMapper = endpoint.getConfiguration().getObjectMapper();
        if (configuredObjectMapper != null) {
            objectMapper = configuredObjectMapper;
        } else {
            objectMapper = OBJECT_MAPPER;
        }

        // check minimum supported API version
        if (Double.parseDouble(endpoint.getConfiguration().getApiVersion()) < MINIMUM_VERSION) {
            throw new IllegalArgumentException("Minimum supported API version for consumer endpoints is " + 24.0);
        }

        topicName = endpoint.getTopicName();
        subscriptionHelper = helper;

        messageKind = MessageKind.fromTopicName(topicName);

        rawPayload = endpoint.getConfiguration().isRawPayload();

        // get sObjectClass to convert to
        final String sObjectName = endpoint.getConfiguration().getSObjectName();
        if (sObjectName != null) {
            sObjectClass = endpoint.getComponent().getClassMap().get(sObjectName);
            if (sObjectClass == null) {
                throw new IllegalArgumentException(String.format("SObject Class not found for %s", sObjectName));
            }
        } else {
            final String className = endpoint.getConfiguration().getSObjectClass();
            if (className != null) {
                sObjectClass = endpoint.getComponent().getCamelContext().getClassResolver().resolveClass(className);
                if (sObjectClass == null) {
                    throw new IllegalArgumentException(String.format("SObject Class not found %s", className));
                }
            } else {
                LOG.warn("Property sObjectName or sObjectClass NOT set, messages will be of type java.lang.Map");
                sObjectClass = null;
            }
        }

    }

    public String getTopicName() {
        return topicName;
    }

    @Override
    public void handleException(String message, Throwable t) {
        super.handleException(message, t);
    }

    public void processMessage(final ClientSessionChannel channel, final Message message) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Received event {} on channel {}", channel.getId(), channel.getChannelId());
        }

        final Exchange exchange = endpoint.createExchange();
        final org.apache.camel.Message in = exchange.getIn();

        switch (messageKind) {
            case PUSH_TOPIC:
                createPushTopicMessage(message, in);
                break;
            case PLATFORM_EVENT:
                createPlatformEventMessage(message, in);
                break;
            case CHANGE_EVENT:
                createChangeEventMessage(message, in);
                break;
            default:
                throw new IllegalStateException("Unknown message kind: " + messageKind);
        }

        try {
            getAsyncProcessor().process(exchange, new AsyncCallback() {
                @Override
                public void done(boolean doneSync) {
                    // noop
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Done processing event: {} {}", channel.getId(), doneSync ? "synchronously" : "asynchronously");
                    }
                }
            });
        } catch (final Exception e) {
            final String msg = String.format("Error processing %s: %s", exchange, e);
            handleException(msg, new SalesforceException(msg, e));
        } finally {
            final Exception ex = exchange.getException();
            if (ex != null) {
                final String msg = String.format("Unhandled exception: %s", ex.getMessage());
                handleException(msg, new SalesforceException(msg, ex));
            }
        }
    }

    @SuppressWarnings("unchecked")
    void createChangeEventMessage(final Message message, final org.apache.camel.Message in) {
        setHeaders(in, message);

        final Map<String, Object> data = message.getDataAsMap();

        final Map<String, Object> event = (Map<String, Object>)data.get(EVENT_PROPERTY);
        final Object replayId = event.get(REPLAY_ID_PROPERTY);
        if (replayId != null) {
            in.setHeader("CamelSalesforceReplayId", replayId);
        }

        in.setHeader("CamelSalesforceChangeEventSchema", data.get(SCHEMA_PROPERTY));
        in.setHeader("CamelSalesforceEventType", topicName.substring(topicName.lastIndexOf('/') + 1));

        final Map<String, Object> payload = (Map<String, Object>)data.get(PAYLOAD_PROPERTY);
        final Map<String, Object> changeEventHeader = (Map<String, Object>)payload.get("ChangeEventHeader");
        in.setHeader("CamelSalesforceChangeType", changeEventHeader.get("changeType"));
        in.setHeader("CamelSalesforceChangeOrigin", changeEventHeader.get("changeOrigin"));
        in.setHeader("CamelSalesforceTransactionKey", changeEventHeader.get("transactionKey"));
        in.setHeader("CamelSalesforceSequenceNumber", changeEventHeader.get("sequenceNumber"));
        in.setHeader("CamelSalesforceIsTransactionEnd", changeEventHeader.get("isTransactionEnd"));
        in.setHeader("CamelSalesforceCommitTimestamp", changeEventHeader.get("commitTimestamp"));
        in.setHeader("CamelSalesforceCommitUser", changeEventHeader.get("commitUser"));
        in.setHeader("CamelSalesforceCommitNumber", changeEventHeader.get("commitNumber"));
        in.setHeader("CamelSalesforceEntityName", changeEventHeader.get("entityName"));
        in.setHeader("CamelSalesforceRecordIds", changeEventHeader.get("recordIds"));

        if (rawPayload) {
            in.setBody(message);
        } else {
            payload.remove("ChangeEventHeader");
            in.setBody(payload);
        }
    }

    void createPlatformEventMessage(final Message message, final org.apache.camel.Message in) {
        setHeaders(in, message);

        final Map<String, Object> data = message.getDataAsMap();

        @SuppressWarnings("unchecked")
        final Map<String, Object> event = (Map<String, Object>)data.get(EVENT_PROPERTY);

        final Object replayId = event.get(REPLAY_ID_PROPERTY);
        if (replayId != null) {
            in.setHeader("CamelSalesforceReplayId", replayId);
        }

        in.setHeader("CamelSalesforcePlatformEventSchema", data.get(SCHEMA_PROPERTY));
        in.setHeader("CamelSalesforceEventType", topicName.substring(topicName.lastIndexOf('/') + 1));

        final Object payload = data.get(PAYLOAD_PROPERTY);

        final PlatformEvent platformEvent = objectMapper.convertValue(payload, PlatformEvent.class);
        in.setHeader("CamelSalesforceCreatedDate", platformEvent.getCreated());

        if (rawPayload) {
            in.setBody(message);
        } else {
            in.setBody(platformEvent);
        }

    }

    void createPushTopicMessage(final Message message, final org.apache.camel.Message in) {
        setHeaders(in, message);

        final Map<String, Object> data = message.getDataAsMap();

        @SuppressWarnings("unchecked")
        final Map<String, Object> event = (Map<String, Object>)data.get(EVENT_PROPERTY);
        final Object eventType = event.get(TYPE_PROPERTY);
        final Object createdDate = event.get(CREATED_DATE_PROPERTY);
        final Object replayId = event.get(REPLAY_ID_PROPERTY);

        in.setHeader("CamelSalesforceTopicName", topicName);
        in.setHeader("CamelSalesforceEventType", eventType);
        in.setHeader("CamelSalesforceCreatedDate", createdDate);
        if (replayId != null) {
            in.setHeader("CamelSalesforceReplayId", replayId);
        }

        // get SObject
        @SuppressWarnings("unchecked")
        final Map<String, Object> sObject = (Map<String, Object>)data.get(SOBJECT_PROPERTY);
        try {

            final String sObjectString = objectMapper.writeValueAsString(sObject);
            LOG.debug("Received SObject: {}", sObjectString);

            if (rawPayload) {
                // return sobject string as exchange body
                in.setBody(sObjectString);
            } else if (sObjectClass == null) {
                // return sobject map as exchange body
                in.setBody(sObject);
            } else {
                // create the expected SObject
                in.setBody(objectMapper.readValue(new StringReader(sObjectString), sObjectClass));
            }
        } catch (final IOException e) {
            final String msg = String.format("Error parsing message [%s] from Topic %s: %s", message, topicName, e.getMessage());
            handleException(msg, new SalesforceException(msg, e));
        }
    }

    void setHeaders(final org.apache.camel.Message in, final Message message) {
        in.setHeader("CamelSalesforceChannel", message.getChannel());
        final String clientId = message.getClientId();
        if (ObjectHelper.isNotEmpty(clientId)) {
            in.setHeader("CamelSalesforceClientId", clientId);
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        final SalesforceEndpointConfig config = endpoint.getConfiguration();

        // is a query configured in the endpoint?
        if (messageKind == MessageKind.PUSH_TOPIC && ObjectHelper.isNotEmpty(config.getSObjectQuery())) {
            // Note that we don't lookup topic if the query is not specified
            // create REST client for PushTopic operations
            final SalesforceComponent salesforceComponent = endpoint.getComponent();
            final RestClient restClient = salesforceComponent.createRestClientFor(endpoint);

            // don't forget to start the client
            ServiceHelper.startService(restClient);

            try {
                final PushTopicHelper helper = new PushTopicHelper(config, topicName, restClient);
                helper.createOrUpdateTopic();
            } finally {
                // don't forget to stop the client
                ServiceHelper.stopService(restClient);
            }
        }

        // subscribe to topic
        ServiceHelper.startService(subscriptionHelper);
        subscriptionHelper.subscribe(topicName, this);
        subscribed = true;
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (subscribed) {
            subscribed = false;
            // unsubscribe from topic
            subscriptionHelper.unsubscribe(topicName, this);
        }
    }

}
