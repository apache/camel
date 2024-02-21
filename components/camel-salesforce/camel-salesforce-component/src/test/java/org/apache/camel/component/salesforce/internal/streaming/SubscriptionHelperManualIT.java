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
package org.apache.camel.component.salesforce.internal.streaming;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelException;
import org.apache.camel.component.salesforce.AuthenticationType;
import org.apache.camel.component.salesforce.SalesforceComponent;
import org.apache.camel.component.salesforce.SalesforceEndpoint;
import org.apache.camel.component.salesforce.SalesforceEndpointConfig;
import org.apache.camel.component.salesforce.StreamingApiConsumer;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.impl.DefaultCamelContext;
import org.cometd.bayeux.Message;
import org.cometd.bayeux.client.ClientSessionChannel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.salesforce.internal.streaming.SubscriptionHelperManualIT.MessageArgumentMatcher.messageForAccountCreationWithName;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@TestInstance(Lifecycle.PER_CLASS)
public class SubscriptionHelperManualIT {

    final CamelContext camel;
    final SalesforceEndpointConfig config = new SalesforceEndpointConfig();
    final BlockingQueue<String> messages = new LinkedBlockingDeque<>();
    final SalesforceComponent salesforce;
    final StubServer server;
    final SubscriptionHelper subscription;

    StreamingApiConsumer toUnsubscribe;

    static class MessageArgumentMatcher implements ArgumentMatcher<Message> {

        private final String name;

        public MessageArgumentMatcher(final String name) {
            this.name = name;
        }

        @Override
        public boolean matches(final Message message) {
            final Map<String, Object> data = message.getDataAsMap();
            @SuppressWarnings("unchecked")
            final Map<String, Object> event = (Map<String, Object>) data.get("event");
            @SuppressWarnings("unchecked")
            final Map<String, Object> sobject = (Map<String, Object>) data.get("sobject");
            return "created".equals(event.get("type")) && name.equals(sobject.get("Name"));
        }

        static Message messageForAccountCreationWithName(final String name) {
            return argThat(new MessageArgumentMatcher(name));
        }
    }

    public SubscriptionHelperManualIT() throws SalesforceException {
        server = new StubServer();
        LoggerFactory.getLogger(SubscriptionHelperManualIT.class).info("Port for wireshark to filter: {}",
                server.port());
        final String instanceUrl = "http://localhost:" + server.port();
        server.replyTo(
                "POST", "/services/oauth2/token",
                "{\n" +
                                                  "    \"instance_url\": \"" + instanceUrl + "\",\n" +
                                                  "    \"access_token\": \"00D4100000xxxxx!faketoken\",\n" +
                                                  "    \"id\": \"https://login.salesforce.com/id/00D4100000xxxxxxxx/0054100000xxxxxxxx\"\n"
                                                  +
                                                  "}");

        server.replyTo("GET", "/services/oauth2/revoke?token=token", 200);

        server.replyTo(
                "POST", "/cometd/" + SalesforceEndpointConfig.DEFAULT_VERSION + "/handshake",
                "[\n"
                                                                                              + "  {\n"
                                                                                              + "    \"ext\": {\n"
                                                                                              + "      \"replay\": true,\n"
                                                                                              + "      \"payload.format\": true\n"
                                                                                              + "    },\n"
                                                                                              + "    \"minimumVersion\": \"1.0\",\n"
                                                                                              + "    \"clientId\": \"5ra4927ikfky6cb12juthkpofeu8\",\n"
                                                                                              + "    \"supportedConnectionTypes\": [\n"
                                                                                              + "      \"long-polling\"\n"
                                                                                              + "    ],\n"
                                                                                              + "    \"channel\": \"/meta/handshake\",\n"
                                                                                              + "    \"id\": \"$id\",\n"
                                                                                              + "    \"version\": \"1.0\",\n"
                                                                                              + "    \"successful\": true\n"
                                                                                              + "  }\n"
                                                                                              + "]");

        server.replyTo("POST", "/cometd/" + SalesforceEndpointConfig.DEFAULT_VERSION + "/connect",
                req -> req.contains("\"timeout\":0"), "[\n"
                                                      + "  {\n"
                                                      + "    \"clientId\": \"1f0agp5a95yiaeb1kifib37r5z4g\",\n"
                                                      + "    \"advice\": {\n"
                                                      + "      \"interval\": 0,\n"
                                                      + "      \"timeout\": 110000,\n"
                                                      + "      \"reconnect\": \"retry\"\n"
                                                      + "    },\n"
                                                      + "    \"channel\": \"/meta/connect\",\n"
                                                      + "    \"id\": \"$id\",\n"
                                                      + "    \"successful\": true\n"
                                                      + "  }\n"
                                                      + "]");

        server.replyTo("POST", "/cometd/" + SalesforceEndpointConfig.DEFAULT_VERSION + "/connect", messages);

        server.replyTo("POST", "/cometd/" + SalesforceEndpointConfig.DEFAULT_VERSION + "/subscribe", "[\n"
                                                                                                     + "  {\n"
                                                                                                     + "    \"clientId\": \"5ra4927ikfky6cb12juthkpofeu8\",\n"
                                                                                                     + "    \"channel\": \"/meta/subscribe\",\n"
                                                                                                     + "    \"id\": \"$id\",\n"
                                                                                                     + "    \"subscription\": \"/topic/Account\",\n"
                                                                                                     + "    \"successful\": true\n"
                                                                                                     + "  }\n"
                                                                                                     + "]");

        server.replyTo("POST", "/cometd/" + SalesforceEndpointConfig.DEFAULT_VERSION + "/unsubscribe", "[\n"
                                                                                                       + "  {\n"
                                                                                                       + "    \"clientId\": \"5ra4927ikfky6cb12juthkpofeu8\",\n"
                                                                                                       + "    \"channel\": \"/meta/unsubscribe\",\n"
                                                                                                       + "    \"id\": \"$id\",\n"
                                                                                                       + "    \"subscription\": \"/topic/Account\",\n"
                                                                                                       + "    \"successful\": true\n"
                                                                                                       + "  }\n"
                                                                                                       + "]");

        server.replyTo("POST", "/cometd/" + SalesforceEndpointConfig.DEFAULT_VERSION + "/disconnect", "[\n"
                                                                                                      + "  {\n"
                                                                                                      + "     \"channel\": \"/meta/disconnect\",\n"
                                                                                                      + "     \"clientId\": \"client-id\"\n"
                                                                                                      + "   }\n"
                                                                                                      + "]");

        server.replyTo("GET", "/services/oauth2/revoke", 200);

        server.stubsAsDefaults();

        camel = new DefaultCamelContext();
        camel.start();
        salesforce = new SalesforceComponent(camel);
        salesforce.setLoginUrl(instanceUrl);
        salesforce.setClientId("clientId");
        salesforce.setClientSecret("clientSecret");
        salesforce.setRefreshToken("refreshToken");
        salesforce.setAuthenticationType(AuthenticationType.REFRESH_TOKEN);
        salesforce.setConfig(config);

        salesforce.start();
        subscription = new SubscriptionHelper(salesforce);
    }

