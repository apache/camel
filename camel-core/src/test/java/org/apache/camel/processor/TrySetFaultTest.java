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

public class TrySetFaultTest extends ContextTestSupport {

    public void testSetFault() throws Exception {
        // only mock:start gets the message as a fault body stops routing
        getMockEndpoint("mock:start").expectedMessageCount(1);
        getMockEndpoint("mock:a").expectedMessageCount(0);
        getMockEndpoint("mock:catch-a").expectedMessageCount(0);
        getMockEndpoint("mock:b").expectedMessageCount(0);
        getMockEndpoint("mock:catch-b").expectedMessageCount(0);

        template.requestBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("mock:start")
                    .to("direct:a")
                    .to("mock:a")
                    .to("direct:b")
                    .to("mock:b");

                from("direct:a")
                    .doTry()
                        .setFaultBody(constant("Failed at A"))
                    .doCatch(Exception.class)
                        // fault will not throw an exception
                        .to("mock:catch-a")
                    .end();

                from("direct:b")
                    .doTry()
                        .setFaultBody(constant("Failed at B"))
                    .doCatch(Exception.class)
                        // fault will not throw an exception
                        .to("mock:catch-b")
                    .end()
                    .to("log:b");
            }
        };
    }
}
