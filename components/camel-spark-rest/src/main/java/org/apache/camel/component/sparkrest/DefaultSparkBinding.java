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
package org.apache.camel.component.sparkrest;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.MessageHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class DefaultSparkBinding implements SparkBinding {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultSparkBinding.class);
    private HeaderFilterStrategy headerFilterStrategy = new SparkHeaderFilterStrategy();

    @Override
    public Message toCamelMessage(Request request, Exchange exchange, SparkConfiguration configuration) throws Exception {
        LOG.trace("toCamelMessage: {}", request);

        SparkMessage answer = new SparkMessage(exchange.getContext(), request, null);
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
        String path = request.raw().getPathInfo();
        SparkEndpoint endpoint = (SparkEndpoint) exchange.getFromEndpoint();
        if (endpoint.getPath() != null) {
            // need to match by lower case as we want to ignore case on context-path
            String endpointPath = endpoint.getPath();
            String matchPath = path.toLowerCase(Locale.US);
            String match = endpointPath.toLowerCase(Locale.US);

            if (match.endsWith("/*")) {
                match = match.substring(0, match.length() - 2);
            }
            if (!match.startsWith("/")) {
                match = "/" + match;
            }
            if (matchPath.startsWith(match)) {
                path = path.substring(match.length());
            }
        }
        headers.put(Exchange.HTTP_PATH, path);

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

        for (String key : request.queryParams()) {
            String value = request.queryParams(key);
            Object decoded = shouldUrlDecodeHeader(configuration, key, value, "UTF-8");
            if (headerFilterStrategy != null 
                    && !headerFilterStrategy.applyFilterToExternalHeaders(key, decoded, exchange)) {
                SparkHelper.appendHeader(headers, key, decoded);
            }
        }

        String[] splat = request.splat();
        String key = SparkConstants.SPLAT;
        if (headerFilterStrategy != null
                && !headerFilterStrategy.applyFilterToExternalHeaders(key, splat, exchange)) {
            SparkHelper.appendHeader(headers, key, splat);
        }
        
        // store the method and query and other info in headers as String types
        headers.putIfAbsent(Exchange.HTTP_METHOD, request.raw().getMethod());
        headers.putIfAbsent(Exchange.HTTP_QUERY, request.raw().getQueryString());
        headers.putIfAbsent(Exchange.HTTP_URL, request.raw().getRequestURL().toString());
        headers.putIfAbsent(Exchange.HTTP_URI, request.raw().getRequestURI());
        headers.putIfAbsent(Exchange.CONTENT_TYPE, request.raw().getContentType());
    }

    @Override
    public void toSparkResponse(Message message, Response response, SparkConfiguration configuration) throws Exception {
        LOG.trace("toSparkResponse: {}", message);

        // the response code is 200 for OK and 500 for failed
        boolean failed = message.getExchange().isFailed();
        int defaultCode = failed ? 500 : 200;

        int code = message.getHeader(Exchange.HTTP_RESPONSE_CODE, defaultCode, int.class);
        response.status(code);
        LOG.trace("HTTP Status Code: {}", code);

        TypeConverter tc = message.getExchange().getContext().getTypeConverter();

        // append headers
        // must use entrySet to ensure case of keys is preserved
        for (Map.Entry<String, Object> entry : message.getHeaders().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (Exchange.CONTENT_TYPE.equalsIgnoreCase(key)) {
               // we set content-type later
                continue;
            }

            // use an iterator as there can be multiple values. (must not use a delimiter)
            final Iterator<?> it = ObjectHelper.createIterator(value, null);
            while (it.hasNext()) {
                String headerValue = tc.convertTo(String.class, it.next());
                if (headerValue != null && headerFilterStrategy != null
                        && !headerFilterStrategy.applyFilterToCamelHeaders(key, headerValue, message.getExchange())) {
                    LOG.trace("HTTP-Header: {}={}", key, headerValue);
                    response.header(key, headerValue);
                }
            }
        }

        // set the content type in the response.
        String contentType = MessageHelper.getContentType(message);
        if (contentType != null) {
            // set content-type
            response.header(Exchange.CONTENT_TYPE, contentType);
            LOG.trace("Content-Type: {}", contentType);
        }

        Object body = message.getBody();
        Exception cause = message.getExchange().getException();

        // if there was an exception then use that as body
        if (cause != null) {
            if (configuration.isTransferException()) {
                // we failed due an exception, and transfer it as java serialized object
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                oos.writeObject(cause);
                oos.flush();
                IOHelper.close(oos, bos);

                body = bos.toByteArray();
                // force content type to be serialized java object
                message.setHeader(Exchange.CONTENT_TYPE, SparkConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT);
            } else {
                // we failed due an exception so print it as plain text
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                cause.printStackTrace(pw);

                // the body should then be the stacktrace
                body = sw.toString().getBytes();
                // force content type to be text/plain as that is what the stacktrace is
                message.setHeader(Exchange.CONTENT_TYPE, "text/plain");
            }

            // and mark the exception as failure handled, as we handled it by returning it as the response
            ExchangeHelper.setFailureHandled(message.getExchange());
        }

        if (body != null) {
            String str = tc.mandatoryConvertTo(String.class, message.getExchange(), body);
            response.body(str);
            // and must set body to the response body as Spark otherwise may output something else
            message.setBody(str);
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
