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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.WebSocketBase;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultAsyncProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VertxWebsocketProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(VertxWebsocketProducer.class);

    public VertxWebsocketProducer(VertxWebsocketEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public VertxWebsocketEndpoint getEndpoint() {
        return (VertxWebsocketEndpoint) super.getEndpoint();
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            Message in = exchange.getIn();
            Object message = in.getBody();

            if (message == null) {
                // Nothing to do for a null body
                callback.done(true);
                return true;
            }

            Map<String, WebSocketBase> connectedPeers = getConnectedPeers(exchange);
            VertxWebsocketResultHandler vertxWebsocketResultHandler
                    = new VertxWebsocketResultHandler(exchange, callback, connectedPeers.keySet());

            if (connectedPeers.isEmpty()) {
                callback.done(true);
            }

            // Send message to each peer then record and process the results asynchronously
            connectedPeers.forEach((connectionKey, webSocket) -> {
                Handler<AsyncResult<Void>> handler = result -> {
                    if (!result.succeeded()) {
                        vertxWebsocketResultHandler.onError(connectionKey, result.cause());
                    }
                    vertxWebsocketResultHandler.onResult(connectionKey);
                };

                if (webSocket != null) {
                    if (webSocket.isClosed()) {
                        LOG.warn("WebSocket peer connection with key {} is already closed", connectionKey);
                        vertxWebsocketResultHandler.onResult(connectionKey);
                    } else {
                        if (message instanceof String) {
                            webSocket.writeTextMessage((String) message, handler);
                        } else if (message instanceof byte[]) {
                            webSocket.writeBinaryMessage(Buffer.buffer((byte[]) message), handler);
                        } else {
                            // Try to fallback on String conversion
                            webSocket.writeTextMessage(in.getBody(String.class), handler);
                        }
                    }
                } else {
                    LOG.warn("No WebSocket peer connection found for connection key {}", connectionKey);
                    vertxWebsocketResultHandler.onResult(connectionKey);
                }
            });

            return false;
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }
    }

    private Map<String, WebSocketBase> getConnectedPeers(Exchange exchange) throws Exception {
        Map<String, WebSocketBase> connectedPeers = new HashMap<>();
        VertxWebsocketEndpoint endpoint = getEndpoint();

        String connectionKey = exchange.getMessage().getHeader(VertxWebsocketContants.CONNECTION_KEY, String.class);
        if (connectionKey != null) {
            if (endpoint.isManagedPort()) {
                Stream.of(connectionKey.split(","))
                        .forEach(key -> connectedPeers.put(key, endpoint.findPeerForConnectionKey(key)));
            } else {
                // The producer is invoking an external server not managed by camel
                connectedPeers.put(UUID.randomUUID().toString(), endpoint.getWebSocket(exchange));
            }
        } else {
            connectedPeers.put(UUID.randomUUID().toString(), endpoint.getWebSocket(exchange));
        }

        if (isSendToAll(exchange.getMessage())) {
            // Try to find all peers connected to an existing vertx-websocket consumer
            Map<String, ServerWebSocket> peers = endpoint.findPeersForHostPort();
            if (peers != null) {
                peers.forEach(connectedPeers::put);
            }
        }

        return connectedPeers;
    }

    private boolean isSendToAll(Message message) {
        Boolean value = message.getHeader(VertxWebsocketContants.SEND_TO_ALL, getEndpoint().getConfiguration().isSendToAll(),
                Boolean.class);
        return value == null ? false : value;
    }
}
