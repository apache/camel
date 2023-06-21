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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.cometd.bayeux.client.ClientSessionChannel.MessageListener;
import org.cometd.client.BayeuxClient;
import org.cometd.client.BayeuxClient.State;
import org.cometd.client.transport.ClientTransport;
import org.cometd.client.transport.LongPollingTransport;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cometd.bayeux.Channel.META_CONNECT;
import static org.cometd.bayeux.Channel.META_HANDSHAKE;
import static org.cometd.bayeux.Channel.META_SUBSCRIBE;
import static org.cometd.bayeux.Message.ERROR_FIELD;
import static org.cometd.bayeux.Message.SUBSCRIPTION_FIELD;

public class SubscriptionHelper extends ServiceSupport {

    static final ReplayExtension REPLAY_EXTENSION = new ReplayExtension();

    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionHelper.class);

    private static final int CONNECT_TIMEOUT = 110;

    private static final String FAILURE_FIELD = "failure";
    private static final String EXCEPTION_FIELD = "exception";
    private static final String SFDC_FIELD = "sfdc";
    private static final String FAILURE_REASON_FIELD = "failureReason";
    private static final int DISCONNECT_INTERVAL = 5000;
    private static final String SERVER_TOO_BUSY_ERROR = "503::";
    private static final String AUTHENTICATION_INVALID = "401::Authentication invalid";
    private static final String INVALID_REPLAY_ID_PATTERN = "400::The replayId \\{.*} you provided was invalid.*";

    BayeuxClient client;

    private final SalesforceComponent component;
    private SalesforceSession session;
    private final long timeout = 60 * 1000L;

    private final Map<SalesforceConsumer, ClientSessionChannel.MessageListener> listenerMap;
    private final long maxBackoff;
    private final long backoffIncrement;

    private ClientSessionChannel.MessageListener handshakeListener;
    private ClientSessionChannel.MessageListener connectListener;

    private volatile String handshakeError;
    private volatile Exception handshakeException;
    private volatile String connectError;
    private volatile Exception connectException;

    private volatile boolean reconnecting;
    private final AtomicLong handshakeBackoff;
    private final AtomicBoolean handshaking = new AtomicBoolean();
    private final AtomicBoolean loggingIn = new AtomicBoolean();

    public SubscriptionHelper(final SalesforceComponent component) {
        this.component = component;
        listenerMap = new ConcurrentHashMap<>();
        handshakeBackoff = new AtomicLong();
        backoffIncrement = component.getConfig().getBackoffIncrement();
        maxBackoff = component.getConfig().getMaxBackoff();
    }

    @Override
    protected void doStart() throws Exception {
        session = component.getSession();

        // create CometD client
        client = createClient(component, session);

        initMessageListeners();
        connect();
    }

    private void initMessageListeners() {
        // listener for handshake error or exception
        if (handshakeListener == null) {
            // first start
            handshakeListener = new ClientSessionChannel.MessageListener() {
                public void onMessage(ClientSessionChannel channel, Message message) {
                    component.getHttpClient().getWorkerPool().execute(() -> {
                        LOG.debug("[CHANNEL:META_HANDSHAKE]: {}", message);

                        if (!message.isSuccessful()) {
                            LOG.warn("Handshake failure: {}", message);
                            handshakeError = (String) message.get(ERROR_FIELD);
                            handshakeException = getFailure(message);
                            if (handshakeError != null) {
                                if (handshakeError.startsWith("403::")) {
                                    String failureReason = getFailureReason(message);
                                    if (failureReason.equals(AUTHENTICATION_INVALID)) {
                                        LOG.debug(
                                                "attempting login due to handshake error: 403 -> 401::Authentication invalid");
                                        attemptLoginUntilSuccessful();
                                    }
                                }
                            }

                            // failed, so keep trying
                            LOG.debug("Handshake failed, so try again.");
                            handshake();

                        } else if (!listenerMap.isEmpty()) {
                            reconnecting = true;
                        }
                    });
                }
            };
        }
        client.getChannel(META_HANDSHAKE).addListener(handshakeListener);

        // listener for connect error
        if (connectListener == null) {
            connectListener = new ClientSessionChannel.MessageListener() {
                public void onMessage(ClientSessionChannel channel, Message message) {
                    component.getHttpClient().getWorkerPool().execute(() -> {
                        LOG.debug("[CHANNEL:META_CONNECT]: {}", message);

                        if (!message.isSuccessful()) {

                            LOG.warn("Connect failure: {}", message);
                            connectError = (String) message.get(ERROR_FIELD);
                            connectException = getFailure(message);

                            if (connectError != null && connectError.equals(AUTHENTICATION_INVALID)) {
                                LOG.debug("connectError: {}", connectError);
                                LOG.debug("Attempting login...");
                                attemptLoginUntilSuccessful();
                            }
                            // Server says don't retry to connect, so we'll handshake instead
                            // Otherwise, Bayeux client automatically re-attempts connection
                            if (message.getAdvice() != null &&
                                    !message.getAdvice().get("reconnect").equals("retry")) {
                                LOG.debug("Advice != retry, so handshaking");
                                handshake();
                            }
                        } else if (reconnecting) {
                            LOG.debug("Refreshing subscriptions to {} channels on reconnect", listenerMap.size());
                            // reconnected to Salesforce, subscribe to existing
                            // channels
                            final Map<SalesforceConsumer, MessageListener> map = new HashMap<>(listenerMap);
                            listenerMap.clear();
                            for (Map.Entry<SalesforceConsumer, ClientSessionChannel.MessageListener> entry : map.entrySet()) {
                                final SalesforceConsumer consumer = entry.getKey();
                                final String topicName = consumer.getTopicName();
                                subscribe(topicName, consumer);
                            }
                            reconnecting = false;
                        }
                    });
                }
            };
        }
        client.getChannel(META_CONNECT).addListener(connectListener);
    }

    private void connect() throws CamelException {
        // connect to Salesforce cometd endpoint
        client.handshake();

        final long waitMs = MILLISECONDS.convert(CONNECT_TIMEOUT, SECONDS);
        if (!client.waitFor(waitMs, BayeuxClient.State.CONNECTED)) {
            if (handshakeException != null) {
                throw new CamelException(
                        String.format("Exception during HANDSHAKE: %s", handshakeException.getMessage()), handshakeException);
            } else if (handshakeError != null) {
                throw new CamelException(String.format("Error during HANDSHAKE: %s", handshakeError));
            } else if (connectException != null) {
                throw new CamelException(
                        String.format("Exception during CONNECT: %s", connectException.getMessage()), connectException);
            } else if (connectError != null) {
                throw new CamelException(String.format("Error during CONNECT: %s", connectError));
            } else {
                throw new CamelException(String.format("Handshake request timeout after %s seconds", CONNECT_TIMEOUT));
            }
        }
    }

    private void handshake() {
        LOG.debug("Begin handshake if not already in progress.");
        if (!handshaking.compareAndSet(false, true)) {
            return;
        }

        LOG.debug("Continuing with handshake.");
        try {
            doHandshake();
        } finally {
            handshaking.set(false);
        }
    }

    private void doHandshake() {
        if (isStoppingOrStopped()) {
            return;
        }

        LOG.info("Handshaking after unexpected disconnect from Salesforce...");
        boolean abort = false;

        // wait for disconnect
        LOG.debug("Waiting to disconnect...");
        while (!abort && !client.isDisconnected()) {
            try {
                Thread.sleep(DISCONNECT_INTERVAL);
            } catch (InterruptedException e) {
                LOG.error("Aborting handshake on interrupt!");
                abort = true;
            }

            abort = abort || isStoppingOrStopped();
        }

        if (!abort) {

            // update handshake attempt backoff
            final long backoff = handshakeBackoff.getAndAdd(backoffIncrement);
            if (backoff > maxBackoff) {
                LOG.error("Handshake aborted after exceeding {} msecs backoff", maxBackoff);
                abort = true;
            } else {

                // pause before handshake attempt
                LOG.debug("Pausing for {} msecs before handshake attempt", backoff);
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException e) {
                    LOG.error("Aborting handshake on interrupt!");
                    abort = true;
                }
            }

            if (!abort) {
                Exception lastError = new SalesforceException("Unknown error", null);
                try {
                    // reset client. If we fail to stop and logout, catch the exception
                    // so we can still continue to doStart()
                    if (client != null) {
                        client.disconnect();
                        boolean disconnected = client.waitFor(timeout, State.DISCONNECTED);
                        if (!disconnected) {
                            LOG.warn("Could not disconnect client connected to: {} after: {} msec.", getEndpointUrl(component),
                                    timeout);
                            client.abort();
                        }

                        client.handshake();
                        final long waitMs = MILLISECONDS.convert(CONNECT_TIMEOUT, SECONDS);
                        client.waitFor(waitMs, BayeuxClient.State.CONNECTED);
                    }
                } catch (Exception e) {
                    LOG.error("Error handshaking: " + e.getMessage(), e);
                    lastError = e;
                }

                if (client != null && client.isHandshook()) {
                    LOG.debug("Successful handshake!");
                    // reset backoff interval
                    handshakeBackoff.set(client.getBackoffIncrement());
                } else {
                    LOG.error("Failed to handshake after pausing for {} msecs", backoff);
                    if ((backoff + backoffIncrement) > maxBackoff) {
                        // notify all consumers
                        String abortMsg = "Aborting handshake attempt due to: " + lastError.getMessage();
                        SalesforceException ex = new SalesforceException(abortMsg, lastError);
                        for (SalesforceConsumer consumer : listenerMap.keySet()) {
                            consumer.handleException(abortMsg, ex);
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Exception getFailure(Message message) {
        Exception exception = null;
        if (message.get(EXCEPTION_FIELD) != null) {
            exception = (Exception) message.get(EXCEPTION_FIELD);
        } else if (message.get(FAILURE_FIELD) != null) {
            exception = (Exception) ((Map<String, Object>) message.get(FAILURE_FIELD)).get("exception");
        } else {
            String failureReason = getFailureReason(message);
            if (failureReason != null) {
                exception = new SalesforceException(failureReason, null);
            }
        }
        return exception;
    }

    private void closeChannel(final String name, MessageListener listener) {
        if (client == null) {
            return;
        }

        final ClientSessionChannel channel = client.getChannel(name);
        channel.removeListener(listener);
        channel.release();
    }

    @Override
    protected void doStop() throws Exception {
        closeChannel(META_CONNECT, connectListener);
        closeChannel(META_HANDSHAKE, handshakeListener);

        for (Map.Entry<SalesforceConsumer, MessageListener> entry : listenerMap.entrySet()) {
            final SalesforceConsumer consumer = entry.getKey();
            final String topic = consumer.getTopicName();

            final MessageListener listener = entry.getValue();
            closeChannel(getChannelName(topic), listener);
        }

        if (client == null) {
            return;
        }

        client.disconnect();
        boolean disconnected = client.waitFor(timeout, State.DISCONNECTED);
        if (!disconnected) {
            LOG.warn("Could not disconnect client connected to: {} after: {} msec.", getEndpointUrl(component), timeout);
            client.abort();
        }

        client = null;

        if (session != null) {
            session.logout();
        }
        LOG.debug("Stopped the helper and destroyed the client");
    }

    static BayeuxClient createClient(final SalesforceComponent component, final SalesforceSession session)
            throws SalesforceException {
        // use default Jetty client from SalesforceComponent, it's shared by all consumers
        final SalesforceHttpClient httpClient = component.getConfig().getHttpClient();

        Map<String, Object> options = new HashMap<>();
        options.put(ClientTransport.MAX_NETWORK_DELAY_OPTION, httpClient.getTimeout());
        if (component.getLongPollingTransportProperties() != null) {
            options = component.getLongPollingTransportProperties();
        }

        // check login access token
        if (session.getAccessToken() == null && !component.getLoginConfig().isLazyLogin()) {
            session.login(null);
        }

        LongPollingTransport transport = new LongPollingTransport(options, httpClient) {
            @Override
            protected void customize(Request request) {
                super.customize(request);

                //accessToken might be null due to lazy login
                String accessToken = session.getAccessToken();
                if (accessToken == null) {
                    try {
                        accessToken = session.login(null);
                    } catch (SalesforceException e) {
                        throw new RuntimeException(e);
                    }
                }
                request.header(HttpHeader.AUTHORIZATION, "OAuth " + accessToken);
            }
        };

        BayeuxClient client = new BayeuxClient(getEndpointUrl(component), transport);

        // added eagerly to check for support during handshake
        client.addExtension(REPLAY_EXTENSION);

        return client;
    }

    public void subscribe(final String topicName, final SalesforceConsumer consumer) {
        subscribe(topicName, consumer, false);
    }

    public void subscribe(
            final String topicName, final SalesforceConsumer consumer,
            final boolean skipReplayId) {
        // create subscription for consumer
        final String channelName = getChannelName(topicName);

        if (!reconnecting && !skipReplayId) {
            setupReplay((SalesforceEndpoint) consumer.getEndpoint());
        }

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

        // listener for subscription
        final ClientSessionChannel.MessageListener subscriptionListener = new ClientSessionChannel.MessageListener() {
            public void onMessage(ClientSessionChannel channel, Message message) {
                LOG.debug("[CHANNEL:META_SUBSCRIBE]: {}", message);
                final String subscribedChannelName = message.get(SUBSCRIPTION_FIELD).toString();
                if (channelName.equals(subscribedChannelName)) {

                    if (!message.isSuccessful()) {
                        String error = (String) message.get(ERROR_FIELD);
                        if (error == null) {
                            error = "Missing error message";
                        }

                        Exception failure = getFailure(message);
                        String msg = String.format("Error subscribing to %s: %s", topicName,
                                failure != null ? failure.getMessage() : error);
                        boolean abort = true;

                        if (isTemporaryError(message)) {
                            LOG.warn(msg);

                            // retry after delay
                            final long backoff = handshakeBackoff.getAndAdd(backoffIncrement);
                            if (backoff > maxBackoff) {
                                LOG.error("Subscribe aborted after exceeding {} msecs backoff", maxBackoff);
                            } else {
                                abort = false;

                                try {
                                    LOG.debug("Pausing for {} msecs before subscribe attempt", backoff);
                                    Thread.sleep(backoff);

                                    component.getHttpClient().getWorkerPool().execute(() -> subscribe(topicName, consumer));
                                } catch (InterruptedException e) {
                                    LOG.warn("Aborting subscribe on interrupt!", e);
                                }
                            }
                        } else if (error.matches(INVALID_REPLAY_ID_PATTERN)) {
                            abort = false;
                            final Long fallBackReplayId
                                    = ((SalesforceEndpoint) consumer.getEndpoint()).getConfiguration().getFallBackReplayId();
                            LOG.warn(error);
                            LOG.warn("Falling back to replayId {} for channel {}", fallBackReplayId, channelName);
                            REPLAY_EXTENSION.addChannelReplayId(channelName, fallBackReplayId);
                            subscribe(topicName, consumer, true);
                        }

                        if (abort && client != null) {
                            consumer.handleException(msg, new SalesforceException(msg, failure));
                        }
                    } else {
                        // remember subscription
                        LOG.info("Subscribed to channel {}", subscribedChannelName);
                        listenerMap.put(consumer, listener);

                        // reset backoff interval
                        handshakeBackoff.set(0);
                    }

                    // remove this subscription listener
                    if (client != null) {
                        client.getChannel(META_SUBSCRIBE).removeListener(this);
                    } else {
                        LOG.warn("Trying to handle a subscription message but the client is already destroyed");
                    }
                }
            }
        };
        client.getChannel(META_SUBSCRIBE).addListener(subscriptionListener);

        // subscribe asynchronously
        final ClientSessionChannel clientChannel = client.getChannel(channelName);
        clientChannel.subscribe(listener);
    }

    private static boolean isTemporaryError(Message message) {
        String failureReason = getFailureReason(message);
        return failureReason != null && failureReason.startsWith(SERVER_TOO_BUSY_ERROR);
    }

    private static String getFailureReason(Message message) {
        String failureReason = null;
        if (message.getExt() != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> sfdcFields = (Map<String, Object>) message.getExt().get(SFDC_FIELD);
            if (sfdcFields != null) {
                failureReason = (String) sfdcFields.get(FAILURE_REASON_FIELD);
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

    private void attemptLoginUntilSuccessful() {
        if (!loggingIn.compareAndSet(false, true)) {
            LOG.debug("already logging in");
            return;
        }

        long backoff = 0;

        try {
            for (;;) {
                try {
                    if (isStoppingOrStopped()) {
                        return;
                    }
                    session.login(session.getAccessToken());
                    break;
                } catch (SalesforceException e) {
                    backoff = backoff + backoffIncrement;
                    if (backoff > maxBackoff) {
                        backoff = maxBackoff;
                    }
                    LOG.warn(String.format("Salesforce login failed. Pausing for %d seconds", backoff), e);
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException("Failed to login.", ex);
                    }
                }
            }
        } finally {
            loggingIn.set(false);
        }
    }

    static Optional<Long> determineReplayIdFor(final SalesforceEndpoint endpoint, final String topicName) {
        final String channelName = getChannelName(topicName);

        final Long replayId = endpoint.getReplayId();

        final SalesforceComponent component = endpoint.getComponent();

        final SalesforceEndpointConfig endpointConfiguration = endpoint.getConfiguration();
        final Map<String, Long> endpointInitialReplayIdMap = endpointConfiguration.getInitialReplayIdMap();
        final Long endpointReplayId
                = endpointInitialReplayIdMap.getOrDefault(topicName, endpointInitialReplayIdMap.get(channelName));
        final Long endpointDefaultReplayId = endpointConfiguration.getDefaultReplayId();

        final SalesforceEndpointConfig componentConfiguration = component.getConfig();
        final Map<String, Long> componentInitialReplayIdMap = componentConfiguration.getInitialReplayIdMap();
        final Long componentReplayId
                = componentInitialReplayIdMap.getOrDefault(topicName, componentInitialReplayIdMap.get(channelName));
        final Long componentDefaultReplayId = componentConfiguration.getDefaultReplayId();

        // the endpoint values have priority over component values, and the
        // default values priority
        // over give topic values
        return Stream.of(replayId, endpointReplayId, componentReplayId, endpointDefaultReplayId, componentDefaultReplayId)
                .filter(Objects::nonNull).findFirst();
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

        return channelName.toString();
    }

    public void unsubscribe(String topicName, SalesforceConsumer consumer) {

        // channel name
        final String channelName = getChannelName(topicName);

        // unsubscribe from channel
        final ClientSessionChannel.MessageListener listener = listenerMap.remove(consumer);
        if (listener != null) {

            LOG.debug("Unsubscribing from channel {}...", channelName);
            final ClientSessionChannel clientChannel = client.getChannel(channelName);
            // if there are other listeners on this channel, an unsubscribe message will not be sent,
            // so we're not going to listen for and expect an unsub response. Just unsub and move on.
            clientChannel.unsubscribe(listener);
        }
    }

    static String getEndpointUrl(final SalesforceComponent component) {
        // In version 36.0 replay is only enabled on a separate endpoint
        if (Double.parseDouble(component.getConfig().getApiVersion()) == 36.0) {
            boolean replayOptionsPresent = component.getConfig().getDefaultReplayId() != null
                    || !component.getConfig().getInitialReplayIdMap().isEmpty();
            if (replayOptionsPresent) {
                return component.getSession().getInstanceUrl() + "/cometd/replay/" + component.getConfig().getApiVersion();
            }
        }
        return component.getSession().getInstanceUrl() + "/cometd/" + component.getConfig().getApiVersion();
    }
}
