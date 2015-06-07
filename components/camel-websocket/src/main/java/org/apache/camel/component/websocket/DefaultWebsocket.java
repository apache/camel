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
package org.apache.camel.component.websocket;

import java.io.Serializable;
import java.util.UUID;

import org.eclipse.jetty.websocket.WebSocket;
import org.eclipse.jetty.websocket.WebSocket.OnBinaryMessage;
import org.eclipse.jetty.websocket.WebSocket.OnTextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultWebsocket implements WebSocket, OnTextMessage, OnBinaryMessage, Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(DefaultWebsocket.class);

    private final WebsocketConsumer consumer;
    private final NodeSynchronization sync;
    private Connection connection;
    private String connectionKey;

    public DefaultWebsocket(NodeSynchronization sync, WebsocketConsumer consumer) {
        this.sync = sync;
        this.consumer = consumer;
    }

    @Override
    public void onClose(int closeCode, String message) {
        LOG.trace("onClose {} {}", closeCode, message);
        sync.removeSocket(this);
    }

    @Override
    public void onOpen(Connection connection) {
        LOG.trace("onOpen {}", connection);
        this.connection = connection;
        this.connectionKey = UUID.randomUUID().toString();
        sync.addSocket(this);
    }

    @Override
    public void onMessage(String message) {
        LOG.debug("onMessage: {}", message);
        if (this.consumer != null) {
            this.consumer.sendMessage(this.connectionKey, message);
        } else {
            LOG.debug("No consumer to handle message received: {}", message);
        }
    }


    @Override
    public void onMessage(byte[] data, int offset, int length) {
        LOG.debug("onMessage: byte[]");
        if (this.consumer != null) {
            byte[] message = new byte[length];
            System.arraycopy(data, offset, message, 0, length);
            this.consumer.sendMessage(this.connectionKey, message);
        } else {
            LOG.debug("No consumer to handle message received: byte[]");
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public String getConnectionKey() {
        return connectionKey;
    }

    public void setConnectionKey(String connectionKey) {
        this.connectionKey = connectionKey;
    }
}
