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

import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultProducer;
import org.atmosphere.websocket.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class WebsocketProducer extends DefaultProducer {
    private static final transient Logger LOG = LoggerFactory.getLogger(WebsocketProducer.class);

    private static ExecutorService executor = Executors.newSingleThreadExecutor();
    
    public WebsocketProducer(WebsocketEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public WebsocketEndpoint getEndpoint() {
        return (WebsocketEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Message in = exchange.getIn();
        //TODO support binary data
        Object message = in.getBody();
        if (message == null) {
            LOG.debug("Ignoring a null message");
            return;
        }
        
        if (!(message instanceof String || message instanceof byte[] 
            || message instanceof Reader || message instanceof InputStream)) {
            // fallback to use String
            if (LOG.isInfoEnabled()) {
                LOG.info("Using String for unexpected message type {} ", message.getClass());
            }
            message = in.getBody(String.class);    
        }
        
        // REVISIT Reader and InputStream handling at Producer 
        // special conversion for Reader and InputStream for now 
        if (message instanceof Reader) {
            message = in.getBody(String.class);
        } else if (message instanceof InputStream) {
            message = in.getBody(byte[].class);
        }

        log.debug("Sending to {}", message);
        if (getEndpoint().isSendToAll()) {
            log.debug("Sending to all -> {}", message);
            //TODO consider using atmosphere's broadcast or a more configurable async send
            for (final WebSocket websocket : getEndpoint().getWebSocketStore().getAllWebSockets()) {
                sendMessage(websocket, message);
            }
        } else if (in.getHeader(WebsocketConstants.CONNECTION_KEY_LIST) != null) {
            List<String> connectionKeyList = in.getHeader(WebsocketConstants.CONNECTION_KEY_LIST, List.class);
            messageDistributor(connectionKeyList, message);
        } else {
            String connectionKey = in.getHeader(WebsocketConstants.CONNECTION_KEY, String.class);
            messageDistributor(Arrays.asList(connectionKey), message);
        }
    }

    private void messageDistributor(final List<String> connectionKeyList, final Object message) {
        if (connectionKeyList == null) {
            throw new IllegalArgumentException("Failed to send message to multiple connections; connetion key list is not set.");
        }

        List<String> notValidConnectionKeys = new ArrayList<>();

        for (final String connectionKey : connectionKeyList) {
            log.debug("Sending to connection key {} -> {}", connectionKey, message);
            sendMessage(getWebSocket(connectionKey, notValidConnectionKeys), message);
        }

        if (!notValidConnectionKeys.isEmpty()) {
            log.debug("Some connections have not received the message {}",  message);
            getEndpoint().getWebsocketConsumer().sendNotDeliveredMessage(notValidConnectionKeys, message);
        }
    }

    private void sendMessage(final WebSocket websocket, final Object message) {
        if (websocket != null && message != null) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (message instanceof String) {
                            websocket.write((String) message);
                        } else if (message instanceof byte[]) {
                            websocket.write((byte[]) message, 0, ((byte[]) message).length);
                        } else {
                            // this should not happen unless one of the supported types is missing above.
                            LOG.warn("unexpected message type {}", message.getClass());
                        }
                    } catch (Exception e) {
                        LOG.error("Error when writing to websocket", e);
                    }
                }
            });
        }
    }

    private WebSocket getWebSocket(final String connectionKey, final List<String> notValidConnectionKeys) {
        WebSocket websocket;
        if (connectionKey == null) {
            throw new IllegalArgumentException("Failed to send message to single connection; connection key is not set.");
        } else {
            websocket = getEndpoint().getWebSocketStore().getWebSocket(connectionKey);
            if (websocket == null) {
                //collect for call back to handle not sent message(s) to guaranty delivery
                notValidConnectionKeys.add(connectionKey);
                log.debug("Failed to send message to single connection; connetion key is not valid. {}",  connectionKey);
            }
        }
        return websocket;
    }
}
