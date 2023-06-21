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
package org.apache.camel.component.websocket;

import java.io.Serializable;
import java.net.SocketAddress;
import java.util.UUID;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebSocket
public class DefaultWebsocket implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(DefaultWebsocket.class);

    private final WebsocketConsumer consumer;
    private final NodeSynchronization sync;
    private Session session;
    private String connectionKey;
    private String pathSpec;
    private String subprotocol;
    private String relativePath;

    public DefaultWebsocket(NodeSynchronization sync, String pathSpec, WebsocketConsumer consumer) {
        this(sync, pathSpec, consumer, null, null);
    }

    public DefaultWebsocket(NodeSynchronization sync,
                            String pathSpec,
                            WebsocketConsumer consumer,
                            String subprotocol,
                            String relativePath) {
        this.sync = sync;
        this.consumer = consumer;
        this.pathSpec = pathSpec;
        this.subprotocol = subprotocol;
        this.relativePath = relativePath;
    }

    @OnWebSocketClose
    public void onClose(int closeCode, String message) {
        LOG.trace("onClose {} {}", closeCode, message);
        sync.removeSocket(this);
    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
        LOG.trace("onConnect {}", session);
        this.session = session;
        this.connectionKey = UUID.randomUUID().toString();
        sync.addSocket(this);
    }

    @OnWebSocketMessage
    public void onMessage(String message) {
        LOG.debug("onMessage: {}", message);
        if (this.consumer != null) {
            this.consumer.sendMessage(this.connectionKey, message, getRemoteAddress(), subprotocol, relativePath);
        } else {
            LOG.debug("No consumer to handle message received: {}", message);
        }
    }

    @OnWebSocketMessage
    public void onMessage(byte[] data, int offset, int length) {
        LOG.debug("onMessage: byte[]");
        if (this.consumer != null) {
            byte[] message = new byte[length];
            System.arraycopy(data, offset, message, 0, length);
            this.consumer.sendMessage(this.connectionKey, message, getRemoteAddress(), subprotocol, relativePath);
        } else {
            LOG.debug("No consumer to handle message received: byte[]");
        }
    }

    private SocketAddress getRemoteAddress() {
        Session current = session;
        return current != null ? current.getRemoteAddress() : null;
    }

    public Session getSession() {
        return session;
    }

    public String getPathSpec() {
        return pathSpec;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public String getConnectionKey() {
        return connectionKey;
    }

    public void setConnectionKey(String connectionKey) {
        this.connectionKey = connectionKey;
    }
}
