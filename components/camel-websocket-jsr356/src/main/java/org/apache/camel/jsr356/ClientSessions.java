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
package org.apache.camel.jsr356;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toList;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.IntStream;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ClientSessions implements Closeable {
    private final Logger log = LoggerFactory.getLogger(ClientSessions.class);

    private final int expectedCount;
    private final URI uri;
    private final ClientEndpointConfig config;
    private final WebSocketContainer container;
    private final BlockingQueue<Session> sessions;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final BiConsumer<Session, Object> onMessage;

    ClientSessions(final int count, final URI uri, final ClientEndpointConfig config,
                   final BiConsumer<Session, Object> onMessage) {
        this.uri = uri;
        this.expectedCount = count;
        this.config = config;
        this.onMessage = onMessage;
        this.sessions = new ArrayBlockingQueue<>(expectedCount);
        // todo: grab it from the context?
        this.container = ContainerProvider.getWebSocketContainer();
    }

    public void prepare() {
        sessions.addAll(IntStream.range(0, expectedCount).mapToObj(idx -> doConnect()).collect(toList()));
    }

    public <T> CompletionStage<T> execute(final Function<Session, CompletionStage<T>> apply) {
        try {
            final Session session = sessions.take();
            return apply.apply(session)
                    .handle((result, exception) -> {
                        sessions.offer(session);
                        if (exception != null) {
                            if (RuntimeException.class.isInstance(exception)) {
                                throw RuntimeException.class.cast(exception);
                            }
                            if (Error.class.isInstance(exception)) {
                                throw Error.class.cast(exception);
                            }
                            throw new IllegalStateException(exception);
                        }
                        return result;
                    });
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return completedFuture(null);
        }
    }

    private Session doConnect() {
        try {
            final Session session = container.connectToServer(new Endpoint() {
                @Override
                public void onOpen(final Session session, final EndpointConfig endpointConfig) {
                    log.debug("Session opened #{}", session.getId());
                }

                @Override
                public void onClose(final Session session, final CloseReason closeReason) {
                    sessions.remove(session);
                    log.debug("Session closed #{}", session.getId());
                }

                @Override
                public void onError(final Session session, final Throwable throwable) {
                    if (session.isOpen()) {
                        try {
                            session.close(
                                    new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, "an exception occured"));
                        } catch (final IOException e) {
                            log.debug("Error closing session #{}", session.getId(), e);
                        }
                    }
                    sessions.remove(session);
                    log.debug("Error on session #{}", session.getId(), throwable);

                    if (!closed.get()) { // try to repopulate it
                        sessions.offer(doConnect());
                    }
                }
            }, config, uri);
            if (onMessage != null) {
                session.addMessageHandler(InputStream.class, message -> onMessage.accept(session, message));
                session.addMessageHandler(String.class, message -> onMessage.accept(session, message));
            }
            return session;
        } catch (final DeploymentException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        closed.set(true);
        sessions.forEach(it -> {
            if (it.isOpen()) {
                try {
                    it.close();
                } catch (final IOException e) {
                    log.debug(e.getMessage(), e);
                }
            }
        });
        sessions.clear();
    }
}
