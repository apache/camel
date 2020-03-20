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
package org.apache.camel.component.platform.http.vertx;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.TypeConversionException;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VertxPlatformHttpSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(VertxPlatformHttpSupport.class);

    private VertxPlatformHttpSupport() {
    }

    static Object toHttpResponse(HttpServerResponse response, Message message, HeaderFilterStrategy headerFilterStrategy) {
        final Exchange exchange = message.getExchange();
        final TypeConverter tc = exchange.getContext().getTypeConverter();

        final int code = determineResponseCode(exchange, message.getBody());
        response.setStatusCode(code);


        // copy headers from Message to Response
        if (headerFilterStrategy != null) {
            for (Map.Entry<String, Object> entry : message.getHeaders().entrySet()) {
                final String key = entry.getKey();
                final Object value = entry.getValue();
                // use an iterator as there can be multiple values. (must not use a delimiter)
                final Iterator<?> it = ObjectHelper.createIterator(value, null);

                String firstValue = null;
                List<String> values = null;

                while (it.hasNext()) {
                    final String headerValue = tc.convertTo(String.class, it.next());
                    if (headerValue != null && !headerFilterStrategy.applyFilterToCamelHeaders(key, headerValue, exchange)) {
                        if (firstValue == null) {
                            firstValue = headerValue;
                        } else {
                            if (values == null) {
                                values = new ArrayList<>();
                                values.add(firstValue);
                            }
                            values.add(headerValue);
                        }
                    }
                }

                if (values != null) {
                    response.putHeader(key, values);
                } else if (firstValue != null) {
                    response.putHeader(key, firstValue);
                }
            }
        }

        Object body = message.getBody();
        final Exception exception = exchange.getException();

        if (exception != null) {
            // we failed due an exception so print it as plain text
            final StringWriter sw = new StringWriter();
            final PrintWriter pw = new PrintWriter(sw);
            exception.printStackTrace(pw);

            // the body should then be the stacktrace
            body = ByteBuffer.wrap(sw.toString().getBytes(StandardCharsets.UTF_8));
            // force content type to be text/plain as that is what the stacktrace is
            message.setHeader(Exchange.CONTENT_TYPE, "text/plain; charset=utf-8");

            // and mark the exception as failure handled, as we handled it by returning it as the response
            ExchangeHelper.setFailureHandled(exchange);
        }

        // set the content type in the response.
        final String contentType = MessageHelper.getContentType(message);
        if (contentType != null) {
            // set content-type
            response.putHeader("Content-Type", contentType);
        }
        return body;
    }

    /*
     * Copied from org.apache.camel.http.common.DefaultHttpBinding.determineResponseCode(Exchange, Object)
     * If DefaultHttpBinding.determineResponseCode(Exchange, Object) is moved to a module without the servlet-api
     * dependency we could eventually consume it from there.
     */
    static int determineResponseCode(Exchange camelExchange, Object body) {
        boolean failed = camelExchange.isFailed();
        int defaultCode = failed ? 500 : 200;

        Message message = camelExchange.getMessage();
        Integer currentCode = message.getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
        int codeToUse = currentCode == null ? defaultCode : currentCode;

        if (codeToUse != 500) {
            if ((body == null) || (body instanceof String && ((String) body).trim().isEmpty())) {
                // no content
                codeToUse = currentCode == null ? 204 : currentCode;
            }
        }

        return codeToUse;
    }

    static void writeResponse(RoutingContext ctx, Exchange camelExchange, HeaderFilterStrategy headerFilterStrategy) {
        final Object body = toHttpResponse(ctx.response(), camelExchange.getMessage(), headerFilterStrategy);

        final HttpServerResponse response = ctx.response();
        if (body == null) {
            LOGGER.trace("No payload to send as reply for exchange: {}", camelExchange);
            response.end();
        } else if (body instanceof String) {
            response.end((String) body);
        } else if (body instanceof InputStream) {
            final byte[] bytes = new byte[4096];
            try (InputStream in = (InputStream) body) {
                int len;
                while ((len = in.read(bytes)) >= 0) {
                    final Buffer b = Buffer.buffer(len);
                    b.appendBytes(bytes, 0, len);
                    response.write(b);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            response.end();
        } else {
            final TypeConverter tc = camelExchange.getContext().getTypeConverter();
            try {
                final ByteBuffer bb = tc.mandatoryConvertTo(ByteBuffer.class, body);
                final Buffer b = Buffer.buffer(bb.capacity());
                b.setBytes(0, bb);
                response.end(b);
            } catch (TypeConversionException | NoTypeConversionAvailableException e) {
                throw new RuntimeException(e);
            }
        }

    }

    static void populateCamelHeaders(
        RoutingContext ctx,
        Map<String, Object> headersMap,
        Exchange exchange,
        HeaderFilterStrategy headerFilterStrategy) {

        final HttpServerRequest request = ctx.request();
        headersMap.put(Exchange.HTTP_PATH, request.path());

        if (headerFilterStrategy != null) {
            final MultiMap requestHeaders = request.headers();
            final String authz = requestHeaders.get("authorization");
            // store a special header that this request was authenticated using HTTP Basic
            if (authz != null && authz.trim().startsWith("Basic")) {
                if (!headerFilterStrategy.applyFilterToExternalHeaders(Exchange.AUTHENTICATION, "Basic", exchange)) {
                    appendHeader(headersMap, Exchange.AUTHENTICATION, "Basic");
                }
            }
            for (String name : requestHeaders.names()) {
                // add the headers one by one, and use the header filter strategy
                for (String value : requestHeaders.getAll(name)) {
                    if (!headerFilterStrategy.applyFilterToExternalHeaders(name.toString(), value, exchange)) {
                        appendHeader(headersMap, name.toString(), value);
                    }
                }
            }

            // process uri parameters as headers
            final MultiMap pathParameters = ctx.queryParams();
            // continue if the map is not empty, otherwise there are no params
            if (!pathParameters.isEmpty()) {
                for (String name : pathParameters.names()) {
                    for (String value : pathParameters.getAll(name)) {
                        if (!headerFilterStrategy.applyFilterToExternalHeaders(name, value, exchange)) {
                            appendHeader(headersMap, name, value);
                        }
                    }
                }
            }
        }

        // Path parameters
        for (Map.Entry<String, String> en : ctx.pathParams().entrySet()) {
            appendHeader(headersMap, en.getKey(), en.getValue());
        }

        // NOTE: these headers is applied using the same logic as camel-http/camel-jetty to be consistent
        headersMap.put(Exchange.HTTP_METHOD, request.method().toString());
        // strip query parameters from the uri
        headersMap.put(Exchange.HTTP_URL, request.absoluteURI());
        // uri is without the host and port
        headersMap.put(Exchange.HTTP_URI, request.uri());
        headersMap.put(Exchange.HTTP_QUERY, request.query());
        headersMap.put(Exchange.HTTP_RAW_QUERY, request.query());
    }

    @SuppressWarnings("unchecked")
    static void appendHeader(Map<String, Object> headers, String key, Object value) {
        if (headers.containsKey(key)) {
            Object existing = headers.get(key);
            List<Object> list;
            if (existing instanceof List) {
                list = (List<Object>) existing;
            } else {
                list = new ArrayList<>();
                list.add(existing);
            }
            list.add(value);
            value = list;
        }

        headers.put(key, value);
    }
}
