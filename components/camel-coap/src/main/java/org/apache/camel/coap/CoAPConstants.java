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

/**
 * CoAP component constants
 */
public interface CoAPConstants {

    /**
     * Supported request methods
     */
    String METHOD_DELETE = "DELETE";
    String METHOD_GET = "GET";
    String METHOD_PING = "PING";
    String METHOD_POST = "POST";
    String METHOD_PUT = "PUT";

    /**
     * Supported CoAP server methods
     */
    String METHOD_RESTRICT_ALL = String.format("%s,%s,%s,%s", METHOD_DELETE, METHOD_GET, METHOD_POST, METHOD_PUT);

    /**
     * CoAP exchange header names
     */
    String COAP_METHOD = "CamelCoapMethod";
    String COAP_RESPONSE_CODE = "CamelCoapResponseCode";
    String COAP_URI = "CamelCoapUri";
}
