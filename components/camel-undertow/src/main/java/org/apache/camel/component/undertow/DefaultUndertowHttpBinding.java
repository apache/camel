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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.connector.ByteBufferPool;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.MimeMappings;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultMessage;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.MessageHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DefaultUndertowHttpBinding represent binding used by default, if user doesn't provide any.
 * By default {@link UndertowHeaderFilterStrategy} is also used.
 */
public class DefaultUndertowHttpBinding implements UndertowHttpBinding {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultUndertowHttpBinding.class);

    //use default filter strategy from Camel HTTP
    private HeaderFilterStrategy headerFilterStrategy;

    public DefaultUndertowHttpBinding() {
        this.headerFilterStrategy = new UndertowHeaderFilterStrategy();
    }

    public DefaultUndertowHttpBinding(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    @Override
    public Message toCamelMessage(HttpServerExchange httpExchange, Exchange exchange) throws Exception {
        Message result = new DefaultMessage();

        populateCamelHeaders(httpExchange, result.getHeaders(), exchange);

        //extract body if the method is allowed to have one
        //body is extracted as byte[] then auto TypeConverter kicks in
        if (Methods.POST.equals(httpExchange.getRequestMethod()) || Methods.PUT.equals(httpExchange.getRequestMethod())) {
            byte[] bytes = readRequestBody(httpExchange);
            result.setBody(bytes);
        } else {
            result.setBody(null);
        }

        return result;
    }

    @Override
    public Message toCamelMessage(ClientExchange clientExchange, Exchange exchange) throws Exception {
        Message result = new DefaultMessage();

        //retrieve response headers
        populateCamelHeaders(clientExchange.getResponse(), result.getHeaders(), exchange);

        result.setBody(readResponseBody(clientExchange));

        return result;
    }

    @Override
    public void populateCamelHeaders(HttpServerExchange httpExchange, Map<String, Object> headersMap, Exchange exchange) throws Exception {
        LOG.trace("populateCamelHeaders: {}");

        // NOTE: these headers is applied using the same logic as camel-http/camel-jetty to be consistent
        headersMap.put(Exchange.HTTP_METHOD, httpExchange.getRequestMethod().toString());
        // strip query parameters from the uri
        headersMap.put(Exchange.HTTP_URL, httpExchange.getRequestURL());
        // uri is without the host and port
        headersMap.put(Exchange.HTTP_URI, httpExchange.getRequestURI());
        headersMap.put(Exchange.HTTP_QUERY, httpExchange.getQueryString());
        headersMap.put(Exchange.HTTP_RAW_QUERY, httpExchange.getQueryString());


        String path = httpExchange.getRequestPath();
        headersMap.put(Exchange.HTTP_PATH, path);

        if (LOG.isTraceEnabled()) {
            LOG.trace("HTTP-Method {}", httpExchange.getRequestMethod());
            LOG.trace("HTTP-Uri {}", httpExchange.getRequestURI());
        }

        for (HttpString name : httpExchange.getRequestHeaders().getHeaderNames()) {
            // mapping the content-type
            //String name = httpName.toString();
            if (name.toString().toLowerCase(Locale.US).equals("content-type")) {
                name = ExchangeHeaders.CONTENT_TYPE;
            }

            if (name.toString().toLowerCase(Locale.US).equals("authorization")) {
                String value = httpExchange.getRequestHeaders().get(name).toString();
                // store a special header that this request was authenticated using HTTP Basic
                if (value != null && value.trim().startsWith("Basic")) {
                    if (headerFilterStrategy != null
                        && !headerFilterStrategy.applyFilterToExternalHeaders(Exchange.AUTHENTICATION, "Basic", exchange)) {
                        UndertowHelper.appendHeader(headersMap, Exchange.AUTHENTICATION, "Basic");
                    }
                }
            }

            // add the headers one by one, and use the header filter strategy
            Iterator<?> it = httpExchange.getRequestHeaders().get(name).iterator();
            while (it.hasNext()) {
                Object value = it.next();
                LOG.trace("HTTP-header: {}", value);
                if (headerFilterStrategy != null
                    && !headerFilterStrategy.applyFilterToExternalHeaders(name.toString(), value, exchange)) {
                    UndertowHelper.appendHeader(headersMap, name.toString(), value);
                }
            }
        }

        //process uri parameters as headers
        Map<String, Deque<String>> pathParameters = httpExchange.getQueryParameters();
        //continue if the map is not empty, otherwise there are no params
        if (!pathParameters.isEmpty()) {

            for (Map.Entry<String, Deque<String>> entry : pathParameters.entrySet()) {
                String name = entry.getKey();
                Object values = entry.getValue();
                Iterator<?> it = ObjectHelper.createIterator(values);
                while (it.hasNext()) {
                    Object value = it.next();
                    LOG.trace("URI-Parameter: {}", value);
                    if (headerFilterStrategy != null
                        && !headerFilterStrategy.applyFilterToExternalHeaders(name, value, exchange)) {
                        UndertowHelper.appendHeader(headersMap, name, value);
                    }
                }
            }
        }
    }

    @Override
    public void populateCamelHeaders(ClientResponse response, Map<String, Object> headersMap, Exchange exchange) throws Exception {
        LOG.trace("populateCamelHeaders: {}");

        headersMap.put(Exchange.HTTP_RESPONSE_CODE, response.getResponseCode());

        for (HttpString name : response.getResponseHeaders().getHeaderNames()) {
            // mapping the content-type
            //String name = httpName.toString();
            if (name.toString().toLowerCase(Locale.US).equals("content-type")) {
                name = ExchangeHeaders.CONTENT_TYPE;
            }

            if (name.toString().toLowerCase(Locale.US).equals("authorization")) {
                String value = response.getResponseHeaders().get(name).toString();
                // store a special header that this request was authenticated using HTTP Basic
                if (value != null && value.trim().startsWith("Basic")) {
                    if (headerFilterStrategy != null
                        && !headerFilterStrategy.applyFilterToExternalHeaders(Exchange.AUTHENTICATION, "Basic", exchange)) {
                        UndertowHelper.appendHeader(headersMap, Exchange.AUTHENTICATION, "Basic");
                    }
                }
            }

            // add the headers one by one, and use the header filter strategy
            Iterator<?> it = response.getResponseHeaders().get(name).iterator();
            while (it.hasNext()) {
                Object value = it.next();
                LOG.trace("HTTP-header: {}", value);
                if (headerFilterStrategy != null
                    && !headerFilterStrategy.applyFilterToExternalHeaders(name.toString(), value, exchange)) {
                    UndertowHelper.appendHeader(headersMap, name.toString(), value);
                }
            }
        }
    }

    @Override
    public Object toHttpResponse(HttpServerExchange httpExchange, Message message) {
        boolean failed = message.getExchange().isFailed();
        int defaultCode = failed ? 500 : 200;

        int code = message.getHeader(Exchange.HTTP_RESPONSE_CODE, defaultCode, int.class);

        httpExchange.setResponseCode(code);

        TypeConverter tc = message.getExchange().getContext().getTypeConverter();

        //copy headers from Message to Response
        for (Map.Entry<String, Object> entry : message.getHeaders().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            // use an iterator as there can be multiple values. (must not use a delimiter)
            final Iterator<?> it = ObjectHelper.createIterator(value, null);
            while (it.hasNext()) {
                String headerValue = tc.convertTo(String.class, it.next());
                if (headerValue != null && headerFilterStrategy != null
                    && !headerFilterStrategy.applyFilterToCamelHeaders(key, headerValue, message.getExchange())) {
                    LOG.trace("HTTP-Header: {}={}", key, headerValue);
                    httpExchange.getResponseHeaders().add(new HttpString(key), headerValue);
                }
            }
        }

        Exception exception = message.getExchange().getException();

        if (exception != null) {
            httpExchange.getResponseHeaders().put(Headers.CONTENT_TYPE, MimeMappings.DEFAULT_MIME_MAPPINGS.get("txt"));

            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            exception.printStackTrace(pw);

            String exceptionMessage = sw.toString();

            ExchangeHelper.setFailureHandled(message.getExchange());
            return exceptionMessage;
        }

        // set the content type in the response.
        String contentType = MessageHelper.getContentType(message);
        if (contentType != null) {
            // set content-type
            httpExchange.getResponseHeaders().put(Headers.CONTENT_TYPE, contentType);
            LOG.trace("Content-Type: {}", contentType);
        }

        return message.getBody();
    }

    @Override
    public Object toHttpRequest(ClientRequest clientRequest, Message message) {

        Object body = message.getBody();

        // set the content type in the response.
        String contentType = MessageHelper.getContentType(message);
        if (contentType != null) {
            // set content-type
            clientRequest.getRequestHeaders().put(Headers.CONTENT_TYPE, contentType);
            LOG.trace("Content-Type: {}", contentType);
        }

        TypeConverter tc = message.getExchange().getContext().getTypeConverter();

        //copy headers from Message to Request
        for (Map.Entry<String, Object> entry : message.getHeaders().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            // use an iterator as there can be multiple values. (must not use a delimiter)
            final Iterator<?> it = ObjectHelper.createIterator(value, null);
            while (it.hasNext()) {
                String headerValue = tc.convertTo(String.class, it.next());
                if (headerValue != null && headerFilterStrategy != null
                    && !headerFilterStrategy.applyFilterToCamelHeaders(key, headerValue, message.getExchange())) {
                    LOG.trace("HTTP-Header: {}={}", key, headerValue);
                    clientRequest.getRequestHeaders().add(new HttpString(key), headerValue);
                }
            }
        }

        return body;
    }

    private byte[] readRequestBody(HttpServerExchange httpExchange) throws IOException {
        ByteBufferPool bufferPool = httpExchange.getConnection().getByteBufferPool();
        PooledByteBuffer pooledByteBuffer = bufferPool.allocate();
        ByteBuffer byteBuffer = pooledByteBuffer.getBuffer();

        byteBuffer.clear();

        httpExchange.getRequestChannel().read(byteBuffer);
        int pos = byteBuffer.position();
        byteBuffer.rewind();
        byte[] bytes = new byte[pos];
        byteBuffer.get(bytes);

        byteBuffer.clear();
        pooledByteBuffer.close();
        return bytes;
    }

    private byte[] readResponseBody(ClientExchange httpExchange) throws IOException {
        ByteBufferPool bufferPool = httpExchange.getConnection().getBufferPool();
        PooledByteBuffer pooledByteBuffer = bufferPool.allocate();
        ByteBuffer byteBuffer = pooledByteBuffer.getBuffer();

        byteBuffer.clear();

        httpExchange.getResponseChannel().read(byteBuffer);
        int pos = byteBuffer.position();
        byteBuffer.rewind();
        byte[] bytes = new byte[pos];
        byteBuffer.get(bytes);

        byteBuffer.clear();
        pooledByteBuffer.close();
        return bytes;
    }

}
