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
package org.apache.camel.opentelemetry.metrics;

import java.util.Collection;
import java.util.Map;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import io.opentelemetry.sdk.metrics.data.PointData;
import org.apache.camel.BindToRegistry;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.HEADER_METRIC_NAME;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.HEADER_TIMER_ACTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TimerRouteTest extends CamelTestSupport {

    private static final long DELAY = 20L;

    @RegisterExtension
    public final CamelOpenTelemetryExtension otelExtension = CamelOpenTelemetryExtension.create();

    @BindToRegistry("metrics")
    private Meter meter = otelExtension.getOpenTelemetry().getMeter("meterTest");

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:in1")
                        .setHeader(HEADER_METRIC_NAME, constant("B"))
                        .to("opentelemetry-metrics:timer:A?action=start")
                        .delay(DELAY)
                        .setHeader(HEADER_METRIC_NAME, constant("B"))
                        .to("opentelemetry-metrics:timer:A?action=stop")
                        .to("mock:out");

                from("direct:in2")
                        .setHeader(HEADER_TIMER_ACTION, constant(OpenTelemetryTimerAction.START))
                        .to("opentelemetry-metrics:timer:A")
                        .delay(DELAY)
                        .setHeader(HEADER_TIMER_ACTION, constant(OpenTelemetryTimerAction.STOP))
                        .to("opentelemetry-metrics:timer:A")
                        .to("mock:out");

                from("direct:in3")
                        .to("opentelemetry-metrics:timer:${body}?action=start")
                        .delay(DELAY)
                        .to("opentelemetry-metrics:timer:${body}?action=stop")
                        .to("mock:out");

                from("direct:in4")
                        .to("opentelemetry-metrics:timer:D?action=start")
                        .delay(DELAY)
                        .to("opentelemetry-metrics:timer:D?action=stop&attributes.a=${body}")
                        .to("mock:out");

                from("direct:in5")
                        .to("opentelemetry-metrics:timer:E?action=start")
                        .delay(2000L)
                        .to("opentelemetry-metrics:timer:E?action=stop&unit=SECONDS")
                        .to("mock:out");
            }
        };
    }

    @Test
    public void testOverrideMetricsName() throws Exception {
        Object body = new Object();
        MockEndpoint mockEndpoint = getMockEndpoint("mock:out");
        mockEndpoint.expectedBodiesReceived(body);
        template.sendBody("direct:in1", body);

        HistogramPointData hpd = getMetric("B");
        assertEquals(1L, hpd.getCount());
        assertTrue(hpd.getMin() >= DELAY);
        MockEndpoint.assertIsSatisfied(context);
    }

    // verify multiple timers on the same producer
    @Test
    public void testOverrideMetricsMultipleName() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:out");
        mockEndpoint.expectedMessageCount(3);

        template.sendBody("direct:in3", "B1");

        template.sendBody("direct:in3", "B2");
        template.sendBody("direct:in3", "B2");

        HistogramPointData hpd = getMetric("B1");
        assertEquals(1L, hpd.getCount());
        assertTrue(hpd.getMin() >= DELAY);

        hpd = getMetric("B2");
        assertEquals(2L, hpd.getCount());
        assertTrue(hpd.getMin() >= DELAY);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testOverrideNoAction() throws Exception {
        Object body = new Object();
        MockEndpoint mockEndpoint = getMockEndpoint("mock:out");
        mockEndpoint.expectedBodiesReceived(body);
        template.sendBody("direct:in2", body);

        HistogramPointData hpd = getMetric("A");
        assertEquals(1L, hpd.getCount());
        assertTrue(hpd.getMin() >= DELAY);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testNormal() throws Exception {
        int count = 10;
        String body = "Hello";
        MockEndpoint mockEndpoint = getMockEndpoint("mock:out");
        mockEndpoint.expectedMessageCount(count);
        for (int i = 0; i < count; i++) {
            template.sendBody("direct:in4", body);
        }

        HistogramPointData hpd = getMetric("D");
        assertEquals(10L, hpd.getCount());
        assertTrue(hpd.getMin() >= DELAY);
        assertTrue(hpd.getSum() >= DELAY * count);

        Map<AttributeKey<?>, Object> attributes = hpd.getAttributes().asMap();
        assertEquals(body, attributes.get(AttributeKey.stringKey("a")));
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testOverrideTimeUnit() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:out");
        template.sendBody("direct:in5", new Object());
        mockEndpoint.expectedMessageCount(1);

        HistogramPointData hpd = getMetric("E");
        assertEquals(1L, hpd.getCount());
        // since we override unit to use seconds and delay is 2000ms
        assertEquals(2L, hpd.getMin());
        assertEquals(2L, hpd.getMax());
        MockEndpoint.assertIsSatisfied(context);
    }

    protected HistogramPointData getMetric(String metricName) {
        PointData pd = otelExtension.getMetrics().stream()
                .filter(d -> d.getName().equals(metricName))
                .map(metricData -> metricData.getData().getPoints())
                .flatMap(Collection::stream)
                .findFirst().orElse(null);

        assertInstanceOf(HistogramPointData.class, pd, "Expected HistogramPointData");
        return (HistogramPointData) pd;
    }
}
