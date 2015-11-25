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

import java.io.IOException;
import java.util.Collection;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultProducer;

public class WebsocketProducer extends DefaultProducer implements WebsocketProducerConsumer {

    private WebsocketStore store;
    private final Boolean sendToAll;
    private final WebsocketEndpoint endpoint;

    public WebsocketProducer(WebsocketEndpoint endpoint) {
        super(endpoint);
        this.sendToAll = endpoint.getSendToAll();
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Message in = exchange.getIn();
        Object message = in.getMandatoryBody();
        if (!(message == null || message instanceof String || message instanceof byte[])) {
            message = in.getMandatoryBody(String.class);
        }
        if (isSendToAllSet(in)) {
            sendToAll(store, message, exchange);
        } else {
            // look for connection key and get Websocket
            String connectionKey = in.getHeader(WebsocketConstants.CONNECTION_KEY, String.class);
            if (connectionKey != null) {
                DefaultWebsocket websocket = store.get(connectionKey);
                log.debug("Sending to connection key {} -> {}", connectionKey, message);
                sendMessage(websocket, message);
            } else {
                throw new IllegalArgumentException("Failed to send message to single connection; connetion key not set.");
            }
        }
    }

    public WebsocketEndpoint getEndpoint() {
        return endpoint;
    }


    @Override
    public void doStart() throws Exception {
        super.doStart();
        endpoint.connect(this);
    }

    @Override
    public void doStop() throws Exception {
        endpoint.disconnect(this);
        super.doStop();
    }

    boolean isSendToAllSet(Message in) {
        // header may be null; have to be careful here (and fallback to use sendToAll option configured from endpoint)
        Boolean value = in.getHeader(WebsocketConstants.SEND_TO_ALL, sendToAll, Boolean.class);
        return value == null ? false : value;
    }

    void sendToAll(WebsocketStore store, Object message, Exchange exchange) throws Exception {
        log.debug("Sending to all {}", message);
        Collection<DefaultWebsocket> websockets = store.getAll();
        Exception exception = null;
        for (DefaultWebsocket websocket : websockets) {
            try {
                sendMessage(websocket, message);
            } catch (Exception e) {
                if (exception == null) {
                    exception = new CamelExchangeException("Failed to deliver message to one or more recipients.", exchange, e);
                }
            }
        }
        if (exception != null) {
            throw exception;
        }
    }

    void sendMessage(DefaultWebsocket websocket, Object message) throws IOException {
        // in case there is web socket and socket connection is open - send message
        if (websocket != null && websocket.getConnection().isOpen()) {
            log.trace("Sending to websocket {} -> {}", websocket.getConnectionKey(), message);
            if (message instanceof String) {
                websocket.getConnection().sendMessage((String) message);
            } else if (message instanceof byte[]) {
                websocket.getConnection().sendMessage((byte[]) message, 0, ((byte[]) message).length);
            }
        }
    }

    //Store is set/unset upon connect/disconnect of the producer
    public void setStore(WebsocketStore store) {
        this.store = store;
    }
}
