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
package org.apache.camel.websocket.jsr356;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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

import static java.util.stream.Collectors.toList;

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

    public void execute(final Consumer<Session> apply) {
        Session session = null;
        try {
            session = sessions.take();
            apply.accept(session);
        } catch (final RuntimeException re) {
            log.error(re.getMessage(), re);
            if (session.isOpen()) {
                try {
                    session.close(new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, re.getMessage()));
                } catch (final IOException errorOnClose) {
                    log.debug(errorOnClose.getMessage(), errorOnClose);
                }
            }
            session = null;
            throw re;
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            if (session != null) {
                sessions.offer(session);
            }
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
