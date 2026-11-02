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

import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.search.RequiredSearch;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.micrometer.MicrometerConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that the current implementation does not perform a MeterRegistry.find() lookup on every exchange completion
 * via setLastTimeExchange(). The test wraps the registry in a counting spy and asserts that after N completed exchanges
 * the find() call count is 0 — i.e. the gauge is registered once at startup and never looked up again on the hot path.
 */
public class MicrometerExchangeEventNotifierLastTimestampTest extends AbstractMicrometerEventNotifierTest {

    private static final String DIRECT_IN = "direct://in";
    private static final String MOCK_OUT = "mock://out";
    private static final String ROUTE_ID = "test";

    /** Counts calls to find(CAMEL_EXCHANGE_LAST_TIME_METER_NAME) made after context startup. */
    private final AtomicInteger findCallsAfterStart = new AtomicInteger(0);

    @Override
    public void addRegistry() {
        meterRegistry = new CountingCompositeMeterRegistry(findCallsAfterStart);
    }

    @Override
    protected AbstractMicrometerEventNotifier<?> getEventNotifier() {
        return new MicrometerExchangeEventNotifier();
    }

    @Test
    public void testLastTimestampGaugeIsNotLookedUpOnEveryExchange() throws Exception {
        int count = 5;
        MockEndpoint mock = getMockEndpoint(MOCK_OUT);
        mock.expectedMessageCount(count);

        // Reset counter after startup so we only measure hot-path lookups
        findCallsAfterStart.set(0);

        for (int i = 0; i < count; i++) {
            template.sendBody(DIRECT_IN, "msg-" + i);
        }
        mock.assertIsSatisfied();

        // The gauge should have been registered once at doStart() — never queried again per exchange.
        // With the current implementation this assertion FAILS because find() is called `count` times.
        assertThat(findCallsAfterStart.get())
                .as("MeterRegistry.find() for the last-timestamp gauge must not be called on every exchange")
                .isEqualTo(0);

        // Sanity: the gauge itself must exist and hold a value > 0
        Gauge gauge = meterRegistry.find(MicrometerConstants.CAMEL_EXCHANGE_LAST_TIME_METER_NAME).gauge();
        assertThat(gauge).as("last-timestamp gauge must be registered").isNotNull();
        assertThat(gauge.value()).as("last-timestamp gauge must hold a non-zero timestamp").isGreaterThan(0);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(DIRECT_IN).routeId(ROUTE_ID).to(MOCK_OUT);
            }
        };
    }

    // ---------------------------------------------------------------------------
    // Spy registry that intercepts find() calls for the last-timestamp meter name
    // ---------------------------------------------------------------------------

    private static class CountingCompositeMeterRegistry extends io.micrometer.core.instrument.composite.CompositeMeterRegistry {

        private final AtomicInteger counter;

        CountingCompositeMeterRegistry(AtomicInteger counter) {
            super();
            add(new SimpleMeterRegistry());
            this.counter = counter;
        }

        @Override
        public RequiredSearch get(String name) {
            if (MicrometerConstants.CAMEL_EXCHANGE_LAST_TIME_METER_NAME.equals(name)) {
                counter.incrementAndGet();
            }
            return super.get(name);
        }

        @Override
        public io.micrometer.core.instrument.search.Search find(String name) {
            if (MicrometerConstants.CAMEL_EXCHANGE_LAST_TIME_METER_NAME.equals(name)) {
                counter.incrementAndGet();
            }
            return super.find(name);
        }
    }
}
