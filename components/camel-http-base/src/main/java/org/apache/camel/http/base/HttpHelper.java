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
package org.apache.camel.http.base;

import java.net.ProtocolException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.support.http.HttpUtil;
import org.apache.camel.util.CollectionHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;

public final class HttpHelper {

    private HttpHelper() {
        // Helper class
    }

    public static boolean isSecureConnection(String uri) {
        return uri.startsWith("https");
    }

    public static int[] parserHttpVersion(String s) throws ProtocolException {
        int major;
        int minor;
        if (s == null) {
            throw new IllegalArgumentException("String may not be null");
        }
        if (!s.startsWith("HTTP/")) {
            throw new ProtocolException("Invalid HTTP version string: " + s);
        }
        int i1 = "HTTP/".length();
        int i2 = s.indexOf('.', i1);
        if (i2 == -1) {
            throw new ProtocolException("Invalid HTTP version number: " + s);
        }
        try {
            major = Integer.parseInt(s.substring(i1, i2));
        } catch (NumberFormatException e) {
            throw new ProtocolException("Invalid HTTP major version number: " + s);
        }
        i1 = i2 + 1;
        i2 = s.length();
        try {
            minor = Integer.parseInt(s.substring(i1, i2));
        } catch (NumberFormatException e) {
            throw new ProtocolException("Invalid HTTP minor version number: " + s);
        }
        return new int[] { major, minor };
    }

    public static void setCharsetFromContentType(String contentType, Exchange exchange) {
        if (contentType != null) {
            String charset = IOHelper.getCharsetNameFromContentType(contentType);
            if (charset != null) {
                exchange.setProperty(ExchangePropertyKey.CHARSET_NAME, charset);
            }
        }
    }

    /**
     * Appends the key/value to the headers.
     * <p/>
     * This implementation supports keys with multiple values. In such situations the value will be a
     * {@link java.util.List} that contains the multiple values.
     *
     * @param headers headers
     * @param key     the key
     * @param value   the value
     */
    public static void appendHeader(Map<String, Object> headers, String key, Object value) {
        CollectionHelper.appendEntry(headers, key, value);
    }

    /**
     * Extracts the parameter value.
     * <p/>
     * This implementation supports HTTP multi value parameters which is based on the syntax of
     * <tt>[value1, value2, value3]</tt> by returning a {@link List} containing the values.
     * <p/>
     * If the value is not a HTTP multi value the value is returned as is.
     *
     * @param  value the parameter value
     * @return       the extracted parameter value, see more details in javadoc.
     */
    public static Object extractHttpParameterValue(String value) {
        if (ObjectHelper.isEmpty(value)) {
            return value;
        }

        // trim value before checking for multiple parameters
        String trimmed = value.trim();

        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            // remove the [ ] markers
            trimmed = trimmed.substring(1, trimmed.length() - 1);
            List<String> list = new ArrayList<>();
            String[] values = trimmed.split(",");
            for (String s : values) {
                list.add(s.trim());
            }
            return list;
        }

        return value;
    }

    /**
     * Checks whether the given http status code is within the ok range
     *
     * @param  statusCode        the status code
     * @param  okStatusCodeRange the ok range (inclusive)
     * @return                   <tt>true</tt> if ok, <tt>false</tt> otherwise
     */
    public static boolean isStatusCodeOk(int statusCode, String okStatusCodeRange) {
        return HttpUtil.isStatusCodeOk(statusCode, okStatusCodeRange);
    }

    /**
     * In the endpoint the user may have defined rest {} placeholders. This helper method map those placeholders with
     * data from the incoming request context path
     *
     * @param headersMap   a Map instance containing the headers
     * @param path         the URL path
     * @param consumerPath the consumer path
     */
    public static void evalPlaceholders(Map<String, Object> headersMap, String path, String consumerPath) {
        evalPlaceholders(headersMap::put, path, consumerPath);
    }

    /**
     * In the endpoint the user may have defined rest {} placeholders. This helper method map those placeholders with
     * data from the incoming request context path
     *
     * @param keyPairConsumer a consumer for the placeholder key pair
     * @param path            the URL path
     * @param consumerPath    the consumer path
     */
    public static void evalPlaceholders(BiConsumer<String, Object> keyPairConsumer, String path, String consumerPath) {
        // split using single char / is optimized in the jdk
        final String[] paths = path.split("/");
        final String[] consumerPaths = consumerPath.split("/");

        for (int i = 0; i < consumerPaths.length; i++) {
            if (paths.length < i) {
                break;
            }
            final String p1 = consumerPaths[i];
            if (p1.startsWith("{") && p1.endsWith("}")) {
                final String key = p1.substring(1, p1.length() - 1);
                final String value = paths[i];
                if (value != null) {
                    keyPairConsumer.accept(key, value);
                }
            }
        }
    }

}
