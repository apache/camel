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
package org.apache.camel.component.undertow.handlers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.util.AttachmentKey;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.MimeMappings;
import io.undertow.util.StatusCodes;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.WebSocketProtocolHandshakeHandler;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedBinaryMessage;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.CloseMessage;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.undertow.UndertowConstants;
import org.apache.camel.component.undertow.UndertowConstants.EventType;
import org.apache.camel.component.undertow.UndertowConsumer;
import org.apache.camel.component.undertow.UndertowEndpoint;
import org.apache.camel.component.undertow.UndertowProducer;
import org.apache.camel.component.undertow.spi.UndertowSecurityProvider;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.http.base.OAuthHttpSecuritySupport;
import org.apache.camel.http.base.OAuthHttpSecuritySupport.Validation;
import org.apache.camel.spi.OAuthTokenValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.ChannelListener;
import org.xnio.Pooled;

/**
 * An {@link HttpHandler} that delegates to {@link WebSocketProtocolHandshakeHandler} and provides some wiring to
 * connect {@link UndertowConsumer} with {@link UndertowProducer}.
 */
public class CamelWebSocketHandler implements HttpHandler {
    private static final Logger LOG = LoggerFactory.getLogger(CamelWebSocketHandler.class);
    // WebSocket handshakes use WebSocketHttpExchange attachments; HTTP requests use UndertowConsumer's key.
    private static final AttachmentKey<OAuthTokenValidationResult> OAUTH_TOKEN_VALIDATION_RESULT_ATTACHMENT
            = AttachmentKey.create(OAuthTokenValidationResult.class);
    private static final AttachmentKey<SecurityProviderResult> SECURITY_PROVIDER_RESULT_ATTACHMENT
            = AttachmentKey.create(SecurityProviderResult.class);
    private static final String SECURITY_PROVIDER_RESULT = CamelWebSocketHandler.class.getName() + ".securityProviderResult";

    private final UndertowWebSocketConnectionCallback callback;

    private UndertowConsumer consumer;

    /**
     * The endpoint of the last consumer set on this handler, whose security settings apply to the path.
     */
    private UndertowEndpoint consumerEndpoint;

    private final List<UndertowEndpoint> producerEndpoints = new CopyOnWriteArrayList<>();

    private final Lock consumerLock = new ReentrantLock();

    private final WebSocketProtocolHandshakeHandler delegate;

    private final ChannelListener<WebSocketChannel> closeListener;

    private final UndertowReceiveListener receiveListener;

    private final HttpHandler upgradeHandler = this::upgrade;

    private final HttpHandler consumerRequestHandler = this::handleConsumerRequest;

    /**
     * The handlers of the consumer, such as its access log, followed by {@link #upgradeHandler}.
     */
    private volatile HttpHandler consumerHandler = upgradeHandler;

    private volatile HttpHandler entryHandler = consumerRequestHandler;

    public CamelWebSocketHandler() {
        this.receiveListener = new UndertowReceiveListener();
        this.callback = new UndertowWebSocketConnectionCallback();
        this.closeListener = (WebSocketChannel channel) -> sendEventNotificationIfNeeded(
                (String) channel.getAttribute(UndertowConstants.CONNECTION_KEY), null, channel, EventType.ONCLOSE);

        this.delegate = Handlers.websocket(callback);
    }

