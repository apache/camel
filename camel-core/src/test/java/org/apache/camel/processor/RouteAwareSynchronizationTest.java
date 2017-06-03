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
package org.apache.camel.processor;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.SynchronizationRouteAware;

public class RouteAwareSynchronizationTest extends ContextTestSupport {

    private static final List<String> EVENTS = new ArrayList<String>();

    public void testRouteAwareSynchronization() throws Exception {
        EVENTS.clear();
        assertEquals(0, EVENTS.size());

        getMockEndpoint("mock:a").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:b").expectedBodiesReceived("Hello World");

        template.send("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.addOnCompletion(new MyRouteAware());
                exchange.getIn().setBody("Hello World");
            }
        });

        assertMockEndpointsSatisfied();

        assertEquals(5, EVENTS.size());
        assertEquals("onBeforeRoute-start", EVENTS.get(0));
        assertEquals("onBeforeRoute-foo", EVENTS.get(1));
        assertEquals("onAfterRoute-foo", EVENTS.get(2));
        assertEquals("onAfterRoute-start", EVENTS.get(3));
        assertEquals("onComplete", EVENTS.get(4));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("start")
                    .to("mock:a")
                    .to("direct:foo")
                    .to("mock:b");

                from("direct:foo").routeId("foo")
                    .to("mock:foo");
            }
        };
    }

    private static final class MyRouteAware implements SynchronizationRouteAware {

        @Override
        public void onBeforeRoute(Route route, Exchange exchange) {
            EVENTS.add("onBeforeRoute-" + route.getId());
        }

        @Override
        public void onAfterRoute(Route route, Exchange exchange) {
            EVENTS.add("onAfterRoute-" + route.getId());
        }

        @Override
        public void onComplete(Exchange exchange) {
            EVENTS.add("onComplete");
        }

        @Override
        public void onFailure(Exchange exchange) {
            EVENTS.add("onFailure");
        }
    }
}
