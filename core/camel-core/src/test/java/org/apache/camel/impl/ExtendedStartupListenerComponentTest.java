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
package org.apache.camel.impl;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.ExtendedStartupListener;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.direct.DirectComponent;
import org.junit.Test;

public class ExtendedStartupListenerComponentTest extends ContextTestSupport {

    private MyComponent my;

    @Test
    public void testExtendedStartupListenerComponent() throws Exception {
        // and now the routes are started
        assertTrue(context.getRouteController().getRouteStatus("foo").isStarted());
        assertTrue(context.getRouteController().getRouteStatus("bar").isStarted());
        assertTrue(context.getRouteController().getRouteStatus("late").isStarted());

        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:late").expectedMessageCount(1);

        template.sendBody("direct:foo", "Hello World");
        template.sendBody("direct:late", "Bye World");

        assertMockEndpointsSatisfied();

        // we are invoked 2 times and then 1 extra when we add new routes
        // because the component
        // will trigger again when the new routes are being started
        assertEquals(2 + 1, my.getInvoked());
    }

    private static class MyComponent extends DirectComponent implements ExtendedStartupListener {

        private int invoked;

        @Override
        public void onCamelContextStarted(CamelContext context, boolean alreadyStarted) throws Exception {
            invoked++;

            // the routes should not have been started as they start afterwards
            if (!alreadyStarted) {
                assertTrue(context.getRouteController().getRouteStatus("foo").isStopped());
                assertTrue(context.getRouteController().getRouteStatus("bar").isStopped());
            }
        }

        public int getInvoked() {
            return invoked;
        }

        @Override
        public void onCamelContextFullyStarted(CamelContext context, boolean alreadyStarted) throws Exception {
            invoked++;

            // the original routes are now started
            assertTrue(context.getRouteController().getRouteStatus("foo").isStarted());
            assertTrue(context.getRouteController().getRouteStatus("bar").isStarted());

            // we can add new routes
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:late").routeId("late").to("mock:late");
                }
            });

        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                my = new MyComponent();
                context.addComponent("my", my);

                from("direct:foo").routeId("foo").to("my:bar");
                from("my:bar").routeId("bar").to("mock:result");
            }
        };
    }
}
