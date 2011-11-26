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

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.RoutePolicy;

public class RoutePolicyOnRemoveTest extends ContextTestSupport {
    private MyRoutPolicy routePolicy = new MyRoutPolicy();

    public void testRemoveCalledWhenRouteIsRemovedById() throws Exception {
        assertEquals(0, routePolicy.getRemoveCount());
        context.stopRoute("foo");
        assertEquals(0, routePolicy.getRemoveCount());

        context.removeRoute("foo");
        assertEquals(1, routePolicy.getRemoveCount());
    }

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
                from("direct:start")
                    .routeId("foo")
                    .routePolicy(routePolicy)
                    .to("mock:result");
            }
        };
    }

    private class MyRoutPolicy implements RoutePolicy {
        private AtomicInteger removeCounter = new AtomicInteger();

        @Override
        public void onInit(Route route) {
        }

        @Override
        public void onRemove(Route route) {
            removeCounter.incrementAndGet();
        }

        @Override
        public void onExchangeBegin(Route route, Exchange exchange) {
        }

        @Override
        public void onExchangeDone(Route route, Exchange exchange) {
        }

        public int getRemoveCount() {
            return removeCounter.get();
        }

    }
}
