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

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamelServerEndpoint extends Endpoint {
    private final Logger log = LoggerFactory.getLogger(CamelServerEndpoint.class);

    private final Collection<BiConsumer<Session, Object>> endpoints = new CopyOnWriteArrayList<>();

    private Session session;

    Collection<BiConsumer<Session, Object>> getEndpoints() {
        return endpoints;
    }

    Session getSession() {
        return session;
    }

    @Override
    public void onOpen(final Session session, final EndpointConfig endpointConfig) {
        this.session = session;
        log.debug("Session opened #{}", session.getId());
        session.addMessageHandler(InputStream.class, this::propagateExchange);
        session.addMessageHandler(String.class, this::propagateExchange);
    }

    @Override
    public void onClose(final Session session, final CloseReason closeReason) {
        log.debug("Session closed #{}", session.getId());
    }

    @Override
    public void onError(final Session session, final Throwable throwable) {
        synchronized (session) {
            if (session.isOpen()) {
                try {
                    session.close(new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, "an exception occurred"));
                } catch (final IOException e) {
                    log.debug("Error closing session #{}", session.getId(), e);
                }
            }
        }
        log.debug("Error on session #{}", session.getId(), throwable);
    }

    private void propagateExchange(final Object message) {
        synchronized (session) {
            endpoints.forEach(consumer -> consumer.accept(session, message));
        }
    }
}
