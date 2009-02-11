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
package org.apache.camel.component.cxf.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.component.cxf.transport.CamelTransportConstants;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.message.Message;

/**
 * Utility class to propagate headers to and from CXF message.
 *
 * @version $Revision$
 */
public final class CxfHeaderHelper {

    /**
     * Utility class does not have public constructor
     */
    private CxfHeaderHelper() {
    }


    /**
     * Progagates Camel headers to CXF message.
     *
     * @param strategy header filter strategy
     * @param headers Camel header
     * @param message CXF meassage
     */
    public static void propagateCamelToCxf(HeaderFilterStrategy strategy,
            Map<String, Object> headers, Message message) {

        Map<String, List<String>> cxfHeaders =
            CastUtils.cast((Map)message.get(Message.PROTOCOL_HEADERS));

        if (cxfHeaders == null) {
            cxfHeaders = new HashMap<String, List<String>>();
            message.put(Message.PROTOCOL_HEADERS, cxfHeaders);
        }

        for (Map.Entry<String, Object> entry : headers.entrySet()) {
            if (strategy != null
                    && !strategy.applyFilterToCamelHeaders(entry.getKey(), entry.getValue())) {

                if (CamelTransportConstants.CONTENT_TYPE.equals(entry.getKey())) {
                    message.put(Message.CONTENT_TYPE, entry.getValue());
                } else if (Client.REQUEST_CONTEXT.equals(entry.getKey())
                            || Client.RESPONSE_CONTEXT.equals(entry.getKey())
                            || Message.RESPONSE_CODE.equals(entry.getKey())) {
                    message.put(entry.getKey(), entry.getValue());
                } else {
                    List<String> listValue = new ArrayList<String>();
                    listValue.add(entry.getValue().toString());
                    cxfHeaders.put(entry.getKey(), listValue);
                }
            }
        }
    }

    public static void propagateCxfToCamel(HeaderFilterStrategy strategy,
            Message message, Map<String, Object> headers) {

        if (strategy == null) {
            return;
        }

        Map<String, List<String>> cxfHeaders =
            CastUtils.cast((Map)message.get(Message.PROTOCOL_HEADERS));

        if (cxfHeaders != null) {
            for (Map.Entry<String, List<String>> entry : cxfHeaders.entrySet()) {
                if (!strategy.applyFilterToExternalHeaders(entry.getKey(), entry.getValue())) {
                    headers.put(entry.getKey(), entry.getValue().get(0));
                }
            }
        }

        // propagate content type
        String key = Message.CONTENT_TYPE;
        Object value = message.get(key);
        if (value != null && !strategy.applyFilterToExternalHeaders(key, value)) {
            headers.put(CamelTransportConstants.CONTENT_TYPE, value);
        }

        // propagate request context
        key = Client.REQUEST_CONTEXT;
        value = message.get(key);        
        if (value != null && !strategy.applyFilterToExternalHeaders(key, value)) {
            headers.put(key, value);
        }

        // propagate response context
        key = Client.RESPONSE_CONTEXT;
        value = message.get(key);        
        if (value != null && !strategy.applyFilterToExternalHeaders(key, value)) {
            headers.put(key, value);
        }      
        
    }
}
