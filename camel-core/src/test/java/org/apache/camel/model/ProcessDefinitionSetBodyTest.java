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
package org.apache.camel.model;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

public class ProcessDefinitionSetBodyTest extends ContextTestSupport {

    private static final String SUPPLIER_MESSAGE = "Hello from a Supplier!";
    private static final String FUNCTION_MESSAGE = "Hello from a Function!";

    public void testProcessDefinitionSetBody() throws InterruptedException {

        MockEndpoint functionMock1 = getMockEndpoint("mock:supplierOutput");
        functionMock1.expectedMessageCount(1);
        functionMock1.expectedBodyReceived().constant(SUPPLIER_MESSAGE);
        MockEndpoint functionMock2 = getMockEndpoint("mock:functionOutput");
        functionMock2.expectedMessageCount(1);
        functionMock2.expectedBodyReceived().constant(FUNCTION_MESSAGE);

        template.sendBody("direct:start", "are you there?");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .setBody(() -> SUPPLIER_MESSAGE)
                    .to("mock:supplierOutput")
                    .setBody(exchange -> FUNCTION_MESSAGE)
                    .to("mock:functionOutput")
                    .to("mock:output");
            }
        };
    }
}
