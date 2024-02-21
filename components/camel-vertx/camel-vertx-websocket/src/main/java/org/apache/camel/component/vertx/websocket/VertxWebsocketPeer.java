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

import java.util.Objects;
import java.util.UUID;

import io.vertx.core.http.ServerWebSocket;

/**
 * Represents a WebSocket peer connection
 */
public class VertxWebsocketPeer {
    private final ServerWebSocket webSocket;
    private final String rawPath;
    private final String path;
    private final String connectionKey;

    public VertxWebsocketPeer(ServerWebSocket webSocket, String rawPath) {
        this.webSocket = Objects.requireNonNull(webSocket);
        this.rawPath = Objects.requireNonNull(rawPath);
        this.path = webSocket.path();
        this.connectionKey = UUID.randomUUID().toString();
    }

    public ServerWebSocket getWebSocket() {
        return webSocket;
    }

    public String getRawPath() {
        return rawPath;
    }

    public String getPath() {
        return path;
    }

    public String getConnectionKey() {
        return connectionKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        VertxWebsocketPeer that = (VertxWebsocketPeer) o;
        return Objects.equals(connectionKey, that.connectionKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionKey);
    }
}
