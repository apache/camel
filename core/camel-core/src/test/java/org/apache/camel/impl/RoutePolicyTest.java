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

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.RoutePolicy;
import org.junit.Test;

public class RoutePolicyTest extends ContextTestSupport {
    private MyRoutPolicy routePolicy = new MyRoutPolicy();

    @Test
    public void testStartCalledWhenCamelStarts() throws Exception {
        assertEquals(1, routePolicy.getStartCount());
    }

    @Test
    public void testStartCalledWhenRouteStarts() throws Exception {
        assertEquals(1, routePolicy.getStartCount());
        context.getRouteController().stopRoute("foo");
        context.getRouteController().startRoute("foo");
        assertEquals(2, routePolicy.getStartCount());
    }

    @Test
    public void testStopCalledWhenCamelStops() throws Exception {
        assertEquals(0, routePolicy.getStopCount());
        stopCamelContext();
        assertEquals(1, routePolicy.getStopCount());
    }

    @Test
    public void testStopCalledWhenRouteStops() throws Exception {
        assertEquals(0, routePolicy.getStopCount());
        context.getRouteController().stopRoute("foo");
        assertEquals(1, routePolicy.getStopCount());
    }

    @Test
    public void testSuspendCalledWhenRouteSuspends() throws Exception {
        assertEquals(0, routePolicy.getSuspendCount());
        context.getRouteController().suspendRoute("foo");
        assertEquals(1, routePolicy.getSuspendCount());
    }

    @Test
    public void testResumeCalledWhenRouteResumes() throws Exception {
        assertEquals(0, routePolicy.getResumeCount());
        context.getRouteController().suspendRoute("foo");
        context.getRouteController().resumeRoute("foo");
        assertEquals(1, routePolicy.getResumeCount());
    }

    @Test
    public void testRemoveCalledWhenRouteIsRemovedById() throws Exception {
        assertEquals(0, routePolicy.getRemoveCount());
        context.getRouteController().stopRoute("foo");
        assertEquals(0, routePolicy.getRemoveCount());

        context.removeRoute("foo");
        assertEquals(1, routePolicy.getRemoveCount());
    }

    @Test
    public void testRemoveCalledWhenCamelIsStopped() throws Exception {
        assertTrue(context.getStatus().isStarted());
        assertEquals(0, routePolicy.getRemoveCount());
        context.stop();
        assertTrue(context.getStatus().isStopped());
        assertEquals(1, routePolicy.getRemoveCount());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("foo").routePolicy(routePolicy).to("mock:result");
            }
        };
    }

    private class MyRoutPolicy implements RoutePolicy {
        private AtomicInteger removeCounter = new AtomicInteger();
        private AtomicInteger startCounter = new AtomicInteger();
        private AtomicInteger stopCounter = new AtomicInteger();
        private AtomicInteger suspendCounter = new AtomicInteger();
        private AtomicInteger resumeCounter = new AtomicInteger();

        @Override
        public void onRemove(Route route) {
            removeCounter.incrementAndGet();
        }

        @Override
        public void onStart(Route route) {
            startCounter.incrementAndGet();
        }

        @Override
        public void onStop(Route route) {
            stopCounter.incrementAndGet();
        }

        @Override
        public void onSuspend(Route route) {
            suspendCounter.incrementAndGet();
        }

        @Override
        public void onResume(Route route) {
            resumeCounter.incrementAndGet();
        }

        @Override
        public void onInit(Route route) {
        }

        @Override
        public void onExchangeBegin(Route route, Exchange exchange) {
        }

        @Override
        public void onExchangeDone(Route route, Exchange exchange) {
        }

        private int getRemoveCount() {
            return removeCounter.get();
        }

        private int getStartCount() {
            return startCounter.get();
        }

        private int getStopCount() {
            return stopCounter.get();
        }

        private int getSuspendCount() {
            return suspendCounter.get();
        }

        private int getResumeCount() {
            return resumeCounter.get();
        }
    }
}
