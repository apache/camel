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
package org.apache.camel.component.vm;

import org.apache.camel.builder.RouteBuilder;

public class VmShouldUseConsumerContext extends AbstractVmTestSupport {

    public void testConsumerContext() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        String out = template2.requestBody("direct:start", "Hello World", String.class);
        assertEquals("Hello World", out);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // using "toD" instead of "to" make use of the exchange context
                // to resolve the direct endpoint at route execution.
                // if the exchange context is not the excepted one, the toD will
                // fail
                from("vm:test").toD("direct:process");
                from("direct:process").to("mock:result");
            }
        };
    }

    @Override
    protected RouteBuilder createRouteBuilderForSecondContext() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("vm:test");
            }
        };
    }
}
