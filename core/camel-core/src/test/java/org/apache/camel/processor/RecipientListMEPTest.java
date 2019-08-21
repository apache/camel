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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class RecipientListMEPTest extends ContextTestSupport {

    @Test
    public void testMEPInOnly() throws Exception {
        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World", "Hello Again");
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World", "Bye World");

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Hello Again");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testMEPInOutOnly() throws Exception {
        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World", "Hello Again");
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World", "Bye World");

        String out = template.requestBody("direct:start", "Hello World", String.class);
        assertEquals("Bye World", out);

        out = template.requestBody("direct:start", "Hello Again", String.class);
        assertEquals("Bye World", out);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").recipientList().constant("seda:foo?exchangePattern=InOut").to("mock:result");

                from("seda:foo").to("mock:foo").transform().constant("Bye World");
            }
        };
    }
}
