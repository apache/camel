/**
 * 
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
