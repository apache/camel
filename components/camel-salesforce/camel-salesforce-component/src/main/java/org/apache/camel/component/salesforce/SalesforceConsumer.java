/**
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
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.apache.camel.component.salesforce.internal.client.RestClient;
import org.apache.camel.component.salesforce.internal.streaming.PushTopicHelper;
import org.apache.camel.component.salesforce.internal.streaming.SubscriptionHelper;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.util.ServiceHelper;
import org.cometd.bayeux.Message;
import org.cometd.bayeux.client.ClientSessionChannel;

/**
 * The Salesforce consumer.
 */
public class SalesforceConsumer extends DefaultConsumer {


    private static final ObjectMapper OBJECT_MAPPER = JsonUtils.createObjectMapper();
    private static final String EVENT_PROPERTY = "event";
    private static final String TYPE_PROPERTY = "type";
    private static final String CREATED_DATE_PROPERTY = "createdDate";
    private static final String SOBJECT_PROPERTY = "sobject";
    private static final String REPLAY_ID_PROPERTY = "replayId";
    private static final double MINIMUM_VERSION = 24.0;

    private final SalesforceEndpoint endpoint;
    private final SubscriptionHelper subscriptionHelper;
    private final ObjectMapper objectMapper;

    private final String topicName;
    private final Class<?> sObjectClass;
    private boolean subscribed;


    public SalesforceConsumer(SalesforceEndpoint endpoint, Processor processor, SubscriptionHelper helper) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        ObjectMapper configuredObjectMapper = endpoint.getConfiguration().getObjectMapper();
        if (configuredObjectMapper != null) {
            this.objectMapper = configuredObjectMapper;
        } else {
            this.objectMapper = OBJECT_MAPPER;
        }

        // check minimum supported API version
        if (Double.valueOf(endpoint.getConfiguration().getApiVersion()) < MINIMUM_VERSION) {
            throw new IllegalArgumentException("Minimum supported API version for consumer endpoints is " + 24.0);
        }

        this.topicName = endpoint.getTopicName();
        this.subscriptionHelper = helper;

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
                log.warn("Property sObjectName or sObjectClass NOT set, messages will be of type java.lang.Map");
                sObjectClass = null;
            }
        }

    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        final SalesforceEndpointConfig config = endpoint.getConfiguration();

        // is a query configured in the endpoint?
        if (config.getSObjectQuery() != null) {
            // Note that we don't lookup topic if the query is not specified
            // create REST client for PushTopic operations
            final SalesforceComponent salesforceComponent = endpoint.getComponent();
            final RestClient restClient = salesforceComponent.createRestClientFor(endpoint);

            // don't forget to start the client
            ServiceHelper.startService(restClient);

            try {
                PushTopicHelper helper = new PushTopicHelper(config, topicName, restClient);
                helper.createOrUpdateTopic();
            } finally {
                // don't forget to stop the client
                ServiceHelper.stopService(restClient);
            }
        }

        // subscribe to topic
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

    public void processMessage(ClientSessionChannel channel, Message message) {
        final Exchange exchange = endpoint.createExchange();
        org.apache.camel.Message in = exchange.getIn();
        setHeaders(in, message);

        // get event data
        // TODO do we need to add NPE checks for message/data.get***???
        Map<String, Object> data = message.getDataAsMap();

        @SuppressWarnings("unchecked")
        final Map<String, Object> event = (Map<String, Object>) data.get(EVENT_PROPERTY);
        final Object eventType = event.get(TYPE_PROPERTY);
        Object createdDate = event.get(CREATED_DATE_PROPERTY);
        Object replayId = event.get(REPLAY_ID_PROPERTY);
        if (log.isDebugEnabled()) {
            log.debug(String.format("Received event %s on channel %s created on %s",
                    eventType, channel.getChannelId(), createdDate));
        }

        in.setHeader("CamelSalesforceEventType", eventType);
        in.setHeader("CamelSalesforceCreatedDate", createdDate);
        if (replayId != null) {
            in.setHeader("CamelSalesforceReplayId", replayId);
        }

        // get SObject
        @SuppressWarnings("unchecked")
        final Map<String, Object> sObject = (Map<String, Object>) data.get(SOBJECT_PROPERTY);
        try {

            final String sObjectString = objectMapper.writeValueAsString(sObject);
            log.debug("Received SObject: {}", sObjectString);

            if (endpoint.getConfiguration().getRawPayload()) {
                // return sobject string as exchange body
                in.setBody(sObjectString);
            } else if (sObjectClass == null) {
                // return sobject map as exchange body
                in.setBody(sObject);
            } else {
                // create the expected SObject
                in.setBody(objectMapper.readValue(
                        new StringReader(sObjectString), sObjectClass));
            }
        } catch (IOException e) {
            final String msg = String.format("Error parsing message [%s] from Topic %s: %s",
                    message, topicName, e.getMessage());
            handleException(msg, new SalesforceException(msg, e));
        }

        try {
            getAsyncProcessor().process(exchange, new AsyncCallback() {
                public void done(boolean doneSync) {
                    // noop
                    if (log.isTraceEnabled()) {
                        log.trace("Done processing event: {} {}", eventType.toString(),
                                doneSync ? "synchronously" : "asynchronously");
                    }
                }
            });
        } catch (Exception e) {
            String msg = String.format("Error processing %s: %s", exchange, e);
            handleException(msg, new SalesforceException(msg, e));
        } finally {
            Exception ex = exchange.getException();
            if (ex != null) {
                String msg = String.format("Unhandled exception: %s", ex.getMessage());
                handleException(msg, new SalesforceException(msg, ex));
            }
        }
    }

    private void setHeaders(org.apache.camel.Message in, Message message) {
        Map<String, Object> headers = new HashMap<String, Object>();
        // set topic name
        headers.put("CamelSalesforceTopicName", topicName);
        // set message properties as headers
        headers.put("CamelSalesforceChannel", message.getChannel());
        headers.put("CamelSalesforceClientId", message.getClientId());

        in.setHeaders(headers);
    }

    @Override
    public void handleException(String message, Throwable t) {
        super.handleException(message, t);
    }

    public String getTopicName() {
        return topicName;
    }

}
