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
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.RoutePolicy;

/**
 * @version 
 */
public class FlipRoutePolicyTest extends ContextTestSupport {

    public void testFlipRoutePolicyTest() throws Exception {
        MockEndpoint foo = getMockEndpoint("mock:foo");
        foo.expectedMinimumMessageCount(3);

        MockEndpoint bar = getMockEndpoint("mock:bar");
        bar.expectedMinimumMessageCount(3);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // create the flip route policy
                RoutePolicy policy = new FlipRoutePolicy("foo", "bar");

                // use the flip route policy in the foo route
                from("timer://foo?delay=0&period=10")
                    .routeId("foo").routePolicy(policy)
                    .setBody().constant("Foo message")
                    .to("log:foo")
                    .to("mock:foo");

                // use the flip route policy in the bar route and do NOT start
                // this route on startup
                from("timer://bar?delay=0&period=10")
                    .routeId("bar").routePolicy(policy).noAutoStartup()
                    .setBody().constant("Bar message")
                    .to("log:bar")
                    .to("mock:bar");
            }
        };
    }
}
