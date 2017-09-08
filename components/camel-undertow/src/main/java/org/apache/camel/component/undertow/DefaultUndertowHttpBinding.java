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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import javax.activation.FileDataSource;

import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.predicate.Predicate;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormData.FormValue;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultAttachment;
import org.apache.camel.impl.DefaultMessage;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.MessageHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.channels.BlockingReadableByteChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * DefaultUndertowHttpBinding represent binding used by default, if user doesn't provide any.
 * By default {@link UndertowHeaderFilterStrategy} is also used.
 */
public class DefaultUndertowHttpBinding implements UndertowHttpBinding {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultUndertowHttpBinding.class);

    //use default filter strategy from Camel HTTP
    private HeaderFilterStrategy headerFilterStrategy;
    private Boolean transferException;

    public DefaultUndertowHttpBinding() {
        this.headerFilterStrategy = new UndertowHeaderFilterStrategy();
        this.transferException = Boolean.FALSE;
    }

    public DefaultUndertowHttpBinding(HeaderFilterStrategy headerFilterStrategy, Boolean transferException) {
        this.headerFilterStrategy = headerFilterStrategy;
        this.transferException = transferException;
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    public Boolean isTransferException() {
        return transferException;
    }

    public void setTransferException(Boolean transferException) {
        this.transferException = transferException;
    }

    @Override
    public Message toCamelMessage(HttpServerExchange httpExchange, Exchange exchange) throws Exception {
        Message result = new DefaultMessage(exchange.getContext());

        populateCamelHeaders(httpExchange, result.getHeaders(), exchange);

        // Map form data which is parsed by undertow form parsers
        FormData formData = httpExchange.getAttachment(FormDataParser.FORM_DATA);
        if (formData != null) {
            Map<String, Object> body = new HashMap<>();
            formData.forEach(key -> {
                formData.get(key).forEach(value -> {
                    if (value.isFile()) {
                        DefaultAttachment attachment = new DefaultAttachment(new FilePartDataSource(value));
                        result.addAttachmentObject(key, attachment);
                        body.put(key, attachment.getDataHandler());
                    } else if (headerFilterStrategy != null
                        && !headerFilterStrategy.applyFilterToExternalHeaders(key, value.getValue(), exchange)) {
                        UndertowHelper.appendHeader(result.getHeaders(), key, value.getValue());
                        UndertowHelper.appendHeader(body, key, value.getValue());
                    }
                });
            });
            result.setBody(body);
        } else {
            //extract body by myself if undertow parser didn't handle and the method is allowed to have one
            //body is extracted as byte[] then auto TypeConverter kicks in
            if (Methods.POST.equals(httpExchange.getRequestMethod()) || Methods.PUT.equals(httpExchange.getRequestMethod())) {
                result.setBody(readFromChannel(httpExchange.getRequestChannel()));
            } else {
                result.setBody(null);
            }
        }
        return result;
    }

    @Override
    public Message toCamelMessage(ClientExchange clientExchange, Exchange exchange) throws Exception {
        Message result = new DefaultMessage(exchange.getContext());

        //retrieve response headers
        populateCamelHeaders(clientExchange.getResponse(), result.getHeaders(), exchange);

        result.setBody(readFromChannel(clientExchange.getResponseChannel()));

        return result;
    }

    @Override
    public void populateCamelHeaders(HttpServerExchange httpExchange, Map<String, Object> headersMap, Exchange exchange) throws Exception {
        LOG.trace("populateCamelHeaders: {}");

        String path = httpExchange.getRequestPath();
        UndertowEndpoint endpoint = (UndertowEndpoint) exchange.getFromEndpoint();
        if (endpoint.getHttpURI() != null) {
            // need to match by lower case as we want to ignore case on context-path
            String endpointPath = endpoint.getHttpURI().getPath();
            String matchPath = path.toLowerCase(Locale.US);
            String match = endpointPath.toLowerCase(Locale.US);
            if (matchPath.startsWith(match)) {
                path = path.substring(endpointPath.length());
            }
        }
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

        // Create headers for REST path placeholder variables
        Map<String, Object> predicateContextParams = httpExchange.getAttachment(Predicate.PREDICATE_CONTEXT);
        if (predicateContextParams != null) {
            // Remove this as it's an unwanted artifact of our Undertow predicate chain
            predicateContextParams.remove("remaining");

            for (String paramName : predicateContextParams.keySet()) {
                LOG.trace("REST Template Variable {}: {})", paramName, predicateContextParams.get(paramName));
                headersMap.put(paramName, predicateContextParams.get(paramName));
            }
        }

        // NOTE: these headers is applied using the same logic as camel-http/camel-jetty to be consistent
        headersMap.put(Exchange.HTTP_METHOD, httpExchange.getRequestMethod().toString());
        // strip query parameters from the uri
        headersMap.put(Exchange.HTTP_URL, httpExchange.getRequestURL());
        // uri is without the host and port
        headersMap.put(Exchange.HTTP_URI, httpExchange.getRequestURI());
        headersMap.put(Exchange.HTTP_QUERY, httpExchange.getQueryString());
        headersMap.put(Exchange.HTTP_RAW_QUERY, httpExchange.getQueryString());
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
    public Object toHttpResponse(HttpServerExchange httpExchange, Message message) throws IOException {
        boolean failed = message.getExchange().isFailed();
        int defaultCode = failed ? 500 : 200;

        int code = message.getHeader(Exchange.HTTP_RESPONSE_CODE, defaultCode, int.class);

        httpExchange.setStatusCode(code);

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

        Object body = message.getBody();
        Exception exception = message.getExchange().getException();

        if (exception != null) {
            if (isTransferException()) {
                // we failed due an exception, and transfer it as java serialized object
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                oos.writeObject(exception);
                oos.flush();
                IOHelper.close(oos, bos);

                // the body should be the serialized java object of the exception
                body = ByteBuffer.wrap(bos.toByteArray());
                // force content type to be serialized java object
                message.setHeader(Exchange.CONTENT_TYPE, "application/x-java-serialized-object");
            } else {
                // we failed due an exception so print it as plain text
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                exception.printStackTrace(pw);

                // the body should then be the stacktrace
                body = ByteBuffer.wrap(sw.toString().getBytes());
                // force content type to be text/plain as that is what the stacktrace is
                message.setHeader(Exchange.CONTENT_TYPE, "text/plain");
            }

            // and mark the exception as failure handled, as we handled it by returning it as the response
            ExchangeHelper.setFailureHandled(message.getExchange());
        }

        // set the content type in the response.
        String contentType = MessageHelper.getContentType(message);
        if (contentType != null) {
            // set content-type
            httpExchange.getResponseHeaders().put(Headers.CONTENT_TYPE, contentType);
            LOG.trace("Content-Type: {}", contentType);
        }
        return body;
    }

    @Override
    public Object toHttpRequest(ClientRequest clientRequest, Message message) {

        Object body = message.getBody();

        final HeaderMap requestHeaders = clientRequest.getRequestHeaders();

        // set the content type in the response.
        String contentType = MessageHelper.getContentType(message);
        if (contentType != null) {
            // set content-type
            requestHeaders.put(Headers.CONTENT_TYPE, contentType);
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
                    requestHeaders.add(new HttpString(key), headerValue);
                }
            }
        }

        return body;
    }

    byte[] readFromChannel(StreamSourceChannel source) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ByteBuffer buffer = ByteBuffer.wrap(new byte[1024]);

        ReadableByteChannel blockingSource = new BlockingReadableByteChannel(source);

        for (;;) {
            int res = blockingSource.read(buffer);
            if (res == -1) {
                return out.toByteArray();
            } else if (res == 0) {
                LOG.error("Channel did not block");
            } else {
                buffer.flip();
                out.write(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.arrayOffset() + buffer.limit());
                buffer.clear();
            }
        }
    }

    class FilePartDataSource extends FileDataSource {
        private String name;
        private String contentType;

        FilePartDataSource(FormValue value) {
            super(value.getPath().toFile());
            this.name = value.getFileName();
            this.contentType = value.getHeaders().getFirst(Headers.CONTENT_TYPE);
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public String getContentType() {
            return this.contentType;
        }
    }
}
