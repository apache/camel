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
package org.apache.camel.impl;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * @version 
 */
public class ShutdownGracefulNoAutoStartedRoutesTest extends ContextTestSupport {

    @Test
    public void testShutdownGraceful() throws Exception {
        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(1);

        context.startRoute("bar");

        template.sendBody("direct:foo", "Hello World");
        template.sendBody("direct:bar", "Bye World");

        assertMockEndpointsSatisfied();

        context.stop();

        assertTrue("Route foo should be stopped", context.getRouteStatus("foo").isStopped());
        assertTrue("Route bar should be stopped", context.getRouteStatus("bar").isStopped());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo").routeId("foo").to("mock:foo");

                from("direct:bar").routeId("bar").noAutoStartup().to("mock:bar");
            }
        };
    }
}