    /**
     * Send the given {@code message} to the given {@code channel} and report the outcome to the given {@code callback}
     * within the given {@code timeoutMillis}.
     *
     * @param  channel       the channel to send the {@code message} to
     * @param  message       the message to send
     * @param  callback      where to report the outcome
     * @param  timeoutMillis the timeout in milliseconds
     * @throws IOException
     */
    private static void send(
            WebSocketChannel channel, Object message, ExtendedWebSocketCallback callback,
            long timeoutMillis)
            throws IOException {
        if (channel.isOpen()) {
            if (message instanceof String) {
                WebSockets.sendText((String) message, channel, callback);
            } else if (message instanceof byte[]) {
                ByteBuffer buffer = ByteBuffer.wrap((byte[]) message);
                WebSockets.sendBinary(buffer, channel, callback, timeoutMillis);
            } else if (message instanceof Reader) {
                Reader r = (Reader) message;
                WebSockets.sendText(IOConverter.toString(r), channel, callback);
            } else if (message instanceof InputStream) {
                InputStream in = (InputStream) message;
                ByteBuffer buffer = ByteBuffer.wrap(IOConverter.toBytes(in));
                WebSockets.sendBinary(buffer, channel, callback, timeoutMillis);
            } else {
                throw new RuntimeCamelException(
                        "Unexpected type of message " + message.getClass().getName() + "; expected String, byte[], "
                                                + Reader.class.getName() + " or " + InputStream.class.getName());
            }
        } else {
            callback.closedBeforeSent(channel);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        entryHandler.handleRequest(exchange);
    }

    /**
     * The handler that applies the security settings of the path to an upgrade request and then performs the WebSocket
     * handshake. A consumer can run its own handlers, such as its access log, before it.
     */
    public HttpHandler getUpgradeHandler() {
        return upgradeHandler;
    }

    /**
     * Lets the given security provider wrap this handler, as it wraps the handlers of HTTP endpoints. The handler stays
     * registered as is, so that it remains shared by the consumer and the producers of the path.
     */
    public void wrapWith(UndertowSecurityProvider securityProvider) throws Exception {
        HttpHandler wrapped = securityProvider.wrapHttpHandler(consumerRequestHandler);
        // a provider that returns no handler disables the path, as it does for HTTP endpoints
        this.entryHandler = wrapped != null ? wrapped : ResponseCodeHandler.HANDLE_405;
    }

    private void handleConsumerRequest(HttpServerExchange exchange) throws Exception {
        consumerHandler.handleRequest(exchange);
    }

    private void upgrade(HttpServerExchange exchange) throws Exception {
        List<UndertowEndpoint> endpoints = securityEndpoints();
        if (endpoints.stream().noneMatch(CamelWebSocketHandler::hasSecurityChecks)) {
            this.delegate.handleRequest(exchange);
            return;
        }
        if (exchange.isInIoThread()) {
            exchange.dispatch(upgradeHandler);
            return;
        }
        Set<String> authenticatedEndpoints = new HashSet<>();
        Map<String, Object> headers = new HashMap<>();
        for (UndertowEndpoint endpoint : endpoints) {
            OAuthHttpSecuritySupport oauthHttpSecurity = endpoint.getOauthHttpSecurity();
            if (oauthHttpSecurity != null) {
                Validation validation = oauthHttpSecurity.validate(endpoint.getCamelContext(), authorizationHeaders(exchange));
                exchange.getRequestHeaders().remove(Headers.AUTHORIZATION);
                if (!validation.isAuthenticated()) {
                    reject(exchange, validation);
                    return;
                }
                exchange.putAttachment(OAUTH_TOKEN_VALIDATION_RESULT_ATTACHMENT, validation.getValidationResult());
            }
            if (endpoint.requiresAuthentication()) {
                int statusCode = endpoint.authenticate(exchange);
                if (statusCode != StatusCodes.OK) {
                    exchange.setStatusCode(statusCode);
                    exchange.endExchange();
                    return;
                }
                authenticatedEndpoints.add(endpoint.getEndpointUri());
                if (endpoint.getSecurityProvider() != null) {
                    endpoint.getSecurityProvider().addHeader(headers::put, exchange);
                }
            }
        }
        if (!authenticatedEndpoints.isEmpty()) {
            exchange.putAttachment(SECURITY_PROVIDER_RESULT_ATTACHMENT,
                    new SecurityProviderResult(authenticatedEndpoints, headers));
        }
        this.delegate.handleRequest(exchange);
    }

    /**
     * The endpoints whose security settings apply to the path: the consumer's, also while it is stopped, otherwise the
     * producers'. All the Camel endpoints of a path share its WebSocket connections.
     */
    private List<UndertowEndpoint> securityEndpoints() {
        consumerLock.lock();
        try {
            if (consumerEndpoint != null) {
                return List.of(consumerEndpoint);
            }
        } finally {
            consumerLock.unlock();
        }
        return producerEndpoints.stream().distinct().toList();
    }

    private static boolean hasSecurityChecks(UndertowEndpoint endpoint) {
        return endpoint.getOauthHttpSecurity() != null || endpoint.requiresAuthentication();
    }

    private static boolean isAuthenticated(WebSocketChannel channel, List<UndertowEndpoint> endpoints) {
        for (UndertowEndpoint endpoint : endpoints) {
            if (!isAuthenticated(channel, endpoint)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the handshake of the given channel passed the security checks of the given endpoint: its OAuth
     * validation, its security provider and its allowed roles. Always {@code true} for an endpoint without such checks.
     */
    public static boolean isAuthenticated(WebSocketChannel channel, UndertowEndpoint endpoint) {
        if (endpoint.getOauthHttpSecurity() != null && (channel == null
                || !(channel.getAttribute(
                        OAuthHttpSecuritySupport.OAUTH_TOKEN_VALIDATION_RESULT) instanceof OAuthTokenValidationResult))) {
            return false;
        }
        if (endpoint.requiresAuthentication()) {
            return channel != null
                    && channel.getAttribute(SECURITY_PROVIDER_RESULT) instanceof SecurityProviderResult result
                    && result.endpointUris.contains(endpoint.getEndpointUri());
        }
        return true;
    }

    /**
     * The headers that the security providers added during the handshake of the given channel.
     */
    public static Map<String, Object> getSecurityProviderHeaders(WebSocketChannel channel) {
        return channel.getAttribute(SECURITY_PROVIDER_RESULT) instanceof SecurityProviderResult result
                ? result.headers : Collections.emptyMap();
    }

    private static void reject(HttpServerExchange exchange, Validation validation) {
        exchange.setStatusCode(validation.getRejectionStatusCode());
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, MimeMappings.DEFAULT_MIME_MAPPINGS.get("txt"));
        if (validation.getWwwAuthenticate() != null) {
            exchange.getResponseHeaders().put(Headers.WWW_AUTHENTICATE, validation.getWwwAuthenticate());
        }
        exchange.getResponseSender().send(validation.getResponseBody());
    }

    private static List<String> authorizationHeaders(HttpServerExchange exchange) {
        HeaderValues values = exchange.getRequestHeaders().get(Headers.AUTHORIZATION);
        if (values == null) {
            return List.of();
        }
        List<String> answer = new ArrayList<>(values.size());
        for (String value : values) {
            answer.add(value);
        }
        return answer;
    }

    /**
     * Send the given {@code message} to one or more channels selected using the given {@code peerFilter} within the
     * given {@code timeout} and report the outcome to the given {@code camelExchange} and {@code camelCallback}.
     *
     * @param  peerFilter    a {@link Predicate} to apply to the set of peers obtained via {@link #delegate}'s
     *                       {@link WebSocketProtocolHandshakeHandler#getPeerConnections()}
     * @param  message       the message to send
     * @param  camelExchange to notify about the outcome
     * @param  camelCallback to notify about the outcome
     * @param  timeout       in milliseconds
     * @return               {@code true} if the execution finished synchronously or {@code false} otherwise
     * @throws IOException
     */
    public boolean send(
            Predicate<WebSocketChannel> peerFilter, Object message, final int timeout,
            final Exchange camelExchange, final AsyncCallback camelCallback)
            throws IOException {
        // only the peers whose handshake passed the security checks of the path receive messages
        List<UndertowEndpoint> endpoints = securityEndpoints();
        List<WebSocketChannel> targetPeers = delegate.getPeerConnections().stream()
                .filter(peer -> isAuthenticated(peer, endpoints))
                .filter(peerFilter)
                .collect(Collectors.toList());
        if (targetPeers.isEmpty()) {
            camelCallback.done(true);
            return true;
        } else {
            /* There are some peers to send the message to */
            MultiCallback wsCallback = new MultiCallback(targetPeers, camelCallback, camelExchange);
            for (WebSocketChannel peer : targetPeers) {
                send(peer, message, wsCallback, timeout);
            }
            return false;
        }
    }

    /**
     * @param consumer the {@link UndertowConsumer} to set
     */
    public void setConsumer(UndertowConsumer consumer) {
        setConsumer(consumer, null);
    }

    /**
     * @param consumer        the {@link UndertowConsumer} to set
     * @param consumerHandler the handler that runs before {@link #getUpgradeHandler()} for this consumer, or
     *                        {@code null}
     */
    public void setConsumer(UndertowConsumer consumer, HttpHandler consumerHandler) {
        consumerLock.lock();
        try {
            if (consumer != null && this.consumer != null) {
                throw new IllegalStateException(
                        "Cannot call " + getClass().getName()
                                                + ".setConsumer(UndertowConsumer) with a non-null consumer before unsetting it via setConsumer(null)");
            }
            this.consumer = consumer;
            if (consumer != null) {
                // both are kept when the consumer is unset, so that the path stays guarded while the consumer is stopped
                this.consumerEndpoint = consumer.getEndpoint();
                this.consumerHandler = consumerHandler != null ? consumerHandler : upgradeHandler;
            }
        } finally {
            consumerLock.unlock();
        }
    }

    /**
     * Registers the endpoint of a producer that sends to the peers of this handler. A path without a consumer applies
     * the security settings of its producers.
     */
    public void addProducer(UndertowEndpoint endpoint) {
        producerEndpoints.add(endpoint);
    }

    public void removeProducer(UndertowEndpoint endpoint) {
        producerEndpoints.remove(endpoint);
    }

    void sendEventNotificationIfNeeded(
            String connectionKey, WebSocketHttpExchange transportExchange, WebSocketChannel channel, EventType eventType) {
        consumerLock.lock();
        try {
            if (consumer != null) {
                if (consumer.getEndpoint().isFireWebSocketChannelEvents()) {
                    consumer.sendEventNotification(connectionKey, transportExchange, channel, eventType);
                }
            } else {
                LOG.debug("No consumer to handle a peer {} event type {}", connectionKey, eventType);
            }
        } finally {
            consumerLock.unlock();
        }
    }

    /**
     * A {@link ExtendedWebSocketCallback} able to track sending one message to multiple peers.
     */
    static class MultiCallback implements ExtendedWebSocketCallback {
        private final AsyncCallback camelCallback;
        private final Exchange camelExchange;

        private Map<String, Throwable> errors;
        private final Lock lock = new ReentrantLock();
        /**
         * Initially, this set contains all peers where we plan to send the message. Then the peers are removed one by
         * one as we are notified via {@link #complete(WebSocketChannel, Void)} or
         * {@link #onError(WebSocketChannel, Void, Throwable)}. This set being empty signals that all peers have
         * finished sending the message.
         */
        private final Set<WebSocketChannel> peers;

        public MultiCallback(Collection<WebSocketChannel> peers, AsyncCallback camelCallback, Exchange camelExchange) {
            this.camelCallback = camelCallback;
            this.camelExchange = camelExchange;
            this.peers = new HashSet<>(peers);
        }

        @Override
        public void closedBeforeSent(WebSocketChannel channel) {
            lock.lock();
            try {
                peers.remove(channel);
                if (peers.isEmpty()) {
                    finish();
                }
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void complete(WebSocketChannel channel, Void context) {
            lock.lock();
            try {
                peers.remove(channel);
                if (peers.isEmpty()) {
                    finish();
                }
            } finally {
                lock.unlock();
            }
        }

        /**
         * This method should be called only inside a <code>synchronized(lock) { ... }</code> block to prevent
         * concurrent access to {@link #errors}.
         */
        private void finish() {
            if (errors != null && !errors.isEmpty()) {
                if (errors.size() == 1) {
                    final Entry<String, Throwable> en = errors.entrySet().iterator().next();
                    final String msg = "Delivery to the WebSocket peer " + en.getKey() + " channels has failed";
                    camelExchange.setException(new CamelExchangeException(msg, camelExchange, en.getValue()));
                } else {
                    final StringBuilder msg = new StringBuilder(
                            "Delivery to the following WebSocket peer channels has failed: ");
                    for (Entry<String, Throwable> en : errors.entrySet()) {
                        msg.append("\n    ").append(en.getKey()).append(en.getValue().getMessage());
                    }
                    camelExchange.setException(new CamelExchangeException(msg.toString(), camelExchange));
                }
            }
            camelCallback.done(false);
        }

        @Override
        public void onError(WebSocketChannel channel, Void context, Throwable throwable) {
            lock.lock();
            try {
                peers.remove(channel);
                final String connectionKey = (String) channel.getAttribute(UndertowConstants.CONNECTION_KEY);
                if (connectionKey == null) {
                    throw new RuntimeCamelException(
                            UndertowConstants.CONNECTION_KEY + " attribute not found on "
                                                    + WebSocketChannel.class.getSimpleName() + " " + channel);
                }
                if (errors == null) {
                    errors = new HashMap<>();
                }
                errors.put(connectionKey, throwable);
                if (peers.isEmpty()) {
                    finish();
                }
            } finally {
                lock.unlock();
            }
        }

    }

    /**
     * A {@link ChannelListener} that forwards the messages received over the WebSocket to
     * {@link CamelWebSocketHandler#consumer}.
     */
    class UndertowReceiveListener extends AbstractReceiveListener {

        @Override
        protected void onFullBinaryMessage(final WebSocketChannel channel, BufferedBinaryMessage message) {
            LOG.debug("onFullBinaryMessage()");
            final String connectionKey = (String) channel.getAttribute(UndertowConstants.CONNECTION_KEY);
            if (connectionKey == null) {
                throw new RuntimeCamelException(
                        UndertowConstants.CONNECTION_KEY + " attribute not found on "
                                                + WebSocketChannel.class.getSimpleName() + " " + channel);
            }
            final Pooled<ByteBuffer[]> data = message.getData();
            try {
                final ByteBuffer[] buffers = data.getResource();
                int len = 0;
                for (ByteBuffer buffer : buffers) {
                    len += buffer.remaining();
                }
                byte[] bytes = new byte[len];
                int offset = 0;
                for (ByteBuffer buffer : buffers) {
                    int increment = buffer.remaining();
                    buffer.get(bytes, offset, increment);
                    offset += increment;
                }
                consumerLock.lock();
                try {
                    if (consumer != null) {
                        final Object outMsg = consumer.getEndpoint().isUseStreaming() ? new ByteArrayInputStream(bytes) : bytes;
                        consumer.sendMessage(connectionKey, channel, outMsg);
                    } else {
                        LOG.debug("No consumer to handle message received: {}", message);
                    }
                } finally {
                    consumerLock.unlock();
                }
            } finally {
                data.free();
            }
        }

        @Override
        protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) {
            final String text = message.getData();
            LOG.debug("onFullTextMessage(): {}", text);
            final String connectionKey = (String) channel.getAttribute(UndertowConstants.CONNECTION_KEY);
            if (connectionKey == null) {
                throw new RuntimeCamelException(
                        UndertowConstants.CONNECTION_KEY + " attribute not found on "
                                                + WebSocketChannel.class.getSimpleName() + " " + channel);
            }
            consumerLock.lock();
            try {
                if (consumer != null) {
                    final Object outMsg = consumer.getEndpoint().isUseStreaming() ? new StringReader(text) : text;
                    consumer.sendMessage(connectionKey, channel, outMsg);
                } else {
                    LOG.debug("No consumer to handle message received: {}", message);
                }
            } finally {
                consumerLock.unlock();
            }
        }

    }

    /**
     * The endpoints whose security provider and allowed roles a handshake passed, and the headers the providers added.
     */
    private static final class SecurityProviderResult {
        private final Set<String> endpointUris;
        private final Map<String, Object> headers;

        private SecurityProviderResult(Set<String> endpointUris, Map<String, Object> headers) {
            this.endpointUris = Collections.unmodifiableSet(endpointUris);
            this.headers = Collections.unmodifiableMap(headers);
        }
    }

    /**
     * Sets the {@link UndertowReceiveListener} to the given channel on connect.
     */
    class UndertowWebSocketConnectionCallback implements WebSocketConnectionCallback {

        public UndertowWebSocketConnectionCallback() {
        }

        @Override
        public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel) {
            LOG.trace("onConnect {}", exchange);
            OAuthTokenValidationResult oauthTokenValidationResult
                    = exchange.getAttachment(OAUTH_TOKEN_VALIDATION_RESULT_ATTACHMENT);
            if (oauthTokenValidationResult != null) {
                channel.setAttribute(OAuthHttpSecuritySupport.OAUTH_TOKEN_VALIDATION_RESULT, oauthTokenValidationResult);
            }
            SecurityProviderResult securityProviderResult = exchange.getAttachment(SECURITY_PROVIDER_RESULT_ATTACHMENT);
            if (securityProviderResult != null) {
                channel.setAttribute(SECURITY_PROVIDER_RESULT, securityProviderResult);
            }
            if (!isAuthenticated(channel, securityEndpoints())) {
                // the handshake did not pass the security checks that now apply to the path, for example because it
                // completed before a consumer requiring them was set on this handler: fail closed
                LOG.warn("Closing WebSocket channel whose handshake did not pass the security checks");
                WebSockets.sendClose(CloseMessage.MSG_VIOLATES_POLICY, "Authentication required", channel, null);
                return;
            }
            final String connectionKey = UUID.randomUUID().toString();
            channel.setAttribute(UndertowConstants.CONNECTION_KEY, connectionKey);
            channel.getReceiveSetter().set(receiveListener);
            channel.addCloseTask(closeListener);
            sendEventNotificationIfNeeded(connectionKey, exchange, channel, EventType.ONOPEN);
            channel.resumeReceives();
        }

    }

}
