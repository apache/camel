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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisabledIfSystemProperty(named = "ci.env.name", matches = "github.com", disabledReason = "Flaky on GitHub Actions")
public class StopTimeoutRouteTest extends ContextTestSupport {

    private CountDownLatch latch = new CountDownLatch(1);

    @Test
    public void testStopTimeout() throws Exception {
        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello Foo");

        // should stop the route before its routed to mock:foo
        template.asyncSendBody("direct:start", "Hello World");
        context.getRouteController().stopRoute("start", 10, TimeUnit.MILLISECONDS);

        // send to the other running route
        template.sendBody("direct:foo", "Hello Foo");

        assertMockEndpointsSatisfied();

        latch.countDown();

        assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("start"));
        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("start")
                        .process(e -> {
                            try {
                                Thread.sleep(500);
                            } catch (Exception ex) {
                                // ignore
                            }
                            latch.countDown();
                        })
                        .to("mock:foo");

                from("direct:foo").routeId("foo").to("mock:foo");
            }
        };
    }

}
