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
package org.apache.camel.http.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.util.GZIPHelper;

/**
 * Some converter methods making it easy to convert the body of a message to servlet types or to switch between
 * the underlying {@link ServletInputStream} or {@link BufferedReader} payloads etc.
 */
@Converter
public final class HttpConverter {

    private HttpConverter() {
    }

    @Converter
    public static HttpServletRequest toServletRequest(Message message) {
        if (message == null) {
            return null;
        }
        return message.getHeader(Exchange.HTTP_SERVLET_REQUEST, HttpServletRequest.class);
    }

    @Converter
    public static HttpServletResponse toServletResponse(Message message) {
        if (message == null) {
            return null;
        }
        return message.getHeader(Exchange.HTTP_SERVLET_RESPONSE, HttpServletResponse.class);
    }

    @Converter
    public static ServletInputStream toServletInputStream(HttpMessage message) throws IOException {
        HttpServletRequest request = toServletRequest(message);
        if (request != null) {
            return request.getInputStream();
        }
        return null;
    }

    @Converter
    public static InputStream toInputStream(HttpMessage message, Exchange exchange) throws Exception {
        return toInputStream(toServletRequest(message), exchange);
    }

    @Converter
    public static BufferedReader toReader(HttpMessage message) throws IOException {
        HttpServletRequest request = toServletRequest(message);
        if (request != null) {
            return request.getReader();
        }
        return null;
    }

    @Converter
    public static InputStream toInputStream(HttpServletRequest request, Exchange exchange) throws IOException {
        if (request == null) {
            return null;
        }
        InputStream is = request.getInputStream();
        if (is != null && is.available() <= 0) {
            // there is no data, so we cannot uncompress etc.
            return is;
        }
        if (exchange == null || !exchange.getProperty(Exchange.SKIP_GZIP_ENCODING, Boolean.FALSE, Boolean.class)) {
            String contentEncoding = request.getHeader(Exchange.CONTENT_ENCODING);
            return GZIPHelper.uncompressGzip(contentEncoding, is);
        } else {
            return is;
        }
    }

}
