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
 * The Salesforce Streaming API consumer.
 */
public class StreamingApiConsumer extends DefaultConsumer {

    private enum MessageKind {
        CHANGE_EVENT,
        PLATFORM_EVENT,
        PUSH_TOPIC;

        public static MessageKind fromTopicName(final String topicName) {
            if (topicName.startsWith("event/") || topicName.startsWith("/event/")) {
                return MessageKind.PLATFORM_EVENT;
            } else if (topicName.startsWith("data/") || topicName.startsWith("/data/")) {
                return MessageKind.CHANGE_EVENT;
            }

            return PUSH_TOPIC;
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(StreamingApiConsumer.class);

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
    private Class<?> sObjectClass;
    private boolean subscribed;
    private final SubscriptionHelper subscriptionHelper;
    private final String topicName;

    public StreamingApiConsumer(final SalesforceEndpoint endpoint, final Processor processor, final SubscriptionHelper helper) {
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
    }

    public String getTopicName() {
        return topicName;
    }

    public SubscriptionHelper getSubscriptionHelper() {
        return subscriptionHelper;
    }

    @Override
    public void handleException(String message, Throwable t) {
        super.handleException(message, t);
    }

    public void processMessage(final ClientSessionChannel channel, final Message message) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Received event {} on channel {}", channel.getId(), channel.getChannelId());
        }

        final Exchange exchange = createExchange(true);
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

        // use default consumer callback
        AsyncCallback cb = defaultConsumerCallback(exchange, true);
        getAsyncProcessor().process(exchange, cb);
    }

    @SuppressWarnings("unchecked")
    void createChangeEventMessage(final Message message, final org.apache.camel.Message in) {
        setHeaders(in, message);

        final Map<String, Object> data = message.getDataAsMap();

        final Map<String, Object> event = (Map<String, Object>) data.get(EVENT_PROPERTY);
        final Object replayId = event.get(REPLAY_ID_PROPERTY);
        if (replayId != null) {
            in.setHeader(SalesforceConstants.HEADER_SALESFORCE_REPLAY_ID, replayId);
        }

        in.setHeader(SalesforceConstants.HEADER_SALESFORCE_CHANGE_EVENT_SCHEMA, data.get(SCHEMA_PROPERTY));
        in.setHeader(SalesforceConstants.HEADER_SALESFORCE_EVENT_TYPE, topicName.substring(topicName.lastIndexOf('/') + 1));

        final Map<String, Object> payload = (Map<String, Object>) data.get(PAYLOAD_PROPERTY);
        final Map<String, Object> changeEventHeader = (Map<String, Object>) payload.get("ChangeEventHeader");
        in.setHeader(SalesforceConstants.HEADER_SALESFORCE_CHANGE_TYPE, changeEventHeader.get("changeType"));
        in.setHeader(SalesforceConstants.HEADER_SALESFORCE_CHANGE_ORIGIN, changeEventHeader.get("changeOrigin"));
        in.setHeader(SalesforceConstants.HEADER_SALESFORCE_TRANSACTION_KEY, changeEventHeader.get("transactionKey"));
        in.setHeader(SalesforceConstants.HEADER_SALESFORCE_SEQUENCE_NUMBER, changeEventHeader.get("sequenceNumber"));
        in.setHeader(SalesforceConstants.HEADER_SALESFORCE_IS_TRANSACTION_END, changeEventHeader.get("isTransactionEnd"));
        in.setHeader(SalesforceConstants.HEADER_SALESFORCE_COMMIT_TIMESTAMP, changeEventHeader.get("commitTimestamp"));
        in.setHeader(SalesforceConstants.HEADER_SALESFORCE_COMMIT_USER, changeEventHeader.get("commitUser"));
        in.setHeader(SalesforceConstants.HEADER_SALESFORCE_COMMIT_NUMBER, changeEventHeader.get("commitNumber"));
        in.setHeader(SalesforceConstants.HEADER_SALESFORCE_ENTITY_NAME, changeEventHeader.get("entityName"));
        in.setHeader(SalesforceConstants.HEADER_SALESFORCE_RECORD_IDS, changeEventHeader.get("recordIds"));

        if (rawPayload) {
            // getJSON is used for raw payload
            in.setBody(new org.cometd.common.JacksonJSONContextClient()
                    .generate(new org.cometd.common.HashMapMessage(message)));
        } else {
            payload.remove("ChangeEventHeader");
            in.setBody(payload);
        }
    }

