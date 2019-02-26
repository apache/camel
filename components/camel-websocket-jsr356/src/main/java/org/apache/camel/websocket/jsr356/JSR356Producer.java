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

import java.io.IOException;
import java.net.URI;
import java.util.function.BiConsumer;

import static java.util.Optional.ofNullable;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.Session;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultAsyncProducer;


public class JSR356Producer extends DefaultAsyncProducer {
    private final int sessionCount;
    private ClientSessions manager;
    private BiConsumer<Exchange, AsyncCallback> onExchange;

    JSR356Producer(final JSR356Endpoint jsr356Endpoint, final int sessionCount) {
        super(jsr356Endpoint);
        this.sessionCount = sessionCount;
    }

    @Override
    public JSR356Endpoint getEndpoint() {
        return JSR356Endpoint.class.cast(super.getEndpoint());
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        final Session session = exchange.getIn().getHeader(JSR356Constants.SESSION, Session.class);
        if (session != null && exchange.getIn().getHeader(JSR356Constants.USE_INCOMING_SESSION, false, Boolean.class)) {
            synchronized (session) {
                doSend(exchange, session);
            }
        } else {
            onExchange.accept(exchange, callback);
        }
        return true;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        final String endpointKey = getEndpoint().getEndpointUri().substring("websocket-jsr356://".length());
        if (!endpointKey.contains("://")) { // we act as a client in all cases
                                            // here
            throw new IllegalArgumentException("You should pass a client uri");
        }
        final ClientEndpointConfig.Builder clientConfig = ClientEndpointConfig.Builder.create();
        manager = new ClientSessions(sessionCount, URI.create(endpointKey), clientConfig.build(), null);
        manager.prepare();
        onExchange = (ex, cb) -> manager.execute(session -> doSend(ex, session));
    }

    private void doSend(final Exchange ex, final Session session) {
        try {
            JSR356WebSocketComponent.sendMessage(session, ex.getIn().getBody());
        } catch (final IOException e) {
            ex.setException(e);
        }
    }

    @Override
    protected void doStop() throws Exception {
        ofNullable(manager).ifPresent(ClientSessions::close);
        super.doStop();
    }
}
