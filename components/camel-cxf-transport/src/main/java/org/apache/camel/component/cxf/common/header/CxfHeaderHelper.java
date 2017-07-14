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
package org.apache.camel.component.cxf.common.header;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.camel.Exchange;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class to propagate headers to and from CXF message.
 *
 * @version 
 */
public final class CxfHeaderHelper {
    private static final Logger LOG = LoggerFactory.getLogger(CxfHeaderHelper.class);

    private static final Map<String, String> CAMEL_TO_CXF_HEADERS = new HashMap<>();
    private static final Map<String, String> CXF_TO_CAMEL_HEADERS = new HashMap<>();

    static {
        // initialize mappings between Camel and CXF header names
        defineMapping(Exchange.HTTP_URI, Message.REQUEST_URI);
        defineMapping(Exchange.HTTP_METHOD, Message.HTTP_REQUEST_METHOD);
        defineMapping(Exchange.HTTP_PATH, Message.PATH_INFO);
        defineMapping(Exchange.CONTENT_TYPE, Message.CONTENT_TYPE);
        defineMapping(Exchange.HTTP_CHARACTER_ENCODING, Message.ENCODING);
        defineMapping(Exchange.HTTP_QUERY, Message.QUERY_STRING);
        defineMapping(Exchange.ACCEPT_CONTENT_TYPE, Message.ACCEPT_CONTENT_TYPE);
        defineMapping(Exchange.HTTP_RESPONSE_CODE, Message.RESPONSE_CODE);
    }

    /**
     * Utility class does not have public constructor
     */
    private CxfHeaderHelper() {
    }

    private static void defineMapping(String camelHeader, String cxfHeader) {
        CAMEL_TO_CXF_HEADERS.put(camelHeader, cxfHeader);
        CXF_TO_CAMEL_HEADERS.put(cxfHeader, camelHeader);
    }

    /**
     * Propagates Camel headers to CXF headers.
     *
     * @param strategy header filter strategy
     * @param camelHeaders Camel headers
     * @param requestHeaders CXF request headers
     * @param camelExchange provides context for filtering
     */
    public static void propagateCamelHeadersToCxfHeaders(HeaderFilterStrategy strategy,
            Map<String, Object> camelHeaders, Map<String, List<String>> requestHeaders,
            Exchange camelExchange) throws Exception {
        if (strategy == null) {
            return;
        }
        camelHeaders.entrySet().forEach(entry -> {
            // Need to make sure the cxf needed header will not be filtered
            if (strategy.applyFilterToCamelHeaders(entry.getKey(), entry.getValue(), camelExchange)
                    && CAMEL_TO_CXF_HEADERS.get(entry.getKey()) == null) {
                LOG.trace("Drop Camel header: {}={}", entry.getKey(), entry.getValue());
                return;
            }

            // we need to make sure the entry value is not null
            if (entry.getValue() == null) {
                LOG.trace("Drop Camel header: {}={}", entry.getKey(), entry.getValue());
                return;
            }

            String cxfHeaderName = CAMEL_TO_CXF_HEADERS.getOrDefault(entry.getKey(), entry.getKey());

            LOG.trace("Propagate Camel header: {}={} as {}", entry.getKey(), entry.getValue(), cxfHeaderName);

            requestHeaders.put(cxfHeaderName, Arrays.asList(entry.getValue().toString()));
        });
    }

