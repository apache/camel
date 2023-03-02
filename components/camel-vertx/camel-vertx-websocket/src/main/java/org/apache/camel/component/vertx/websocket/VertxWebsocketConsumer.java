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

import java.util.Map;

import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.ext.web.RoutingContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;

/**
 * Implements a Vert.x Handler to handle WebSocket upgrade
 */
public class VertxWebsocketConsumer extends DefaultConsumer {

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

    public void onMessage(String connectionKey, Object message, SocketAddress remote, RoutingContext routingContext) {
        Exchange exchange = createExchange(true);
        exchange.getMessage().setBody(message);
        populateExchangeHeaders(exchange, connectionKey, remote, routingContext, VertxWebsocketEvent.MESSAGE);
        processExchange(exchange, routingContext);
    }

    public void onException(String connectionKey, Throwable cause, SocketAddress remote, RoutingContext routingContext) {
        if (cause == ConnectionBase.CLOSED_EXCEPTION) {
            // Ignore as VertxWebsocketHost registers a closeHandler to trap WebSocket close events
            return;
        }

        Exchange exchange = createExchange(false);
        populateExchangeHeaders(exchange, connectionKey, remote, routingContext, VertxWebsocketEvent.ERROR);

        getExceptionHandler().handleException("Error processing exchange", exchange, cause);
        releaseExchange(exchange, false);
    }

    public void onOpen(String connectionKey, SocketAddress remote, RoutingContext routingContext, ServerWebSocket webSocket) {
        Exchange exchange = createExchange(true);
        populateExchangeHeaders(exchange, connectionKey, remote, routingContext, VertxWebsocketEvent.OPEN);
        exchange.getMessage().setBody(webSocket);
        processExchange(exchange, routingContext);
    }

    public void onClose(String connectionKey, SocketAddress remote, RoutingContext routingContext) {
        Exchange exchange = createExchange(true);
        populateExchangeHeaders(exchange, connectionKey, remote, routingContext, VertxWebsocketEvent.CLOSE);
        processExchange(exchange, routingContext);
    }

    protected void populateExchangeHeaders(
            Exchange exchange, String connectionKey, SocketAddress remote, RoutingContext routingContext,
            VertxWebsocketEvent event) {
        Message message = exchange.getMessage();
        Map<String, Object> headers = message.getHeaders();
        message.setHeader(VertxWebsocketConstants.REMOTE_ADDRESS, remote);
        message.setHeader(VertxWebsocketConstants.CONNECTION_KEY, connectionKey);
        message.setHeader(VertxWebsocketConstants.EVENT, event);
        routingContext.queryParams()
                .forEach((name, value) -> VertxWebsocketHelper.appendHeader(headers, name, value));
        routingContext.pathParams()
                .forEach((name, value) -> VertxWebsocketHelper.appendHeader(headers, name, value));
    }

    protected void processExchange(Exchange exchange, RoutingContext routingContext) {
        routingContext.vertx().executeBlocking(
                promise -> {
                    try {
                        createUoW(exchange);
                    } catch (Exception e) {
                        promise.fail(e);
                        return;
                    }

                    getAsyncProcessor().process(exchange, c -> {
                        promise.complete();
                    });
                },
                false,
                result -> {
                    try {
                        if (result.failed()) {
                            Throwable cause = result.cause();
                            getExceptionHandler().handleException(cause);
                            routingContext.fail(cause);
                        }
                    } finally {
                        doneUoW(exchange);
                        releaseExchange(exchange, false);
                    }
                });
    }
}
