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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.WebSocketProtocolHandshakeHandler;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedBinaryMessage;
import io.undertow.websockets.core.BufferedTextMessage;
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
import org.apache.camel.component.undertow.UndertowProducer;
import org.apache.camel.converter.IOConverter;
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

    private final UndertowWebSocketConnectionCallback callback;

    private UndertowConsumer consumer;

    private final Object consumerLock = new Object();

    private final WebSocketProtocolHandshakeHandler delegate;

    private final ChannelListener<WebSocketChannel> closeListener;

    private final UndertowReceiveListener receiveListener;

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
     * @param  channel       the channel to sent the {@code message} to
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
        this.delegate.handleRequest(exchange);
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
        List<WebSocketChannel> targetPeers
                = delegate.getPeerConnections().stream().filter(peerFilter).collect(Collectors.toList());
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
        synchronized (consumerLock) {
            if (consumer != null && this.consumer != null) {
                throw new IllegalStateException(
                        "Cannot call " + getClass().getName()
                                                + ".setConsumer(UndertowConsumer) with a non-null consumer before unsetting it via setConsumer(null)");
            }
            this.consumer = consumer;
        }
    }

    void sendEventNotificationIfNeeded(
            String connectionKey, WebSocketHttpExchange transportExchange, WebSocketChannel channel, EventType eventType) {
        synchronized (consumerLock) {
            synchronized (consumerLock) {
                if (consumer != null) {
                    if (consumer.getEndpoint().isFireWebSocketChannelEvents()) {
                        consumer.sendEventNotification(connectionKey, transportExchange, channel, eventType);
                    }
                } else {
                    LOG.debug("No consumer to handle a peer {} event type {}", connectionKey, eventType);
                }
            }
        }
    }

    /**
     * A {@link ExtendedWebSocketCallback} able to track sending one message to multiple peers.
     */
    static class MultiCallback implements ExtendedWebSocketCallback {
        private final AsyncCallback camelCallback;
        private final Exchange camelExchange;

        private Map<String, Throwable> errors;
        private final Object lock = new Object();
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
            synchronized (lock) {
                this.peers = new HashSet<>(peers);
            }
        }

        @Override
        public void closedBeforeSent(WebSocketChannel channel) {
            synchronized (lock) {
                peers.remove(channel);
                if (peers.isEmpty()) {
                    finish();
                }
            }
        }

        @Override
        public void complete(WebSocketChannel channel, Void context) {
            synchronized (lock) {
                peers.remove(channel);
                if (peers.isEmpty()) {
                    finish();
                }
            }
        }

        /**
         * {@link #finish()} should be called only inside a <code>synchronized(lock) { ... }</code> block to prevent
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
            synchronized (lock) {
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
            }
        }

    }

    /**
     * A {@link ChannelListener} that forwards the messages received over the WebSocket to
     * {@link CamelWebSocketHandler#consumer}.
     */
    class UndertowReceiveListener extends AbstractReceiveListener {

        @Override
        protected void onFullBinaryMessage(final WebSocketChannel channel, BufferedBinaryMessage message)
                throws IOException {
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
                synchronized (consumerLock) {
                    if (consumer != null) {
                        final Object outMsg = consumer.getEndpoint().isUseStreaming() ? new ByteArrayInputStream(bytes) : bytes;
                        consumer.sendMessage(connectionKey, channel, outMsg);
                    } else {
                        LOG.debug("No consumer to handle message received: {}", message);
                    }
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
            synchronized (consumerLock) {
                if (consumer != null) {
                    final Object outMsg = consumer.getEndpoint().isUseStreaming() ? new StringReader(text) : text;
                    consumer.sendMessage(connectionKey, channel, outMsg);
                } else {
                    LOG.debug("No consumer to handle message received: {}", message);
                }
            }
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
            final String connectionKey = UUID.randomUUID().toString();
            channel.setAttribute(UndertowConstants.CONNECTION_KEY, connectionKey);
            channel.getReceiveSetter().set(receiveListener);
            channel.addCloseTask(closeListener);
            sendEventNotificationIfNeeded(connectionKey, exchange, channel, EventType.ONOPEN);
            channel.resumeReceives();
        }

    }

}
