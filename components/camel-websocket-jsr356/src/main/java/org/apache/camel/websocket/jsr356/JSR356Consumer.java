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
package org.apache.camel.websocket.jsr356;

import java.net.URI;
import java.util.function.BiConsumer;

import static java.util.Optional.ofNullable;


import javax.websocket.ClientEndpointConfig;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpointConfig;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;


public class JSR356Consumer extends DefaultConsumer {
    private final int sessionCount;
    private final String context;
    private ClientSessions manager;
    private Runnable closeTask;

    private final BiConsumer<Session, Object> onMessage = (session, message) -> {
        final Exchange exchange = getEndpoint().createExchange();
        exchange.getIn().setHeader(JSR356Constants.SESSION, session);
        exchange.getIn().setBody(message);
        getAsyncProcessor().process(exchange, doneSync -> {
            if (exchange.getException() != null) {
                getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
            }
        });
    };;

    JSR356Consumer(final JSR356Endpoint jsr356Endpoint, final Processor processor, final int sessionCount, final String context) {
        super(jsr356Endpoint, processor);
        this.sessionCount = sessionCount;
        this.context = context;
    }

    @Override
    public JSR356Endpoint getEndpoint() {
        return JSR356Endpoint.class.cast(super.getEndpoint());
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        final String endpointKey = getEndpoint().getEndpointUri().substring("websocket-jsr356://".length());
        if (endpointKey.contains("://")) { // we act as a client
            final ClientEndpointConfig.Builder clientConfig = ClientEndpointConfig.Builder.create(); // todo:
                                                                                                     // config
            manager = new ClientSessions(sessionCount, URI.create(endpointKey), clientConfig.build(), onMessage);
            manager.prepare();
        } else {
            final JSR356WebSocketComponent.ContextBag bag = JSR356WebSocketComponent.getContext(context);
            final CamelServerEndpoint endpoint = bag.getEndpoints().get(endpointKey);
            if (endpoint == null) {
                // todo: make it customizable (the endpoint config)
                final ServerEndpointConfig.Builder configBuilder = ServerEndpointConfig.Builder.create(CamelServerEndpoint.class, endpointKey);
                final CamelServerEndpoint serverEndpoint = new CamelServerEndpoint();
                bag.getEndpoints().put(endpointKey, serverEndpoint);
                closeTask = addObserver(serverEndpoint);
                configBuilder.configurator(new ServerEndpointConfig.Configurator() {
                    @Override
                    public <T> T getEndpointInstance(final Class<T> clazz) {
                        return clazz.cast(serverEndpoint);
                    }
                });
                bag.getContainer().addEndpoint(configBuilder.build());
            } else {
                closeTask = addObserver(endpoint);
            }
        }
    }

    private Runnable addObserver(final CamelServerEndpoint endpoint) {
        endpoint.getEndpoints().add(onMessage);
        return () -> endpoint.getEndpoints().remove(onMessage);
    }

    @Override
    protected void doStop() throws Exception {
        ofNullable(manager).ifPresent(ClientSessions::close);
        ofNullable(closeTask).ifPresent(Runnable::run);
        super.doStop();
    }
}
