/**
 * 
 */
package org.apache.camel.component.atmosphere.websocket;

import java.util.Collection;

import org.apache.camel.Service;
import org.atmosphere.websocket.WebSocket;

/**
 *
 */
public interface WebSocketStore extends Service {
    void addWebSocket(String connectionKey, WebSocket websocket);
    void removeWebSocket(String connectionKey);
    void removeWebSocket(WebSocket websocket);
    String getConnectionKey(WebSocket websocket);
    WebSocket getWebSocket(String connectionKey);
    Collection<WebSocket> getAllWebSockets();
}
