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
package org.apache.camel.opentelemetry2;

import java.util.List;
import java.util.Map;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.opentelemetry2.CamelOpenTelemetryExtension.OtelTrace;
import org.apache.camel.opentelemetry2.mock.MockHttpComponent;
import org.apache.camel.opentelemetry2.mock.MockKafkaComponent;
import org.apache.camel.telemetry.Op;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test that verifies SpanKind is set correctly for different component types.
 */
public class SpanKindTest extends OpenTelemetryTracerTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        OpenTelemetryTracer tst = new OpenTelemetryTracer();
        tst.setTracer(otelExtension.getOpenTelemetry().getTracer("spanKindTest"));
        tst.setContextPropagators(otelExtension.getOpenTelemetry().getPropagators());
        CamelContext context = super.createCamelContext();

        // Register mock HTTP component for testing
        context.addComponent("mock-http", new MockHttpComponent());
        // Register mock Kafka component for testing
        context.addComponent("mock-kafka", new MockKafkaComponent());

        CamelContextAware.trySetCamelContext(tst, context);
        tst.init(context);
        return context;
    }

    @Test
    void testDirectComponentHasInternalSpanKind() {
        template.sendBody("direct:start", "test");

        List<OtelTrace> traces = List.copyOf(otelExtension.getTraces().values());
        assertEquals(1, traces.size());

        List<SpanData> spans = traces.get(0).getSpans();

        // Find the direct:start EVENT_SENT span
        SpanData directSentSpan = getSpan(spans, "direct://start", Op.EVENT_SENT);
        assertEquals(SpanKind.INTERNAL, directSentSpan.getKind(),
                "direct:start EVENT_SENT should have INTERNAL SpanKind");

        // Find the direct:start EVENT_RECEIVED span
        SpanData directReceivedSpan = getSpan(spans, "direct://start", Op.EVENT_RECEIVED);
        assertEquals(SpanKind.INTERNAL, directReceivedSpan.getKind(),
                "direct:start EVENT_RECEIVED should have INTERNAL SpanKind");
    }

    @Test
    void testHttpComponentHasClientServerSpanKind() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(1);

        template.sendBody("direct:httpClient", "test message");

        mockEndpoint.assertIsSatisfied();

        List<OtelTrace> traces = List.copyOf(otelExtension.getTraces().values());
        assertEquals(1, traces.size());

        List<SpanData> spans = traces.get(0).getSpans();

        // Find the mock-http EVENT_SENT span (client side)
        SpanData httpClientSpan = getSpan(spans, "mock-http://testEndpoint", Op.EVENT_SENT);
        assertEquals(SpanKind.CLIENT, httpClientSpan.getKind(),
                "HTTP EVENT_SENT should have CLIENT SpanKind");
    }

    @Test
    void testKafkaComponentHasProducerSpanKindAndInheritedProperties() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(1);

        // Send with Kafka headers that would normally be set before/during sending
        template.sendBodyAndHeaders("direct:kafkaProducer", "test message",
                Map.of("CamelKafkaKey", "test-key",
                        "CamelKafkaPartition", 0,
                        "CamelKafkaOffset", "12345"));

        mockEndpoint.assertIsSatisfied();

        List<OtelTrace> traces = List.copyOf(otelExtension.getTraces().values());
        assertEquals(1, traces.size());

        List<SpanData> spans = traces.get(0).getSpans();

        // Find the mock-kafka EVENT_SENT span
        SpanData kafkaSpan = getSpan(spans, "mock-kafka://testTopic", Op.EVENT_SENT);

        // Verify OpenTelemetry-specific SpanKind
        assertEquals(SpanKind.PRODUCER, kafkaSpan.getKind(),
                "Kafka EVENT_SENT should have PRODUCER SpanKind");

        // Verify inherited properties from camel-telemetry KafkaSpanDecorator
        assertEquals("0", kafkaSpan.getAttributes().get(AttributeKey.stringKey("kafka.partition")),
                "Should have kafka.partition tag from inherited decorator");
        assertEquals("12345", kafkaSpan.getAttributes().get(AttributeKey.stringKey("kafka.offset")),
                "Should have kafka.offset tag from inherited decorator");
        assertEquals("test-key", kafkaSpan.getAttributes().get(AttributeKey.stringKey("kafka.key")),
                "Should have kafka.key tag from inherited decorator");
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .log("Processing message");

                // Mock HTTP client route - tests CLIENT SpanKind
                from("direct:httpClient")
                        .to("mock-http://testEndpoint")
                        .to("mock:result");

                // Mock Kafka producer route - tests PRODUCER SpanKind and inherited properties
                from("direct:kafkaProducer")
                        .to("mock-kafka://testTopic")
                        .to("mock:result");
            }
        };
    }

}
