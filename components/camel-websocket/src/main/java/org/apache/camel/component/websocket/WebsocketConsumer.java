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
package org.apache.camel.component.websocket;

import java.net.SocketAddress;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;

public class WebsocketConsumer extends DefaultConsumer implements WebsocketProducerConsumer {

    private final WebsocketEndpoint endpoint;

    public WebsocketConsumer(WebsocketEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
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

    @Override
    public WebsocketEndpoint getEndpoint() {
        return endpoint;
    }

    public String getPath() {
        return endpoint.getPath();
    }

    public void sendMessage(
            final String connectionKey,
            final String message,
            final SocketAddress remote,
            final String subprotocol,
            final String relativePath) {
        sendMessage(connectionKey, (Object) message, remote, subprotocol, relativePath);
    }

    public void sendMessage(
            final String connectionKey,
            final Object message,
            final SocketAddress remote,
            final String subprotocol,
            final String relativePath) {

        final Exchange exchange = createExchange(true);

        // set header and body
        exchange.getIn().setHeader(WebsocketConstants.REMOTE_ADDRESS, remote);
        exchange.getIn().setHeader(WebsocketConstants.CONNECTION_KEY, connectionKey);
        if (subprotocol != null) {
            exchange.getIn().setHeader(WebsocketConstants.SUBPROTOCOL, subprotocol);
        }
        if (relativePath != null) {
            exchange.getIn().setHeader(WebsocketConstants.RELATIVE_PATH, relativePath);
        }
        exchange.getIn().setBody(message);

        // use default consumer callback
        AsyncCallback cb = defaultConsumerCallback(exchange, true);
        getAsyncProcessor().process(exchange, cb);
    }

}
