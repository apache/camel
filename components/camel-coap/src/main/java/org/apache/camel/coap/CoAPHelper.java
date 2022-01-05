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
package org.apache.camel.coap;

import java.util.LinkedList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.util.ObjectHelper;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.MediaTypeRegistry;

/**
 * Various helper methods for CoAP
 */
public final class CoAPHelper {

    private CoAPHelper() {
    }

    /**
     * Determines which CoAP request method to use based on the content of the target request URI, the message body or
     * value from the CamelCoapMethod header.
     *
     * @param  exchange the exchange
     * @param  client   the CoAP client
     * @return          the CoAP request method
     */
    public static String getDefaultMethod(Exchange exchange, CoapClient client) {
        String method = exchange.getIn().getHeader(CoAPConstants.COAP_METHOD, String.class);
        if (method == null) {
            Object body = exchange.getIn().getBody();
            if (body == null || client.getURI().contains("?")) {
                method = CoAPConstants.METHOD_GET;
            } else {
                method = CoAPConstants.METHOD_POST;
            }
        }
        return method;
    }

    /**
     * Determines which method verbs the CoAP server should be restricted to handling.
     */
    public static String getDefaultMethodRestrict(String methodRestrict) {
        if (ObjectHelper.isNotEmpty(methodRestrict)) {
            return methodRestrict;
        }
        return CoAPConstants.METHOD_RESTRICT_ALL;
    }

    public static List<String> getPathSegmentsFromPath(String path) {
        List<String> segments = new LinkedList<>();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        while (!path.isEmpty()) {
            int idx = path.indexOf('/');
            if (idx == -1) {
                segments.add(path);
                break;
            }
            segments.add(path.substring(0, idx));
            path = path.substring(idx + 1);
        }
        return segments;
    }

    public static void convertCoapResponseToMessage(CoapResponse coapResponse, Message message) {
        String mt = MediaTypeRegistry.toString(coapResponse.getOptions().getContentFormat());
        message.setHeader(CoAPConstants.CONTENT_TYPE, mt);
        message.setHeader(CoAPConstants.COAP_RESPONSE_CODE, coapResponse.getCode().toString());
        message.setBody(coapResponse.getPayload());
    }
}
