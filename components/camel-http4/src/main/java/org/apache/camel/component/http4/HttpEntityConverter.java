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
package org.apache.camel.component.http4;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.GZIPHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.http.HttpEntity;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.InputStreamEntity;

/**
 * Some converter methods to make it easier to convert the body to RequestEntity types.
 */
@Converter
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
        InputStreamEntity entity;
        if (!exchange.getProperty(Exchange.SKIP_GZIP_ENCODING, Boolean.FALSE, Boolean.class)) {
            String contentEncoding = exchange.getIn().getHeader(Exchange.CONTENT_ENCODING, String.class);
            InputStream stream = GZIPHelper.compressGzip(contentEncoding, in);
            entity = new InputStreamEntity(stream, stream instanceof ByteArrayInputStream
                ? stream.available() != 0 ? stream.available() : -1 : -1);
        } else {
            Message inMessage = exchange.getIn();
            String length = inMessage.getHeader(Exchange.CONTENT_LENGTH, String.class);
            
            if (ObjectHelper.isEmpty(length)) {
                entity = new InputStreamEntity(in, -1);
            } else {
                entity = new InputStreamEntity(in, Long.parseLong(length));
            }
        }
        if (exchange != null) {
            String contentEncoding = exchange.getIn().getHeader(Exchange.CONTENT_ENCODING, String.class);
            String contentType = ExchangeHelper.getContentType(exchange);
            entity.setContentEncoding(contentEncoding);
            entity.setContentType(contentType);
        }
        return entity;
    }

    private static HttpEntity asHttpEntity(byte[] data, Exchange exchange) throws Exception {
        AbstractHttpEntity entity;
        if (exchange != null && !exchange.getProperty(Exchange.SKIP_GZIP_ENCODING, Boolean.FALSE, Boolean.class)) {
            String contentEncoding = exchange.getIn().getHeader(Exchange.CONTENT_ENCODING, String.class);
            InputStream stream = GZIPHelper.compressGzip(contentEncoding, data);
            entity = new InputStreamEntity(stream, stream instanceof ByteArrayInputStream
                ? stream.available() != 0 ? stream.available() : -1 : -1);
        } else {
            // create the Repeatable HttpEntity
            entity = new ByteArrayEntity(data);
        }
        if (exchange != null) {
            String contentEncoding = exchange.getIn().getHeader(Exchange.CONTENT_ENCODING, String.class);
            String contentType = ExchangeHelper.getContentType(exchange);
            entity.setContentEncoding(contentEncoding);
            entity.setContentType(contentType);
        }
        return entity;
    }
}