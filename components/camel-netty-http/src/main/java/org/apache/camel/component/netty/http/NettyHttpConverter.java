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

import java.io.InputStream;
import java.nio.charset.Charset;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.FallbackConverter;
import org.apache.camel.component.netty.NettyConverter;
import org.apache.camel.spi.TypeConverterRegistry;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

@Converter
public final class NettyHttpConverter {

    private NettyHttpConverter() {
    }

    /**
     * A fallback converter that allows us to easily call Java beans and use the raw Netty {@link HttpRequest} as parameter types.
     */
    @FallbackConverter
    public static Object convertToHttpRequest(Class<?> type, Exchange exchange, Object value, TypeConverterRegistry registry) {
        // if we want to covert to HttpRequest
        if (value != null && HttpRequest.class.isAssignableFrom(type)) {

            // okay we may need to cheat a bit when we want to grab the HttpRequest as its stored on the NettyHttpMessage
            // so if the message instance is a NettyHttpMessage and its body is the value, then we can grab the
            // HttpRequest from the NettyHttpMessage
            NettyHttpMessage msg;
            if (exchange.hasOut()) {
                msg = exchange.getOut(NettyHttpMessage.class);
            } else {
                msg = exchange.getIn(NettyHttpMessage.class);
            }
            if (msg != null && msg.getBody() == value) {
                // ensure the http request content is reset so we can read all the content out-of-the-box
                HttpRequest request = msg.getHttpRequest();
                request.getContent().resetReaderIndex();
                return request;
            }
        }

        return null;
    }

    /**
     * A fallback converter that allows us to easily call Java beans and use the raw Netty {@link HttpRequest} as parameter types.
     */
    @FallbackConverter
    public static Object convertToHttpResponse(Class<?> type, Exchange exchange, Object value, TypeConverterRegistry registry) {
        // if we want to covert to convertToHttpResponse
        if (value != null && HttpResponse.class.isAssignableFrom(type)) {

            // okay we may need to cheat a bit when we want to grab the HttpRequest as its stored on the NettyHttpMessage
            // so if the message instance is a NettyHttpMessage and its body is the value, then we can grab the
            // HttpRequest from the NettyHttpMessage
            NettyHttpMessage msg;
            if (exchange.hasOut()) {
                msg = exchange.getOut(NettyHttpMessage.class);
            } else {
                msg = exchange.getIn(NettyHttpMessage.class);
            }
            if (msg != null && msg.getBody() == value) {
                return msg.getHttpResponse();
            }
        }

        return null;
    }

    @Converter
    public static String toString(HttpResponse response, Exchange exchange) {
        String contentType = response.headers().get(Exchange.CONTENT_TYPE);
        String charset = NettyHttpHelper.getCharsetFromContentType(contentType);
        if (charset == null && exchange != null) {
            charset = exchange.getProperty(Exchange.CHARSET_NAME, String.class);
        }
        if (charset != null) {
            return response.getContent().toString(Charset.forName(charset));
        } else {
            return response.getContent().toString(Charset.defaultCharset());
        }
    }

    @Converter
    public static byte[] toBytes(HttpResponse response, Exchange exchange) {
        return NettyConverter.toByteArray(response.getContent(), exchange);
    }

    @Converter
    public static InputStream toInputStream(HttpResponse response, Exchange exchange) {
        return NettyConverter.toInputStream(response.getContent(), exchange);
    }

}
