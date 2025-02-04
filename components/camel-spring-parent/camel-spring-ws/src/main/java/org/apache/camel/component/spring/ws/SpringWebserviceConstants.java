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
package org.apache.camel.component.spring.ws;

import org.apache.camel.Exchange;
import org.apache.camel.spi.Metadata;

public final class SpringWebserviceConstants {

    @Metadata(label = "producer", description = "The endpoint URI", javaType = "String")
    public static final String SPRING_WS_ENDPOINT_URI = "CamelSpringWebserviceEndpointUri";
    @Metadata(label = "producer",
              description = "SOAP action to include inside a SOAP request when accessing remote web services",
              javaType = "String")
    public static final String SPRING_WS_SOAP_ACTION = "CamelSpringWebserviceSoapAction";
    @Metadata(label = "producer", description = "The soap header source", javaType = "javax.xml.transform.Source")
    public static final String SPRING_WS_SOAP_HEADER = "CamelSpringWebserviceSoapHeader";
    /**
     * WS-Addressing 1.0 action header to include when accessing web services. The To header is set to the address of
     * the web service as specified in the endpoint URI (default Spring-WS behavior).
     */
    @Metadata(label = "producer", javaType = "java.net.URI")
    public static final String SPRING_WS_ADDRESSING_ACTION = "CamelSpringWebserviceAddressingAction";
    /**
     * Signifies the value for the faultAction response WS-Addressing <code>FaultTo</code> header that is provided by
     * the method.
     *
     * See org.springframework.ws.soap.addressing.server.annotation.Action annotation for more details.
     */
    @Metadata(label = "producer", javaType = "java.net.URI")
    public static final String SPRING_WS_ADDRESSING_PRODUCER_FAULT_TO = "CamelSpringWebserviceAddressingFaultTo";
    /**
     * Signifies the value for the replyTo response WS-Addressing <code>ReplyTo</code> header that is provided by the
     * method.
     *
     * See org.springframework.ws.soap.addressing.server.annotation.Action annotation for more details.
     */
    @Metadata(label = "producer", javaType = "java.net.URI")
    public static final String SPRING_WS_ADDRESSING_PRODUCER_REPLY_TO = "CamelSpringWebserviceAddressingReplyTo";
    public static final String SPRING_WS_ADDRESSING_CONSUMER_OUTPUT_ACTION = "CamelSpringWebserviceAddressingOutputAction";
    public static final String SPRING_WS_ADDRESSING_CONSUMER_FAULT_ACTION = "CamelSpringWebserviceAddressingFaultAction";
    @Metadata(label = "consumer", description = "The breadcrumb id.", javaType = "String")
    public static final String BREADCRUMB_ID = Exchange.BREADCRUMB_ID;

    private SpringWebserviceConstants() {
    }
}
