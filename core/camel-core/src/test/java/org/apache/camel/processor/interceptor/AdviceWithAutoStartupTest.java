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
package org.apache.camel.processor.interceptor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.reifier.RouteReifier;
import org.junit.Test;

public class AdviceWithAutoStartupTest extends ContextTestSupport {

    @Test
    public void testAdvised() throws Exception {
        assertFalse(context.getRouteController().getRouteStatus("foo").isStarted());
        assertFalse(context.getRouteController().getRouteStatus("bar").isStarted());

        RouteReifier.adviceWith(context.getRouteDefinition("bar"), context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                replaceFromWith("seda:newBar");
            }
        });

        assertFalse(context.getRouteController().getRouteStatus("foo").isStarted());
        assertFalse(context.getRouteController().getRouteStatus("bar").isStarted());

        context.getRouteController().startRoute("foo");
        context.getRouteController().startRoute("bar");

        assertTrue(context.getRouteController().getRouteStatus("foo").isStarted());
        assertTrue(context.getRouteController().getRouteStatus("bar").isStarted());

        getMockEndpoint("mock:newBar").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.setAutoStartup(false);

                from("direct:start").routeId("foo").to("seda:newBar");

                from("seda:bar").routeId("bar").to("mock:newBar");
            }
        };
    }
}
