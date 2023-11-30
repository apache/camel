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
package org.apache.camel.component.http;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.GZIPHelper;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.AbstractHttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;

/**
 * Some converter methods to make it easier to convert the body to RequestEntity types.
 */
@Converter(generateLoader = true)
public final class HttpEntityConverter {

    private HttpEntityConverter() {
    }

    @Converter
    public static HttpEntity toHttpEntity(byte[] data, Exchange exchange) throws Exception {
        return asHttpEntity(data, exchange);
    }

    @Converter
    public static HttpEntity toHttpEntity(InputStream inStream, Exchange exchange) throws Exception {
        return asHttpEntity(inStream, exchange);
    }

    @Converter
    public static HttpEntity toHttpEntity(String str, Exchange exchange) throws Exception {
        if (exchange != null && GZIPHelper.isGzip(exchange.getIn())) {
            byte[] data = exchange.getContext().getTypeConverter().convertTo(byte[].class, str);
            return asHttpEntity(data, exchange);
        } else {
            // will use the default StringRequestEntity
            return null;
        }
    }

    private static HttpEntity asHttpEntity(InputStream in, Exchange exchange) throws IOException {
        String contentEncoding = null;
        ContentType contentType = null;
        if (exchange != null) {
            contentEncoding = exchange.getIn().getHeader(HttpConstants.CONTENT_ENCODING, String.class);
            contentType = getContentType(exchange, contentType);
        }

        InputStreamEntity entity;
        if (exchange != null && !exchange.getProperty(Exchange.SKIP_GZIP_ENCODING, Boolean.FALSE, Boolean.class)) {
            InputStream stream = GZIPHelper.compressGzip(contentEncoding, in);
            int available = stream.available();
            entity = new InputStreamEntity(
                    stream, stream instanceof ByteArrayInputStream ? available != 0 ? available : -1 : -1, contentType,
                    contentEncoding);
        } else {
            entity = new InputStreamEntity(in, -1, contentType, contentEncoding);
        }

        return entity;
    }

    private static ContentType getContentType(Exchange exchange, ContentType contentType) {
        String contentTypeAsString = ExchangeHelper.getContentType(exchange);
        if (contentTypeAsString != null) {
            contentType = ContentType.parse(contentTypeAsString);
        }
        return contentType;
    }

    private static HttpEntity asHttpEntity(byte[] data, Exchange exchange) throws Exception {
        AbstractHttpEntity entity;

        String contentEncoding = null;
        ContentType contentType = null;
        if (exchange != null) {
            contentEncoding = exchange.getIn().getHeader(HttpConstants.CONTENT_ENCODING, String.class);
            contentType = getContentType(exchange, contentType);
        }

        if (exchange != null && !exchange.getProperty(Exchange.SKIP_GZIP_ENCODING, Boolean.FALSE, Boolean.class)) {
            boolean gzip = GZIPHelper.isGzip(contentEncoding);
            if (gzip) {
                InputStream stream = GZIPHelper.compressGzip(contentEncoding, data);
                int available = stream.available();
                entity = new InputStreamEntity(
                        stream, stream instanceof ByteArrayInputStream
                                ? available != 0 ? available : -1 : -1,
                        contentType, contentEncoding);
            } else {
                // use a byte array entity as-is
                entity = new ByteArrayEntity(data, contentType, contentEncoding);
            }
        } else {
            // create the Repeatable HttpEntity
            entity = new ByteArrayEntity(data, contentType, contentEncoding);
        }
        return entity;
    }
}
