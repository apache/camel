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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.servlet.http.HttpServletRequest;

import org.eclipse.jetty.websocket.WebSocket;
import org.eclipse.jetty.websocket.WebSocketServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebsocketComponentServlet extends WebSocketServlet {
    private static final long serialVersionUID = 1L;
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final NodeSynchronization sync;
    private WebsocketConsumer consumer;

    private ConcurrentMap<String, WebsocketConsumer> consumers = new ConcurrentHashMap<String, WebsocketConsumer>();
    private Map<String, WebSocketFactory> socketFactory;

    public WebsocketComponentServlet(NodeSynchronization sync, Map<String, WebSocketFactory> socketFactory) {
        this.sync = sync;
        this.socketFactory = socketFactory;
    }

    public WebsocketConsumer getConsumer() {
        return consumer;
    }

    public void setConsumer(WebsocketConsumer consumer) {
        this.consumer = consumer;
    }

    public void connect(WebsocketConsumer consumer) {
        log.debug("Connecting consumer: {}", consumer);
        consumers.put(consumer.getPath(), consumer);
    }

    public void disconnect(WebsocketConsumer consumer) {
        log.debug("Disconnecting consumer: {}", consumer);
        consumers.remove(consumer.getPath());
    }

    @Override
    public WebSocket doWebSocketConnect(HttpServletRequest request, String protocol) {
        String protocolKey = protocol;

        if (protocol == null || !socketFactory.containsKey(protocol)) {
            log.debug("No factory found for the socket protocol: {}, returning default implementation", protocol);
            protocolKey = "default";
        }

        WebSocketFactory factory = socketFactory.get(protocolKey);
        return factory.newInstance(request, protocolKey, sync, consumer);
    }

    public Map<String, WebSocketFactory> getSocketFactory() {
        return socketFactory;
    }

    public void setSocketFactory(Map<String, WebSocketFactory> socketFactory) {
        this.socketFactory = socketFactory;
    }
}
