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
package org.apache.camel.component.cxf.transport;

/**
 * @version 
 */
public final class CamelTransportConstants {

    public static final String TEXT_MESSAGE_TYPE = "text";
    public static final String BINARY_MESSAGE_TYPE = "binary";
    public static final String CAMEL_TARGET_ENDPOINT_URI = "org.apache.cxf.camel.target.endpoint.uri";
    public static final String CAMEL_SERVER_REQUEST_HEADERS = "org.apache.cxf.camel.server.request.headers";
    public static final String CAMEL_SERVER_RESPONSE_HEADERS = "org.apache.cxf.camel.server.response.headers";
    public static final String CAMEL_REQUEST_MESSAGE = "org.apache.cxf.camel.request.message";
    public static final String CAMEL_RESPONSE_MESSAGE = "org.apache.cxf.camel.reponse.message";
    public static final String CAMEL_CLIENT_REQUEST_HEADERS = "org.apache.cxf.camel.template.request.headers";
    public static final String CAMEL_CLIENT_RESPONSE_HEADERS =
            "org.apache.cxf.camel.template.response.headers";
    public static final String CAMEL_CLIENT_RECEIVE_TIMEOUT = "org.apache.cxf.camel.template.timeout";
    public static final String CAMEL_SERVER_CONFIGURATION_URI =
            "http://cxf.apache.org/configuration/transport/camel-server";
    public static final String CAMEL_CLIENT_CONFIGURATION_URI =
            "http://cxf.apache.org/configuration/transport/camel-template";
    public static final String ENDPOINT_CONFIGURATION_URI =
            "http://cxf.apache.org/jaxws/endpoint-config";
    public static final String SERVICE_CONFIGURATION_URI =
            "http://cxf.apache.org/jaxws/service-config";
    public static final String PORT_CONFIGURATION_URI =
            "http://cxf.apache.org/jaxws/port-config";
    public static final String CAMEL_CLIENT_CONFIG_ID = "camel-template";
    public static final String CAMEL_SERVER_CONFIG_ID = "camel-server";
    public static final String CAMEL_REBASED_REPLY_TO = "org.apache.cxf.camel.server.replyto";
    public static final String CAMEL_CORRELATION_ID = "org.apache.cxf.camel.correlationId";
    public static final String CXF_EXCHANGE = "org.apache.cxf.message.exchange";
    public static final String CAMEL_TRANSPORT_PREFIX = "camel:";
    public static final String CAMEL_EXCHANGE = "org.apache.camel.exchange";
    
    private CamelTransportConstants() {
        // Utility class
    }
}
