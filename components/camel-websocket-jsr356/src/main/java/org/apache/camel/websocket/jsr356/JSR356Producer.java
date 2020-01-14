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
package org.apache.camel.websocket.jsr356;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.function.BiConsumer;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.util.IOHelper;

import static java.util.Optional.ofNullable;

public class JSR356Producer extends DefaultAsyncProducer {
    private ClientSessions manager;
    private BiConsumer<Exchange, AsyncCallback> onExchange;

    JSR356Producer(final JSR356Endpoint jsr356Endpoint) {
        super(jsr356Endpoint);
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
                doSend(exchange, callback, session);
            }
        } else {
            onExchange.accept(exchange, callback);
        }
        return true;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        final URI uri = getEndpoint().getUri();
        if (uri.getScheme() != null && !uri.getScheme().equals("ws")) {
            throw new IllegalArgumentException("WebSocket endpoint URI must be in the format: websocket-jsr356:ws://host:port/path");
        }
        final ClientEndpointConfig.Builder clientConfig = ClientEndpointConfig.Builder.create();
        manager = new ClientSessions(getEndpoint().getSessionCount(), uri, clientConfig.build(), null);
        manager.prepare();
        onExchange = (exchange, callback) -> manager.execute(session -> doSend(exchange, callback, session));
    }

    private void doSend(final Exchange exchange, final AsyncCallback callback, final Session session) {
        try {
            Object body = exchange.getMessage().getBody();
            synchronized (session) {
                final RemoteEndpoint.Basic basicRemote = session.getBasicRemote();
                if (String.class.isInstance(body)) {
                    basicRemote.sendText(String.valueOf(body));
                } else if (ByteBuffer.class.isInstance(body)) {
                    basicRemote.sendBinary(ByteBuffer.class.cast(body));
                } else if (InputStream.class.isInstance(body)) {
                    IOHelper.copy(InputStream.class.cast(body), basicRemote.getSendStream());
                } else {
                    throw new IllegalArgumentException("Unsupported input: " + body);
                }
            }
        } catch (final IOException e) {
            exchange.setException(e);
        } finally {
            callback.done(true);
        }
    }

    @Override
    protected void doStop() throws Exception {
        ofNullable(manager).ifPresent(ClientSessions::close);
        super.doStop();
    }
}
