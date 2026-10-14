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
package org.apache.camel.component.vertx.websocket;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;

import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocket;
import io.vertx.core.net.impl.ConnectionBase;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VertxWebsocketClientConsumer extends DefaultConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(VertxWebsocketClientConsumer.class);

    // the WebSocket that a reconnect attempt closed because the consumer was stopped: its close event is not a lost
    // connection
    private volatile WebSocket discarded;

    public VertxWebsocketClientConsumer(Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    public VertxWebsocketEndpoint getEndpoint() {
        return (VertxWebsocketEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        connect(false);
    }

    /**
     * Returns the WebSocket of the endpoint, connected with the handlers of this consumer. One call at a time for all
     * the client consumers of the endpoint (a route that is stopped and started again gets a new consumer): a start and
     * a reconnect attempt that both find no open WebSocket would each open one.
     *
     * @param reconnect whether this is a reconnect attempt. A reconnect attempt that opened a WebSocket and finds the
     *                  consumer no longer started closes that WebSocket and returns {@code null}. This is done while
     *                  the lock is held: a start that waits for the lock then finds the WebSocket closed and connects
     *                  again, instead of taking over a WebSocket that is about to be closed.
     */
    private WebSocket connect(boolean reconnect) throws Exception {
        Lock connectLock = getEndpoint().getClientConsumerConnectLock();
        connectLock.lock();
        try {
            // Use the BiConsumer overload: getWebSocket tells the callback whether the socket was already open.
            // This avoids a racy external isWebSocketOpen() check: a producer (which does not hold connectLock)
            // could open and cache a WebSocket between that check and getWebSocket(), causing the consumer to
            // treat a pre-existing producer socket as its own and close it on stop.
            AtomicReference<WebSocket> opened = new AtomicReference<>();
            WebSocket webSocket = getEndpoint().getWebSocket((ws, wasAlreadyOpen) -> {
                configureWebSocketHandlers(ws);
                if (!wasAlreadyOpen) {
                    opened.set(ws);
                }
            });
            WebSocket mine = opened.get();
            if (reconnect && mine != null && !isStarted()) {
                // Stopped while connecting. Not for a WebSocket that was already open: it belongs to the endpoint.
                discarded = mine;
                mine.close();
                return null;
            }
            return webSocket;
        } finally {
            connectLock.unlock();
        }
    }

    @Override
    protected void doStop() throws Exception {
        // Detach this consumer's handlers so a stopped consumer no longer processes incoming messages.
        Lock connectLock = getEndpoint().getClientConsumerConnectLock();
        connectLock.lock();
        try {
            getEndpoint().clearWebSocketHandlers(this);
        } finally {
            connectLock.unlock();
        }
        super.doStop();
    }

    protected void configureWebSocketHandlers(WebSocket webSocket) {
        getEndpoint().setHandlerOwner(this);
        webSocket.binaryMessageHandler(buffer -> this.handleResult(buffer.getBytes()));
        webSocket.textMessageHandler(this::handleResult);
        webSocket.closeHandler(event -> {
            if (isStarted() && webSocket != discarded) {
                LOG.info("WebSocket disconnected from {}. Attempting to reconnect...", webSocket.remoteAddress());
                VertxWebsocketConfiguration configuration = getEndpoint().getConfiguration();
                AtomicInteger reconnectAttempts = new AtomicInteger();

                Vertx vertx = getEndpoint().getVertx();
                vertx.setPeriodic(configuration.getReconnectInitialDelay(), configuration.getReconnectInterval(), timerId -> {
                    vertx.executeBlocking(() -> {
                        connect(true);
                        vertx.cancelTimer(timerId);
                        return null;
                    }, false)
                            .onComplete(result -> {
                                if (result.failed()) {
                                    Throwable cause = result.cause();
                                    if (cause != null) {
                                        LOG.debug("WebSocket reconnect to {} failed due to {}", webSocket.remoteAddress(),
                                                cause);
                                    }

                                    if (configuration.getMaxReconnectAttempts() > 0) {
                                        if (reconnectAttempts.incrementAndGet() == configuration.getMaxReconnectAttempts()) {
                                            LOG.warn(
                                                    "Reconnect max attempts ({}) exhausted. Giving up trying to reconnect to {}",
                                                    configuration.getMaxReconnectAttempts(), webSocket.remoteAddress());
                                            vertx.cancelTimer(timerId);
                                        }
                                    }
                                }
                            });
                });
            }
        });
        webSocket.exceptionHandler(exception -> {
            Throwable cause = exception.getCause();
            if (cause == ConnectionBase.CLOSED_EXCEPTION) {
                // Ignore as there's already a close handler registered
                return;
            }
            Exchange exchange = createExchange(false);
            getExceptionHandler().handleException("Error processing exchange", exchange, cause);
            releaseExchange(exchange, false);
        });
    }

    protected void handleResult(Object result) {
        Exchange exchange = createExchange(false);
        Message message = exchange.getMessage();
        message.setBody(result);
        processExchange(exchange);
    }

    protected void processExchange(Exchange exchange) {
        Vertx vertx = getEndpoint().getVertx();
        vertx.executeBlocking(() -> {
            createUoW(exchange);
            getProcessor().process(exchange);
            return null;
        }, false)
                .onComplete(result -> {
                    try {
                        if (result.failed()) {
                            Throwable cause = result.cause();
                            getExceptionHandler().handleException(cause);
                        }
                    } finally {
                        doneUoW(exchange);
                        releaseExchange(exchange, false);
                    }
                });
    }
}
