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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.camel.Exchange;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.message.Message;

/**
 * Utility class to propagate headers to and from CXF message.
 *
 * @version 
 */
public final class CxfHeaderHelper {

    /**
     * Utility class does not have public constructor
     */
    private CxfHeaderHelper() {
    }

    /**
     * Propagates Camel headers to CXF message.
     *
     * @param strategy header filter strategy
     * @param headers Camel header
     * @param message CXF message
     * @param exchange provides context for filtering
     */
    public static void propagateCamelToCxf(HeaderFilterStrategy strategy,
            Map<String, Object> headers, Message message, Exchange exchange) {

        Map<String, List<String>> cxfHeaders =
            CastUtils.cast((Map<?, ?>)message.get(Message.PROTOCOL_HEADERS));

        if (cxfHeaders == null) {
            // use a treemap to keep ordering and ignore key case
            cxfHeaders = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
            message.put(Message.PROTOCOL_HEADERS, cxfHeaders);
        }

        for (Map.Entry<String, Object> entry : headers.entrySet()) {
            if (strategy != null
                    && !strategy.applyFilterToCamelHeaders(entry.getKey(), entry.getValue(), exchange)) {

                if (Exchange.CONTENT_TYPE.equals(entry.getKey())) {
                    message.put(Message.CONTENT_TYPE, entry.getValue());
                }
                if (Client.REQUEST_CONTEXT.equals(entry.getKey())
                    || Client.RESPONSE_CONTEXT.equals(entry.getKey())
                    || Message.RESPONSE_CODE.equals(entry.getKey())) {
                    message.put(entry.getKey(), entry.getValue());
                } else {
                    Object values = entry.getValue();
                    if (values instanceof List<?>) {
                        cxfHeaders.put(entry.getKey(), CastUtils.cast((List<?>)values, String.class));
                    } else {
                        List<String> listValue = new ArrayList<String>();
                        listValue.add(entry.getValue().toString());
                        cxfHeaders.put(entry.getKey(), listValue);
                    }
                }
            }
        }
    }

    public static void propagateCxfToCamel(HeaderFilterStrategy strategy,
            Message message, Map<String, Object> headers, Exchange exchange) {

        if (strategy == null) {
            return;
        }

        // Copy the CXF protocol headers to the camel headers
        Map<String, List<String>> cxfHeaders =
            CastUtils.cast((Map<?, ?>)message.get(Message.PROTOCOL_HEADERS));
        if (cxfHeaders != null) {
            for (Map.Entry<String, List<String>> entry : cxfHeaders.entrySet()) {
                if (!strategy.applyFilterToExternalHeaders(entry.getKey(), entry.getValue(), exchange)) {
                    List<String> values = entry.getValue();
                    //headers.put(entry.getKey(), entry.getValue().get(0));
                    Object evalue;
                    if (values.size() > 1) {
                        if (exchange.getProperty(CxfConstants.CAMEL_CXF_PROTOCOL_HEADERS_MERGED, Boolean.FALSE, Boolean.class)) {
                            StringBuilder sb = new StringBuilder();
                            for (Iterator<String> it = values.iterator(); it.hasNext();) {
                                sb.append(it.next());
                                if (it.hasNext()) {
                                    sb.append(',').append(' ');
                                }
                            }
                            evalue = sb.toString();
                        } else {
                            evalue = values;
                        }
                    } else {
                        evalue = values.get(0);
                    }
                    headers.put(entry.getKey(), evalue);
                }
            }
        }

        // propagate content type with the encoding information
        // We need to do it as the CXF does this kind of thing in transport level
        String key = Message.CONTENT_TYPE;
        Object value = determineContentType(message);
        
        if (value != null && !strategy.applyFilterToExternalHeaders(key, value, exchange)) {
            headers.put(Exchange.CONTENT_TYPE, value);
        }

        // propagate request context
        key = Client.REQUEST_CONTEXT;
        value = message.get(key);
        if (value != null && !strategy.applyFilterToExternalHeaders(key, value, exchange)) {
            headers.put(key, value);
        }

        // propagate response context
        key = Client.RESPONSE_CONTEXT;
        value = message.get(key);
        if (value != null && !strategy.applyFilterToExternalHeaders(key, value, exchange)) {
            headers.put(key, value);
        }
        
        // propagate response code
        key = Message.RESPONSE_CODE;
        value = message.get(key);
        if (value != null && !strategy.applyFilterToExternalHeaders(key, value, exchange)) {
            headers.put(Exchange.HTTP_RESPONSE_CODE, value);
        }
    }
    
    private static String determineContentType(Message message) {
        String ct  = (String)message.get(Message.CONTENT_TYPE);
        String enc = (String)message.get(Message.ENCODING);

        if (null != ct) {
            if (enc != null 
                && ct.indexOf("charset=") == -1
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

}
