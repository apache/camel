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
import org.eclipse.jetty.websocket.WebSocket.OnTextMessage;

public class DefaultWebsocket implements WebSocket, OnTextMessage, Serializable {

    private static final long serialVersionUID = -575701599776801400L;
    private Connection connection;
    private String connectionKey;
    private NodeSynchronization sync;

    private transient WebsocketConsumer consumer;

    public DefaultWebsocket(NodeSynchronization sync, WebsocketConsumer consumer) {
        this.sync = sync;
        this.consumer = consumer;
    }

    @Override
    public void onClose(int closeCode, String message) {
        sync.removeSocket(this);
    }

    @Override
    public void onOpen(Connection connection) {
        this.connection = connection;

        this.connectionKey = UUID.randomUUID().toString();
        sync.addSocket(this);
    }

    @Override
    public void onMessage(String message) {
        if (this.consumer != null) {
            this.consumer.sendExchange(this.connectionKey, message);
        }
        // consumer is not set, this is produce only websocket
        // TODO - 06.06.2011, LK - deliver exchange to dead letter channel
    }

    // getters and setters
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

    public void setConsumer(WebsocketConsumer consumer) {
        this.consumer = consumer;
    }

}
