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

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.CookieStore;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.apache.camel.CamelException;
import org.apache.camel.component.salesforce.SalesforceComponent;
import org.apache.camel.component.salesforce.SalesforceEndpoint;
import org.apache.camel.component.salesforce.SalesforceEndpointConfig;
import org.apache.camel.component.salesforce.SalesforceHttpClient;
import org.apache.camel.component.salesforce.StreamingApiConsumer;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.internal.SalesforceSession;
import org.apache.camel.support.service.ServiceSupport;
import org.cometd.bayeux.Message;
import org.cometd.bayeux.client.ClientSessionChannel;
import org.cometd.bayeux.client.ClientSessionChannel.MessageListener;
import org.cometd.client.BayeuxClient;
import org.cometd.client.BayeuxClient.State;
import org.cometd.client.http.jetty.JettyHttpClientTransport;
import org.cometd.client.transport.ClientTransport;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.http.HttpCookie;
import org.eclipse.jetty.http.HttpCookieStore;
import org.eclipse.jetty.http.HttpHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Collections.emptySet;
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

    private static final int HANDSHAKE_TIMEOUT_SEC = 120;

    private static final String FAILURE_FIELD = "failure";
    private static final String EXCEPTION_FIELD = "exception";
    private static final String SFDC_FIELD = "sfdc";
    private static final String FAILURE_REASON_FIELD = "failureReason";

    private static final String SERVER_TOO_BUSY_ERROR = "503::";
    private static final String AUTHENTICATION_INVALID = "401::Authentication invalid";
    private static final String INVALID_REPLAY_ID_PATTERN = "400::The replayId \\{.*} you provided was invalid.*";

    BayeuxClient client;

    private final SalesforceComponent component;
    private SalesforceSession session;

    private final long maxBackoff;
    private final long backoffIncrement;
    private volatile String handshakeError;
    private volatile Exception handshakeException;
    private volatile String connectError;
    private volatile Exception connectException;

    private final AtomicLong handshakeBackoff;

    private final Map<String, Set<StreamingApiConsumer>> channelToConsumers = new ConcurrentHashMap<>();

    private final Map<StreamingApiConsumer, ClientSessionChannel.MessageListener> consumerToListener
            = new ConcurrentHashMap<>();

    private final Set<String> channelsToSubscribe = ConcurrentHashMap.newKeySet();

    private final ClientSessionChannel.MessageListener handshakeListener = createHandshakeListener();

    private final ClientSessionChannel.MessageListener subscriptionListener = createSubscriptionListener();

    private final ClientSessionChannel.MessageListener connectListener = createConnectionListener();

    public SubscriptionHelper(final SalesforceComponent component) {
        this.component = component;
        handshakeBackoff = new AtomicLong();
        backoffIncrement = component.getConfig().getBackoffIncrement();
        maxBackoff = component.getConfig().getMaxBackoff();
    }

    private MessageListener createHandshakeListener() {
        return (channel, message) -> component.getHttpClient().getWorkerPool().execute(() -> {
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
                            session.attemptLoginUntilSuccessful(backoffIncrement, maxBackoff);
                        }
                    }
                }
                // failed, so keep trying
                LOG.debug("Handshake failed, so try again.");
                client.handshake();
            } else if (!channelToConsumers.isEmpty()) {
                channelsToSubscribe.clear();
                channelsToSubscribe.addAll(channelToConsumers.keySet());
                LOG.info("Handshake successful. Channels to subscribe: {}", channelsToSubscribe);
            }
        });
    }

    private MessageListener createConnectionListener() {
        return (channel, message) -> component.getHttpClient().getWorkerPool().execute(() -> {
            LOG.debug("[CHANNEL:META_CONNECT]: {}", message);

            if (!message.isSuccessful()) {
                LOG.warn("Connect failure: {}", message);
                connectError = (String) message.get(ERROR_FIELD);
                connectException = getFailure(message);

                if (connectError != null && connectError.equals(AUTHENTICATION_INVALID)) {
                    LOG.debug("connectError: {}", connectError);
                    LOG.debug("Attempting login...");
                    session.attemptLoginUntilSuccessful(backoffIncrement, maxBackoff);
                }
                if (message.getAdvice() == null || "none".equals(message.getAdvice().get("reconnect"))) {
                    LOG.debug("Advice == none, so handshaking");
                    client.handshake();
                }
            } else if (!channelsToSubscribe.isEmpty()) {
                LOG.info("Subscribing to channels: {}", channelsToSubscribe);
                for (var channelName : channelsToSubscribe) {
                    for (var consumer : channelToConsumers.get(channelName)) {
                        subscribe(consumer);
                    }
                }
            }
        });
    }

    private MessageListener createSubscriptionListener() {
        return (channel, message) -> component.getHttpClient().getWorkerPool().execute(() -> {
            LOG.debug("[CHANNEL:META_SUBSCRIBE]: {}", message);
            var channelName = message.getOrDefault(SUBSCRIPTION_FIELD, "").toString();
            if (!message.isSuccessful()) {
                LOG.warn("Subscription failure: {}", message);
                var consumers = channelToConsumers.getOrDefault(channelName, emptySet());
                consumers.stream().findFirst().ifPresent(salesforceConsumer -> subscriptionFailed(salesforceConsumer, message));
            } else {
                // remember subscription
                LOG.info("Subscribed to channel {}", channelName);
                channelsToSubscribe.remove(channelName);

                // reset backoff interval
                handshakeBackoff.set(0);
            }
        });
    }

    private void subscriptionFailed(StreamingApiConsumer firstConsumer, Message message) {
        var channelName = message.getOrDefault(SUBSCRIPTION_FIELD, "").toString();
        var consumers = channelToConsumers.getOrDefault(channelName, emptySet());

        String error = (String) message.get(ERROR_FIELD);
        if (error == null) {
            error = "Missing error message";
        }

        Exception failure = getFailure(message);
        String msg = String.format("Error subscribing to %s: %s", firstConsumer.getTopicName(),
                failure != null ? failure.getMessage() : error);
        boolean abort = true;

        LOG.warn(msg);
        if (isTemporaryError(message)) {

            // retry after delay
            final long backoff = handshakeBackoff.getAndAdd(backoffIncrement);
            if (backoff > maxBackoff) {
                LOG.error("Subscribe aborted after exceeding {} msecs backoff", maxBackoff);
            } else {
                abort = false;

                try {
                    LOG.debug("Pausing for {} msecs before subscribe attempt", backoff);
                    Thread.sleep(backoff);
                    for (var consumer : consumers) {
                        subscribe(consumer);
                    }
                } catch (InterruptedException e) {
                    LOG.warn("Aborting subscribe on interrupt!", e);
                }
            }
        } else if (error.matches(INVALID_REPLAY_ID_PATTERN)) {
            abort = false;
            long fallBackReplayId
                    = firstConsumer.getEndpoint().getConfiguration().getFallBackReplayId();
            LOG.warn(error);
            LOG.warn("Falling back to replayId {} for channel {}", fallBackReplayId, channelName);
            REPLAY_EXTENSION.setReplayId(channelName, fallBackReplayId);
            for (var consumer : consumers) {
                subscribe(consumer);
            }
        }

        if (abort && client != null) {
            for (var consumer : consumers) {
                consumer.handleException(msg, new SalesforceException(msg, failure));
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        session = component.getSession();

        if (component.getLoginConfig().isLazyLogin()) {
            throw new CamelException("Lazy login is not supported by salesforce consumers.");
        }

        // create CometD client
        client = createClient(component, session);

        initMessageListeners();
        handshake();
    }

    private void initMessageListeners() {
        client.getChannel(META_HANDSHAKE).addListener(handshakeListener);
        client.getChannel(META_SUBSCRIBE).addListener(subscriptionListener);
        client.getChannel(META_CONNECT).addListener(connectListener);
    }

    private void handshake() throws CamelException {
        // connect to Salesforce cometd endpoint
        client.handshake();

        final long waitMs = MILLISECONDS.convert(HANDSHAKE_TIMEOUT_SEC, SECONDS);
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
                throw new CamelException(
                        String.format("Handshake request timeout after %s seconds",
                                HANDSHAKE_TIMEOUT_SEC));
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

    private void closeChannel(final String name) {
        if (client == null) {
            return;
        }

        final ClientSessionChannel channel = client.getChannel(name);
        for (var listener : channel.getListeners()) {
            channel.removeListener(listener);
        }
        channel.release();
    }

    @Override
    protected void doStop() throws Exception {
        closeChannel(META_CONNECT);
        closeChannel(META_SUBSCRIBE);
        closeChannel(META_HANDSHAKE);

        if (client == null) {
            return;
        }

        client.disconnect();
        boolean disconnected = client.waitFor(60_000, State.DISCONNECTED);
        if (!disconnected) {
            LOG.warn("Could not disconnect client connected to: {}", getEndpointUrl(component));
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
        /*
        The timeout should be greater than 110 sec as per https://github.com/cometd/cometd/issues/1142#issuecomment-1048256297
        and https://developer.salesforce.com/docs/atlas.en-us.api_streaming.meta/api_streaming/using_streaming_api_timeouts.htm
        */
        options.put(ClientTransport.MAX_NETWORK_DELAY_OPTION, 120000);
        if (component.getLongPollingTransportProperties() != null) {
            options.putAll(component.getLongPollingTransportProperties());
        }

        // check login access token
        if (session.getAccessToken() == null && !component.getLoginConfig().isLazyLogin()) {
            session.login(null);
        }

        CookieStore cookieStore = new CookieManager().getCookieStore();
        HttpCookieStore httpCookieStore = new HttpCookieStore.Default();

        ClientTransport transport = new JettyHttpClientTransport(options, httpClient) {
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
                String finalAccessToken = new String(accessToken);
                request.headers(h -> h.add(HttpHeader.AUTHORIZATION, "OAuth " + finalAccessToken));
            }

            @Override
            protected void storeCookies(URI uri, Map<String, List<String>> cookies) {
                try {
                    CookieManager cookieManager = new CookieManager(cookieStore, CookiePolicy.ACCEPT_ALL);
                    cookieManager.put(uri, cookies);

                    for (java.net.HttpCookie httpCookie : cookieManager.getCookieStore().getCookies()) {
                        httpCookieStore.add(uri, HttpCookie.from(httpCookie));
                    }
                } catch (IOException x) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Could not parse cookies", x);
                    }
                }
            }

            @Override
            protected HttpCookieStore getHttpCookieStore() {
                return httpCookieStore;
            }
        };

        BayeuxClient client = new BayeuxClient(getEndpointUrl(component), transport);

        // added eagerly to check for support during handshake
        client.addExtension(REPLAY_EXTENSION);

        return client;
    }

    public synchronized void subscribe(StreamingApiConsumer consumer) {
        // create subscription for consumer
        final String channelName = getChannelName(consumer.getTopicName());
        channelToConsumers.computeIfAbsent(channelName, key -> ConcurrentHashMap.newKeySet()).add(consumer);
        channelsToSubscribe.add(channelName);

        setReplayIdIfAbsent(consumer.getEndpoint());

        // channel message listener
        LOG.info("Subscribing to channel {}...", channelName);
        var messageListener = consumerToListener.computeIfAbsent(consumer, key -> (channel, message) -> {
            LOG.debug("Received Message: {}", message);
            // convert CometD message to Camel Message
            consumer.processMessage(channel, message);
        });

        // subscribe asynchronously
        final ClientSessionChannel clientChannel = client.getChannel(channelName);
        clientChannel.subscribe(messageListener);
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

    private void setReplayIdIfAbsent(final SalesforceEndpoint endpoint) {
        final String topicName = endpoint.getTopicName();

        final Optional<Long> replayId = determineReplayIdFor(endpoint, topicName);
        if (replayId.isPresent()) {
            final String channelName = getChannelName(topicName);

            final Long replayIdValue = replayId.get();

            REPLAY_EXTENSION.setReplayIdIfAbsent(channelName, replayIdValue);
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

    public synchronized void unsubscribe(StreamingApiConsumer consumer) {
        // channel name
        final String channelName = getChannelName(consumer.getTopicName());

        // unsubscribe from channel
        var consumers = channelToConsumers.get(channelName);
        if (consumers != null) {
            consumers.remove(consumer);
            if (consumers.isEmpty()) {
                channelToConsumers.remove(channelName);
            }
        }
        final ClientSessionChannel.MessageListener listener = consumerToListener.remove(consumer);
        if (listener != null) {
            LOG.debug("Unsubscribing from channel {}...", channelName);
            final ClientSessionChannel clientChannel = client.getChannel(channelName);
            // if there are other listeners on this channel, an unsubscribe message will not be sent,
            // so we're not going to listen for and expect an unsub response. Just unsub and move on.
            clientChannel.unsubscribe(listener);
            clientChannel.release();
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
