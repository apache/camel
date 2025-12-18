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
package org.apache.camel.component.platform.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.TypeConverter;
import org.apache.camel.component.platform.http.spi.PlatformHttpConsumer;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultMessage;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.http.HttpUtil;
import org.apache.camel.util.IOHelper;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.util.Callback;

public class JettyCustomPlatformHttpConsumer extends DefaultConsumer implements PlatformHttpConsumer {

    public JettyCustomPlatformHttpConsumer(PlatformHttpEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        final PlatformHttpEndpoint endpoint = getEndpoint();
        final String path = endpoint.getPath();

        JettyEmbeddedServer jettyServerTest = CamelContextHelper.mandatoryLookup(
                getEndpoint().getCamelContext(),
                JettyEmbeddedServer.JETTY_SERVER_NAME,
                JettyEmbeddedServer.class);

        ContextHandler contextHandler = createHandler(endpoint, path);
        // add handler after starting server.
        jettyServerTest.addHandler(contextHandler);
    }

    private ContextHandler createHandler(PlatformHttpEndpoint endpoint, String path) {
        ContextHandler contextHandler = new ContextHandler();
        contextHandler.setContextPath(path);
        contextHandler.setBaseResourceAsString(".");
        contextHandler.setClassLoader(Thread.currentThread().getContextClassLoader());
        contextHandler.setAllowNullPathInContext(true);

        contextHandler.setHandler(new Handler.Abstract() {
            @Override
            public boolean handle(Request request, Response response, Callback callback) throws Exception {
                Exchange exchg = null;
                try {
                    StringBuilder bodyRequest = new StringBuilder(1024);
                    while (true) {
                        Content.Chunk chunk = request.read();
                        if (chunk.isLast()) {
                            break;
                        }

                        byte[] bytes = new byte[chunk.getByteBuffer().remaining()];
                        chunk.getByteBuffer().get(bytes);
                        String chunkString = new String(bytes, StandardCharsets.UTF_8);
                        bodyRequest.append(chunkString);
                    }
                    final Exchange exchange = exchg = toExchange(request, bodyRequest.toString());
                    if (getEndpoint().isHttpProxy()) {
                        exchange.getMessage().removeHeader("Proxy-Connection");
                    }
                    exchange.getMessage().setHeader(Exchange.HTTP_SCHEME, request.getHttpURI().getScheme());
                    exchange.getMessage().setHeader(Exchange.HTTP_HOST, Request.getServerName(request));
                    exchange.getMessage().setHeader(Exchange.HTTP_PORT, Request.getServerPort(request));
                    exchange.getMessage().setHeader(Exchange.HTTP_PATH, Request.getPathInContext(request));
                    if (getEndpoint().isHttpProxy()) {
                        exchange.getExchangeExtension().setStreamCacheDisabled(true);
                    }
                    createUoW(exchange);
                    getProcessor().process(exchange);
                    response.setStatus(HttpServletResponse.SC_OK);
                    if (getEndpoint().isHttpProxy()) {
                        // extract response
                        InputStream responseStream = exchange.getMessage().getBody(InputStream.class);
                        String body = JettyCustomPlatformHttpConsumer.toString(responseStream);
                        exchange.getMessage().setBody(body);
                    }

                    copyMessageHeadersToResponse(response, exchange.getMessage(), getEndpoint().getHeaderFilterStrategy(),
                            exchange);
                    response.write(true,
                            ByteBuffer.wrap(exchange.getMessage().getBody(String.class).getBytes(StandardCharsets.UTF_8)),
                            callback);
                } catch (Exception e) {
                    getExceptionHandler().handleException("Failed handling platform-http endpoint " + endpoint.getPath(), exchg,
                            e);

                    callback.failed(e);
                    return false;
                } finally {
                    if (exchg != null) {
                        doneUoW(exchg);
                    }
                }

                callback.succeeded();
                return true;
            }
        });
        return contextHandler;
    }

    private void copyMessageHeadersToResponse(
            Response response,
            Message message,
            HeaderFilterStrategy headerFilterStrategy,
            Exchange exchange) {
        final TypeConverter tc = exchange.getContext().getTypeConverter();
        for (Map.Entry<String, Object> entry : message.getHeaders().entrySet()) {
            final String key = entry.getKey();
            final Object value = entry.getValue();
            final Iterator<?> it = ObjectHelper.createIterator(value, null, true);

            Set<String> responseHeadersFilledByJetty = Set.of("content-length");
            if (responseHeadersFilledByJetty.contains(entry.getKey().toLowerCase())) {
                continue;
            }

            HttpUtil.applyHeader(headerFilterStrategy, exchange, it, tc, key,
                    (values, firstValue) -> applyHeader(response, key, values, firstValue));
        }

    }

    private void applyHeader(Response response, String key, List<String> values, String firstValue) {
        if (values != null) {
            response.getHeaders().put(key, values);
        } else if (firstValue != null) {
            response.getHeaders().put(key, firstValue);
        }
    }

    private Exchange toExchange(Request request, String body) {
        final Exchange exchange = getEndpoint().createExchange();
        final Message message = new DefaultMessage(exchange);

        final String charset = request.getHeaders().get("charset");
        if (charset != null) {
            exchange.setProperty(Exchange.CHARSET_NAME, charset);
            message.setHeader(Exchange.HTTP_CHARACTER_ENCODING, charset);
        }

        for (HttpField header : request.getHeaders()) {
            String headerName = header.getName();
            String headerValue = header.getValue();
            if (getEndpoint().getHeaderFilterStrategy().applyFilterToExternalHeaders(headerName, headerValue, exchange)) {
                continue;
            }
            message.setHeader(header.getName(), header.getValue());
        }

        message.setBody(!body.isEmpty() ? body : null);
        exchange.setMessage(message);
        return exchange;
    }

    @Override
    public PlatformHttpEndpoint getEndpoint() {
        return (PlatformHttpEndpoint) super.getEndpoint();
    }

    private static String toString(InputStream input) throws IOException {
        BufferedReader reader = IOHelper.buffered(new InputStreamReader(input));
        StringJoiner builder = new StringJoiner(" ");
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                return builder.toString();
            }
            builder.add(line);
        }
    }

}
