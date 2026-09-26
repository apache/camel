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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.throttling.ThrottlingInflightRoutePolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@link ThrottlingInflightRoutePolicy} must only resume a consumer that it suspended itself. A consumer that was
 * already stopped (such as by the shutdown strategy while it waits for the inflight exchanges of the route) must not be
 * claimed by the policy when too many exchanges are inflight, and then be resumed when they complete.
 */
class ThrottlingInflightRoutePolicyStoppedConsumerTest extends ContextTestSupport {

    private final ThrottlingInflightRoutePolicy policy = new ThrottlingInflightRoutePolicy();

    @Test
    void testDoesNotResumeConsumerStoppedByOthers() throws Exception {
        Route route = context.getRoute("foo");
        ServiceSupport consumer = (ServiceSupport) route.getConsumer();

        // the consumer is stopped by someone else, while the route is still started
        ServiceHelper.stopService(consumer);
        assertTrue(consumer.isStopped());
        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo"));

        // an exchange completes while more exchanges than the maximum are inflight
        InflightRepository inflight = context.getInflightRepository();
        Exchange first = new DefaultExchange(context);
        Exchange second = new DefaultExchange(context);
        inflight.add(first, "foo");
        inflight.add(second, "foo");
        policy.onExchangeDone(route, first);

        // and then the inflight exchanges complete
        inflight.remove(first, "foo");
        inflight.remove(second, "foo");
        policy.onExchangeDone(route, second);

        assertTrue(consumer.isStopped(), "The policy should not resume a consumer that it did not suspend");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                policy.setMaxInflightExchanges(1);

                from("seda:foo").routeId("foo").routePolicy(policy)
                        .to("mock:result");
            }
        };
    }
}
