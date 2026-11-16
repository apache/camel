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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.micrometer.MicrometerConstants;
import org.apache.camel.component.micrometer.routepolicy.MicrometerRoutePolicyFactory;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.opentelemetry.metrics.AbstractOpenTelemetryTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTE_POLICY_METER_NAME;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies that the OpenTelemetry and the Micrometer route policies both measure a route they share.
 */
public class OpenTelemetryAndMicrometerRoutePolicyTest extends AbstractOpenTelemetryTestSupport {

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        MicrometerRoutePolicyFactory micrometerFactory = new MicrometerRoutePolicyFactory();
        micrometerFactory.setMeterRegistry(meterRegistry);
        context.addRoutePolicyFactory(micrometerFactory);
        OpenTelemetryRoutePolicyFactory openTelemetryFactory = new OpenTelemetryRoutePolicyFactory();
        openTelemetryFactory.setMeter(otelExtension.getOpenTelemetry().getMeter("meterTest"));
        context.addRoutePolicyFactory(openTelemetryFactory);
        return context;
    }

    @Test
    public void testBothRoutePoliciesMeasureTheRoute() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(1);
        template.sendBody("direct:foo", "Hello");
        MockEndpoint.assertIsSatisfied(context);

        // each policy measures the route, and the CamelContext, whose metrics have the route id ""
        assertAll(
                () -> assertEquals(1, micrometerTimer("foo").count(), "Micrometer route timer count"),
                () -> assertEquals(1, micrometerTimer("").count(), "Micrometer context timer count"),
                () -> assertEquals(1, openTelemetryHistogram("foo").getCount(), "OpenTelemetry route histogram count"),
                () -> assertEquals(1, openTelemetryHistogram("").getCount(), "OpenTelemetry context histogram count"));
    }

    private Timer micrometerTimer(String routeId) {
        Timer timer = meterRegistry.find(MicrometerConstants.DEFAULT_CAMEL_ROUTE_POLICY_METER_NAME)
                .tag(MicrometerConstants.ROUTE_ID_TAG, routeId).timer();
        assertNotNull(timer, "Micrometer timer for route id '" + routeId + "'");
        return timer;
    }

    private HistogramPointData openTelemetryHistogram(String routeId) {
        return assertInstanceOf(HistogramPointData.class,
                getPointDataForRouteId(DEFAULT_CAMEL_ROUTE_POLICY_METER_NAME, routeId));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:foo").routeId("foo").to("mock:result");
            }
        };
    }
}
