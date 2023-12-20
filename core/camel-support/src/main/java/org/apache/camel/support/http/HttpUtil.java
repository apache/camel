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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Message;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.HeaderFilterStrategy;
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
     *
     * @param  map the map of options
     * @param  url the base URL
     * @return     the recreated URL
     */
    public static String recreateUrl(Map<String, Object> map, String url) {
        // get the endpoint
        String query = URISupport.createQueryString(map);
        if (!query.isEmpty()) {
            url = url + "?" + query;
        }
        return url;
    }

    /**
     * Add common in/out filters used in HTTP components
     *
     * @param filterSet The set instance containing the out filters
     */
    public static void addCommonFilters(Set<String> filterSet) {
        filterSet.add("content-length");
        filterSet.add("content-type");
        filterSet.add("host");
        // Add the filter for the Generic Message header
        // http://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html#sec4.5
        filterSet.add("cache-control");
        filterSet.add("connection");
        filterSet.add("date");
        filterSet.add("pragma");
        filterSet.add("trailer");
        filterSet.add("transfer-encoding");
        filterSet.add("upgrade");
        filterSet.add("via");
        filterSet.add("warning");
    }

    /**
     * Checks whether the given http status code is within the ok range
     *
     * @param  statusCode        the status code
     * @param  okStatusCodeRange the ok range (inclusive)
     * @return                   <tt>true</tt> if ok, <tt>false</tt> otherwise
     */
    public static boolean isStatusCodeOk(int statusCode, String okStatusCodeRange) {
        String[] ranges = okStatusCodeRange.split(",");
        for (String range : ranges) {
            boolean ok;
            if (range.contains("-")) {
                int from = Integer.parseInt(StringHelper.before(range, "-"));
                int to = Integer.parseInt(StringHelper.after(range, "-"));
                ok = statusCode >= from && statusCode <= to;
            } else {
                int exact = Integer.parseInt(range);
                ok = exact == statusCode;
            }
            if (ok) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parse a HTTP status range and passes the values to the consumer method
     *
     * @param  range    the HTTP status range in the format "XXX-YYY" (i.e.: 200-299)
     * @param  consumer a consumer method to receive the parse ranges
     * @return          true if the range was parsed or false otherwise
     */
    public static boolean parseStatusRange(String range, BiConsumer<Integer, Integer> consumer) {
        // default is 200-299 so lets optimize for this
        if (range.contains("-")) {
            final String minRangeStr = StringHelper.before(range, "-");
            final String maxRangeStr = StringHelper.after(range, "-");

            if (minRangeStr == null || maxRangeStr == null) {
                return false;
            }

            final int minOkRange = Integer.parseInt(minRangeStr);
            final int maxOkRange = Integer.parseInt(maxRangeStr);

            consumer.accept(minOkRange, maxOkRange);

            return true;
        }

        return false;
    }

    /**
     * Iterates over a list of values and passes them to the consumer after applying the filter strategy. This is mostly
     * used to simplify setting headers for HTTP responses
     *
     * @param headerFilterStrategy the filter strategy to apply
     * @param exchange             an exchange to apply the header strategy
     * @param it                   the iterator providing the values
     * @param tc                   a type converter instance so that the values can be converted to string
     * @param key                  a key associated with the values being iterated
     * @param consumer             a consumer method to receive the converted values. It can receive either a list of
     *                             values or a single value.
     */
    public static void applyHeader(
            HeaderFilterStrategy headerFilterStrategy, Exchange exchange, Iterator<?> it,
            TypeConverter tc, String key, BiConsumer<List<String>, String> consumer) {
        String firstValue = null;
        List<String> values = null;

        while (it.hasNext()) {
            final String headerValue = tc.convertTo(String.class, it.next());
            if (headerValue != null && !headerFilterStrategy.applyFilterToCamelHeaders(key, headerValue, exchange)) {
                if (firstValue == null) {
                    firstValue = headerValue;
                } else {
                    if (values == null) {
                        values = new ArrayList<>();
                        values.add(firstValue);
                    }
                    values.add(headerValue);
                }
            }
        }

        consumer.accept(values, firstValue);
    }

    /*
     * Using rest producer then headers are mapping to uri and query parameters using {key} syntax
     * if there is a match to an existing Camel Message header, then we should filter (=true) this
     * header as its already been mapped by the RestProducer from camel-core, and we do not want
     * the header to include as HTTP header also (eg as duplicate value)
     */
    public static boolean filterCheck(String templateUri, String queryParameters, String headerName, boolean answer) {
        if (!answer) {
            if (templateUri != null) {
                String token = "{" + headerName + "}";
                if (templateUri.contains(token)) {
                    answer = true;
                }
            }
            if (!answer && queryParameters != null) {
                String token = "=%7B" + headerName + "%7D"; // encoded values for { }
                if (queryParameters.contains(token)) {
                    answer = true;
                }
            }
        }
        return answer;
    }
}
