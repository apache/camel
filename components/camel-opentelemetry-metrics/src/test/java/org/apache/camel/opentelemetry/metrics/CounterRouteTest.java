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
import java.util.List;
import java.util.Map;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.PointData;
import org.apache.camel.CamelExecutionException;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    @Test
    public void testIncrementFromBody() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(1);
        template.sendBody("direct:in7", "42");

        assertEquals(42L, getMetric("G").getValue());
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testIncrementFromBodyNotNumber() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(0);

        assertIncrementFails(Map.of("count", 5));
        assertIncrementFails(List.of(new Object()));
        assertIncrementFails(new Object());
        assertIncrementFails(null);

        // the counter is not updated (the exchange fails with IllegalArgumentException)
        assertNull(findMetric("G"));
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testIncrementOrDecrementFromHeader() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(2);
        template.sendBodyAndHeader("direct:in8", null, "inc", 10L);
        template.sendBodyAndHeader("direct:in8", null, "dec", 3L);

        assertEquals(7L, getMetric("H").getValue());
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testIncrementOrDecrementFromHeaderMissing() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(0);

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:in8", "Hello"));
        assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertEquals("The increment (${header.inc}) and decrement (${header.dec}) of counter H did not evaluate to a number",
                e.getCause().getMessage());
        assertNull(findMetric("H"));
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testDecrementFromBodyNotNumber() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(0);

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:in9", Map.of("count", 5)));
        assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertEquals("The decrement (${body}) of counter I did not evaluate to a number",
                e.getCause().getMessage());
        assertNull(findMetric("I"));
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testHeaderAttributesAreUsedAsGiven() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(1);
        template.sendBodyAndHeaders("direct:in10", "Hello", Map.of(
                "k", "key",
                HEADER_METRIC_ATTRIBUTES, Attributes.of(
                        AttributeKey.stringKey("c"), "${body}",
                        AttributeKey.stringKey("${header.k}"), "d")));

        Attributes attributes = getMetric("J").getAttributes();
        // the attributes from the uri are resolved per exchange
        assertEquals("5", attributes.get(AttributeKey.stringKey("a")));
        assertEquals("uri", attributes.get(AttributeKey.stringKey("b")));
        // the attributes from the header are not
        assertEquals("${body}", attributes.get(AttributeKey.stringKey("c")));
        assertEquals("d", attributes.get(AttributeKey.stringKey("${header.k}")));
        assertNull(attributes.get(AttributeKey.stringKey("key")));
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testHeaderAttributeOverridesUriAttributeWithSameKey() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(1);
        template.sendBodyAndHeader("direct:in10", "Hello", HEADER_METRIC_ATTRIBUTES,
                Attributes.of(AttributeKey.stringKey("b"), "header"));

        Attributes attributes = getMetric("J").getAttributes();
        assertEquals("5", attributes.get(AttributeKey.stringKey("a")));
        assertEquals("header", attributes.get(AttributeKey.stringKey("b")));
        MockEndpoint.assertIsSatisfied(context);
    }

    private void assertIncrementFails(Object body) {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:in7", body));
        assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertEquals("The increment (${body}) of counter G did not evaluate to a number",
                e.getCause().getMessage());
    }

    protected LongPointData findMetric(String metricName) {
        return (LongPointData) otelExtension.getMetrics().stream()
                .filter(d -> d.getName().equals(metricName))
                .map(metricData -> metricData.getData().getPoints())
                .flatMap(Collection::stream)
                .findFirst().orElse(null);
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
