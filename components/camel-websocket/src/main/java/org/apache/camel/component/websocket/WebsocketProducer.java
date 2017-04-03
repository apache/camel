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
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.StopWatch;

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
                String pathSpec = "";
                if (endpoint.getResourceUri() != null) {
                    pathSpec = WebsocketComponent.createPathSpec(endpoint.getResourceUri());
                }
                DefaultWebsocket websocket = store.get(connectionKey + pathSpec);
                log.debug("Sending to connection key {} -> {}", connectionKey, message);
                Future<Void> future = sendMessage(websocket, message);
                if (future != null) {
                    int timeout = endpoint.getSendTimeout();
                    future.get(timeout, TimeUnit.MILLISECONDS);
                    if (!future.isCancelled() && !future.isDone()) {
                        throw new WebsocketSendException("Failed to send message to the connection within " + timeout + " millis.", exchange);
                    }
                }
            } else {
                throw new WebsocketSendException("Failed to send message to single connection; connection key not set.", exchange);
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

        List<Future> futures = new CopyOnWriteArrayList<>();
        for (DefaultWebsocket websocket : websockets) {
            boolean isOkToSendMessage = false;
            if (endpoint.getResourceUri() == null) {
                isOkToSendMessage = true;
            } else if (websocket.getPathSpec().equals(WebsocketComponent.createPathSpec(endpoint.getResourceUri()))) {
                isOkToSendMessage = true;
            }
            if (isOkToSendMessage) {
                try {
                    Future<Void> future = sendMessage(websocket, message);
                    if (future != null) {
                        futures.add(future);
                    }
                } catch (Exception e) {
                    if (exception == null) {
                        exception = new WebsocketSendException("Failed to deliver message to one or more recipients.", exchange, e);
                    }
                }
            }
        }

        // check if they are all done within the timed out period
        StopWatch watch = new StopWatch();
        int timeout = endpoint.getSendTimeout();
        while (!futures.isEmpty() && watch.taken() < timeout) {
            // remove all that are done/cancelled
            for (Future future : futures) {
                if (future.isDone() || future.isCancelled()) {
                    futures.remove(future);
                }
                // if there are still more then we need to wait a little bit before checking again, to avoid burning cpu cycles in the while loop
                if (!futures.isEmpty()) {
                    long interval = Math.min(1000, timeout);
                    log.debug("Sleeping {} millis waiting for sendToAll to complete sending with timeout {} millis", interval, timeout);
                    try {
                        Thread.sleep(interval);
                    } catch (InterruptedException e) {
                        handleSleepInterruptedException(e, exchange);
                    }
                }
            }

        }
        if (!futures.isEmpty()) {
            exception = new WebsocketSendException("Failed to deliver message within " + endpoint.getSendTimeout() + " millis to one or more recipients.", exchange);
        }

        if (exception != null) {
            throw exception;
        }
    }

    Future<Void> sendMessage(DefaultWebsocket websocket, Object message) throws IOException {
        Future<Void> future = null;
        // in case there is web socket and socket connection is open - send message
        if (websocket != null && websocket.getSession().isOpen()) {
            log.trace("Sending to websocket {} -> {}", websocket.getConnectionKey(), message);
            if (message instanceof String) {
                future = websocket.getSession().getRemote().sendStringByFuture((String) message);
            } else if (message instanceof byte[]) {
                ByteBuffer buf = ByteBuffer.wrap((byte[]) message);
                future = websocket.getSession().getRemote().sendBytesByFuture(buf);
            }
        }
        return future;
    }

    //Store is set/unset upon connect/disconnect of the producer
    public void setStore(WebsocketStore store) {
        this.store = store;
    }

    /**
     * Called when a sleep is interrupted; allows derived classes to handle this case differently
     */
    protected void handleSleepInterruptedException(InterruptedException e, Exchange exchange) throws InterruptedException {
        if (log.isDebugEnabled()) {
            log.debug("Sleep interrupted, are we stopping? {}", isStopping() || isStopped());
        }
        Thread.currentThread().interrupt();
        throw e;
    }

}
