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
package org.apache.camel.component.undertow;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.net.ssl.SSLContext;

import io.undertow.client.ClientRequest;
import io.undertow.client.UndertowClient;
import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.TypeConverter;
import org.apache.camel.component.undertow.handlers.CamelWebSocketHandler;
import org.apache.camel.http.base.cookie.CookieHandler;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.OptionMap;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.ssl.XnioSsl;

/**
 * The Undertow producer.
 *
 * The implementation of Producer is considered as experimental. The Undertow client classes are not thread safe, their
 * purpose is for the reverse proxy usage inside Undertow itself. This may change in the future versions and general
 * purpose HTTP client wrapper will be added. Therefore this Producer may be changed too.
 */
public class UndertowProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(UndertowProducer.class);

    private UndertowClient client;
    private final UndertowEndpoint endpoint;
    private final OptionMap options;
    private DefaultByteBufferPool pool;
    private XnioSsl ssl;
    private XnioWorker worker;
    private CamelWebSocketHandler webSocketHandler;

    public UndertowProducer(final UndertowEndpoint endpoint, final OptionMap options) {
        super(endpoint);
        this.endpoint = endpoint;
        this.options = options;
    }

    @Override
    public UndertowEndpoint getEndpoint() {
        return endpoint;
    }

    boolean isSendToAll(Message in) {
        // header may be null; have to be careful here (and fallback to use sendToAll option configured from endpoint)
        Boolean value = in.getHeader(UndertowConstants.SEND_TO_ALL, endpoint.getSendToAll(), Boolean.class);
        return value != null && value;
    }

    @Override
    public boolean process(final Exchange camelExchange, final AsyncCallback callback) {
        if (endpoint.isWebSocket()) {
            return processWebSocket(camelExchange, callback);
        }

        /* not a WebSocket */
        final URI uri;
        final HttpString method;
        try {
            final String exchangeUri = UndertowHelper.createURL(camelExchange, getEndpoint());
            uri = UndertowHelper.createURI(camelExchange, exchangeUri, getEndpoint());
            method = UndertowHelper.createMethod(camelExchange, endpoint, camelExchange.getIn().getBody() != null);
        } catch (final URISyntaxException e) {
            camelExchange.setException(e);
            callback.done(true);
            return true;
        }

        final String pathAndQuery = URISupport.pathAndQueryOf(uri);

        final UndertowHttpBinding undertowHttpBinding = endpoint.getUndertowHttpBinding();

        final CookieHandler cookieHandler = endpoint.getCookieHandler();
        final Map<String, List<String>> cookieHeaders;
        if (cookieHandler != null) {
            try {
                cookieHeaders = cookieHandler.loadCookies(camelExchange, uri);
            } catch (final IOException e) {
                camelExchange.setException(e);
                callback.done(true);
                return true;
            }
        } else {
            cookieHeaders = Collections.emptyMap();
        }

        final ClientRequest request = new ClientRequest();
        request.setMethod(method);
        request.setPath(pathAndQuery);

        final HeaderMap requestHeaders = request.getRequestHeaders();

        // Set the Host header
        final Message message = camelExchange.getIn();
        final String host = message.getHeader(UndertowConstants.HOST_STRING, String.class);
        if (endpoint.isPreserveHostHeader()) {
            requestHeaders.put(Headers.HOST, Optional.ofNullable(host).orElseGet(uri::getAuthority));
        } else {
            requestHeaders.put(Headers.HOST, uri.getAuthority());
        }
        cookieHeaders.forEach((key, values) -> {
            requestHeaders.putAll(HttpString.tryFromString(key), values);
        });

        final Object body = undertowHttpBinding.toHttpRequest(request, camelExchange.getIn());
        final UndertowClientCallback clientCallback;
        final boolean streaming = getEndpoint().isUseStreaming();
        if (streaming && body instanceof InputStream) {
            // For streaming, make it chunked encoding instead of specifying content length
            requestHeaders.put(Headers.TRANSFER_ENCODING, "chunked");
            clientCallback = new UndertowStreamingClientCallback(
                    camelExchange, callback, getEndpoint(),
                    request, (InputStream) body);
        } else {
            final TypeConverter tc = endpoint.getCamelContext().getTypeConverter();
            final ByteBuffer bodyAsByte = tc.tryConvertTo(ByteBuffer.class, body);

            // As tryConvertTo is used to convert the body, we should do null check
            // or the call bodyAsByte.remaining() may throw an NPE
            if (body != null && bodyAsByte != null) {
                requestHeaders.put(Headers.CONTENT_LENGTH, bodyAsByte.remaining());
            }

            if (streaming) {
                // response may receive streaming
                clientCallback = new UndertowStreamingClientCallback(
                        camelExchange, callback, getEndpoint(),
                        request, bodyAsByte);
            } else {
                clientCallback = new UndertowClientCallback(
                        camelExchange, callback, getEndpoint(),
                        request, bodyAsByte);
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Executing http {} method: {}", method, pathAndQuery);
        }

        // when connect succeeds or fails UndertowClientCallback will
        // get notified on a I/O thread run by Xnio worker. The writing
        // of request and reading of response is performed also in the
        // callback
        client.connect(clientCallback, uri, worker, ssl, pool, options);

        // the call above will proceed on Xnio I/O thread we will
        // notify the exchange asynchronously when the HTTP exchange
        // ends with success or failure from UndertowClientCallback
        return false;
    }

    private boolean processWebSocket(final Exchange camelExchange, final AsyncCallback camelCallback) {
        final Message in = camelExchange.getIn();
        try {
            Object message = in.getBody();
            if (!(message instanceof String || message instanceof byte[] || message instanceof Reader
                    || message instanceof InputStream)) {
                message = in.getBody(String.class);
            }

            if (message != null) {
                final int timeout = endpoint.getSendTimeout();
                if (isSendToAll(in)) {
                    return webSocketHandler.send(peer -> true, message, timeout, camelExchange, camelCallback);
                }
                final List<String> connectionKeys = in.getHeader(UndertowConstants.CONNECTION_KEY_LIST, List.class);
                if (connectionKeys != null) {
                    return webSocketHandler.send(
                            peer -> connectionKeys.contains(peer.getAttribute(UndertowConstants.CONNECTION_KEY)), message,
                            timeout, camelExchange, camelCallback);
                }
                final String connectionKey = in.getHeader(UndertowConstants.CONNECTION_KEY, String.class);
                if (connectionKey != null) {
                    return webSocketHandler.send(
                            peer -> connectionKey.equals(peer.getAttribute(UndertowConstants.CONNECTION_KEY)), message,
                            timeout, camelExchange, camelCallback);
                }
                throw new IllegalStateException(
                        String.format("Cannot process message which has none of the headers %s, %s or %s set: %s",
                                UndertowConstants.SEND_TO_ALL, UndertowConstants.CONNECTION_KEY_LIST,
                                UndertowConstants.CONNECTION_KEY, in));
            } else {
                /* nothing to do for a null body */
                camelCallback.done(true);
                return true;
            }
        } catch (Exception e) {
            camelExchange.setException(e);
            camelCallback.done(true);
            return true;
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // as in Undertow tests
        pool = new DefaultByteBufferPool(true, 17 * 1024);

        final Xnio xnio = Xnio.getInstance();
        worker = xnio.createWorker(options);

        final SSLContext sslContext = getEndpoint().getSslContext();
        if (sslContext != null) {
            ssl = new UndertowXnioSsl(xnio, options, sslContext);
        }

        client = UndertowClient.getInstance();

        if (endpoint.isWebSocket()) {
            this.webSocketHandler = (CamelWebSocketHandler) endpoint.getComponent().registerEndpoint(null,
                    endpoint.getHttpHandlerRegistrationInfo(), endpoint.getSslContext(), new CamelWebSocketHandler());
        }

        LOG.debug("Created worker: {} with options: {}", worker, options);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (endpoint.isWebSocket()) {
            endpoint.getComponent().unregisterEndpoint(null, endpoint.getHttpHandlerRegistrationInfo(),
                    endpoint.getSslContext());
        }

        if (worker != null && !worker.isShutdown()) {
            LOG.debug("Shutting down worker: {}", worker);
            worker.shutdown();
        }
    }

}
