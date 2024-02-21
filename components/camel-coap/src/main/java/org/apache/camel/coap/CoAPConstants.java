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

import org.apache.camel.Exchange;
import org.apache.camel.spi.Metadata;

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
    @Metadata(description = "The CoAP ETag for the response.", javaType = "byte[]")
    String COAP_ETAG = "CamelCoapETag";
    @Metadata(description = "The CoAP Max-Age for the response body.", javaType = "java.lang.Long")
    String COAP_MAX_AGE = "CamelCoapMaxAge";
    @Metadata(description = "The request method that the CoAP producer should use when calling the target CoAP\n" +
                            "server URI. Valid options are DELETE, GET, PING, POST & PUT.",
              javaType = "String")
    String COAP_METHOD = "CamelCoapMethod";
    @Metadata(description = "The CoAP response code sent by the external server. See RFC 7252 for details\n" +
                            "of what each code means.",
              javaType = "String")
    String COAP_RESPONSE_CODE = "CamelCoapResponseCode";
    @Metadata(description = "The content type", javaType = "String")
    String CONTENT_TYPE = Exchange.CONTENT_TYPE;
}
