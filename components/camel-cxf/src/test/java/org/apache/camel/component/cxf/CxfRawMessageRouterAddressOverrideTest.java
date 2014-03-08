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
package org.apache.camel.component.cxf;


import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;

public class CxfRawMessageRouterAddressOverrideTest extends CxfRawMessageRouterTest {
    private String routerEndpointURI = "cxf://" + getRouterAddress() + "?" + SERVICE_CLASS + "&dataFormat=MESSAGE";
    private String serviceEndpointURI = "cxf://http://localhost:9002/badAddress" + "?" + SERVICE_CLASS + "&dataFormat=MESSAGE";
    
    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(routerEndpointURI).to("log:org.apache.camel?level=DEBUG")
                .setHeader(Exchange.DESTINATION_OVERRIDE_URL, constant(getServiceAddress()))
                .to(serviceEndpointURI).to("mock:result");
            }
        };
    }
}
