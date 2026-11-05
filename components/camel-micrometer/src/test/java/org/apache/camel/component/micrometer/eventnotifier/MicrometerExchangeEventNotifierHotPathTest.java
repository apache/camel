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
package org.apache.camel.component.micrometer.eventnotifier;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.search.RequiredSearch;
import io.micrometer.core.instrument.search.Search;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.micrometer.MicrometerConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The exchange events must not scan the meter registry (find() copies and filters every registered meter), and routes
 * created by kamelets must stay excluded without looking the route up on every event, including routes added or removed
 * after startup.
 */
public class MicrometerExchangeEventNotifierHotPathTest extends AbstractMicrometerEventNotifierTest {

    private static final String SENT = "ExchangeSentEvent";

    private final AtomicInteger searches = new AtomicInteger();

    @Override
    public void addRegistry() {
        meterRegistry = new CompositeMeterRegistry() {
            @Override
            public Search find(String name) {
                searches.incrementAndGet();
                return super.find(name);
            }

            @Override
            public RequiredSearch get(String name) {
                searches.incrementAndGet();
                return super.get(name);
            }
        };
        meterRegistry.add(new SimpleMeterRegistry());
    }

    @Override
    protected AbstractMicrometerEventNotifier<?> getEventNotifier() {
        return new MicrometerExchangeEventNotifier();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("start")
                        .to("mock:a")
                        .to("direct:kamelet");

                from("direct:kamelet").routeId("kamelet").to("mock:insideKamelet").setKamelet(true);
            }
        };
    }

    @Test
    public void testNoRegistrySearchPerExchange() {
        int count = 50;
        searches.set(0);
        for (int i = 0; i < count; i++) {
            template.sendBody("direct:start", "msg-" + i);
        }
        assertEquals(0, searches.get(), "the meter registry must not be searched per exchange");

        // the timers are still found and updated through register()
        assertEquals(count, sentTimerCount("mock://a"));
    }

    @Test
    public void testKameletRoutesAreNotMetered() throws Exception {
        template.sendBody("direct:start", "msg");
        assertEquals(1, sentTimerCount("mock://a"));
        assertEquals(0, sentTimerCount("mock://insideKamelet"), "route created by a kamelet must not be metered");

        // a kamelet route added after startup is skipped as well
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:late").routeId("late").to("mock:insideLate").setKamelet(true);
            }
        });
        template.sendBody("direct:late", "msg");
        assertEquals(0, sentTimerCount("mock://insideLate"), "kamelet route added at runtime must not be metered");

        // once removed, the same route id may be reused by a route that is metered
        context.getRouteController().stopRoute("late");
        assertTrue(context.removeRoute("late"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:late").routeId("late").to("mock:insideLate");
            }
        });
        template.sendBody("direct:late", "msg");
        assertEquals(1, sentTimerCount("mock://insideLate"), "regular route reusing the id must be metered");
    }

    private long sentTimerCount(String endpoint) {
        List<Meter> meters = meterRegistry.getMeters();
        return meters.stream()
                .filter(m -> m instanceof Timer)
                .filter(m -> MicrometerConstants.DEFAULT_CAMEL_EXCHANGE_EVENT_METER_NAME.equals(m.getId().getName()))
                .filter(m -> SENT.equals(m.getId().getTag(MicrometerConstants.EVENT_TYPE_TAG)))
                .filter(m -> endpoint.equals(m.getId().getTag(MicrometerConstants.ENDPOINT_NAME)))
                .mapToLong(m -> ((Timer) m).count())
                .sum();
    }
}
