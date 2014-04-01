package org.apache.camel.component.atmosphere.websocket;

import java.io.InputStream;
import java.io.Reader;
import java.util.List;

import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.websocket.WebSocket;
import org.atmosphere.websocket.WebSocketProtocolStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebsocketStreamHandler extends WebsocketHandler implements WebSocketProtocolStream {
    private static final transient Logger LOG = LoggerFactory.getLogger(WebsocketStreamHandler.class);

    @Override
    public List<AtmosphereRequest> onTextStream(WebSocket webSocket, Reader data) {
        LOG.info("processing reader message {}", data);
        String connectionKey = store.getConnectionKey(webSocket);
        consumer.sendMessage(connectionKey, data);
        LOG.info("reader message sent");
        return null;
    }

    @Override
    public List<AtmosphereRequest> onBinaryStream(WebSocket webSocket, InputStream data) {
        LOG.info("processing inputstream message {}", data);
        String connectionKey = store.getConnectionKey(webSocket);
        consumer.sendMessage(connectionKey, data);
        LOG.info("reader message sent");
        return null;
    }
}
