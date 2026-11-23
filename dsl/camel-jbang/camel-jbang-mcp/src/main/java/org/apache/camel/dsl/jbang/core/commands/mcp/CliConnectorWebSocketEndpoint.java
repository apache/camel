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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;

import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnPingMessage;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.UserData.TypedKey;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import io.vertx.core.buffer.Buffer;
import org.jboss.logging.Logger;

/** Inbound endpoint for the existing camel-cli-connector WebSocket protocol. */
@SessionScoped
@WebSocket(path = "/connect", endpointId = RemoteRuntimeUpgradeCheck.ENDPOINT_ID)
public class CliConnectorWebSocketEndpoint {

    private static final Logger LOG = Logger.getLogger(CliConnectorWebSocketEndpoint.class);
    private static final TypedKey<String> RESERVATION_KEY = RemoteRuntimeUpgradeCheck.RESERVATION_KEY;

    @Inject
    WebSocketConnection connection;

    @Inject
    RemoteRuntimeRegistry registry;

    @Inject
    RemoteRuntimeConfig config;

    private RemoteRuntimeConnection runtimeConnection;
    private RemoteRuntimeEntry runtimeEntry;

    @OnOpen
    void onOpen() {
        String reservation = connection.userData().get(RESERVATION_KEY);
        if (reservation == null) {
            LOG.warn("Rejecting a CLI connector WebSocket without an authenticated reservation");
            connection.closeAndAwait();
            return;
        }
        try {
            runtimeConnection = new RemoteRuntimeConnection(connection, config);
            runtimeEntry = registry.claim(reservation, runtimeConnection);
            LOG.infof("Remote Camel integration connected: %s", runtimeEntry.executionId());
        } catch (RuntimeException e) {
            registry.cancelReservation(reservation);
            LOG.warn("Could not register remote Camel integration WebSocket", e);
            connection.closeAndAwait();
        }
    }

    @OnTextMessage
    void onTextMessage(String text) {
        if (runtimeConnection == null) {
            connection.closeAndAwait();
            return;
        }
        try {
            runtimeConnection.acceptText(text);
        } catch (RuntimeException e) {
            LOG.debugf("Closing invalid remote CLI connector frame for %s: %s",
                    runtimeEntry != null ? runtimeEntry.executionId() : "unregistered", e.getMessage());
            connection.closeAndAwait();
        }
    }

    @OnPingMessage
    void onPing(Buffer payload) {
        if (runtimeConnection != null) {
            runtimeConnection.touch();
        }
    }

    @OnClose
    void onClose() {
        if (runtimeEntry != null && runtimeConnection != null) {
            registry.disconnect(runtimeEntry, runtimeConnection);
            LOG.infof("Remote Camel integration disconnected: %s", runtimeEntry.executionId());
        }
    }
}
