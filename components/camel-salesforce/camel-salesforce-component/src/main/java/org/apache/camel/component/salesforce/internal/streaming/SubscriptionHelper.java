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
package org.apache.camel.component.salesforce.internal.streaming;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import org.apache.camel.CamelException;
import org.apache.camel.component.salesforce.SalesforceComponent;
import org.apache.camel.component.salesforce.SalesforceConsumer;
import org.apache.camel.component.salesforce.internal.SalesforceSession;
import org.apache.camel.component.salesforce.internal.client.SalesforceSecurityListener;
import org.apache.camel.support.ServiceSupport;
import org.cometd.bayeux.Message;
import org.cometd.bayeux.client.ClientSessionChannel;
import org.cometd.client.BayeuxClient;
import org.cometd.client.transport.ClientTransport;
import org.cometd.client.transport.LongPollingTransport;
import org.eclipse.jetty.client.ContentExchange;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.http.HttpHeaders;
import org.eclipse.jetty.http.HttpSchemes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.cometd.bayeux.Channel.META_CONNECT;
import static org.cometd.bayeux.Channel.META_HANDSHAKE;
import static org.cometd.bayeux.Channel.META_SUBSCRIBE;
import static org.cometd.bayeux.Channel.META_UNSUBSCRIBE;
import static org.cometd.bayeux.Message.ERROR_FIELD;
import static org.cometd.bayeux.Message.SUBSCRIPTION_FIELD;

