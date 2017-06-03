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

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.atmosphere.websocket.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class MemoryWebSocketStore implements WebSocketStore {
    private static final transient Logger LOG = LoggerFactory.getLogger(MemoryWebSocketStore.class);
    
    private Map<String, WebSocket> values;
    private Map<WebSocket, String> keys;
    
    public MemoryWebSocketStore() {
        values = new ConcurrentHashMap<String, WebSocket>();
        keys = new ConcurrentHashMap<WebSocket, String>();
    }
    
    /* (non-Javadoc)
     * @see org.apache.camel.Service#start()
     */
    @Override
    public void start() throws Exception {
    }

    /* (non-Javadoc)
     * @see org.apache.camel.Service#stop()
     */
    @Override
    public void stop() throws Exception {
        values.clear();
        keys.clear();
    }

    /* (non-Javadoc)
     * @see org.apache.camel.component.websocket2.WebsocketStore#addWebSocket(java.lang.String, org.atmosphere.websocket.WebSocket)
     */
    @Override
    public void addWebSocket(String connectionKey, WebSocket websocket) {
        values.put(connectionKey, websocket);
        keys.put(websocket, connectionKey);
        if (LOG.isDebugEnabled()) {
            LOG.debug("added websocket {} => {}", connectionKey, websocket);
        }
    }

    /* (non-Javadoc)
     * @see org.apache.camel.component.websocket2.WebsocketStore#removeWebSocket(java.lang.String)
     */
    @Override
    public void removeWebSocket(String connectionKey) {
        Object obj = values.remove(connectionKey);
        if (obj != null) {
            keys.remove(obj);
        }
        LOG.debug("removed websocket {}", connectionKey);
    }

    /* (non-Javadoc)
     * @see org.apache.camel.component.websocket2.WebsocketStore#removeWebSocket(org.atmosphere.websocket.WebSocket)
     */
    @Override
    public void removeWebSocket(WebSocket websocket) {
        Object obj = keys.remove(websocket);
        if (obj != null) {
            values.remove(obj);
        }
        LOG.debug("removed websocket {}", websocket);
    }

    /* (non-Javadoc)
     * @see org.apache.camel.component.websocket2.WebsocketStore#getConnectionKey(org.atmosphere.websocket.WebSocket)
     */
    @Override
    public String getConnectionKey(WebSocket websocket) {
        return keys.get(websocket);
    }

    /* (non-Javadoc)
     * @see org.apache.camel.component.websocket2.WebsocketStore#getWebSocket(java.lang.String)
     */
    @Override
    public WebSocket getWebSocket(String connectionKey) {
        return values.get(connectionKey);
    }

    /* (non-Javadoc)
     * @see org.apache.camel.component.websocket2.WebsocketStore#getAllWebSockets()
     */
    @Override
    public Collection<WebSocket> getAllWebSockets() {
        return values.values();
    }

}
