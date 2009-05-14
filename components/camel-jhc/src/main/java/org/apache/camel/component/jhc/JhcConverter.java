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
package org.apache.camel.component.jhc;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.util.ExchangeHelper;
import org.apache.http.HttpEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;

@Converter
public final class JhcConverter {

    private JhcConverter() {
    }

    @Converter
    public static InputStream toInputStream(HttpEntity entity) throws IOException {
        return entity.getContent();
    }

    @Converter
    public static byte[] toByteArray(HttpEntity entity) throws IOException {
        return EntityUtils.toByteArray(entity);
    }

    @Converter
    public static String toString(HttpEntity entity) throws IOException {
        return EntityUtils.toString(entity);
    }

    @Converter
    public static HttpEntity toEntity(InputStream is, Exchange exchange) {
        InputStreamEntity answer = new InputStreamEntity(is, -1);
        String contentType = ExchangeHelper.getContentType(exchange);
        if (contentType != null) {
            answer.setContentType(contentType);
        }
        return answer;
    }

    @Converter
    public static HttpEntity toEntity(String str, Exchange exchange) throws UnsupportedEncodingException {
        String charset = exchange.getProperty(Exchange.CHARSET_NAME, String.class);
        StringEntity answer =  new StringEntity(str, charset);
        String contentType = ExchangeHelper.getContentType(exchange);
        if (contentType != null) {
            answer.setContentType(contentType);
        }
        return answer;
    }
}
