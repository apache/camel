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
package org.apache.camel.builder;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.component.mock.MockEndpoint;

public class ExpressionClauseSupplierTest extends ContextTestSupport {

    private static final String BODY_SUPPLIER_MSG = "I am the body supplier!";

    public void testBodySupplier() throws Exception {
        MockEndpoint functionMock1 = getMockEndpoint("mock:output1");
        functionMock1.expectedMessageCount(1);
        functionMock1.expectedBodyReceived().constant(BODY_SUPPLIER_MSG);

        template.sendBody("direct:supplier1", "are you there?");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:supplier1")
                    .transform().body(() -> BODY_SUPPLIER_MSG)
                    .to("mock:output1");
            }
        };
    }

}
