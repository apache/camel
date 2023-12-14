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

import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.streams.Pump;
import io.vertx.ext.web.RoutingContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.http.HttpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.support.http.HttpUtil.determineResponseCode;
import static org.apache.camel.util.CollectionHelper.appendEntry;

/*
 * Supporting class for the platform-http-vertx component.
 *
 * Please note that many of the methods in this class are part of the component's hot-path, therefore, please be mindful
 * about the performance implications of the code (i.e.: keep methods small, avoid costly operations, etc).
 */
public final class VertxPlatformHttpSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(VertxPlatformHttpSupport.class);
    public static final String DEFAULT_CONTENT_TYPE_ON_EXCEPTION = "text/plain; charset=utf-8";

    private VertxPlatformHttpSupport() {
    }

    static Object toHttpResponse(
            HttpServerResponse response, Message message, HeaderFilterStrategy headerFilterStrategy,
            boolean muteExceptions) {
        final Exchange exchange = message.getExchange();

        final int code = determineResponseCode(exchange, message.getBody());
        response.setStatusCode(code);

        // copy headers from Message to Response
        if (headerFilterStrategy != null) {
            copyMessageHeadersToResponse(response, message, headerFilterStrategy, exchange);
        }

        final Object body = getBody(message, muteExceptions, exchange);

        // set the content-length if it can be determined, or chunked encoding
        final Integer length = determineContentLength(body);
        if (length != null) {
            response.putHeader("Content-Length", String.valueOf(length));
        } else {
            response.setChunked(true);
        }

        // set the content type in the response.
        final String contentType = MessageHelper.getContentType(message);
        if (contentType != null) {
            // set content-type
            response.putHeader("Content-Type", contentType);
        }
        return body;
    }

    private static Object getBody(Message message, boolean muteExceptions, Exchange exchange) {
        final Exception exception = exchange.getException();

        if (exception != null) {
            return handleExceptions(message, muteExceptions, exception, exchange);
        }
        return message.getBody();
    }

    private static Object handleExceptions(Message message, boolean muteExceptions, Exception exception, Exchange exchange) {
        Object body;
        if (muteExceptions) {
            body = ""; // do not include stacktrace in body
            // force content type to be text/plain as that is what the stacktrace is
            message.setHeader(Exchange.CONTENT_TYPE, DEFAULT_CONTENT_TYPE_ON_EXCEPTION);
        } else {
            // we failed due an exception so print it as plain text
            final StringWriter sw = new StringWriter();
            final PrintWriter pw = new PrintWriter(sw);
            exception.printStackTrace(pw);

            // the body should then be the stacktrace
            body = ByteBuffer.wrap(sw.toString().getBytes(StandardCharsets.UTF_8));
            // force content type to be text/plain as that is what the stacktrace is
            message.setHeader(Exchange.CONTENT_TYPE, DEFAULT_CONTENT_TYPE_ON_EXCEPTION);
        }

        // and mark the exception as failure handled, as we handled it by returning it as the response
        ExchangeHelper.setFailureHandled(exchange);
        return body;
    }

    private static void copyMessageHeadersToResponse(
            HttpServerResponse response, Message message, HeaderFilterStrategy headerFilterStrategy, Exchange exchange) {
        final TypeConverter tc = exchange.getContext().getTypeConverter();

        for (Map.Entry<String, Object> entry : message.getHeaders().entrySet()) {
            final String key = entry.getKey();
            final Object value = entry.getValue();
            // use an iterator as there can be multiple values. (must not use a delimiter)
            final Iterator<?> it = ObjectHelper.createIterator(value, null, true);

            putHeader(response, headerFilterStrategy, exchange, it, tc, key);
        }
    }

    private static void putHeader(
            HttpServerResponse response, HeaderFilterStrategy headerFilterStrategy, Exchange exchange, Iterator<?> it,
            TypeConverter tc, String key) {

        HttpUtil.applyHeader(headerFilterStrategy, exchange, it, tc, key,
                (values, firstValue) -> applyHeader(response, key, values, firstValue));
    }

    private static void applyHeader(HttpServerResponse response, String key, List<String> values, String firstValue) {
        if (values != null) {
            response.putHeader(key, values);
        } else if (firstValue != null) {
            response.putHeader(key, firstValue);
        }
    }

    static Integer determineContentLength(Object body) {
        if (body instanceof byte[]) {
            return ((byte[]) body).length;
        } else if (body instanceof ByteBuffer) {
            return ((ByteBuffer) body).remaining();
        }
        return null;
    }

    static Future<Void> writeResponse(
            RoutingContext ctx, Exchange camelExchange, HeaderFilterStrategy headerFilterStrategy, boolean muteExceptions) {
        final Object body = toHttpResponse(ctx.response(), camelExchange.getMessage(), headerFilterStrategy, muteExceptions);
        final Promise<Void> promise = Promise.promise();

        if (body == null) {
            LOGGER.trace("No payload to send as reply for exchange: {}", camelExchange);
            ctx.end();
            promise.complete();
        } else if (body instanceof String) {
            ctx.end((String) body);
            promise.complete();
        } else if (body instanceof InputStream) {
            writeResponseAs(promise, ctx, (InputStream) body);
        } else if (body instanceof Buffer) {
            ctx.end((Buffer) body);
            promise.complete();
        } else {
            try {
                writeResponseAsFallback(promise, camelExchange, body, ctx);
            } catch (NoTypeConversionAvailableException e) {
                promise.fail(e);
            }
        }

        return promise.future();
    }

    private static void writeResponseAsFallback(Promise<Void> promise, Exchange camelExchange, Object body, RoutingContext ctx)
            throws NoTypeConversionAvailableException {
        final TypeConverter tc = camelExchange.getContext().getTypeConverter();
        // Try to convert to ByteBuffer for performance reason
        final ByteBuffer bb = tc.tryConvertTo(ByteBuffer.class, camelExchange, body);
        if (bb != null) {
            writeResponseAs(promise, ctx, bb);
        } else {
            // Otherwise fallback to most generic InputStream conversion
            final InputStream is = tc.mandatoryConvertTo(InputStream.class, camelExchange, body);
            writeResponseAs(promise, ctx, is);
        }
    }

    private static void writeResponseAs(Promise<Void> promise, RoutingContext ctx, ByteBuffer bb) {
        final Buffer b = Buffer.buffer(bb.capacity());
        b.setBytes(0, bb);
        ctx.end(b);
        promise.complete();
    }

    private static void writeResponseAs(Promise<Void> promise, RoutingContext ctx, InputStream is) {
        HttpServerResponse response = ctx.response();
        Vertx vertx = ctx.vertx();
        Context context = vertx.getOrCreateContext();

        // Process the InputStream async to avoid blocking the Vert.x event loop on large responses
        AsyncInputStream asyncInputStream = new AsyncInputStream(vertx, context, is);
        asyncInputStream.exceptionHandler(promise::fail);
        asyncInputStream.endHandler(event -> endHandler(promise, response, asyncInputStream));

        // Pump the InputStream content into the HTTP response WriteStream
        Pump pump = Pump.pump(asyncInputStream, response);
        context.runOnContext(event -> pump.start());
    }

    private static void endHandler(Promise<Void> promise, HttpServerResponse response, AsyncInputStream asyncInputStream) {
        response.end().onComplete(result -> onComplete(promise, asyncInputStream));
    }

    private static void onComplete(Promise<Void> promise, AsyncInputStream asyncInputStream) {
        asyncInputStream.close(closeResult -> promise.complete());
    }

    static void populateCamelHeaders(
            RoutingContext ctx,
            Map<String, Object> headersMap,
            Exchange exchange,
            HeaderFilterStrategy headerFilterStrategy) {

        final HttpServerRequest request = ctx.request();
        headersMap.put(Exchange.HTTP_PATH, ctx.normalizedPath());

        if (headerFilterStrategy != null) {
            applyHeaderFilterStrategy(ctx, headersMap, exchange, headerFilterStrategy, request);
        }

        // Path parameters
        for (Map.Entry<String, String> en : ctx.pathParams().entrySet()) {
            appendEntry(headersMap, en.getKey(), en.getValue());
        }

        SocketAddress localAddress = request.localAddress();
        if (localAddress != null) {
            headersMap.put(VertxPlatformHttpConstants.LOCAL_ADDRESS, localAddress);
        }

        SocketAddress remoteAddress = request.remoteAddress();
        if (remoteAddress != null) {
            headersMap.put(VertxPlatformHttpConstants.REMOTE_ADDRESS, remoteAddress);
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

    // Note: this is in the hot path of the platform http, so be mindful with performance here
    private static void applyHeaderFilterStrategy(
            RoutingContext ctx, Map<String, Object> headersMap, Exchange exchange, HeaderFilterStrategy headerFilterStrategy,
            HttpServerRequest request) {
        final MultiMap requestHeaders = request.headers();
        applyAuthHeaders(headersMap, exchange, headerFilterStrategy, requestHeaders);
        applyHeaders(headersMap, exchange, headerFilterStrategy, requestHeaders);

        // process uri parameters as headers
        final MultiMap pathParameters = ctx.queryParams();
        // continue if the map is not empty, otherwise there are no params
        if (!pathParameters.isEmpty()) {
            applyHeaders(headersMap, exchange, headerFilterStrategy, pathParameters);
        }
    }

    private static void applyHeaders(
            Map<String, Object> headersMap, Exchange exchange, HeaderFilterStrategy headerFilterStrategy,
            MultiMap requestHeaders) {

        final List<Map.Entry<String, String>> entries = requestHeaders.entries();
        for (var entry : entries) {
            final String name = entry.getKey();
            final String value = entry.getValue();

            // add the headers one by one, and use the header filter strategy
            if (!headerFilterStrategy.applyFilterToExternalHeaders(name, value, exchange)) {
                appendEntry(headersMap, name, value);
            }
        }
    }

    private static void applyAuthHeaders(
            Map<String, Object> headersMap, Exchange exchange, HeaderFilterStrategy headerFilterStrategy,
            MultiMap requestHeaders) {
        final String authorization = requestHeaders.get("authorization");
        // store a special header that this request was authenticated using HTTP Basic
        if (authorization != null && authorization.trim().startsWith("Basic")) {
            if (!headerFilterStrategy.applyFilterToExternalHeaders(Exchange.AUTHENTICATION, "Basic", exchange)) {
                appendEntry(headersMap, Exchange.AUTHENTICATION, "Basic");
            }
        }
    }

    static boolean isMultiPartFormData(RoutingContext ctx) {
        return isContentTypeMatching(ctx, HttpHeaderValues.MULTIPART_FORM_DATA.toString());
    }

    static boolean isFormUrlEncoded(RoutingContext ctx) {
        return isContentTypeMatching(ctx, HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED.toString());
    }

    private static boolean isContentTypeMatching(RoutingContext ctx, String expectedContentType) {
        String contentType = ctx.parsedHeaders().contentType().value();
        boolean match = false;
        if (org.apache.camel.util.ObjectHelper.isNotEmpty(contentType)) {
            String lowerCaseContentType = contentType.toLowerCase();
            match = lowerCaseContentType.startsWith(expectedContentType);
        }
        return match;
    }
}
