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
package org.apache.camel.component.spark;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;

public class DefaultSparkBinding implements SparkBinding {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultSparkBinding.class);
    private HeaderFilterStrategy headerFilterStrategy = new SparkHeaderFilterStrategy();

    @Override
    public Message toCamelMessage(Request request, Exchange exchange, SparkConfiguration configuration) throws Exception {
        LOG.trace("toCamelMessage: {}", request);

        SparkMessage answer = new SparkMessage(request, null);
        answer.setExchange(exchange);
        if (configuration.isMapHeaders()) {
            populateCamelHeaders(request, answer.getHeaders(), exchange, configuration);
        }

        if (configuration.isDisableStreamCache()) {
            // keep the body as a input stream
            answer.setBody(request.raw().getInputStream());
        } else {
            answer.setBody(request.body());
        }
        return answer;
    }

    @Override
    public void populateCamelHeaders(Request request, Map<String, Object> headers, Exchange exchange, SparkConfiguration configuration) throws Exception {
        // store the method and query and other info in headers as String types
        headers.put(Exchange.HTTP_METHOD, request.raw().getMethod());
        headers.put(Exchange.HTTP_QUERY, request.raw().getQueryString());
        headers.put(Exchange.HTTP_URL, request.raw().getRequestURL().toString());
        headers.put(Exchange.HTTP_URI, request.raw().getRequestURI());
        headers.put(Exchange.HTTP_PATH, request.raw().getPathInfo());
        headers.put(Exchange.CONTENT_TYPE, request.raw().getContentType());

        for (String key : request.attributes()) {
            Object value = request.attribute(key);
            Object decoded = shouldUrlDecodeHeader(configuration, key, value, "UTF-8");
            if (headerFilterStrategy != null
                    && !headerFilterStrategy.applyFilterToExternalHeaders(key, decoded, exchange)) {
                SparkHelper.appendHeader(headers, key, decoded);
            }
        }

        for (String key : request.headers()) {
            Object value = request.headers(key);
            Object decoded = shouldUrlDecodeHeader(configuration, key, value, "UTF-8");
            if (headerFilterStrategy != null
                    && !headerFilterStrategy.applyFilterToExternalHeaders(key, decoded, exchange)) {
                SparkHelper.appendHeader(headers, key, decoded);
            }
        }

        for (Map.Entry<String, String> entry : request.params().entrySet()) {
            String key = mapKey(entry.getKey());
            String value = entry.getValue();
            Object decoded = shouldUrlDecodeHeader(configuration, key, value, "UTF-8");
            if (headerFilterStrategy != null
                    && !headerFilterStrategy.applyFilterToExternalHeaders(key, decoded, exchange)) {
                SparkHelper.appendHeader(headers, key, decoded);
            }
        }
    }

    /**
     * Decodes the header if needed to, or returns the header value as is.
     *
     * @param configuration the configuration
     * @param headerName    the header name
     * @param value         the current header value
     * @param charset       the charset to use for decoding
     * @return the decoded value (if decoded was needed) or a <tt>toString</tt> representation of the value.
     * @throws java.io.UnsupportedEncodingException is thrown if error decoding.
     */
    protected String shouldUrlDecodeHeader(SparkConfiguration configuration, String headerName, Object value, String charset) throws
            UnsupportedEncodingException {
        // do not decode Content-Type
        if (Exchange.CONTENT_TYPE.equals(headerName)) {
            return value.toString();
        } else if (configuration.isUrlDecodeHeaders()) {
            return URLDecoder.decode(value.toString(), charset);
        } else {
            return value.toString();
        }
    }

    protected String mapKey(String key) {
        if (key.startsWith(":")) {
            return key.substring(1);
        } else {
            return key;
        }
    }

}
