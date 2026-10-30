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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Ordered;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.throttling.ThrottlingInflightRoutePolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With the context scope, the {@link ThrottlingInflightRoutePolicy} must also check the inflight exchanges when an
 * exchange fails, like the route scope does, otherwise a consumer it has suspended is never resumed when the exchanges
 * that are still inflight all fail.
 */
class ThrottlingInflightRoutePolicyContextScopeFailedExchangesTest extends ContextTestSupport {

    private final ThrottlingInflightRoutePolicy policy = new ThrottlingInflightRoutePolicy();
    private final CountDownLatch entered = new CountDownLatch(3);
    private final CountDownLatch okGate = new CountDownLatch(1);
    private final CountDownLatch failGate = new CountDownLatch(1);
    private final CountDownLatch completed = new CountDownLatch(1);
    private final CountDownLatch failed = new CountDownLatch(2);

    @Test
    void testResumeWhenTheInflightExchangesFail() throws Exception {
        // called after the event notifier of the policy, for the same event, for the exchanges of the route
        context.getManagementStrategy().addEventNotifier(new LastEventNotifier());

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("ok", "after");

        template.sendBody("seda:foo", "ok");
        template.sendBody("seda:foo", "fail");
        template.sendBody("seda:foo", "fail");
        assertTrue(entered.await(10, TimeUnit.SECONDS));

        // one exchange completes while three are inflight, so the policy suspends the consumer
        okGate.countDown();
        assertTrue(completed.await(10, TimeUnit.SECONDS));
        ServiceSupport consumer = (ServiceSupport) context.getRoute("foo").getConsumer();
        assertTrue(consumer.isSuspended(), "The policy should suspend the consumer");

        // then the two other exchanges fail, and nothing is inflight any more
        failGate.countDown();
        assertTrue(failed.await(10, TimeUnit.SECONDS));
        assertFalse(consumer.isSuspended(), "The policy should resume the consumer when the inflight exchanges fail");

        template.sendBody("seda:foo", "after");
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                policy.setScope(ThrottlingInflightRoutePolicy.ThrottlingScope.Context);
                policy.setMaxInflightExchanges(1);

                from("seda:foo?concurrentConsumers=3").routeId("foo").routePolicy(policy)
                        .process(exchange -> {
                            String body = exchange.getMessage().getBody(String.class);
                            if ("after".equals(body)) {
                                return;
                            }
                            entered.countDown();
                            if ("ok".equals(body)) {
                                assertTrue(okGate.await(10, TimeUnit.SECONDS));
                            } else {
                                assertTrue(failGate.await(10, TimeUnit.SECONDS));
                                throw new IllegalArgumentException("Forced");
                            }
                        })
                        .to("mock:result");
            }
        };
    }

    private final class LastEventNotifier extends EventNotifierSupport implements Ordered {

        @Override
        public void notify(CamelEvent event) {
            // only the exchanges of the route (the exchanges of the producer template complete as well)
            if (!"foo".equals(((CamelEvent.ExchangeEvent) event).getExchange().getFromRouteId())) {
                return;
            }
            if (event instanceof CamelEvent.ExchangeFailedEvent) {
                failed.countDown();
            } else if (event instanceof CamelEvent.ExchangeCompletedEvent) {
                completed.countDown();
            }
        }

        @Override
        public boolean isEnabled(CamelEvent event) {
            return event instanceof CamelEvent.ExchangeCompletedEvent || event instanceof CamelEvent.ExchangeFailedEvent;
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST;
        }
    }
}
