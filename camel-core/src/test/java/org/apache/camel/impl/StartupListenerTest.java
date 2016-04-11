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

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.StartupListener;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class StartupListenerTest extends ContextTestSupport {

    private MyStartupListener my = new MyStartupListener();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.addStartupListener(my);
        return context;
    }

    private static class MyStartupListener implements StartupListener {

        private int invoked;
        private boolean alreadyStarted;

        public void onCamelContextStarted(CamelContext context, boolean alreadyStarted) throws Exception {
            invoked++;
            this.alreadyStarted = alreadyStarted;

            if (alreadyStarted) {
                // the routes should already been started as we add the listener afterwards
                assertTrue(context.getRouteStatus("foo").isStarted());
            } else {
                // the routes should not have been started as they start afterwards
                assertTrue(context.getRouteStatus("foo").isStopped());
            }
        }

        public int getInvoked() {
            return invoked;
        }

        public boolean isAlreadyStarted() {
            return alreadyStarted;
        }
    }

    public void testStartupListenerComponent() throws Exception {
        // and now the routes are started
        assertTrue(context.getRouteStatus("foo").isStarted());

        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:foo", "Hello World");

        assertMockEndpointsSatisfied();

        assertEquals(1, my.getInvoked());
        assertFalse(my.isAlreadyStarted());
    }

    public void testStartupListenerComponentAlreadyStarted() throws Exception {
        // and now the routes are started
        assertTrue(context.getRouteStatus("foo").isStarted());

        MyStartupListener other = new MyStartupListener();
        context.addStartupListener(other);

        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:foo", "Hello World");

        assertMockEndpointsSatisfied();

        assertEquals(1, other.getInvoked());
        assertTrue(other.isAlreadyStarted());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo").routeId("foo").to("mock:result");
            }
        };
    }
}