    @BeforeEach
    public void cleanSlate() throws CamelException {
        if (toUnsubscribe != null) {
            subscription.unsubscribe("Account", toUnsubscribe);
        }
        server.reset();
    }

    @AfterAll
    public void stop() {
        salesforce.stop();
        camel.stop();
        server.stop();
    }

    @Test
    void shouldResubscribeOnConnectionFailures() throws InterruptedException {
        // handshake and connect
        subscription.start();

        final StreamingApiConsumer consumer
                = toUnsubscribe = mock(StreamingApiConsumer.class, "shouldResubscribeOnConnectionFailures:consumer");

        final SalesforceEndpoint endpoint = mock(SalesforceEndpoint.class, "shouldResubscribeOnConnectionFailures:endpoint");

        // subscribe
        when(consumer.getTopicName()).thenReturn("Account");

        when(consumer.getEndpoint()).thenReturn(endpoint);
        when(endpoint.getConfiguration()).thenReturn(config);
        when(endpoint.getComponent()).thenReturn(salesforce);
        when(endpoint.getTopicName()).thenReturn("Account");

        subscription.subscribe("Account", consumer);

        // push one message so we know connection is established and consumer
        // receives notifications
        messages.add("[\n"
                     + "  {\n"
                     + "    \"data\": {\n"
                     + "      \"event\": {\n"
                     + "        \"createdDate\": \"2020-12-11T13:44:56.891Z\",\n"
                     + "        \"replayId\": 1,\n"
                     + "        \"type\": \"created\"\n"
                     + "      },\n"
                     + "      \"sobject\": {\n"
                     + "        \"Id\": \"0011n00002XWMgVAAX\",\n"
                     + "        \"Name\": \"shouldResubscribeOnConnectionFailures 1\"\n"
                     + "      }\n"
                     + "    },\n"
                     + "    \"channel\": \"/topic/Account\"\n"
                     + "  },\n"
                     + "  {\n"
                     + "    \"clientId\": \"5ra4927ikfky6cb12juthkpofeu8\",\n"
                     + "    \"channel\": \"/meta/connect\",\n"
                     + "    \"id\": \"$id\",\n"
                     + "    \"successful\": true\n"
                     + "  }\n"
                     + "]");

        verify(consumer, Mockito.timeout(100)).processMessage(any(ClientSessionChannel.class),
                messageForAccountCreationWithName("shouldResubscribeOnConnectionFailures 1"));

        // send failed connection message w/o reconnect advice so we handshake again

        messages.add("[\n" +
                     "  {\n" +
                     "    \"clientId\": \"5ra4927ikfky6cb12juthkpofeu8\",\n" +
                     "    \"channel\": \"/meta/connect\",\n" +
                     "    \"id\": \"$id\",\n" +
                     "    \"successful\": false,\n" +
                     "    \"advice\": {\n" +
                     "       \"reconnect\": \"none\"\n" +
                     "    }\n" +
                     "  }\n" +
                     "]");

        // queue next message for when the client recovers
        messages.add("[\n"
                     + "  {\n"
                     + "    \"data\": {\n"
                     + "      \"event\": {\n"
                     + "        \"createdDate\": \"2020-12-11T13:44:56.891Z\",\n"
                     + "        \"replayId\": 2,\n"
                     + "        \"type\": \"created\"\n"
                     + "      },\n"
                     + "      \"sobject\": {\n"
                     + "        \"Id\": \"0011n00002XWMgVAAX\",\n"
                     + "        \"Name\": \"shouldResubscribeOnConnectionFailures 2\"\n"
                     + "      }\n"
                     + "    },\n"
                     + "    \"channel\": \"/topic/Account\"\n"
                     + "  },\n"
                     + "  {\n"
                     + "    \"clientId\": \"5ra4927ikfky6cb12juthkpofeu8\",\n"
                     + "    \"channel\": \"/meta/connect\",\n"
                     + "    \"id\": \"$id\",\n"
                     + "    \"successful\": true\n"
                     + "  }\n"
                     + "]");

        // assert last message was received, recovery can take a bit
        verify(consumer, timeout(10000)).processMessage(any(ClientSessionChannel.class),
                messageForAccountCreationWithName("shouldResubscribeOnConnectionFailures 2"));

        verify(consumer, atLeastOnce()).getEndpoint();
        verify(consumer, atLeastOnce()).getTopicName();
        verifyNoMoreInteractions(consumer);
    }

