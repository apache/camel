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
package org.apache.camel.component.vertx.websocket;

import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.ConnectionBase;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements a Vert.x Handler to handle WebSocket upgrade
 */
public class VertxWebsocketConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(VertxWebsocketConsumer.class);

    private final VertxWebsocketEndpoint endpoint;

    public VertxWebsocketConsumer(VertxWebsocketEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        getComponent().connectConsumer(this);
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        getComponent().disconnectConsumer(this);
        super.doStop();
    }

    @Override
    public VertxWebsocketEndpoint getEndpoint() {
        return endpoint;
    }

    public VertxWebsocketComponent getComponent() {
        return endpoint.getComponent();
    }

    public void onMessage(String connectionKey, Object message, SocketAddress remote) {
        Exchange exchange = createExchange(true);
        exchange.getMessage().setHeader(VertxWebsocketConstants.REMOTE_ADDRESS, remote);
        exchange.getMessage().setHeader(VertxWebsocketConstants.CONNECTION_KEY, connectionKey);
        exchange.getMessage().setBody(message);

        // use default consumer callback
        AsyncCallback cb = defaultConsumerCallback(exchange, true);
        getAsyncProcessor().process(exchange, cb);
    }

    public void onException(String connectionKey, Throwable cause, SocketAddress remote) {
        if (cause == ConnectionBase.CLOSED_EXCEPTION) {
            // Ignore as VertxWebsocketHost registers a closeHandler to trap WebSocket close events
            return;
        }

        Exchange exchange = createExchange(false);
        exchange.getMessage().setHeader(VertxWebsocketConstants.REMOTE_ADDRESS, remote);
        exchange.getMessage().setHeader(VertxWebsocketConstants.CONNECTION_KEY, connectionKey);
        getExceptionHandler().handleException("Error processing exchange", exchange, cause);
        releaseExchange(exchange, false);
    }
}
