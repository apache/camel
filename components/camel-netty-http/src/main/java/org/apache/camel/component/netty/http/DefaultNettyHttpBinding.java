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
package org.apache.camel.component.netty.http;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.util.MessageHelper;
import org.apache.camel.util.ObjectHelper;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link NettyHttpBinding}.
 */
public class DefaultNettyHttpBinding implements NettyHttpBinding {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultNettyHttpBinding.class);
    private HeaderFilterStrategy headerFilterStrategy;

    public DefaultNettyHttpBinding() {
    }

    public DefaultNettyHttpBinding(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    @Override
    public Message toCamelMessage(HttpRequest request, Exchange exchange) throws Exception {
        LOG.trace("toCamelMessage: {}", request);

        NettyHttpMessage answer = new NettyHttpMessage(request, this);
        // force getting headers which will populate them
        answer.getHeaders();

        // keep the body as is, and use type converters
        answer.setBody(request.getContent());
        return answer;
    }

    @Override
    public void populateCamelHeaders(HttpRequest request, Map<String, Object> headers, Exchange exchange) throws Exception {
        LOG.trace("populateCamelHeaders: {}", request);

        headers.put(Exchange.HTTP_METHOD, request.getMethod().getName());
        headers.put(Exchange.HTTP_URI, request.getUri());

        if (LOG.isTraceEnabled()) {
            LOG.trace("HTTP-Method {}", request.getMethod().getName());
            LOG.trace("HTTP-Uri {}", request.getUri());
        }

        for (String name : request.getHeaderNames()) {
            // mapping the content-type
            if (name.toLowerCase().equals("content-type")) {
                name = Exchange.CONTENT_TYPE;
            }
            // add the headers one by one, and use the header filter strategy
            List<String> values = request.getHeaders(name);
            Iterator<?> it = ObjectHelper.createIterator(values);
            while (it.hasNext()) {
                Object extracted = it.next();
                LOG.trace("HTTP-header: {}", extracted);
                if (headerFilterStrategy != null
                        && !headerFilterStrategy.applyFilterToExternalHeaders(name, extracted, exchange)) {
                    NettyHttpHelper.appendHeader(headers, name, extracted);
                }
            }
        }
    }

    @Override
    public HttpResponse fromCamelMessage(Message message) throws Exception {
        LOG.trace("fromCamelMessage: {}", message);

        // the status code is default 200, but a header can override that
        Integer code = message.getHeader(Exchange.HTTP_RESPONSE_CODE, 200, Integer.class);
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(code));
        LOG.trace("HTTP Status Code: {}", code);

        TypeConverter tc = message.getExchange().getContext().getTypeConverter();

        // append headers
        // must use entrySet to ensure case of keys is preserved
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
                    response.addHeader(key, headerValue);
                }
            }
        }

        Object body = message.getBody();
        if (body != null) {
            // support bodies as native Netty
            ChannelBuffer buffer;
            if (body instanceof ChannelBuffer) {
                buffer = (ChannelBuffer) body;
            } else {
                // try to convert to buffer first
                buffer = message.getBody(ChannelBuffer.class);
                if (buffer == null) {
                    // fallback to byte array as last resort
                    byte[] data = message.getMandatoryBody(byte[].class);
                    buffer = ChannelBuffers.copiedBuffer(data);
                }
            }
            if (buffer != null) {
                response.setContent(buffer);
                int len = buffer.readableBytes();
                // set content-length
                response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, len);
                LOG.trace("Content-Length: {}", len);
            }
        }

        // set the content type in the response.
        String contentType = MessageHelper.getContentType(message);
        if (contentType != null) {
            // set content-type
            response.setHeader(HttpHeaders.Names.CONTENT_TYPE, contentType);
            LOG.trace("Content-Type: {}", contentType);
        }

        // TODO: keep alive should be something we can control
        String keepAlive = HttpHeaders.Values.CLOSE;
        response.setHeader(HttpHeaders.Names.CONNECTION, keepAlive);
        LOG.trace("Connection: {}", keepAlive);

        return response;
    }

    @Override
    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    @Override
    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }
}
