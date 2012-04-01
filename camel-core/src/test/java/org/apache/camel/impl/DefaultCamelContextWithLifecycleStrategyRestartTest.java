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

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Route;
import org.apache.camel.VetoCamelContextStartException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.LifecycleStrategySupport;

/**
 *
 */
public class DefaultCamelContextWithLifecycleStrategyRestartTest extends ContextTestSupport {

    private MyStrategy strategy = new MyStrategy();

    public void testRestart() throws Exception {
        assertTrue(context.getStatus().isStarted());
        assertFalse(context.getStatus().isStopped());
        assertEquals(1, context.getRoutes().size());
        assertEquals(1, strategy.getContextStartCounter());

        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();

        // now stop
        context.stop();
        assertFalse(context.getStatus().isStarted());
        assertTrue(context.getStatus().isStopped());
        assertEquals(0, context.getRoutes().size());

        // now start
        context.start();
        assertTrue(context.getStatus().isStarted());
        assertFalse(context.getStatus().isStopped());
        assertEquals(1, context.getRoutes().size());
        assertEquals(2, strategy.getContextStartCounter());

        // must obtain a new template
        template = context.createProducerTemplate();

        // should still work
        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:start", "Bye World");
        assertMockEndpointsSatisfied();
    }

    public void testRouteStopped() throws Exception {
        assertTrue(context.getRouteStatus("foo").isStarted());
        assertEquals(0, strategy.getRemoveCounter());

        context.stopRoute("foo");
        assertEquals(0, strategy.getRemoveCounter());

        context.removeRoute("foo");
        assertEquals(1, strategy.getRemoveCounter());
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.addLifecycleStrategy(strategy);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("foo")
                    .to("mock:result");
            }
        };
    }

    private class MyStrategy extends LifecycleStrategySupport {

        private AtomicInteger contextStartCounter = new AtomicInteger();
        private AtomicInteger removeCounter = new AtomicInteger();

        @Override
        public void onContextStart(CamelContext context) throws VetoCamelContextStartException {
            contextStartCounter.incrementAndGet();
        }

        @Override
        public void onRoutesRemove(Collection<Route> routes) {
            removeCounter.incrementAndGet();
        }

        public int getContextStartCounter() {
            return contextStartCounter.get();
        }

        public int getRemoveCounter() {
            return removeCounter.get();
        }
    }
}
