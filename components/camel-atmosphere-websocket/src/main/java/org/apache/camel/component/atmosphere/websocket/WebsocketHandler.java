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
        // TODO Auto-generated method stub
    }
    
    @Override
    public void onClose(WebSocket webSocket) {
        LOG.info("closing websocket");
        store.removeWebSocket(webSocket);
        
        LOG.info("websocket closed");
    }

    @Override
    public void onError(WebSocket webSocket, WebSocketException t) {
        LOG.error("websocket on error", t);
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        LOG.info("opening websocket");
        String connectionKey = UUID.randomUUID().toString();
        store.addWebSocket(connectionKey, webSocket);
        LOG.info("websocket opened");
    }

    @Override
    public List<AtmosphereRequest> onMessage(WebSocket webSocket, String data) {
        LOG.info("processing text message {}", data);
        String connectionKey = store.getConnectionKey(webSocket);
        consumer.sendMessage(connectionKey, data);
        LOG.info("text message sent");
        return null;
    }
    
    @Override
    public List<AtmosphereRequest> onMessage(WebSocket webSocket, byte[] data, int offset, int length) {
        LOG.info("processing byte message {}", data);
        String connectionKey = store.getConnectionKey(webSocket);
        if (length < data.length) {
            // create a copy that contains the relevant section as camel expects bytes without offset.
            // alternatively, we could pass a BAIS reading this byte array from the offset.
            byte[] rawdata = data;
            data = new byte[length];
            System.arraycopy(rawdata, offset, data, 0, length);
        }
        consumer.sendMessage(connectionKey, data);
        LOG.info("byte message sent");
        return null;
    }

    public void setConsumer(WebsocketConsumer consumer) {
        this.consumer = consumer;
        this.store = consumer.getEndpoint().getWebSocketStore();
    }

}
