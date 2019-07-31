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
package org.apache.camel.component.cxf;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class CxfMultipleConsumersSupportTest extends CamelTestSupport {
    protected static int port1 = CXFTestSupport.getPort1(); 
    protected static int port2 = CXFTestSupport.getPort2(); 
    

    protected static final String SIMPLE_ENDPOINT_ADDRESS = "http://localhost:" + port1 + "/CxfMultipleConsumersSupportTest/test";
    protected static final String SIMPLE_ENDPOINT_URI = "cxf://" + SIMPLE_ENDPOINT_ADDRESS
        + "?serviceClass=org.apache.camel.component.cxf.HelloService";

    protected static final String SIMPLE_OTHER_ADDRESS = "http://localhost:" + port2 + "/CxfMultipleConsumersSupportTest/test";
    protected static final String SIMPLE_OTHER_URI = "cxf://" + SIMPLE_OTHER_ADDRESS
        + "?serviceClass=org.apache.camel.component.cxf.HelloService";

    @Test
    public void testMultipleConsumersNotAllowed() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(SIMPLE_ENDPOINT_URI).to("mock:a");

                from("direct:start").to("mock:result");

                from(SIMPLE_ENDPOINT_URI).to("mock:b");
            }
        });
        try {
            context.start();
            fail("Should have thrown an exception");
        } catch (Exception e) {
            assertTrue(e.getMessage().endsWith(
                "Multiple consumers for the same endpoint is not allowed: cxf://http://localhost:" + port1 
                + "/CxfMultipleConsumersSupportTest/test?serviceClass=org.apache.camel.component.cxf.HelloService"));
        }
    }

    @Test
    public void testNoMultipleConsumers() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(SIMPLE_ENDPOINT_URI).to("mock:a");

                from("direct:start").to("mock:result");

                from(SIMPLE_OTHER_URI).to("mock:b");
            }
        });

        // is allowed
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}
