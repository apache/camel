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
package org.apache.camel.component.spring.ws;

public final class SpringWebserviceConstants {

    public static final String SPRING_WS_ENDPOINT_URI = "CamelSpringWebserviceEndpointUri";

    public static final String SPRING_WS_SOAP_ACTION = "CamelSpringWebserviceSoapAction";
    public static final String SPRING_WS_SOAP_HEADER = "CamelSpringWebserviceSoapHeader";

    public static final String SPRING_WS_ADDRESSING_ACTION = "CamelSpringWebserviceAddressingAction";
    public static final String SPRING_WS_ADDRESSING_PRODUCER_FAULT_TO =  "CamelSpringWebserviceAddressingFaultTo";
    public static final String SPRING_WS_ADDRESSING_PRODUCER_REPLY_TO = "CamelSpringWebserviceAddressingReplyTo";
    public static final String SPRING_WS_ADDRESSING_CONSUMER_OUTPUT_ACTION = "CamelSpringWebserviceAddressingOutputAction";
    public static final String SPRING_WS_ADDRESSING_CONSUMER_FAULT_ACTION = "CamelSpringWebserviceAddressingFaultAction";

    private SpringWebserviceConstants() {
    }
}
