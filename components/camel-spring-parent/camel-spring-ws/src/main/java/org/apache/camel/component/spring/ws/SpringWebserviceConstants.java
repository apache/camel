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

    @Metadata(label = "producer",
              description = "The address that the producer sends the request to. This header replaces the webServiceEndpointUri"
                            + " option, and the default URI of the WebServiceTemplate of the webServiceTemplate option.",
              javaType = "String")
    public static final String SPRING_WS_ENDPOINT_URI = "CamelSpringWebserviceEndpointUri";
    @Metadata(label = "producer",
              description = "The SOAP action of the request that the producer sends. This header replaces the soapAction"
                            + " option.",
              javaType = "String")
    public static final String SPRING_WS_SOAP_ACTION = "CamelSpringWebserviceSoapAction";
    @Metadata(label = "producer",
              description = "The SOAP header as XML. The consumer sets it to the SOAP header of the request, as a"
                            + " javax.xml.transform.Source of the soap:Header element. The producer converts it to a Source and"
                            + " adds the XML to the content of the SOAP header of the request that it sends. When"
                            + " allowResponseHeaderOverride is true, the producer sets it to the SOAP header of the response.",
              javaType = "javax.xml.transform.Source")
    public static final String SPRING_WS_SOAP_HEADER = "CamelSpringWebserviceSoapHeader";
    @Metadata(label = "producer",
              description = "The WS-Addressing 1.0 Action header of the request that the producer sends. This header replaces"
                            + " the wsAddressingAction option.",
              javaType = "java.net.URI")
    public static final String SPRING_WS_ADDRESSING_ACTION = "CamelSpringWebserviceAddressingAction";
    @Metadata(label = "producer",
              description = "The address of the WS-Addressing FaultTo header of the request that the producer sends. Only used"
                            + " when the wsAddressingAction option or the CamelSpringWebserviceAddressingAction header is set."
                            + " This header replaces the faultTo option.",
              javaType = "java.net.URI")
    public static final String SPRING_WS_ADDRESSING_PRODUCER_FAULT_TO = "CamelSpringWebserviceAddressingFaultTo";
    @Metadata(label = "producer",
              description = "The address of the WS-Addressing ReplyTo header of the request that the producer sends. Only used"
                            + " when the wsAddressingAction option or the CamelSpringWebserviceAddressingAction header is set."
                            + " This header replaces the replyTo option.",
              javaType = "java.net.URI")
    public static final String SPRING_WS_ADDRESSING_PRODUCER_REPLY_TO = "CamelSpringWebserviceAddressingReplyTo";
    public static final String SPRING_WS_ADDRESSING_CONSUMER_OUTPUT_ACTION = "CamelSpringWebserviceAddressingOutputAction";
    public static final String SPRING_WS_ADDRESSING_CONSUMER_FAULT_ACTION = "CamelSpringWebserviceAddressingFaultAction";
    @Metadata(label = "consumer",
              description = "The breadcrumb id of the request. The consumer sets it to the value of the breadcrumbId transport"
                            + " header of the request, for example an HTTP header, when breadcrumb is enabled on the"
                            + " CamelContext.",
              javaType = "String")
    public static final String BREADCRUMB_ID = Exchange.BREADCRUMB_ID;

    private SpringWebserviceConstants() {
    }
}
