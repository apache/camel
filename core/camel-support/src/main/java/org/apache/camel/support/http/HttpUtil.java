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

package org.apache.camel.support.http;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Message;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;

public final class HttpUtil {
    private static final int INTERNAL_SERVER_ERROR = 500;
    private static final int OK = 200;
    private static final int NO_CONTENT = 204;

    private HttpUtil() {
    }

    /**
     * Given an exchange handling HTTP, determines the status response code to return for the caller
     *
     * @param  camelExchange the exchange to evaluate
     * @param  body          an optional payload (i.e.: the message body) carrying a response code
     * @return               An integer value with the response code
     */
    public static int determineResponseCode(Exchange camelExchange, Object body) {
        boolean failed = camelExchange.isFailed();
        int defaultCode = failed ? INTERNAL_SERVER_ERROR : OK;

        Message message = camelExchange.getMessage();
        Integer currentCode = message.getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
        int codeToUse = currentCode == null ? defaultCode : currentCode;

        if (codeToUse != INTERNAL_SERVER_ERROR) {
            if (body == null || body instanceof String && ((String) body).isBlank()) {
                // no content
                codeToUse = currentCode == null ? NO_CONTENT : currentCode;
            }
        }

        return codeToUse;
    }

    /**
     * Deprecated way to extract the charset value from the content type string
     *
     * @deprecated             use {@link IOHelper#getCharsetNameFromContentType(String)}
     * @param      contentType the content type string
     * @return                 the charset value or null if there is nothing to extract
     */
    @Deprecated
    public static String getCharsetFromContentType(String contentType) {
        if (contentType != null) {
            // find the charset and set it to the Exchange
            int index = contentType.indexOf("charset=");
            if (index > 0) {
                String charset = contentType.substring(index + 8);
                // there may be another parameter after a semi colon, so skip that
                if (charset.contains(";")) {
                    charset = StringHelper.before(charset, ";");
                }
                return IOHelper.normalizeCharset(charset);
            }
        }
        return null;
    }

    /**
     * Extracts the charset value from the content type string and sets it on the given exchange
     *
     * @param contentType the content type string
     * @param exchange    the exchange to set the charset value
     */
    public static void setCharsetFromContentType(String contentType, Exchange exchange) {
        String charset = HttpUtil.getCharsetFromContentType(contentType);
        if (charset != null) {
            exchange.setProperty(ExchangePropertyKey.CHARSET_NAME, IOHelper.normalizeCharset(charset));
        }
    }

    /**
     * Recreates the URL from a map of options
     * @param map the map of options
     * @param url the base URL
     * @return the recreated URL
     */
    public static String recreateUrl(Map<String, Object> map, String url) {
        // get the endpoint
        String query = URISupport.createQueryString(map);
        if (!query.isEmpty()) {
            url = url + "?" + query;
        }
        return url;
    }
}