    void createPlatformEventMessage(final Message message, final org.apache.camel.Message in) {
        setHeaders(in, message);

        final Map<String, Object> data = message.getDataAsMap();

        @SuppressWarnings("unchecked")
        final Map<String, Object> event = (Map<String, Object>) data.get(EVENT_PROPERTY);

        final Object replayId = event.get(REPLAY_ID_PROPERTY);
        if (replayId != null) {
            in.setHeader(SalesforceConstants.HEADER_SALESFORCE_REPLAY_ID, replayId);
        }

        in.setHeader(SalesforceConstants.HEADER_SALESFORCE_PLATFORM_EVENT_SCHEMA, data.get(SCHEMA_PROPERTY));
        in.setHeader(SalesforceConstants.HEADER_SALESFORCE_EVENT_TYPE, topicName.substring(topicName.lastIndexOf('/') + 1));

        final Object payload = data.get(PAYLOAD_PROPERTY);

        final PlatformEvent platformEvent = objectMapper.convertValue(payload, PlatformEvent.class);
        in.setHeader(SalesforceConstants.HEADER_SALESFORCE_CREATED_DATE, platformEvent.getCreated());

        if (rawPayload) {
            // getJSON is used for raw payload
            in.setBody(new org.cometd.common.JacksonJSONContextClient()
                    .generate(new org.cometd.common.HashMapMessage(message)));
        } else {
            in.setBody(platformEvent);
        }

    }

    void createPushTopicMessage(final Message message, final org.apache.camel.Message in) {
        setHeaders(in, message);

        final Map<String, Object> data = message.getDataAsMap();

        @SuppressWarnings("unchecked")
        final Map<String, Object> event = (Map<String, Object>) data.get(EVENT_PROPERTY);
        final Object eventType = event.get(TYPE_PROPERTY);
        final Object createdDate = event.get(CREATED_DATE_PROPERTY);
        final Object replayId = event.get(REPLAY_ID_PROPERTY);

        in.setHeader(SalesforceConstants.HEADER_SALESFORCE_TOPIC_NAME, topicName);
        in.setHeader(SalesforceConstants.HEADER_SALESFORCE_EVENT_TYPE, eventType);
        in.setHeader(SalesforceConstants.HEADER_SALESFORCE_CREATED_DATE, createdDate);
        if (replayId != null) {
            in.setHeader(SalesforceConstants.HEADER_SALESFORCE_REPLAY_ID, replayId);
        }

        // get SObject
        @SuppressWarnings("unchecked")
        final Map<String, Object> sObject = (Map<String, Object>) data.get(SOBJECT_PROPERTY);
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
            final String msg
                    = String.format("Error parsing message [%s] from Topic %s: %s", message, topicName, e.getMessage());
            handleException(msg, new SalesforceException(msg, e));
        }
    }

    void setHeaders(final org.apache.camel.Message in, final Message message) {
        in.setHeader(SalesforceConstants.HEADER_SALESFORCE_CHANNEL, message.getChannel());
        final String clientId = message.getClientId();
        if (ObjectHelper.isNotEmpty(clientId)) {
            in.setHeader(SalesforceConstants.HEADER_SALESFORCE_CLIENT_ID, clientId);
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        determineSObjectClass();
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

    /**
     * Stops this consumer.
     *
     * If alsoStopSubscription=true, any underlying subscriptions will be stopped as well. SubscriptionHelper also logs
     * out, so this will terminate the salesforce session as well.
     *
     * @param alsoStopSubscription to also stop subscription
     */
    public void stop(boolean alsoStopSubscription) {
        if (alsoStopSubscription) {
            LOG.info("Force stopping Consumer and SubscriptionHelper");
        }
        stop();
        if (alsoStopSubscription) {
            try {
                ServiceHelper.stopService(subscriptionHelper);
            } catch (Exception e) {
                LOG.warn("Failed to stop subscription due to: {}. This exception is ignored.", e.getMessage(), e);
            }
        }
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

    // May be necessary to call from some unit tests.
    void determineSObjectClass() {
        // get sObjectClass to convert to
        if (!rawPayload) {
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
        } else {
            sObjectClass = null;
        }
    }
}
