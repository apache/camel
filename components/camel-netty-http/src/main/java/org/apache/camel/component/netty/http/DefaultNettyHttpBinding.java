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

/**
 * Default {@link NettyHttpBinding}.
 */
public class DefaultNettyHttpBinding implements NettyHttpBinding {

    private HeaderFilterStrategy headerFilterStrategy;

    public DefaultNettyHttpBinding() {
    }

    public DefaultNettyHttpBinding(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    @Override
    public Message toCamelMessage(HttpRequest request, Exchange exchange) throws Exception {
        NettyHttpMessage answer = new NettyHttpMessage(request);
        answer.setHeader(Exchange.HTTP_METHOD, request.getMethod().getName());
        answer.setHeader(Exchange.HTTP_URI, request.getUri());

        // populate the headers from the request
        Map<String, Object> headers = answer.getHeaders();

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
                if (headerFilterStrategy != null
                        && !headerFilterStrategy.applyFilterToExternalHeaders(name, extracted, exchange)) {
                    NettyHttpHelper.appendHeader(headers, name, extracted);
                }
            }
        }

        // keep the body as is, and use type converters
        answer.setBody(request.getContent());
        return answer;
    }

    @Override
    public HttpResponse fromCamelMessage(Message message) throws Exception {

        // the status code is default 200, but a header can override that
        Integer code = message.getHeader(Exchange.HTTP_RESPONSE_CODE, 200, Integer.class);
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(code));

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
                response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, buffer.readableBytes());
            }
        }

        // set the content type in the response.
        String contentType = MessageHelper.getContentType(message);
        if (contentType != null) {
            response.setHeader(HttpHeaders.Names.CONTENT_TYPE, contentType);
        }

        // TODO: keep alive should be something we can control
        response.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);

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