    /**
     * Propagates Camel headers to CXF message.
     *
     * @param strategy header filter strategy
     * @param camelHeaders Camel header
     * @param cxfMessage CXF message
     * @param exchange provides context for filtering
     */
    public static void propagateCamelToCxf(HeaderFilterStrategy strategy,
            Map<String, Object> camelHeaders, Message cxfMessage, Exchange exchange) {

        // use copyProtocolHeadersFromCxfToCamel treemap to keep ordering and ignore key case
        cxfMessage.putIfAbsent(Message.PROTOCOL_HEADERS, new TreeMap<>(String.CASE_INSENSITIVE_ORDER));
        final Map<String, List<String>> cxfHeaders =
            CastUtils.cast((Map<?, ?>) cxfMessage.get(Message.PROTOCOL_HEADERS));

        if (strategy == null) {
            return;
        }

        camelHeaders.entrySet().forEach(entry -> {
            // Need to make sure the cxf needed header will not be filtered
            if (strategy.applyFilterToCamelHeaders(entry.getKey(), entry.getValue(), exchange)) {
                LOG.trace("Drop external header: {}={}", entry.getKey(), entry.getValue());
                return;
            }

            // we need to make sure the entry value is not null
            if (entry.getValue() == null) {
                LOG.trace("Drop Camel header: {}={}", entry.getKey(), entry.getValue());
                return;
            }

            String cxfHeaderName = CAMEL_TO_CXF_HEADERS.getOrDefault(entry.getKey(), entry.getKey());

            LOG.trace("Propagate Camel header: {}={} as {}", entry.getKey(), entry.getValue(), cxfHeaderName);

            if (Exchange.CONTENT_TYPE.equals(entry.getKey())) {
                cxfMessage.put(cxfHeaderName, entry.getValue());
            }
            if (Exchange.HTTP_RESPONSE_CODE.equals(entry.getKey())
                || Client.REQUEST_CONTEXT.equals(entry.getKey())
                || Client.RESPONSE_CONTEXT.equals(entry.getKey())) {
                cxfMessage.put(cxfHeaderName, entry.getValue());
            } else {
                Object values = entry.getValue();
                if (values instanceof List<?>) {
                    cxfHeaders.put(cxfHeaderName, CastUtils.cast((List<?>) values, String.class));
                } else {
                    List<String> listValue = new ArrayList<>();
                    listValue.add(entry.getValue().toString());
                    cxfHeaders.put(cxfHeaderName, listValue);
                }
            }
        });
    }

    /**
     * Propagates CXF headers to Camel headers.
     *
     * @param strategy header filter strategy
     * @param responseHeaders CXF response headers
     * @param camelHeaders Camel headers
     * @param camelExchange provides context for filtering
     */
    public static void propagateCxfHeadersToCamelHeaders(HeaderFilterStrategy strategy,
            Map<String, List<Object>> responseHeaders, Map<String, Object> camelHeaders,
            Exchange camelExchange) throws Exception {
        if (strategy == null) {
            return;
        }
        responseHeaders.entrySet().forEach(entry -> {
            if (strategy.applyFilterToExternalHeaders(entry.getKey(), entry.getValue(), camelExchange)) {
                LOG.trace("Drop external header: {}={}", entry.getKey(), entry.getValue());
                return;
            }

            String camelHeaderName = CXF_TO_CAMEL_HEADERS.getOrDefault(entry.getKey(), entry.getKey());

            LOG.trace("Populate external header: {}={} as {}", entry.getKey(), entry.getValue(), camelHeaderName);

            camelHeaders.put(camelHeaderName, entry.getValue().get(0));
        });
    }

    /**
     * Propagates CXF headers to Camel message.
     *
     * @param strategy header filter strategy
     * @param cxfMessage CXF message
     * @param camelMessage Camel message
     * @param exchange provides context for filtering
     */
    public static void propagateCxfToCamel(HeaderFilterStrategy strategy, Message cxfMessage,
            org.apache.camel.Message camelMessage, Exchange exchange) {

        if (strategy == null) {
            return;
        }

        // Copy the CXF protocol headers to the camel headers
        copyProtocolHeadersFromCxfToCamel(strategy, exchange, cxfMessage, camelMessage);

        // Copy the CXF HTTP headers to the camel headers
        copyHttpHeadersFromCxfToCamel(strategy, cxfMessage, camelMessage, exchange);

        // propagate request context
        copyCxfHeaderToCamel(strategy, exchange, cxfMessage, camelMessage, Client.REQUEST_CONTEXT);

        // propagate response context
        copyCxfHeaderToCamel(strategy, exchange, cxfMessage, camelMessage, Client.RESPONSE_CONTEXT);
    }

