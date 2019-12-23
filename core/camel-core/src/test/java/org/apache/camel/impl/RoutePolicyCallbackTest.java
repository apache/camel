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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.RoutePolicySupport;
import org.junit.Test;

/**
 *
 */
public class RoutePolicyCallbackTest extends ContextTestSupport {

    protected MyRoutePolicy policy = new MyRoutePolicy();

    public static class MyRoutePolicy extends RoutePolicySupport {

        boolean begin;
        boolean done;
        boolean init;
        boolean remove;
        boolean resume;
        boolean start;
        boolean stop;
        boolean suspend;
        boolean doStart;
        boolean doStop;

        @Override
        public void onExchangeBegin(Route route, Exchange exchange) {
            begin = true;
        }

        @Override
        public void onExchangeDone(Route route, Exchange exchange) {
            done = true;
        }

        @Override
        public void onInit(Route route) {
            init = true;
        }

        @Override
        public void onRemove(Route route) {
            remove = true;
        }

        @Override
        public void onResume(Route route) {
            resume = true;
        }

        @Override
        public void onStart(Route route) {
            start = true;
        }

        @Override
        public void onStop(Route route) {
            stop = true;
        }

        @Override
        public void onSuspend(Route route) {
            suspend = true;
        }

        @Override
        protected void doStop() throws Exception {
            doStop = true;
        }

        @Override
        protected void doStart() throws Exception {
            doStart = true;
        }
    }

    protected MyRoutePolicy getAndInitMyRoutePolicy() {
        return policy;
    }

    @Test
    public void testCallback() throws Exception {
        policy = getAndInitMyRoutePolicy();

        assertTrue(policy.doStart);
        assertTrue(policy.init);

        assertFalse(policy.begin);
        assertFalse(policy.done);
        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();
        assertTrue(policy.begin);
        assertTrue(policy.done);

        assertFalse(policy.suspend);
        context.getRouteController().suspendRoute("foo");
        assertTrue(policy.suspend);

        assertFalse(policy.resume);
        context.getRouteController().resumeRoute("foo");
        assertTrue(policy.resume);

        assertFalse(policy.stop);
        context.getRouteController().stopRoute("foo");
        assertTrue(policy.stop);

        // previously started, so force flag to be false
        policy.start = false;
        assertFalse(policy.start);
        context.getRouteController().startRoute("foo");
        assertTrue(policy.start);

        assertFalse(policy.remove);
        context.getRouteController().stopRoute("foo");
        context.removeRoute("foo");
        assertTrue(policy.remove);

        // stop camel which should stop policy as well
        context.stop();
        assertTrue(policy.doStop);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("foo").routePolicy(policy).to("mock:result");
            }
        };
    }
}
