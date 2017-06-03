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
public class FailoverStickyTest extends ContextTestSupport {

    public void testFailoverSticky() throws Exception {
        getMockEndpoint("mock:bad").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:bad2").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:good").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:good2").expectedMessageCount(0);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        // as its sticky based it remembers that last good endpoint
        // and will invoke the last good

        resetMocks();

        getMockEndpoint("mock:bad").expectedMessageCount(0);
        getMockEndpoint("mock:bad2").expectedMessageCount(0);
        getMockEndpoint("mock:good").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:good2").expectedMessageCount(0);

        template.sendBody("direct:start", "Bye World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("direct:start")
                    // Use failover load balancer in stateful sticky mode
                    // which mean it will failover immediately in case of an exception
                    // as it does NOT inherit error handler. It will also keep retrying as
                    // its configured to newer exhaust.
                    .loadBalance().failover(-1, false, false, true).
                        to("direct:bad", "direct:bad2", "direct:good", "direct:good2");
                // END SNIPPET: e1

                from("direct:bad")
                    .to("mock:bad")
                    .throwException(new IllegalArgumentException("Damn"));

                from("direct:bad2")
                    .to("mock:bad2")
                    .throwException(new IllegalArgumentException("Damn Again"));

                from("direct:good")
                    .to("mock:good");

                from("direct:good2")
                    .to("mock:good2");
            }
        };
    }

}