    @Test
    void shouldResubscribeOnHelperRestart() {
        // handshake and connect
        subscription.start();

        final StreamingApiConsumer consumer
                = toUnsubscribe = mock(StreamingApiConsumer.class, "shouldResubscribeOnHelperRestart:consumer");

        final SalesforceEndpoint endpoint = mock(SalesforceEndpoint.class, "shouldResubscribeOnHelperRestart:endpoint");

        // subscribe
        when(consumer.getTopicName()).thenReturn("Account");

        when(consumer.getEndpoint()).thenReturn(endpoint);
        when(endpoint.getConfiguration()).thenReturn(config);
        when(endpoint.getComponent()).thenReturn(salesforce);
        when(endpoint.getTopicName()).thenReturn("Account");

        subscription.subscribe("Account", consumer);

        // push one message so we know connection is established and consumer
        // receives notifications
        messages.add("[\n"
                     + "  {\n"
                     + "    \"data\": {\n"
                     + "      \"event\": {\n"
                     + "        \"createdDate\": \"2020-12-11T13:44:56.891Z\",\n"
                     + "        \"replayId\": 1,\n"
                     + "        \"type\": \"created\"\n"
                     + "      },\n"
                     + "      \"sobject\": {\n"
                     + "        \"Id\": \"0011n00002XWMgVAAX\",\n"
                     + "        \"Name\": \"shouldResubscribeOnHelperRestart 1\"\n"
                     + "      }\n"
                     + "    },\n"
                     + "    \"channel\": \"/topic/Account\"\n"
                     + "  },\n"
                     + "  {\n"
                     + "    \"clientId\": \"5ra4927ikfky6cb12juthkpofeu8\",\n"
                     + "    \"channel\": \"/meta/connect\",\n"
                     + "    \"id\": \"$id\",\n"
                     + "    \"successful\": true\n"
                     + "  }\n"
                     + "]");
        verify(consumer, timeout(100)).processMessage(any(ClientSessionChannel.class),
                messageForAccountCreationWithName("shouldResubscribeOnHelperRestart 1"));

        // stop and start the subscription helper
        subscription.stop();
        subscription.start();

        // queue next message for when the client recovers
        messages.add("[\n"
                     + "  {\n"
                     + "    \"data\": {\n"
                     + "      \"event\": {\n"
                     + "        \"createdDate\": \"2020-12-11T13:44:56.891Z\",\n"
                     + "        \"replayId\": 2,\n"
                     + "        \"type\": \"created\"\n"
                     + "      },\n"
                     + "      \"sobject\": {\n"
                     + "        \"Id\": \"0011n00002XWMgVAAX\",\n"
                     + "        \"Name\": \"shouldResubscribeOnHelperRestart 2\"\n"
                     + "      }\n"
                     + "    },\n"
                     + "    \"channel\": \"/topic/Account\"\n"
                     + "  },\n"
                     + "  {\n"
                     + "    \"clientId\": \"5ra4927ikfky6cb12juthkpofeu8\",\n"
                     + "    \"channel\": \"/meta/connect\",\n"
                     + "    \"id\": \"$id\",\n"
                     + "    \"successful\": true\n"
                     + "  }\n"
                     + "]");

        // assert last message was received, recovery can take a bit
        verify(consumer, timeout(2000)).processMessage(any(ClientSessionChannel.class),
                messageForAccountCreationWithName("shouldResubscribeOnHelperRestart 2"));

        verify(consumer, atLeastOnce()).getEndpoint();
        verify(consumer, atLeastOnce()).getTopicName();
        verifyNoMoreInteractions(consumer);
    }
}
