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

/**
 * @version 
 */
public class FailoverMaximumFailoverAttemptsTest extends ContextTestSupport {

    public void testFailoverMaximumFailoverAttempts() throws Exception {
        getMockEndpoint("mock:bad").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:bad2").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:bad3").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:good").expectedMessageCount(0);

        try {
            template.sendBody("direct:start", "Hello World");
            fail("Should throw exception");
        } catch (Exception e) {
            assertEquals("Damn Again Again", e.getCause().getMessage());
        }

        assertMockEndpointsSatisfied();

        // as its round robin based it remembers that last good endpoint
        // and will invoke the next

        resetMocks();

        getMockEndpoint("mock:bad").expectedMessageCount(0);
        getMockEndpoint("mock:bad2").expectedMessageCount(0);
        getMockEndpoint("mock:bad3").expectedMessageCount(0);
        getMockEndpoint("mock:good").expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", "Bye World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .loadBalance().failover(2, false, true).
                        to("direct:bad", "direct:bad2", "direct:bad3", "direct:good");

                from("direct:bad")
                    .to("log:bad")
                    .to("mock:bad")
                    .throwException(new IllegalArgumentException("Damn"));

                from("direct:bad2")
                    .to("log:bad2")
                    .to("mock:bad2")
                    .throwException(new IllegalArgumentException("Damn Again"));

                from("direct:bad3")
                    .to("log:bad3")
                    .to("mock:bad3")
                    .throwException(new IllegalArgumentException("Damn Again Again"));

                from("direct:good")
                    .to("log:good")
                    .to("mock:good");
            }
        };
    }

}