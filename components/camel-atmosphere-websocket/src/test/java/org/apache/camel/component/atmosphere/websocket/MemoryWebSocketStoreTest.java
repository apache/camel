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

import java.util.UUID;

import org.atmosphere.websocket.WebSocket;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;


public class MemoryWebSocketStoreTest extends Assert {
    
    @Test
    public void testAddAndRemove() throws Exception {
        MemoryWebSocketStore store = new MemoryWebSocketStore();
        WebSocket webSocket1 = Mockito.mock(WebSocket.class);
        WebSocket webSocket2 = Mockito.mock(WebSocket.class);
        
        String connectionKey1 = UUID.randomUUID().toString();
        String connectionKey2 = UUID.randomUUID().toString();
        
        store.addWebSocket(connectionKey1, webSocket1);
        verifyGet(store, connectionKey1, webSocket1, true);
        assertEquals(1, store.getAllWebSockets().size());
        
        store.addWebSocket(connectionKey2, webSocket2);
        verifyGet(store, connectionKey2, webSocket2, true);
        verifyGet(store, connectionKey1, webSocket1, true);
        assertEquals(2, store.getAllWebSockets().size());
        
        store.removeWebSocket(connectionKey1);
        verifyGet(store, connectionKey1, webSocket1, false);

        store.removeWebSocket(webSocket2);
        verifyGet(store, connectionKey2, webSocket2, false);
        
        assertEquals(0, store.getAllWebSockets().size());
    }

    private void verifyGet(MemoryWebSocketStore store, String ck, WebSocket ws, boolean exists) {
        WebSocket aws = store.getWebSocket(ck);
        String ack = store.getConnectionKey(ws);
        if (exists) {
            assertNotNull(aws);
            assertEquals(ws, aws);
            assertNotNull(ack);
            assertEquals(ck, ack);
        } else {
            assertNull(aws);
            assertNull(ack);
        }
    }
}
