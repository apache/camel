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

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.PointData;
import org.apache.camel.BindToRegistry;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.HEADER_COUNTER_DECREMENT;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.HEADER_COUNTER_INCREMENT;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.HEADER_METRIC_ATTRIBUTES;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.HEADER_METRIC_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class CounterRouteTest extends CamelTestSupport {

    public final CamelOpenTelemetryExtension otelExtension = CamelOpenTelemetryExtension.create();

    @BindToRegistry("metrics")
    private Meter meter = otelExtension.getOpenTelemetry().getMeter("meterTest");

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:in1")
                        .to("opentelemetry-metrics:counter:A?increment=5")
                        .to("mock:result");

                from("direct:in2")
                        .to("opentelemetry-metrics:counter:B?decrement=9")
                        .to("mock:result");

                from("direct:in3")
                        .setHeader(HEADER_COUNTER_INCREMENT, constant(417L))
                        .to("opentelemetry-metrics:counter:C")
                        .to("mock:result");

                from("direct:in4")
                        .to("opentelemetry-metrics:counter:D?increment=${header.inc}&attributes.a=${body.length}&attributes.b=2")
                        .to("mock:result");

                from("direct:in5")
                        .setHeader(HEADER_METRIC_ATTRIBUTES,
                                constant(Attributes.of(AttributeKey.stringKey("dynamic-key"), "dynamic-value")))
                        .to("opentelemetry-metrics:counter:E?increment=5")
                        .to("mock:result");

                from("direct:in6")
                        .setHeader(OpenTelemetryConstants.HEADER_COUNTER_INCREMENT, simple("${body.length}"))
                        .to("opentelemetry-metrics:counter:F")
                        .to("mock:result");
            }
        };
    }

    @Test
    public void testOverrideMetricsName() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(1);
        template.sendBodyAndHeader("direct:in1", new Object(), HEADER_METRIC_NAME, "A1");

        assertEquals(5L, getMetric("A1").getValue());
        MockEndpoint.assertIsSatisfied(context);
    }

    // verify multiple counters on the same producer
    @Test
    public void testOverrideMetricsMultipleName() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(3);
        template.sendBodyAndHeader("direct:in1", new Object(), HEADER_METRIC_NAME, "A1");

        template.sendBodyAndHeader("direct:in1", new Object(), HEADER_METRIC_NAME, "A2");
        template.sendBodyAndHeader("direct:in1", new Object(), HEADER_METRIC_NAME, "A2");

        assertEquals(5L, getMetric("A1").getValue());
        assertEquals(10L, getMetric("A2").getValue());
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testIterativeIncrement() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(3);
        for (int i = 0; i < 3; i++) {
            template.sendBody("direct:in1", new Object());
        }

        assertEquals(15L, getMetric("A").getValue());
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testIterativeOverrideMetricsName() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(3);
        for (int i = 0; i < 3; i++) {
            template.sendBodyAndHeader("direct:in1", new Object(), HEADER_METRIC_NAME, "A1");
        }

        assertEquals(15L, getMetric("A1").getValue());
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testOverrideIncrement() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(1);
        template.sendBodyAndHeader("direct:in1", new Object(), HEADER_COUNTER_INCREMENT, 14L);

        assertEquals(14L, getMetric("A").getValue());
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testOverrideDecrement() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(1);
        template.sendBodyAndHeader("direct:in2", new Object(), HEADER_COUNTER_DECREMENT, 7L);

        assertEquals(-7L, getMetric("B").getValue());
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testIterativeOverrideDecrement() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(3);
        for (int i = 0; i < 3; i++) {
            template.sendBodyAndHeader("direct:in2", new Object(), HEADER_COUNTER_DECREMENT, 7L);
        }

        assertEquals(-21L, getMetric("B").getValue());
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testOverrideDecrementMultipleRoute() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(10);
        for (int i = 0; i < 10; i++) {
            if (i % 2 == 0) {
                template.sendBodyAndHeader("direct:in1", new Object(), HEADER_COUNTER_INCREMENT, 14L);
            } else {
                template.sendBodyAndHeader("direct:in2", new Object(), HEADER_COUNTER_DECREMENT, 7L);
            }
        }

        assertEquals(70L, getMetric("A").getValue());
        assertEquals(-35L, getMetric("B").getValue());
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testOverrideUsingConstantValue() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(1);
        template.sendBody("direct:in3", new Object());

        assertEquals(417L, getMetric("C").getValue());
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testUsingScriptEvaluation() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(1);
        String message = "Hello from Camel Metrics!";
        template.sendBodyAndHeader("direct:in4", message, "inc", "5");

        LongPointData pd = getMetric("D");
        assertEquals(5, pd.getValue());
        assertEquals(Integer.toString(message.length()), pd.getAttributes().get(AttributeKey.stringKey("a")));
        assertEquals("2", pd.getAttributes().get(AttributeKey.stringKey("b")));
        MockEndpoint.assertIsSatisfied(context);
    }

    // verify addition of attributes via header
    @Test
    public void testDynamicAttribute() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(1);
        template.sendBody("direct:in5", new Object());

        LongPointData lpd = getMetric("E");
        assertEquals(5L, lpd.getValue());
        assertEquals("dynamic-value", lpd.getAttributes().get(AttributeKey.stringKey("dynamic-key")));
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testSimpleLanguageIncrement() throws Exception {
        String message = "Hello from Camel Metrics!";
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(1);
        template.sendBody("direct:in6", message);

        assertEquals(message.length(), getMetric("F").getValue());
        MockEndpoint.assertIsSatisfied(context);
    }

    protected LongPointData getMetric(String metricName) {
        PointData pd = otelExtension.getMetrics().stream()
                .filter(d -> d.getName().equals(metricName))
                .map(metricData -> metricData.getData().getPoints())
                .flatMap(Collection::stream)
                .findFirst().orElse(null);

        assertInstanceOf(LongPointData.class, pd, "Expected LongPointData");
        return (LongPointData) pd;
    }
}
