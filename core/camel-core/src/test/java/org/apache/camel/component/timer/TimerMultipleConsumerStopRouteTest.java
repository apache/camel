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
package org.apache.camel.component.timer;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 *
 */
public class TimerMultipleConsumerStopRouteTest extends ContextTestSupport {

    @Test
    public void testMultipleConsumers() throws Exception {
        getMockEndpoint("mock:foo").expectedMinimumMessageCount(1);
        getMockEndpoint("mock:bar").expectedMinimumMessageCount(1);

        assertMockEndpointsSatisfied();

        context.getRouteController().stopRoute("bar");

        resetMocks();

        // stopping bar route, we should still keep getting messages to foo
        getMockEndpoint("mock:foo").expectedMinimumMessageCount(2);
        getMockEndpoint("mock:bar").expectedMessageCount(0);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("timer:mytimer?period=100").routeId("foo").to("mock:foo");

                from("timer:mytimer?period=100").routeId("bar").to("mock:bar");
            }
        };
    }
}