    private static void copyProtocolHeadersFromCxfToCamel(HeaderFilterStrategy strategy, Exchange exchange,
        Message cxfMessage, org.apache.camel.Message camelMessage) {
        Map<String, List<String>> cxfHeaders =
            CastUtils.cast((Map<?, ?>) cxfMessage.getOrDefault(Message.PROTOCOL_HEADERS, Collections.emptyMap()));
        cxfHeaders.entrySet().forEach(cxfHeader -> {
            String camelHeaderName = CXF_TO_CAMEL_HEADERS.getOrDefault(cxfHeader.getKey(), cxfHeader.getKey());
            Object value = convertCxfProtocolHeaderValues(cxfHeader.getValue(), exchange);
            copyCxfHeaderToCamel(strategy, exchange, cxfMessage, camelMessage, cxfHeader.getKey(), camelHeaderName, value);
        });
    }

    private static Object convertCxfProtocolHeaderValues(List<String> values, Exchange exchange) {
        if (values.size() == 1) {
            return values.get(0);
        }
        if (exchange.getProperty(CxfConstants.CAMEL_CXF_PROTOCOL_HEADERS_MERGED, Boolean.FALSE, Boolean.class)) {
            return String.join(", ", values);
        }
        return values;
    }

    public static void copyHttpHeadersFromCxfToCamel(HeaderFilterStrategy strategy, Message cxfMessage,
            org.apache.camel.Message camelMessage, Exchange exchange) {
        CXF_TO_CAMEL_HEADERS.entrySet().forEach(entry ->
                copyCxfHeaderToCamel(strategy, exchange, cxfMessage, camelMessage, entry.getKey(), entry.getValue()));
    }

    private static void copyCxfHeaderToCamel(HeaderFilterStrategy strategy, Exchange exchange,
            Message cxfMessage, org.apache.camel.Message camelMessage, String key) {
        copyCxfHeaderToCamel(strategy, exchange, cxfMessage, camelMessage, key, key);
    }

    private static void copyCxfHeaderToCamel(HeaderFilterStrategy strategy, Exchange exchange,
            Message cxfMessage, org.apache.camel.Message camelMessage, String cxfKey, String camelKey) {
        copyCxfHeaderToCamel(strategy, exchange, cxfMessage, camelMessage, cxfKey, camelKey, cxfMessage.get(cxfKey));
    }

    private static void copyCxfHeaderToCamel(HeaderFilterStrategy strategy, Exchange exchange,
            Message cxfMessage, org.apache.camel.Message camelMessage, String cxfKey, String camelKey,
            Object initialValue) {
        Object value = initialValue;
        if (Message.PATH_INFO.equals(cxfKey)) {
            // We need remove the BASE_PATH from the PATH_INFO
            value = convertPathInfo(cxfMessage);
        } else if (Message.CONTENT_TYPE.equals(cxfKey)) {
            // propagate content type with the encoding information
            // We need to do it as the CXF does this kind of thing in transport level
            value = determineContentType(cxfMessage);
        }
        if (value != null && !strategy.applyFilterToExternalHeaders(cxfKey, value, exchange)) {
            camelMessage.setHeader(camelKey, value);
        }
    }

    private static String convertPathInfo(Message message) {
        String pathInfo = findHeaderValue(message, Message.PATH_INFO);
        String basePath = findHeaderValue(message, Message.BASE_PATH);
        if (pathInfo != null && basePath != null && pathInfo.startsWith(basePath)) {
            return pathInfo.substring(basePath.length());
        }
        return pathInfo;
    }

    private static String determineContentType(Message message) {
        String ct = findHeaderValue(message, Message.CONTENT_TYPE);
        String enc = findHeaderValue(message, Message.ENCODING);

        if (null != ct) {
            if (enc != null 
                && !ct.contains("charset=")
                && !ct.toLowerCase().contains("multipart/related")) {
                ct = ct + "; charset=" + enc;
            }
        } else if (enc != null) {
            ct = "text/xml; charset=" + enc;
        } else {
            ct = "text/xml";
        }
        // update the content_type value in the message
        message.put(Message.CONTENT_TYPE, ct);
        return ct;
    }

    private static String findHeaderValue(Message message, String key) {
        String value = (String) message.get(key);
        if (value != null) {
            return value;
        }
        Map<String, List<String>> protocolHeaders =
            CastUtils.cast((Map<?, ?>) message.getOrDefault(Message.PROTOCOL_HEADERS, Collections.emptyMap()));
        return protocolHeaders.getOrDefault(key, Collections.singletonList(null)).get(0);
    }

}
