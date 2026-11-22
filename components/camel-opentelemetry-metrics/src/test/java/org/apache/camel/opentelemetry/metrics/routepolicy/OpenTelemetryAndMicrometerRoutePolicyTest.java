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
package org.apache.camel.opentelemetry.metrics.routepolicy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import org.apache.camel.CamelContext;
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.micrometer.routepolicy.MicrometerRoutePolicyFactory;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.RoutePolicyFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that a route with both the Micrometer and the OpenTelemetry route policy fails to start, and that the policy
 * that comes first on the route is the one that refuses it.
 */
public class OpenTelemetryAndMicrometerRoutePolicyTest {

    @Test
    public void testRouteFailsToStartWithMicrometerFirst() throws Exception {
        String message = "Route foo has both MicrometerRoutePolicy and OpenTelemetryRoutePolicy."
                         + " Use only one of camel-micrometer or camel-opentelemetry-metrics.";
        assertRouteFailsToStart(micrometerFactory(), openTelemetryFactory(), message);
    }

    @Test
    public void testRouteFailsToStartWithOpenTelemetryFirst() throws Exception {
        String message = "Route foo has both OpenTelemetryRoutePolicy and MicrometerRoutePolicy."
                         + " Use only one of camel-opentelemetry-metrics or camel-micrometer.";
        assertRouteFailsToStart(openTelemetryFactory(), micrometerFactory(), message);
    }

    private static RoutePolicyFactory micrometerFactory() {
        MicrometerRoutePolicyFactory factory = new MicrometerRoutePolicyFactory();
        factory.setMeterRegistry(new SimpleMeterRegistry());
        return factory;
    }

    private static RoutePolicyFactory openTelemetryFactory() {
        OpenTelemetryRoutePolicyFactory factory = new OpenTelemetryRoutePolicyFactory();
        factory.setMeter(OpenTelemetry.noop().getMeter("test"));
        return factory;
    }

    private static void assertRouteFailsToStart(RoutePolicyFactory first, RoutePolicyFactory second, String message)
            throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.addRoutePolicyFactory(first);
            context.addRoutePolicyFactory(second);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:foo").routeId("foo").to("mock:result");
                }
            });

            FailedToCreateRouteException e = assertThrows(FailedToCreateRouteException.class, context::start);
            IllegalStateException cause = assertInstanceOf(IllegalStateException.class, e.getCause());
            assertEquals(message, cause.getMessage());
        }
    }
}
