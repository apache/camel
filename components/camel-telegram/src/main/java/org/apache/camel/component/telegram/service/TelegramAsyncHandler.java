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

package org.apache.camel.component.telegram.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.telegram.model.MessageResult;
import org.apache.camel.support.GZIPHelper;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TelegramAsyncHandler {
    private static final Logger LOG = LoggerFactory.getLogger(TelegramAsyncHandler.class);

    private final String uri;
    private final Class<? extends MessageResult> resultClass;
    private final ObjectMapper mapper;
    private final Exchange exchange;
    private final AsyncCallback callback;

    TelegramAsyncHandler(String uri, Class<? extends MessageResult> resultClass, ObjectMapper mapper, Exchange exchange,
                         AsyncCallback callback) {
        this.uri = uri;
        this.resultClass = resultClass;
        this.mapper = mapper;
        this.exchange = exchange;
        this.callback = callback;
    }

    public static String extractCharset(String line, String defaultValue) {
        if (line == null) {
            return defaultValue;
        }

        final String[] parts = line.split(" ");
        String charsetInfo = "";

        for (var part : parts) {
            if (part.startsWith("charset")) {
                charsetInfo = part;
                break;
            }
        }

        final String charset = charsetInfo.replace("charset=", "").replace(";", "");

        if (charset.isBlank()) {
            return defaultValue;
        }

        return charset;

    }

    public Object handleCompressedResponse(HttpResponse<InputStream> response) {
        Object result;
        final boolean success = response.statusCode() >= 200 && response.statusCode() < 300;

        String charsetInfo = response.headers().firstValue("Content-Type").orElse(null);
        final String charset = extractCharset(charsetInfo, StandardCharsets.UTF_8.name());

        final String contentEncoding = response.headers().firstValue("Content-Encoding").orElse(null);

        try (InputStream is = GZIPHelper.uncompressGzip(contentEncoding, response.body());
             Reader r = new InputStreamReader(is, charset)) {

            if (LOG.isDebugEnabled()) {
                response.headers().map().forEach((key, value) -> LOG.debug("header {}={}", key, value));
            }

            if (success) {
                if (LOG.isTraceEnabled()) {
                    final String body = IOHelper.toString(r);
                    LOG.trace("Received body for {}: {}", uri, body);
                    result = mapper.readValue(body, resultClass);
                } else {
                    result = mapper.readValue(r, resultClass);
                }

                exchange.getMessage().setBody(result);
            } else {
                throw new RuntimeCamelException(
                        uri + " responded: " + response.statusCode() + IOHelper.toString(r));
            }
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(ex);
        } catch (IOException ex) {
            throw new RuntimeCamelException("Could not parse the response from " + uri, ex);
        } finally {
            callback.done(false);
        }

        return exchange;
    }
}
