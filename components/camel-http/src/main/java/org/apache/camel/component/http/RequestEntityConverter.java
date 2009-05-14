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
package org.apache.camel.component.http;

import java.io.InputStream;
import java.nio.ByteBuffer;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.component.http.helper.GZIPHelper;
import org.apache.camel.util.ExchangeHelper;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.RequestEntity;

/**
 * Some converter methods to make it easier to convert the body to RequestEntity types.
 */
@Converter
public class RequestEntityConverter {

    @Converter
    public RequestEntity toRequestEntity(ByteBuffer buffer, Exchange exchange) throws Exception {
        return new InputStreamRequestEntity(
                GZIPHelper.toGZIPInputStreamIfRequested(
                        exchange.getIn().getHeader(GZIPHelper.CONTENT_ENCODING, String.class),
                        buffer.array()), ExchangeHelper.getContentType(exchange));
    }

    @Converter
    public RequestEntity toRequestEntity(byte[] array, Exchange exchange) throws Exception {
        return new InputStreamRequestEntity(
                GZIPHelper.toGZIPInputStreamIfRequested(
                        exchange.getIn().getHeader(GZIPHelper.CONTENT_ENCODING, String.class),
                        array), ExchangeHelper.getContentType(exchange));
    }

    @Converter
    public RequestEntity toRequestEntity(InputStream inStream, Exchange exchange) throws Exception {
        return new InputStreamRequestEntity(
                GZIPHelper.getGZIPWrappedInputStream(
                        exchange.getIn().getHeader(GZIPHelper.CONTENT_ENCODING, String.class),
                        inStream), ExchangeHelper.getContentType(exchange));
    }


    @Converter
    public RequestEntity toRequestEntity(String str, Exchange exchange) throws Exception {
        if (GZIPHelper.containsGzip(exchange.getIn().getHeader(GZIPHelper.CONTENT_ENCODING, String.class))) {            
            return new InputStreamRequestEntity(
                GZIPHelper.toGZIPInputStreamIfRequested(
                        exchange.getIn().getHeader(GZIPHelper.CONTENT_ENCODING, String.class),
                        str.getBytes()), ExchangeHelper.getContentType(exchange));
        } else {
            // will use the default StringRequestEntity
            return null;
        }
    }


}

