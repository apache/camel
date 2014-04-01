/**
 * 
 */
package org.apache.camel.component.wsservlet;

import java.util.UUID;

import org.apache.camel.component.atmosphere.websocket.MemoryWebSocketStore;
import org.atmosphere.websocket.WebSocket;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 */
public class MemoryWebSocketStoreTest extends Assert {
    
    @Test
    public void testAddAndRemove() throws Exception {
        MemoryWebSocketStore store = new MemoryWebSocketStore();
        WebSocket webSocket1 = EasyMock.createMock(WebSocket.class);
        WebSocket webSocket2 = EasyMock.createMock(WebSocket.class);
        
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
