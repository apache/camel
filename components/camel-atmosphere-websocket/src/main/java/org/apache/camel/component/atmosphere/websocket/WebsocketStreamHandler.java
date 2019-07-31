/*
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
        LOG.debug("processing reader message {}", data);
        String connectionKey = store.getConnectionKey(webSocket);
        consumer.sendMessage(connectionKey, data);
        LOG.debug("reader message sent");
        return null;
    }

    @Override
    public List<AtmosphereRequest> onBinaryStream(WebSocket webSocket, InputStream data) {
        LOG.debug("processing inputstream message {}", data);
        String connectionKey = store.getConnectionKey(webSocket);
        consumer.sendMessage(connectionKey, data);
        LOG.debug("reader message sent");
        return null;
    }
}
