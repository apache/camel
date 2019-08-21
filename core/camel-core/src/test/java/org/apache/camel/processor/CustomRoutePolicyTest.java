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
package org.apache.camel.processor;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.RoutePolicySupport;
import org.junit.Test;

public class CustomRoutePolicyTest extends ContextTestSupport {

    private final MyCustomRoutePolicy policy = new MyCustomRoutePolicy();

    private static class MyCustomRoutePolicy extends RoutePolicySupport {

        private volatile AtomicBoolean stopped = new AtomicBoolean();

        @Override
        public void onExchangeDone(Route route, Exchange exchange) {
            String body = exchange.getIn().getBody(String.class);
            if ("stop".equals(body)) {
                try {
                    stopped.set(true);
                    stopConsumer(route.getConsumer());
                } catch (Exception e) {
                    handleException(e);
                }
            }
        }

        @Override
        public boolean isStopped() {
            return stopped.get();
        }
    }

    @Test
    public void testCustomPolicy() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("direct:foo", "Hello World");

        assertMockEndpointsSatisfied();

        mock.reset();
        mock.expectedBodiesReceived("stop");

        // we send stop command so we should only get 1 message
        template.sendBody("direct:foo", "stop");

        assertMockEndpointsSatisfied();

        assertTrue("Should be stopped", policy.isStopped());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo").routeId("foo").routePolicy(policy).to("mock:result");
            }
        };
    }
}
