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
package org.apache.camel.component.undertow;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;

import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.client.UndertowClient;
import io.undertow.server.XnioByteBufferPool;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Protocols;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.BufferAllocator;
import org.xnio.ByteBufferSlicePool;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

/**
 * The Undertow producer.
 *
 * The implementation of Producer is considered as experimental. The Undertow client classes are not thread safe,
 * their purpose is for the reverse proxy usage inside Undertow itself. This may change in the future versions and
 * general purpose HTTP client wrapper will be added. Therefore this Producer may be changed too.
 */
public class UndertowProducer extends DefaultAsyncProducer {
    private static final Logger LOG = LoggerFactory.getLogger(UndertowProducer.class);
    private UndertowEndpoint endpoint;
    private XnioWorker worker;
    private ByteBufferSlicePool pool;
    private OptionMap options;

    public UndertowProducer(UndertowEndpoint endpoint, OptionMap options) {
        super(endpoint);
        this.endpoint = endpoint;
        this.options = options;
    }

    @Override
    public UndertowEndpoint getEndpoint() {
        return endpoint;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        ClientConnection connection = null;

        try {
            final UndertowClient client = UndertowClient.getInstance();

            IoFuture<ClientConnection> connect = client.connect(endpoint.getHttpURI(), worker, new XnioByteBufferPool(pool), options);

            // creating the url to use takes 2-steps
            String url = UndertowHelper.createURL(exchange, getEndpoint());
            URI uri = UndertowHelper.createURI(exchange, url, getEndpoint());
            // get the url from the uri
            url = uri.toASCIIString();

            // what http method to use
            HttpString method = UndertowHelper.createMethod(exchange, endpoint, exchange.getIn().getBody() != null);

            ClientRequest request = new ClientRequest();
            request.setProtocol(Protocols.HTTP_1_1);
            request.setPath(url);
            request.setMethod(method);

            Object body = getRequestBody(request, exchange);

            TypeConverter tc = endpoint.getCamelContext().getTypeConverter();
            ByteBuffer bodyAsByte = tc.tryConvertTo(ByteBuffer.class, body);

            if (body != null) {
                request.getRequestHeaders().put(Headers.CONTENT_LENGTH, bodyAsByte.array().length);
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("Executing http {} method: {}", method, url);
            }
            connection = connect.get();
            connection.sendRequest(request, new UndertowProducerCallback(connection, bodyAsByte, exchange, callback));

        } catch (Exception e) {
            IOHelper.close(connection);
            exchange.setException(e);
            callback.done(true);
            return true;
        }

        // use async routing engine
        return false;
    }

    private Object getRequestBody(ClientRequest request, Exchange camelExchange) {
        return endpoint.getUndertowHttpBinding().toHttpRequest(request, camelExchange.getIn());
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        pool = new ByteBufferSlicePool(BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR, 8192, 8192 * 8192);
        worker = Xnio.getInstance().createWorker(options);

        LOG.debug("Created worker: {} with options: {}", worker, options);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (worker != null && !worker.isShutdown()) {
            LOG.debug("Shutting down worker: {}", worker);
            worker.shutdown();
        }
    }

    /**
     * Everything important happens in callback
     */
    private class UndertowProducerCallback implements ClientCallback<ClientExchange> {

        private final ClientConnection connection;
        private final ByteBuffer body;
        private final Exchange camelExchange;
        private final AsyncCallback callback;

        public UndertowProducerCallback(ClientConnection connection, ByteBuffer body, Exchange camelExchange, AsyncCallback callback) {
            this.connection = connection;
            this.body = body;
            this.camelExchange = camelExchange;
            this.callback = callback;
        }

        @Override
        public void completed(final ClientExchange clientExchange) {
            clientExchange.setResponseListener(new ClientCallback<ClientExchange>() {
                @Override
                public void completed(ClientExchange clientExchange) {
                    LOG.trace("completed: {}", clientExchange);
                    try {
                        Message message = endpoint.getUndertowHttpBinding().toCamelMessage(clientExchange, camelExchange);
                        if (ExchangeHelper.isOutCapable(camelExchange)) {
                            camelExchange.setOut(message);
                        } else {
                            camelExchange.setIn(message);
                        }
                    } catch (Exception e) {
                        camelExchange.setException(e);
                    } finally {
                        IOHelper.close(connection);
                        // make sure to call callback
                        callback.done(false);
                    }
                }

                @Override
                public void failed(IOException e) {
                    LOG.trace("failed: {}", e);
                    camelExchange.setException(e);
                    try {
                        IOHelper.close(connection);
                    } finally {
                        // make sure to call callback
                        callback.done(false);
                    }
                }
            });

            try {
                //send body if exists
                if (body != null) {
                    clientExchange.getRequestChannel().write(body);
                }
            } catch (IOException e) {
                camelExchange.setException(e);
                IOHelper.close(connection);
                // make sure to call callback
                callback.done(false);
            }
        }

        @Override
        public void failed(IOException e) {
            LOG.trace("failed: {}", e);
            camelExchange.setException(e);
            IOHelper.close(connection);
            // make sure to call callback
            callback.done(false);
        }
    }

}
