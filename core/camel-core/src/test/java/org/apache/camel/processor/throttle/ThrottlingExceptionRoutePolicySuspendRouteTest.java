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
package org.apache.camel.processor.throttle;

import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.throttling.ThrottlingExceptionRoutePolicy;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When the route is suspended by the route controller while the circuit is open, the half open timer of the
 * {@link ThrottlingExceptionRoutePolicy} must not resume the consumer.
 */
class ThrottlingExceptionRoutePolicySuspendRouteTest extends ContextTestSupport {

    private ThrottlingExceptionRoutePolicy policy;

    @Test
    void testSuspendedRouteStaysSuspendedWhenHalfOpen() throws Exception {
        ServiceSupport consumer = (ServiceSupport) context.getRoute("foo").getConsumer();

        // a failure opens the circuit, which suspends the consumer
        assertThrows(Exception.class, () -> template.sendBody("direct:start", "Kaboom"));
        await().atMost(10, TimeUnit.SECONDS).until(consumer::isSuspended);
        assertEquals("opened", policy.getStateAsString());

        // the route is suspended (such as by an operator) while the circuit is open
        context.getRouteController().suspendRoute("foo");
        assertEquals(ServiceStatus.Suspended, context.getRouteController().getRouteStatus("foo"));
        assertEquals("opened", policy.getStateAsString(), "The half open timer should not have fired yet");

        // the half open timer fires, but the route must stay suspended
        await().atMost(10, TimeUnit.SECONDS).until(() -> "half opened".equals(policy.getStateAsString()));
        assertTrue(consumer.isSuspended(), "The consumer of the suspended route should not be resumed");
        assertEquals(ServiceStatus.Suspended, context.getRouteController().getRouteStatus("foo"));

        // when the route is resumed then the circuit closes on success
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        context.getRouteController().resumeRoute("foo");
        template.sendBody("direct:start", "Hello World");
        mock.assertIsSatisfied();
        assertEquals("closed", policy.getStateAsString());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // open on the first failure, and only count failures in the last 100 millis, so the circuit closes on
                // the first success after it has been half opened. Half open after 3 seconds, so the timer does not
                // fire before the test has checked that the circuit is open, also on a slow machine
                policy = new ThrottlingExceptionRoutePolicy(1, 100, 3000, null);

                from("direct:start?block=false").routeId("foo").routePolicy(policy)
                        .filter(body().isEqualTo("Kaboom"))
                            .throwException(new IllegalArgumentException("Forced"))
                        .end()
                        .to("mock:result");
            }
        };
    }
}
