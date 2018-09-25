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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * @version 
 */
public class AOPNestedRouteTest extends ContextTestSupport {

    @Test
    public void testAOPNestedRoute() throws Exception {
        getMockEndpoint("mock:start").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:before").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:after").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:middle").expectedBodiesReceived("Bye");
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye Bye World");

        String out = template.requestBody("direct:start", "Hello World", String.class);
        assertEquals("Bye Bye World", out);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @SuppressWarnings("deprecation")
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("mock:start")
                    .aop().around("mock:before", "mock:after")
                        .transform(constant("Bye")).to("mock:middle").transform(body().append(" World"))
                    .end()
                    .transform(body().prepend("Bye "))
                    .to("mock:result");
            }
        };
    }
}