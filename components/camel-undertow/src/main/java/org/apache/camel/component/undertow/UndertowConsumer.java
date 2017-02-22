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
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import io.undertow.Handlers;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.EagerFormParsingHandler;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.MimeMappings;
import io.undertow.util.StatusCodes;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.IoUtils;

/**
 * The Undertow consumer which is also an Undertow HttpHandler implementation to handle incoming request.
 */
public class UndertowConsumer extends DefaultConsumer implements HttpHandler {

    static final int CHUNK_BUFF_SIZE = 1024 * 1024;

    private static final Logger LOG = LoggerFactory.getLogger(UndertowConsumer.class);

    private HttpHandlerRegistrationInfo registrationInfo;

    public UndertowConsumer(UndertowEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    public UndertowEndpoint getEndpoint() {
        return (UndertowEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        getEndpoint().getComponent().registerConsumer(this);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        getEndpoint().getComponent().unregisterConsumer(this);
    }

    public HttpHandlerRegistrationInfo getHttpHandlerRegistrationInfo() {
        if (registrationInfo == null) {
            UndertowEndpoint endpoint = getEndpoint();

            registrationInfo = new HttpHandlerRegistrationInfo();
            registrationInfo.setUri(endpoint.getHttpUri());
            registrationInfo.setMethodRestrict(endpoint.getHttpMethodRestrict());
            registrationInfo.setMatchOnUriPrefix(endpoint.isMatchOnUriPrefix());
        }
        return registrationInfo;
    }

    public HttpHandler getHttpHandler() {
        // allow for HTTP 1.1 continue
        return Handlers.httpContinueRead(
                // wrap with EagerFormParsingHandler to enable undertow form parsers
                new EagerFormParsingHandler().setNext(this));
    }

    @Override
    public void handleRequest(HttpServerExchange httpExchange) throws Exception {
        HttpString requestMethod = httpExchange.getRequestMethod();

        final UndertowEndpoint endpoint = getEndpoint();
        final Sender responseSender = httpExchange.getResponseSender();
        if (Methods.OPTIONS.equals(requestMethod) && !endpoint.isOptionsEnabled()) {
            String allowedMethods;
            if (endpoint.getHttpMethodRestrict() != null) {
                allowedMethods = "OPTIONS," + endpoint.getHttpMethodRestrict();
            } else {
                allowedMethods = "GET,HEAD,POST,PUT,DELETE,TRACE,OPTIONS,CONNECT,PATCH";
            }
            //return list of allowed methods in response headers
            httpExchange.setStatusCode(StatusCodes.OK);
            httpExchange.getResponseHeaders().put(ExchangeHeaders.CONTENT_TYPE, MimeMappings.DEFAULT_MIME_MAPPINGS.get("txt"));
            httpExchange.getResponseHeaders().put(ExchangeHeaders.CONTENT_LENGTH, 0);
            httpExchange.getResponseHeaders().put(Headers.ALLOW, allowedMethods);
            responseSender.close();
            return;
        }

        //perform blocking operation on exchange
        if (httpExchange.isInIoThread()) {
            httpExchange.dispatch(this);
            return;
        }

        //create new Exchange
        //binding is used to extract header and payload(if available)
        Exchange camelExchange = endpoint.createExchange(httpExchange);

        //Unit of Work to process the Exchange
        createUoW(camelExchange);
        try {
            getProcessor().process(camelExchange);
        } catch (Exception e) {
            getExceptionHandler().handleException(e);
        } finally {
            doneUoW(camelExchange);
        }

        Object body = getResponseBody(httpExchange, camelExchange);
        TypeConverter tc = endpoint.getCamelContext().getTypeConverter();

        if (body == null) {
            LOG.trace("No payload to send as reply for exchange: " + camelExchange);
            httpExchange.getResponseHeaders().put(ExchangeHeaders.CONTENT_TYPE, MimeMappings.DEFAULT_MIME_MAPPINGS.get("txt"));
            responseSender.send("No response available");

            return;
        }

        final Message message = fetchMessage(camelExchange);
        final boolean chunked = message.getHeader(Exchange.HTTP_CHUNKED, endpoint.isChunked(), Boolean.class);
        if (chunked) {
            sendChunked(responseSender, message);
        } else {
            ByteBuffer bodyAsByteBuffer = tc.convertTo(ByteBuffer.class, body);
            responseSender.send(bodyAsByteBuffer);
        }
    }

    /**
     * Sends the given message body as HTTP/1.1 chunked transfer.
     *
     * @param responseSender
     *            Undertow {@link Sender} to transfer the body to
     * @param message
     *            Camel message from which the body is sent
     * @throws InvalidPayloadException
     *             if the message does not contain a body of {@link ReadableByteChannel} or {@link InputStream} type
     * @throws IOException
     */
    void sendChunked(final Sender responseSender, final Message message) throws InvalidPayloadException, IOException {
        final Object body = message.getBody();

        if (body instanceof ReadableByteChannel) {
            sendChunked(responseSender, (ReadableByteChannel) body);
        } else {
            final InputStream stream = message.getMandatoryBody(InputStream.class);

            sendChunked(responseSender, Channels.newChannel(stream));
        }
    }

    /**
     * Sends the bytes received on the {@link ReadableByteChannel} to the exchange using HTTP/1.1 chuncked transfer.
     * 
     * @param httpExchange
     *            Undertow HTTP exchange to transfer upon
     * @param channel
     *            the channel on which the response to be sent resides
     * @throws IOException
     */
    void sendChunked(final Sender responseSender, final ReadableByteChannel channel) throws IOException {
        final ByteBuffer buffy = ByteBuffer.allocate(CHUNK_BUFF_SIZE);

        final int read = channel.read(buffy);
        if (read == -1) {
            return;
        }

        buffy.flip();
        final IoCallback callback = new IoCallback() {
            @Override
            public void onComplete(final HttpServerExchange exchange, final Sender sender) {
                if (buffy.hasRemaining()) {
                    sender.send(buffy, this);
                } else {
                    buffy.clear();

                    try {
                        final int read = channel.read(buffy);
                        if (read == -1) {
                            responseSender.close();
                            return;
                        }

                        buffy.flip();
                        sender.send(buffy, this);
                    } catch (final IOException e) {
                        try {
                            handleException("Unable to read from the given body", e);
                        } finally {
                            exchange.endExchange();
                            IoUtils.safeClose(exchange.getConnection());
                        }
                    }
                }
            }

            @Override
            public void onException(final HttpServerExchange exchange, final Sender sender,
                    final IOException exception) {
                try {
                    handleException("Unable to send response to client", exception);
                } finally {
                    exchange.endExchange();
                    IoUtils.safeClose(exchange.getConnection());
                }
            }
        };

        responseSender.send(buffy, callback);
    }

    /**
     * Returns the OUT, or if not present the IN message.
     *
     * @param exchange
     *            exchange containing IN or OUT messages
     * @return OUT or IN message if OUT message is not present
     */
    Message fetchMessage(final Exchange exchange) {
        if (exchange.hasOut()) {
            return exchange.getOut();
        } else {
            return exchange.getIn();
        }
    }

    Object getResponseBody(HttpServerExchange httpExchange, Exchange camelExchange) throws IOException {
        final Message message = fetchMessage(camelExchange);
        final UndertowHttpBinding undertowHttpBinding = getEndpoint().getUndertowHttpBinding();

        return undertowHttpBinding.toHttpResponse(httpExchange, message);
    }

}
