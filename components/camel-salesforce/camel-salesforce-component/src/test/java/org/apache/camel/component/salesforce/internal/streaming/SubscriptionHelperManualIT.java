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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.camel.component.salesforce.internal.streaming.SubscriptionHelperManualIT.MessageArgumentMatcher.messageWithName;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.cometd.bayeux.client.ClientSessionChannel.MessageListener;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

@TestInstance(Lifecycle.PER_CLASS)
public class SubscriptionHelperManualIT {

    final CamelContext camel;
    final SalesforceEndpointConfig config = new SalesforceEndpointConfig();
    final BlockingQueue<String> messages = new LinkedBlockingDeque<>();
    final SalesforceComponent salesforce;
    final StubServer server;
    private SubscriptionHelper subscription;

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

        static Message messageWithName(final String name) {
            return argThat(new MessageArgumentMatcher(name));
        }
    }

    public SubscriptionHelperManualIT() throws SalesforceException {
        server = new StubServer();
        LoggerFactory.getLogger(SubscriptionHelperManualIT.class)
                .info("Port for wireshark to filter: {}", server.port());
        final String instanceUrl = "http://localhost:" + server.port();
        server.replyTo(
                "POST",
                "/services/oauth2/token",
                "{\n" + "    \"instance_url\": \""
                        + instanceUrl + "\",\n" + "    \"access_token\": \"00D4100000xxxxx!faketoken\",\n"
                        + "    \"id\": \"https://login.salesforce.com/id/00D4100000xxxxxxxx/0054100000xxxxxxxx\"\n"
                        + "}");

        server.replyTo("GET", "/services/oauth2/revoke?token=token", 200);

        server.replyTo(
                "POST",
                "/cometd/" + SalesforceEndpointConfig.DEFAULT_VERSION + "/handshake",
                """
                        [
                          {
                            "ext": {
                              "replay": true,
                              "payload.format": true
                            },
                            "minimumVersion": "1.0",
                            "clientId": "5ra4927ikfky6cb12juthkpofeu8",
                            "supportedConnectionTypes": [
                              "long-polling"
                            ],
                            "channel": "/meta/handshake",
                            "id": "$id",
                            "version": "1.0",
                            "successful": true
                          }
                        ]""");

        server.replyTo(
                "POST",
                "/cometd/" + SalesforceEndpointConfig.DEFAULT_VERSION + "/connect",
                req -> {
                    return req.contains("\"timeout\":0");
                },
                """
                        [
                          {
                            "clientId": "1f0agp5a95yiaeb1kifib37r5z4g",
                            "advice": {
                              "interval": 0,
                              "timeout": 110000,
                              "reconnect": "retry"
                            },
                            "channel": "/meta/connect",
                            "id": "$id",
                            "successful": true
                          }
                        ]""");

        server.replyTo("POST", "/cometd/" + SalesforceEndpointConfig.DEFAULT_VERSION + "/connect", messages);

        server.replyTo(
                "POST",
                "/cometd/" + SalesforceEndpointConfig.DEFAULT_VERSION + "/disconnect",
                """
                        [
                          {
                             "channel": "/meta/disconnect",
                             "clientId": "client-id"
                           }
                        ]""");

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
        subscription.start();
    }

    @BeforeEach
    public void resetServer() throws CamelException {
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
        var consumer = createConsumer("Account");
        subscription.subscribe(consumer);

        messages.add(
                """
                [
                  {
                    "data": {
                      "event": {
                        "createdDate": "2020-12-11T13:44:56.891Z",
                        "replayId": 1,
                        "type": "created"
                      },
                      "sobject": {
                        "Id": "0011n00002XWMgVAAX",
                        "Name": "shouldResubscribeOnConnectionFailures 1"
                      }
                    },
                    "channel": "/topic/Account"
                  },
                  {
                    "clientId": "5ra4927ikfky6cb12juthkpofeu8",
                    "channel": "/meta/connect",
                    "id": "$id",
                    "successful": true
                  }
                ]""");
        verify(consumer, Mockito.timeout(10000))
                .processMessage(
                        any(ClientSessionChannel.class), messageWithName("shouldResubscribeOnConnectionFailures 1"));

        subscription.client.getChannel("/meta/subscribe").addListener((MessageListener)
                (clientSessionChannel, message) -> {
                    var subscription = (String) message.get("subscription");
                    if (subscription != null && subscription.contains("Account")) {
                        messages.add(
                                """
                                [
                                  {
                                    "data": {
                                      "event": {
                                        "createdDate": "2020-12-11T13:44:57.891Z",
                                        "replayId": 2,
                                        "type": "created"
                                      },
                                      "sobject": {
                                        "Id": "0011n00002XWMgVAAX",
                                        "Name": "shouldResubscribeOnConnectionFailures 2"
                                      }
                                    },
                                    "channel": "/topic/Account"
                                  },
                                  {
                                    "clientId": "5ra4927ikfky6cb12juthkpofeu8",
                                    "channel": "/meta/connect",
                                    "id": "$id",
                                    "successful": true
                                  }
                                ]""");
                    }
                });
        subscription.client.disconnect(10000);
        messages.add(
                """
                [
                  {
                    "clientId": "5ra4927ikfky6cb12juthkpofeu8",
                    "channel": "/meta/connect",
                    "id": "$id",
                    "successful": false,
                    "advice": {
                       "reconnect": "none"
                    }
                  }
                ]""");

        verify(consumer, timeout(20000))
                .processMessage(
                        any(ClientSessionChannel.class), messageWithName("shouldResubscribeOnConnectionFailures 2"));

        verify(consumer, atLeastOnce()).getEndpoint();
        verify(consumer, atLeastOnce()).getTopicName();
        verifyNoMoreInteractions(consumer);
    }

    @Test
    void shouldResubscribeOnSubscriptionFailure() {
        var consumer = createConsumer("Contact");
        var subscribeAttempts = new AtomicInteger(0);
        subscription.client.getChannel("/meta/subscribe").addListener((MessageListener)
                (clientSessionChannel, message) -> {
                    var subscription = (String) message.get("subscription");
                    if (subscription != null && subscription.contains("Contact")) {
                        subscribeAttempts.incrementAndGet();
                    }
                });

        server.reset();
        server.replyTo(
                "POST",
                "/cometd/" + SalesforceEndpointConfig.DEFAULT_VERSION + "/subscribe",
                s -> s.contains("/topic/Contact"),
                """
                        [
                          {
                            "clientId": "5ra4927ikfky6cb12juthkpofeu8aaa",
                            "channel": "/meta/subscribe",
                            "id": "$id",
                            "subscription": "/topic/Contact",
                            "successful": false
                          }
                        ]""");
        subscription.subscribe(consumer);
        await().atMost(10, SECONDS).until(() -> subscribeAttempts.get() == 1);
        assertThat(subscription.client.getChannel("/topic/Contact").getSubscribers(), hasSize(0));

        server.reset();
        server.replyTo(
                "POST",
                "/cometd/" + SalesforceEndpointConfig.DEFAULT_VERSION + "/subscribe",
                s -> s.contains("/topic/Contact"),
                """
                        [
                          {
                            "clientId": "5ra4927ikfky6cb12juthkpofeu8bbb",
                            "channel": "/meta/subscribe",
                            "id": "$id",
                            "subscription": "/topic/Contact",
                            "successful": true
                          }
                        ]""");
        messages.add(
                """
                [
                  {
                    "clientId": "5ra4927ikfky6cb12juthkpofeu8",
                    "channel": "/meta/connect",
                    "id": "$id",
                    "successful": true
                  }
                ]""");
        await().atMost(10, SECONDS).until(() -> subscribeAttempts.get() == 2);
        assertThat(subscription.client.getChannel("/topic/Contact").getSubscribers(), hasSize(1));
    }

    @Test
    void shouldResubscribeOnHelperRestart() {
        var consumer = createConsumer("Person");
        subscription.subscribe(consumer);

        messages.add(
                """
                [
                  {
                    "data": {
                      "event": {
                        "createdDate": "2020-12-11T13:44:56.891Z",
                        "replayId": 1,
                        "type": "created"
                      },
                      "sobject": {
                        "Id": "0011n00002XWMgVAAX",
                        "Name": "shouldResubscribeOnHelperRestart 1"
                      }
                    },
                    "channel": "/topic/Person"
                  },
                  {
                    "clientId": "5ra4927ikfky6cb12juthkpofeu8",
                    "channel": "/meta/connect",
                    "id": "$id",
                    "successful": true
                  }
                ]""");
        verify(consumer, timeout(10000))
                .processMessage(any(ClientSessionChannel.class), messageWithName("shouldResubscribeOnHelperRestart 1"));

        // stop and start the subscription helper
        subscription.stop();
        subscription.start();

        // queue next message for when the client recovers
        messages.add(
                """
                [
                  {
                    "data": {
                      "event": {
                        "createdDate": "2020-12-11T13:44:56.891Z",
                        "replayId": 2,
                        "type": "created"
                      },
                      "sobject": {
                        "Id": "0011n00002XWMgVAAX",
                        "Name": "shouldResubscribeOnHelperRestart 2"
                      }
                    },
                    "channel": "/topic/Person"
                  },
                  {
                    "clientId": "5ra4927ikfky6cb12juthkpofeu8",
                    "channel": "/meta/connect",
                    "id": "$id",
                    "successful": true
                  }
                ]""");

        // assert last message was received, recovery can take a bit
        verify(consumer, timeout(10000))
                .processMessage(any(ClientSessionChannel.class), messageWithName("shouldResubscribeOnHelperRestart 2"));

        verify(consumer, atLeastOnce()).getEndpoint();
        verify(consumer, atLeastOnce()).getTopicName();
        verifyNoMoreInteractions(consumer);
    }

    private StreamingApiConsumer createConsumer(String topic) {
        var endpoint = createAccountEndpoint(topic);
        var consumer = mock(StreamingApiConsumer.class, topic + ":consumer");
        when(consumer.getTopicName()).thenReturn(topic);
        when(consumer.getEndpoint()).thenReturn(endpoint);
        server.replyTo(
                "POST",
                "/cometd/" + SalesforceEndpointConfig.DEFAULT_VERSION + "/subscribe",
                s -> s.contains("\"subscription\":\"/topic/" + topic + "\""),
                """
                        [
                          {
                            "clientId": "5ra4927ikfky6cb12juthkpofeu8qqq",
                            "channel": "/meta/subscribe",
                            "id": "$id",
                            "subscription": "/topic/"""
                        + topic + "\","
                        + """
                            "successful": true
                          }
                        ]""");

        server.replyTo(
                "POST",
                "/cometd/" + SalesforceEndpointConfig.DEFAULT_VERSION + "/unsubscribe",
                s -> s.contains("\"subscription\":\"/topic/" + topic + "\""),
                """
                        [
                          {
                            "clientId": "5ra4927ikfky6cb12juthkpofeu8",
                            "channel": "/meta/unsubscribe",
                            "id": "$id",
                            "subscription": "/topic/"""
                        + topic + "\","
                        + """
                            "successful": true
                          }
                        ]""");
        return consumer;
    }

    private SalesforceEndpoint createAccountEndpoint(String topic) {
        SalesforceEndpoint endpoint = mock(SalesforceEndpoint.class, topic + ":endpoint");
        when(endpoint.getConfiguration()).thenReturn(config);
        when(endpoint.getComponent()).thenReturn(salesforce);
        when(endpoint.getTopicName()).thenReturn(topic);
        return endpoint;
    }
}