public class SubscriptionHelper extends ServiceSupport {

    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionHelper.class);

    private static final int CONNECT_TIMEOUT = 110;
    private static final int CHANNEL_TIMEOUT = 40;

    private static final String EXCEPTION_FIELD = "exception";

    private final SalesforceComponent component;
    private final SalesforceSession session;
    private final BayeuxClient client;
    private final long timeout = 60 * 1000L;

    private final Map<SalesforceConsumer, ClientSessionChannel.MessageListener> listenerMap;

    private ClientSessionChannel.MessageListener handshakeListener;
    private ClientSessionChannel.MessageListener connectListener;

    private String handshakeError;
    private Exception handshakeException;
    private String connectError;
    private boolean reconnecting;

    public SubscriptionHelper(SalesforceComponent component) throws Exception {
        this.component = component;
        this.session = component.getSession();

        this.listenerMap = new ConcurrentHashMap<SalesforceConsumer, ClientSessionChannel.MessageListener>();

        // create CometD client
        this.client = createClient();
    }

    @Override
    protected void doStart() throws Exception {
        // listener for handshake error or exception
        if (handshakeListener == null) {
            // first start
            handshakeListener = new ClientSessionChannel.MessageListener() {
                public void onMessage(ClientSessionChannel channel, Message message) {
                    LOG.debug("[CHANNEL:META_HANDSHAKE]: {}", message);

                    if (!message.isSuccessful()) {
                        String error = (String) message.get(ERROR_FIELD);
                        if (error != null) {
                            handshakeError = error;
                        }
                        Exception exception = (Exception) message.get(EXCEPTION_FIELD);
                        if (exception != null) {
                            handshakeException = exception;
                        }
                    } else if (!listenerMap.isEmpty()) {
                        reconnecting = true;
                    }
                }
            };
        }
        client.getChannel(META_HANDSHAKE).addListener(handshakeListener);

        // listener for connect error
        if (connectListener == null) {
            connectListener = new ClientSessionChannel.MessageListener() {
                public void onMessage(ClientSessionChannel channel, Message message) {
                    LOG.debug("[CHANNEL:META_CONNECT]: {}", message);

                    if (!message.isSuccessful()) {
                        String error = (String) message.get(ERROR_FIELD);
                        if (error != null) {
                            connectError = error;
                        }
                    } else if (reconnecting) {

                        reconnecting = false;

                        LOG.debug("Refreshing subscriptions to {} channels on reconnect", listenerMap.size());
                        // reconnected to Salesforce, subscribe to existing channels
                        final Map<SalesforceConsumer, ClientSessionChannel.MessageListener> map =
                                new HashMap<SalesforceConsumer, ClientSessionChannel.MessageListener>();
                        map.putAll(listenerMap);
                        listenerMap.clear();
                        for (Map.Entry<SalesforceConsumer, ClientSessionChannel.MessageListener> entry : map.entrySet()) {
                            final SalesforceConsumer consumer = entry.getKey();
                            final String topicName = consumer.getTopicName();
                            try {
                                subscribe(topicName, consumer);
                            } catch (CamelException e) {
                                // let the consumer handle the exception
                                consumer.handleException(
                                        String.format("Error refreshing subscription to topic [%s]: %s",
                                                topicName, e.getMessage()),
                                        e);
                            }
                        }

                    }
                }
            };
        }
        client.getChannel(META_CONNECT).addListener(connectListener);

        // connect to Salesforce cometd endpoint
        client.handshake();

        final long waitMs = MILLISECONDS.convert(CONNECT_TIMEOUT, SECONDS);
        if (!client.waitFor(waitMs, BayeuxClient.State.CONNECTED)) {
            if (handshakeException != null) {
                throw new CamelException(
                        String.format("Exception during HANDSHAKE: %s", handshakeException.getMessage()),
                        handshakeException);
            } else if (handshakeError != null) {
                throw new CamelException(String.format("Error during HANDSHAKE: %s", handshakeError));
            } else if (connectError != null) {
                throw new CamelException(String.format("Error during CONNECT: %s", connectError));
            } else {
                throw new CamelException(
                        String.format("Handshake request timeout after %s seconds", CONNECT_TIMEOUT));
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        client.getChannel(META_CONNECT).removeListener(connectListener);
        client.getChannel(META_HANDSHAKE).removeListener(handshakeListener);

        boolean disconnected = client.disconnect(timeout);
        if (!disconnected) {
            LOG.warn("Could not disconnect client connected to: {} after: {} msec.", getEndpointUrl(), timeout);
        }
    }

    private BayeuxClient createClient() throws Exception {
        // use default Jetty client from SalesforceComponent, its shared by all consumers
        final HttpClient httpClient = component.getConfig().getHttpClient();

        Map<String, Object> options = new HashMap<String, Object>();
        options.put(ClientTransport.TIMEOUT_OPTION, httpClient.getTimeout());

        // check login access token
        if (session.getAccessToken() == null) {
            // lazy login here!
            session.login(null);
        }

        LongPollingTransport transport = new LongPollingTransport(options, httpClient) {
            @Override
            protected void customize(ContentExchange exchange) {
                super.customize(exchange);
                // add SalesforceSecurityListener to handle token expiry
                final String accessToken = session.getAccessToken();
                try {
                    final boolean isHttps = HttpSchemes.HTTPS.equals(String.valueOf(exchange.getScheme()));
                    exchange.setEventListener(new SalesforceSecurityListener(
                            httpClient.getDestination(exchange.getAddress(), isHttps),
                            exchange, session, accessToken));
                } catch (IOException e) {
                    throw new RuntimeException(
                            String.format("Error adding SalesforceSecurityListener to exchange %s", e.getMessage()),
                            e);
                }

                // add current security token obtained from session
                exchange.setRequestHeader(HttpHeaders.AUTHORIZATION,
                        "OAuth " + accessToken);
            }
        };

        BayeuxClient client = new BayeuxClient(getEndpointUrl(), transport);
        client.setDebugEnabled(false);
        return client;
    }

    public void subscribe(final String topicName, final SalesforceConsumer consumer) throws CamelException {
        // create subscription for consumer
        final String channelName = getChannelName(topicName);

        // channel message listener
        LOG.info("Subscribing to channel {}...", channelName);
        final ClientSessionChannel.MessageListener listener = new ClientSessionChannel.MessageListener() {

            @Override
            public void onMessage(ClientSessionChannel channel, Message message) {
                LOG.debug("Received Message: {}", message);
                // convert CometD message to Camel Message
                consumer.processMessage(channel, message);
            }

        };

        final ClientSessionChannel clientChannel = client.getChannel(channelName);

        // listener for subscribe error
        final CountDownLatch latch = new CountDownLatch(1);
        final String[] subscribeError = {null};
        final ClientSessionChannel.MessageListener subscriptionListener = new ClientSessionChannel.MessageListener() {
            public void onMessage(ClientSessionChannel channel, Message message) {
                LOG.debug("[CHANNEL:META_SUBSCRIBE]: {}", message);
                final String subscribedChannelName = message.get(SUBSCRIPTION_FIELD).toString();
                if (channelName.equals(subscribedChannelName)) {

                    if (!message.isSuccessful()) {
                        String error = (String) message.get(ERROR_FIELD);
                        if (error != null) {
                            subscribeError[0] = error;
                        }
                    } else {
                        // remember subscription
                        LOG.info("Subscribed to channel {}", subscribedChannelName);
                    }
                    latch.countDown();
                }
            }
        };
        client.getChannel(META_SUBSCRIBE).addListener(subscriptionListener);

        try {
            clientChannel.subscribe(listener);

            // confirm that a subscription was created
            try {
                if (!latch.await(CHANNEL_TIMEOUT, SECONDS)) {
                    String message;
                    if (subscribeError[0] != null) {
                        message = String.format("Error subscribing to topic %s: %s",
                                topicName, subscribeError[0]);
                    } else {
                        message = String.format("Timeout error subscribing to topic %s after %s seconds",
                                topicName, CHANNEL_TIMEOUT);
                    }
                    throw new CamelException(message);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // probably shutting down, so forget subscription
            }

            listenerMap.put(consumer, listener);

        } finally {
            client.getChannel(META_SUBSCRIBE).removeListener(subscriptionListener);
        }
    }

    private String getChannelName(String topicName) {
        return "/topic/" + topicName;
    }

    public void unsubscribe(String topicName, SalesforceConsumer consumer) throws CamelException {

        // channel name
        final String channelName = getChannelName(topicName);

        // listen for unsubscribe error
        final CountDownLatch latch = new CountDownLatch(1);
        final String[] unsubscribeError = {null};
        final ClientSessionChannel.MessageListener unsubscribeListener = new ClientSessionChannel.MessageListener() {
            public void onMessage(ClientSessionChannel channel, Message message) {
                LOG.debug("[CHANNEL:META_UNSUBSCRIBE]: {}", message);
                String unsubscribedChannelName = message.get(SUBSCRIPTION_FIELD).toString();
                if (channelName.equals(unsubscribedChannelName)) {

                    if (!message.isSuccessful()) {
                        String error = (String) message.get(ERROR_FIELD);
                        if (error != null) {
                            unsubscribeError[0] = error;
                        }
                    } else {
                        // forget subscription
                        LOG.info("Unsubscribed from channel {}", unsubscribedChannelName);
                    }
                    latch.countDown();
                }
            }
        };
        client.getChannel(META_UNSUBSCRIBE).addListener(unsubscribeListener);

        try {
            // unsubscribe from channel
            final ClientSessionChannel.MessageListener listener = listenerMap.remove(consumer);
            if (listener != null) {

                LOG.info("Unsubscribing from channel {}...", channelName);
                final ClientSessionChannel clientChannel = client.getChannel(channelName);
                clientChannel.unsubscribe(listener);

                // confirm unsubscribe
                try {
                    if (!latch.await(CHANNEL_TIMEOUT, SECONDS)) {
                        String message;
                        if (unsubscribeError[0] != null) {
                            message = String.format("Error unsubscribing from topic %s: %s",
                                    topicName, unsubscribeError[0]);
                        } else {
                            message = String.format("Timeout error unsubscribing from topic %s after %s seconds",
                                    topicName, CHANNEL_TIMEOUT);
                        }
                        throw new CamelException(message);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    // probably shutting down, forget unsubscribe and return
                }

            }
        } finally {
            client.getChannel(META_UNSUBSCRIBE).removeListener(unsubscribeListener);
        }
    }

    public String getEndpointUrl() {
        return component.getSession().getInstanceUrl() + "/cometd/" + component.getConfig().getApiVersion();
    }

}
