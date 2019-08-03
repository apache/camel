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
package org.apache.camel.component.cxf.converter;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.StreamCache;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.CxfSimpleRouterTest;

/**
 * A unit test for testing reading SOAP body in PAYLOAD mode.
 */
public class CxfPayLoadStreamCacheRouterTest extends CxfSimpleRouterTest {

    private String routerEndpointURI = "cxf://" + getRouterAddress() + "?" + SERVICE_CLASS + "&dataFormat=PAYLOAD";
    private String serviceEndpointURI = "cxf://" + getServiceAddress() + "?" + SERVICE_CLASS + "&dataFormat=PAYLOAD";

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: payload
                from(routerEndpointURI).streamCaching().process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        Object payload = exchange.getIn().getBody();
                        assertTrue("payload is not a StreamCache", payload instanceof StreamCache);
                    }                    
                })
                .to(serviceEndpointURI);
                // END SNIPPET: payload
            }
        };
    }
}
