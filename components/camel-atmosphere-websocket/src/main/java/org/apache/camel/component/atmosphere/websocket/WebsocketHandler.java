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
package org.apache.camel.component.atmosphere.websocket;

import java.util.List;
import java.util.UUID;

import org.atmosphere.cpr.AtmosphereConfig;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.websocket.WebSocket;
import org.atmosphere.websocket.WebSocketProcessor.WebSocketException;
import org.atmosphere.websocket.WebSocketProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebsocketHandler implements WebSocketProtocol {
    private static final transient Logger LOG = LoggerFactory.getLogger(WebsocketHandler.class);
    
    protected WebsocketConsumer consumer;
    protected WebSocketStore store;

    @Override
    public void configure(AtmosphereConfig config) {
        // noop
    }
    
    @Override
    public void onClose(WebSocket webSocket) {
        LOG.debug("closing websocket");
        String connectionKey = store.getConnectionKey(webSocket);
        sendEventNotification(connectionKey, WebsocketConstants.ONCLOSE_EVENT_TYPE);
        store.removeWebSocket(webSocket);
        LOG.debug("websocket closed");
    }

    @Override
    public void onError(WebSocket webSocket, WebSocketException t) {
        LOG.error("websocket on error", t);
        String connectionKey = store.getConnectionKey(webSocket);
        sendEventNotification(connectionKey, WebsocketConstants.ONERROR_EVENT_TYPE);
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        LOG.debug("opening websocket");
        String connectionKey = UUID.randomUUID().toString();
        store.addWebSocket(connectionKey, webSocket);
        sendEventNotification(connectionKey, WebsocketConstants.ONOPEN_EVENT_TYPE);
        LOG.debug("websocket opened");
    }

    @Override
    public List<AtmosphereRequest> onMessage(WebSocket webSocket, String data) {
        LOG.debug("processing text message {}", data);
        String connectionKey = store.getConnectionKey(webSocket);
        consumer.sendMessage(connectionKey, data);
        LOG.debug("text message sent");
        return null;
    }
    
    @Override
    public List<AtmosphereRequest> onMessage(WebSocket webSocket, byte[] data, int offset, int length) {
        LOG.debug("processing byte message {}", data);
        String connectionKey = store.getConnectionKey(webSocket);
        if (length < data.length) {
            // create a copy that contains the relevant section as camel expects bytes without offset.
            // alternatively, we could pass a BAIS reading this byte array from the offset.
            byte[] rawdata = data;
            data = new byte[length];
            System.arraycopy(rawdata, offset, data, 0, length);
        }
        consumer.sendMessage(connectionKey, data);
        LOG.debug("byte message sent");
        return null;
    }

    public void setConsumer(WebsocketConsumer consumer) {
        this.consumer = consumer;
        this.store = consumer.getEndpoint().getWebSocketStore();
    }

    private void sendEventNotification(final String connectionKey, final int eventType) {
        if (consumer.isEnableEventsResending()) {
            consumer.sendEventNotification(connectionKey, eventType);
        }
    }
}
