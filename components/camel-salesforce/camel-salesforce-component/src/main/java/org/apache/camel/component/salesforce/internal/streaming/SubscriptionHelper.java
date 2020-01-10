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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cometd.bayeux.Channel.META_CONNECT;
import static org.cometd.bayeux.Channel.META_DISCONNECT;
import static org.cometd.bayeux.Channel.META_HANDSHAKE;
import static org.cometd.bayeux.Channel.META_SUBSCRIBE;
import static org.cometd.bayeux.Channel.META_UNSUBSCRIBE;
import static org.cometd.bayeux.Message.ERROR_FIELD;
import static org.cometd.bayeux.Message.SUBSCRIPTION_FIELD;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.apache.camel.CamelException;
import org.apache.camel.component.salesforce.SalesforceComponent;
import org.apache.camel.component.salesforce.SalesforceConsumer;
import org.apache.camel.component.salesforce.SalesforceEndpoint;
import org.apache.camel.component.salesforce.SalesforceEndpointConfig;
import org.apache.camel.component.salesforce.SalesforceHttpClient;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.internal.SalesforceSession;
import org.apache.camel.support.service.ServiceSupport;
import org.cometd.bayeux.Message;
import org.cometd.bayeux.client.ClientSessionChannel;
import org.cometd.client.BayeuxClient;
import org.cometd.client.BayeuxClient.State;
import org.cometd.client.transport.ClientTransport;
import org.cometd.client.transport.LongPollingTransport;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubscriptionHelper extends ServiceSupport {

    static final CometDReplayExtension REPLAY_EXTENSION = new CometDReplayExtension();

    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionHelper.class);

    private static final int CONNECT_TIMEOUT = 110;
    private static final int CHANNEL_TIMEOUT = 40;

    private static final String FAILURE_FIELD = "failure";
    private static final String EXCEPTION_FIELD = "exception";
    private static final String SFDC_FIELD = "sfdc";
    private static final String FAILURE_REASON_FIELD = "failureReason";
    private static final int DISCONNECT_INTERVAL = 5000;
    private static final String SERVER_TOO_BUSY_ERROR = "503::";

    BayeuxClient client;

    private final SalesforceComponent component;
    private final SalesforceSession session;
    private final long timeout = 60 * 1000L;

    private final Map<SalesforceConsumer, ClientSessionChannel.MessageListener> listenerMap;
    private final long maxBackoff;
    private final long backoffIncrement;

    private ClientSessionChannel.MessageListener handshakeListener;
    private ClientSessionChannel.MessageListener connectListener;
    private ClientSessionChannel.MessageListener disconnectListener;

    private volatile String handshakeError;
    private volatile Exception handshakeException;
    private volatile String connectError;
    private volatile Exception connectException;

    private volatile boolean reconnecting;
    private final AtomicLong restartBackoff;

    public SubscriptionHelper(final SalesforceComponent component) throws SalesforceException {
        this.component = component;
        this.session = component.getSession();

        this.listenerMap = new ConcurrentHashMap<>();

        this.restartBackoff = new AtomicLong(0);
        this.backoffIncrement = component.getConfig().getBackoffIncrement();
        this.maxBackoff = component.getConfig().getMaxBackoff();
    }

    @Override
    protected void doStart() throws Exception {

        // create CometD client
        this.client = createClient(this.component);

        // reset all error conditions
        this.handshakeError = null;
        this.handshakeException = null;
        this.connectError = null;
        this.connectException = null;

        // listener for handshake error or exception
        if (this.handshakeListener == null) {
            // first start
            this.handshakeListener = new ClientSessionChannel.MessageListener() {
                @Override
                public void onMessage(final ClientSessionChannel channel, final Message message) {
                    LOG.debug("[CHANNEL:META_HANDSHAKE]: {}", message);

                    if (!message.isSuccessful()) {
                        LOG.warn("Handshake failure: {}", message);
                        SubscriptionHelper.this.handshakeError = (String)message.get(ERROR_FIELD);
                        SubscriptionHelper.this.handshakeException = getFailure(message);

                        if (SubscriptionHelper.this.handshakeError != null) {
                            // refresh oauth token, if it's a 401 error
                            if (SubscriptionHelper.this.handshakeError.startsWith("401::")) {
                                try {
                                    LOG.info("Refreshing OAuth token...");
                                    SubscriptionHelper.this.session.login(SubscriptionHelper.this.session.getAccessToken());
                                    LOG.info("Refreshed OAuth token for re-handshake");
                                } catch (SalesforceException e) {
                                    LOG.warn("Error renewing OAuth token on 401 error: " + e.getMessage(), e);
                                }
                            }
                            if (SubscriptionHelper.this.handshakeError.startsWith("403::")) {
                                try {
                                    LOG.info("Cleaning session (logout) from SalesforceSession before restarting client");
                                    SubscriptionHelper.this.session.logout();
                                } catch (SalesforceException e) {
                                    LOG.warn("Error while cleaning session: " + e.getMessage(), e);
                                }
                            }
                        }

                        // restart if handshake fails for any reason
                        restartClient();

                    } else if (!SubscriptionHelper.this.listenerMap.isEmpty()) {
                        SubscriptionHelper.this.reconnecting = true;
                    }
                }
            };
        }
        this.client.getChannel(META_HANDSHAKE).addListener(this.handshakeListener);

        // listener for connect error
        if (this.connectListener == null) {
            this.connectListener = new ClientSessionChannel.MessageListener() {
                @Override
                public void onMessage(final ClientSessionChannel channel, final Message message) {
                    LOG.debug("[CHANNEL:META_CONNECT]: {}", message);

                    if (!message.isSuccessful()) {

                        LOG.warn("Connect failure: {}", message);
                        SubscriptionHelper.this.connectError = (String)message.get(ERROR_FIELD);
                        SubscriptionHelper.this.connectException = getFailure(message);

                    } else if (SubscriptionHelper.this.reconnecting) {

                        SubscriptionHelper.this.reconnecting = false;

                        LOG.debug("Refreshing subscriptions to {} channels on reconnect", SubscriptionHelper.this.listenerMap.size());
                        // reconnected to Salesforce, subscribe to existing
                        // channels
                        final Map<SalesforceConsumer, ClientSessionChannel.MessageListener> map = new HashMap<>();
                        map.putAll(SubscriptionHelper.this.listenerMap);
                        SubscriptionHelper.this.listenerMap.clear();
                        for (Map.Entry<SalesforceConsumer, ClientSessionChannel.MessageListener> entry : map.entrySet()) {
                            final SalesforceConsumer consumer = entry.getKey();
                            final String topicName = consumer.getTopicName();
                            subscribe(topicName, consumer);
                        }

                    }
                }
            };
        }
        this.client.getChannel(META_CONNECT).addListener(this.connectListener);

        // handle fatal disconnects by reconnecting asynchronously
        if (this.disconnectListener == null) {
            this.disconnectListener = new ClientSessionChannel.MessageListener() {
                @Override
                public void onMessage(final ClientSessionChannel clientSessionChannel, final Message message) {
                    restartClient();
                }
            };
        }
        this.client.getChannel(META_DISCONNECT).addListener(this.disconnectListener);

        // connect to Salesforce cometd endpoint
        this.client.handshake();

        final long waitMs = MILLISECONDS.convert(CONNECT_TIMEOUT, SECONDS);
        if (!this.client.waitFor(waitMs, BayeuxClient.State.CONNECTED)) {
            if (this.handshakeException != null) {
                throw new CamelException(String.format("Exception during HANDSHAKE: %s", this.handshakeException.getMessage()), this.handshakeException);
            } else if (this.handshakeError != null) {
                throw new CamelException(String.format("Error during HANDSHAKE: %s", this.handshakeError));
            } else if (this.connectException != null) {
                throw new CamelException(String.format("Exception during CONNECT: %s", this.connectException.getMessage()), this.connectException);
            } else if (this.connectError != null) {
                throw new CamelException(String.format("Error during CONNECT: %s", this.connectError));
            } else {
                throw new CamelException(String.format("Handshake request timeout after %s seconds", CONNECT_TIMEOUT));
            }
        }
    }

    // launch an async task to restart
    private void restartClient() {

        // launch a new restart command
        final SalesforceHttpClient httpClient = this.component.getConfig().getHttpClient();
        httpClient.getExecutor().execute(new Runnable() {
            @Override
            public void run() {

                LOG.info("Restarting on unexpected disconnect from Salesforce...");
                boolean abort = false;

                // wait for disconnect
                LOG.debug("Waiting to disconnect...");
                while (!SubscriptionHelper.this.client.isDisconnected()) {
                    try {
                        Thread.sleep(DISCONNECT_INTERVAL);
                    } catch (InterruptedException e) {
                        LOG.error("Aborting restart on interrupt!");
                        abort = true;
                    }
                }

                if (!abort) {

                    // update restart attempt backoff
                    final long backoff = SubscriptionHelper.this.restartBackoff.getAndAdd(SubscriptionHelper.this.backoffIncrement);
                    if (backoff > SubscriptionHelper.this.maxBackoff) {
                        LOG.error("Restart aborted after exceeding {} msecs backoff", SubscriptionHelper.this.maxBackoff);
                        abort = true;
                    } else {

                        // pause before restart attempt
                        LOG.debug("Pausing for {} msecs before restart attempt", backoff);
                        try {
                            Thread.sleep(backoff);
                        } catch (InterruptedException e) {
                            LOG.error("Aborting restart on interrupt!");
                            abort = true;
                        }
                    }

                    if (!abort) {
                        Exception lastError = new SalesforceException("Unknown error", null);
                        try {
                            // reset client
                            doStop();

                            // register listeners and restart
                            doStart();

                        } catch (Exception e) {
                            LOG.error("Error restarting: " + e.getMessage(), e);
                            lastError = e;
                        }

                        if (SubscriptionHelper.this.client != null && SubscriptionHelper.this.client.isHandshook()) {
                            LOG.info("Successfully restarted!");
                            // reset backoff interval
                            SubscriptionHelper.this.restartBackoff.set(SubscriptionHelper.this.client.getBackoffIncrement());
                        } else {
                            LOG.error("Failed to restart after pausing for {} msecs", backoff);
                            if (backoff + SubscriptionHelper.this.backoffIncrement > SubscriptionHelper.this.maxBackoff) {
                                // notify all consumers
                                String abortMsg = "Aborting restart attempt due to: " + lastError.getMessage();
                                SalesforceException ex = new SalesforceException(abortMsg, lastError);
                                for (SalesforceConsumer consumer : SubscriptionHelper.this.listenerMap.keySet()) {
                                    consumer.handleException(abortMsg, ex);
                                }
                            }
                        }
                    }
                }

            }
        });
    }

    @SuppressWarnings("unchecked")
    private static Exception getFailure(final Message message) {
        Exception exception = null;
        if (message.get(EXCEPTION_FIELD) != null) {
            exception = (Exception)message.get(EXCEPTION_FIELD);
        } else if (message.get(FAILURE_FIELD) != null) {
            exception = (Exception)((Map<String, Object>)message.get("failure")).get("exception");
        } else {
            String failureReason = getFailureReason(message);
            if (failureReason != null) {
                exception = new SalesforceException(failureReason, null);
            }
        }
        return exception;
    }

    @Override
    protected void doStop() throws Exception {
        this.client.getChannel(META_DISCONNECT).removeListener(this.disconnectListener);
        this.client.getChannel(META_CONNECT).removeListener(this.connectListener);
        this.client.getChannel(META_HANDSHAKE).removeListener(this.handshakeListener);

        this.client.disconnect();
        boolean disconnected = this.client.waitFor(this.timeout, State.DISCONNECTED);
        if (!disconnected) {
            LOG.warn("Could not disconnect client connected to: {} after: {} msec.", getEndpointUrl(this.component), this.timeout);
            this.client.abort();
        }

        this.client = null;
    }

    static BayeuxClient createClient(final SalesforceComponent component) throws SalesforceException {
        // use default Jetty client from SalesforceComponent, its shared by all
        // consumers
        final SalesforceHttpClient httpClient = component.getConfig().getHttpClient();

        Map<String, Object> options = new HashMap<>();
        options.put(ClientTransport.MAX_NETWORK_DELAY_OPTION, httpClient.getTimeout());
        if (component.getLongPollingTransportProperties() != null) {
            options = component.getLongPollingTransportProperties();
        }

        final SalesforceSession session = component.getSession();
        // check login access token
        if (session.getAccessToken() == null) {
            // lazy login here!
            session.login(null);
        }

        LongPollingTransport transport = new LongPollingTransport(options, httpClient) {
            @Override
            protected void customize(final Request request) {
                super.customize(request);

                // add current security token obtained from session
                // replace old token
                request.getHeaders().put(HttpHeader.AUTHORIZATION, "OAuth " + session.getAccessToken());
            }
        };

        BayeuxClient client = new BayeuxClient(getEndpointUrl(component), transport);

        // added eagerly to check for support during handshake
        client.addExtension(REPLAY_EXTENSION);

        return client;
    }

    public void subscribe(final String topicName, final SalesforceConsumer consumer) {
        // create subscription for consumer
        final String channelName = getChannelName(topicName);

        setupReplay((SalesforceEndpoint)consumer.getEndpoint());

        // channel message listener
        LOG.info("Subscribing to channel {}...", channelName);
        final ClientSessionChannel.MessageListener listener = new ClientSessionChannel.MessageListener() {

            @Override
            public void onMessage(final ClientSessionChannel channel, final Message message) {
                LOG.debug("Received Message: {}", message);
                // convert CometD message to Camel Message
                consumer.processMessage(channel, message);
            }

        };

        final ClientSessionChannel clientChannel = this.client.getChannel(channelName);

        // listener for subscription
        final ClientSessionChannel.MessageListener subscriptionListener = new ClientSessionChannel.MessageListener() {
            @Override
            public void onMessage(final ClientSessionChannel channel, final Message message) {
                LOG.debug("[CHANNEL:META_SUBSCRIBE]: {}", message);
                final String subscribedChannelName = message.get(SUBSCRIPTION_FIELD).toString();
                if (channelName.equals(subscribedChannelName)) {

                    if (!message.isSuccessful()) {
                        String error = (String)message.get(ERROR_FIELD);
                        if (error == null) {
                            error = "Missing error message";
                        }

                        Exception failure = getFailure(message);
                        String msg = String.format("Error subscribing to %s: %s", topicName, failure != null ? failure.getMessage() : error);
                        boolean abort = true;

                        if (isTemporaryError(message)) {
                            LOG.warn(msg);

                            // retry after delay
                            final long backoff = SubscriptionHelper.this.restartBackoff.getAndAdd(SubscriptionHelper.this.backoffIncrement);
                            if (backoff > SubscriptionHelper.this.maxBackoff) {
                                LOG.error("Subscribe aborted after exceeding {} msecs backoff", SubscriptionHelper.this.maxBackoff);
                            } else {
                                abort = false;

                                try {
                                    LOG.debug("Pausing for {} msecs before subscribe attempt", backoff);
                                    Thread.sleep(backoff);

                                    final SalesforceHttpClient httpClient = SubscriptionHelper.this.component.getConfig().getHttpClient();
                                    httpClient.getExecutor().execute(new Runnable() {
                                        @Override
                                        public void run() {
                                            subscribe(topicName, consumer);
                                        }
                                    });
                                } catch (InterruptedException e) {
                                    LOG.warn("Aborting subscribe on interrupt!", e);
                                }
                            }
                        }

                        if (abort) {
                            consumer.handleException(msg, new SalesforceException(msg, failure));
                        }
                    } else {
                        // remember subscription
                        LOG.info("Subscribed to channel {}", subscribedChannelName);
                        SubscriptionHelper.this.listenerMap.put(consumer, listener);

                        // reset backoff interval
                        SubscriptionHelper.this.restartBackoff.set(0);
                    }

                    // remove this subscription listener
                    SubscriptionHelper.this.client.getChannel(META_SUBSCRIBE).removeListener(this);
                }
            }
        };
        this.client.getChannel(META_SUBSCRIBE).addListener(subscriptionListener);

        // subscribe asynchronously
        clientChannel.subscribe(listener);
    }

    private static boolean isTemporaryError(final Message message) {
        String failureReason = getFailureReason(message);
        return failureReason != null && failureReason.startsWith(SERVER_TOO_BUSY_ERROR);
    }

    private static String getFailureReason(final Message message) {
        String failureReason = null;
        if (message.getExt() != null) {
            Map<String, Object> sfdcFields = (Map<String, Object>) message.getExt().get(SFDC_FIELD);
            if (sfdcFields != null) {
                failureReason  = (String) sfdcFields.get(FAILURE_REASON_FIELD);
            }
        }
        return failureReason;
    }

    void setupReplay(final SalesforceEndpoint endpoint) {
        final String topicName = endpoint.getTopicName();

        final Optional<Long> replayId = determineReplayIdFor(endpoint, topicName);
        if (replayId.isPresent()) {
            final String channelName = getChannelName(topicName);

            final Long replayIdValue = replayId.get();

            LOG.info("Set Replay extension to replay from `{}` for channel `{}`", replayIdValue, channelName);

            REPLAY_EXTENSION.addChannelReplayId(channelName, replayIdValue);
        }
    }

    static Optional<Long> determineReplayIdFor(final SalesforceEndpoint endpoint, final String topicName) {
        final String channelName = getChannelName(topicName);

        final Long replayId = endpoint.getReplayId();

        final SalesforceComponent component = endpoint.getComponent();

        final SalesforceEndpointConfig endpointConfiguration = endpoint.getConfiguration();
        final Map<String, Long> endpointInitialReplayIdMap = endpointConfiguration.getInitialReplayIdMap();
        final Long endpointReplayId = endpointInitialReplayIdMap.getOrDefault(topicName, endpointInitialReplayIdMap.get(channelName));
        final Long endpointDefaultReplayId = endpointConfiguration.getDefaultReplayId();

        final SalesforceEndpointConfig componentConfiguration = component.getConfig();
        final Map<String, Long> componentInitialReplayIdMap = componentConfiguration.getInitialReplayIdMap();
        final Long componentReplayId = componentInitialReplayIdMap.getOrDefault(topicName, componentInitialReplayIdMap.get(channelName));
        final Long componentDefaultReplayId = componentConfiguration.getDefaultReplayId();

        // the endpoint values have priority over component values, and the
        // default values posteriority
        // over give topic values
        return Stream.of(replayId, endpointReplayId, componentReplayId, endpointDefaultReplayId, componentDefaultReplayId).filter(Objects::nonNull).findFirst();
    }

    static String getChannelName(final String topicName) {
        final StringBuilder channelName = new StringBuilder();
        if (topicName.charAt(0) != '/') {
            channelName.append('/');
        }

        if (topicName.indexOf('/', 1) > 0) {
            channelName.append(topicName);
        } else {
            channelName.append("topic/");
            channelName.append(topicName);
        }

        final int typeIdx = channelName.indexOf("/", 1);
        if ("event".equals(channelName.substring(1, typeIdx)) && !topicName.endsWith("__e")) {
            channelName.append("__e");
        }

        return channelName.toString();
    }

    public void unsubscribe(final String topicName, final SalesforceConsumer consumer) throws CamelException {

        // channel name
        final String channelName = getChannelName(topicName);

        // listen for unsubscribe error
        final CountDownLatch latch = new CountDownLatch(1);
        final String[] unsubscribeError = {null};
        final Exception[] unsubscribeFailure = {null};

        final ClientSessionChannel.MessageListener unsubscribeListener = new ClientSessionChannel.MessageListener() {
            @Override
            public void onMessage(final ClientSessionChannel channel, final Message message) {
                LOG.debug("[CHANNEL:META_UNSUBSCRIBE]: {}", message);
                Object subscription = message.get(SUBSCRIPTION_FIELD);
                if (subscription != null) {
                    String unsubscribedChannelName = subscription.toString();
                    if (channelName.equals(unsubscribedChannelName)) {

                        if (!message.isSuccessful()) {
                            unsubscribeError[0] = (String)message.get(ERROR_FIELD);
                            unsubscribeFailure[0] = getFailure(message);
                        } else {
                            // forget subscription
                            LOG.info("Unsubscribed from channel {}", unsubscribedChannelName);
                        }
                        latch.countDown();
                    }
                }
            }
        };
        this.client.getChannel(META_UNSUBSCRIBE).addListener(unsubscribeListener);

        try {
            // unsubscribe from channel
            final ClientSessionChannel.MessageListener listener = this.listenerMap.remove(consumer);
            if (listener != null) {

                LOG.info("Unsubscribing from channel {}...", channelName);
                final ClientSessionChannel clientChannel = this.client.getChannel(channelName);
                clientChannel.unsubscribe(listener);

                // confirm unsubscribe
                try {
                    if (!latch.await(CHANNEL_TIMEOUT, SECONDS)) {
                        String message;
                        if (unsubscribeFailure[0] != null) {
                            message = String.format("Error unsubscribing from topic %s: %s", topicName, unsubscribeFailure[0].getMessage());
                        } else if (unsubscribeError[0] != null) {
                            message = String.format("Error unsubscribing from topic %s: %s", topicName, unsubscribeError[0]);
                        } else {
                            message = String.format("Timeout error unsubscribing from topic %s after %s seconds", topicName, CHANNEL_TIMEOUT);
                        }
                        throw new CamelException(message, unsubscribeFailure[0]);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    // probably shutting down, forget unsubscribe and return
                }

            }
        } finally {
            this.client.getChannel(META_UNSUBSCRIBE).removeListener(unsubscribeListener);
        }
    }

    static String getEndpointUrl(final SalesforceComponent component) {
        // In version 36.0 replay is only enabled on a separate endpoint
        if (Double.valueOf(component.getConfig().getApiVersion()) == 36.0) {
            boolean replayOptionsPresent = component.getConfig().getDefaultReplayId() != null || !component.getConfig().getInitialReplayIdMap().isEmpty();
            if (replayOptionsPresent) {
                return component.getSession().getInstanceUrl() + "/cometd/replay/" + component.getConfig().getApiVersion();
            }
        }
        return component.getSession().getInstanceUrl() + "/cometd/" + component.getConfig().getApiVersion();
    }

}
