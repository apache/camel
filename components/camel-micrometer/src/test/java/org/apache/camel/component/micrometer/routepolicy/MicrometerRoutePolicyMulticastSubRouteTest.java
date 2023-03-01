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
package org.apache.camel.component.micrometer.routepolicy;

import java.util.List;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.micrometer.MicrometerConstants.DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_EXTERNAL_REDELIVERIES_METER_NAME;
import static org.apache.camel.component.micrometer.MicrometerConstants.DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_FAILED_METER_NAME;
import static org.apache.camel.component.micrometer.MicrometerConstants.DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_FAILURES_HANDLED_METER_NAME;
import static org.apache.camel.component.micrometer.MicrometerConstants.DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_SUCCEEDED_METER_NAME;
import static org.apache.camel.component.micrometer.MicrometerConstants.DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_TOTAL_METER_NAME;
import static org.apache.camel.component.micrometer.MicrometerConstants.DEFAULT_CAMEL_ROUTE_POLICY_METER_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * CAMEL-9226 - check metrics are counted correctly in multicast sub-routes
 */
public class MicrometerRoutePolicyMulticastSubRouteTest extends AbstractMicrometerRoutePolicyTest {

    @Test
    public void testMetricsRoutePolicy() throws Exception {

        int count = 10;
        getMockEndpoint("mock:foo").expectedMessageCount(count);
        getMockEndpoint("mock:bar1").expectedMessageCount(count);
        getMockEndpoint("mock:bar2").expectedMessageCount(count);

        for (int i = 0; i < count; i++) {
            template.sendBody("direct:multicast", "Hello World");
            template.send("direct:failure", e -> e.getMessage().setBody("Hello World"));
        }

        MockEndpoint.assertIsSatisfied(context);

        // there should be 6 metrics per route
        List<Meter> meters = meterRegistry.getMeters();
        assertEquals(6 * context.getRouteDefinitions().size(), meters.size());

        meters.forEach(meter -> {
            String meterName = meter.getId().getName();
            switch (meterName) {
                case DEFAULT_CAMEL_ROUTE_POLICY_METER_NAME:
                    Timer timer = (Timer) meter;
                    assertEquals(count, timer.count(), "Timer " + timer.getId() + " should have count of " + count);
                    break;
                case DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_SUCCEEDED_METER_NAME: {
                    Counter counter = (Counter) meter;
                    int expectedCount;
                    String routeId = counter.getId().getTag("routeId");
                    if (routeId.equals("failure")) {
                        expectedCount = 0;
                    } else {
                        expectedCount = count;
                    }
                    assertEquals(expectedCount, counter.count(), 0.01D,
                            "Counter " + counter.getId() + " should have count of " + count);
                    break;
                }
                case DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_FAILED_METER_NAME: {
                    Counter counter = (Counter) meter;
                    int expectedCount;
                    String routeId = counter.getId().getTag("routeId");
                    if (routeId.equals("failure")) {
                        expectedCount = count;
                    } else {
                        expectedCount = 0;
                    }
                    assertEquals(expectedCount, counter.count(), 0.01D,
                            "Counter " + counter.getId() + " should have count of " + 0);
                    break;
                }
                case DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_TOTAL_METER_NAME: {
                    Counter counter = (Counter) meter;
                    assertEquals(count, counter.count(), 0.01D,
                            "Counter " + counter.getId() + " should have count of " + count);
                    break;
                }
                case DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_EXTERNAL_REDELIVERIES_METER_NAME: {
                    Counter counter = (Counter) meter;
                    assertEquals(0, counter.count(), 0.01D,
                            "Counter " + counter.getId() + " should have count of " + count);
                    break;
                }
                case DEFAULT_CAMEL_ROUTE_POLICY_EXCHANGES_FAILURES_HANDLED_METER_NAME: {
                    Counter counter = (Counter) meter;
                    int expectedCount;
                    String routeId = counter.getId().getTag("routeId");
                    if (routeId.equals("failureHandled")) {
                        expectedCount = count;
                    } else {
                        expectedCount = 0;
                    }
                    assertEquals(expectedCount, counter.count(), 0.01D,
                            "Counter " + counter.getId() + " should have count of " + count);
                    break;
                }
                default: {
                    fail("Unexpected meter " + meterName);
                    break;
                }
            }
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(IllegalStateException.class)
                        .handled(true);

                from("direct:foo").routeId("foo").to("mock:foo");

                from("direct:bar").routeId("bar").multicast().to("mock:bar1", "mock:bar2");

                from("direct:multicast").routeId("multicast").multicast().to("direct:foo", "direct:bar",
                        "direct:failureHandled");

                from("direct:failure").routeId("failure").throwException(new Exception("forced"));

                from("direct:failureHandled").routeId("failureHandled").throwException(new IllegalStateException("forced"));
            }
        };
    }
}
