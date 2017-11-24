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
package org.apache.camel.coap;

import org.apache.camel.Exchange;
import org.apache.camel.util.ObjectHelper;
import org.eclipse.californium.core.CoapClient;

/**
 * Various helper methods for CoAP
 */
public final class CoAPHelper {

    private CoAPHelper() {
    }

    /**
     * Determines which CoAP request method to use based on the content of the target
     * request URI, the message body or value from the CamelCoapMethod header.
     *
     * @param exchange the exchange
     * @param client the CoAP client
     * @return the CoAP request method
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
     *
     * @param methodRestrict
     * @return
     */
    public static String getDefaultMethodRestrict(String methodRestrict) {
        if (ObjectHelper.isNotEmpty(methodRestrict)) {
            return methodRestrict;
        }
        return CoAPConstants.METHOD_RESTRICT_ALL;
    }
}
