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
                Map.of("kafka.KEY", "test-key",
                        "kafka.PARTITION", 0,
                        "kafka.OFFSET", "12345"));

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

    // ========== Mock HTTP Component Classes ==========

    /**
     * Mock HTTP component for testing SpanKind. This component is recognized by HttpSpanDecorator based on its class
     * name.
     */
    static class MockHttpComponent extends org.apache.camel.support.DefaultComponent {

        @Override
        protected org.apache.camel.Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters)
                throws Exception {
            MockHttpEndpoint endpoint = new MockHttpEndpoint(uri, this);
            setProperties(endpoint, parameters);
            return endpoint;
        }
    }

    /**
     * Mock HTTP endpoint for testing SpanKind.
     */
    static class MockHttpEndpoint extends org.apache.camel.support.DefaultEndpoint {

        public MockHttpEndpoint(String endpointUri, org.apache.camel.Component component) {
            super(endpointUri, component);
        }

        @Override
        public org.apache.camel.Producer createProducer() throws Exception {
            return new MockHttpProducer(this);
        }

        @Override
        public org.apache.camel.Consumer createConsumer(org.apache.camel.Processor processor) throws Exception {
            throw new IllegalArgumentException("Not used in MockHttpEndpoint");
        }
    }

    /**
     * Mock HTTP producer that just echoes the input.
     */
    static class MockHttpProducer extends org.apache.camel.support.DefaultProducer {

        public MockHttpProducer(org.apache.camel.Endpoint endpoint) {
            super(endpoint);
        }

        @Override
        public void process(org.apache.camel.Exchange exchange) throws Exception {
            // Simple echo - set response body to request body
            exchange.getMessage().setBody("HTTP Response: " + exchange.getIn().getBody());
        }
    }

    /**
     * Span decorator for mock HTTP component used in tests.
     */
    public static class MockHttpSpanDecorator extends org.apache.camel.telemetry.decorators.AbstractHttpSpanDecorator {

        @Override
        public String getComponent() {
            return "mock-http";
        }

        @Override
        public String getComponentClassName() {
            return MockHttpComponent.class.getName();
        }
    }

    // ========== Mock Kafka Component Classes ==========

    /**
     * Mock Kafka component for testing SpanKind and inherited properties.
     */
    static class MockKafkaComponent extends org.apache.camel.support.DefaultComponent {

        @Override
        protected org.apache.camel.Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters)
                throws Exception {
            MockKafkaEndpoint endpoint = new MockKafkaEndpoint(uri, this);
            setProperties(endpoint, parameters);
            return endpoint;
        }
    }

    /**
     * Mock Kafka endpoint for testing SpanKind and inherited properties.
     */
    static class MockKafkaEndpoint extends org.apache.camel.support.DefaultEndpoint {

        public MockKafkaEndpoint(String endpointUri, org.apache.camel.Component component) {
            super(endpointUri, component);
        }

        @Override
        public org.apache.camel.Producer createProducer() throws Exception {
            return new MockKafkaProducer(this);
        }

        @Override
        public org.apache.camel.Consumer createConsumer(org.apache.camel.Processor processor) throws Exception {
            throw new UnsupportedOperationException("Consumer not implemented for mock Kafka");
        }
    }

    /**
     * Mock Kafka producer that simulates Kafka headers.
     */
    static class MockKafkaProducer extends org.apache.camel.support.DefaultProducer {

        public MockKafkaProducer(org.apache.camel.Endpoint endpoint) {
            super(endpoint);
        }

        @Override
        public void process(org.apache.camel.Exchange exchange) throws Exception {
            // Simulate Kafka response with partition, offset, and key
            // These headers would normally be set by the real Kafka producer
            exchange.getMessage().setHeader("kafka.PARTITION", 0);
            exchange.getMessage().setHeader("kafka.OFFSET", "12345");
            exchange.getMessage().setHeader("kafka.KEY", "test-key");
            exchange.getMessage().setBody("Kafka Response: " + exchange.getIn().getBody());
        }
    }

    /**
     * Span decorator for mock Kafka component used in tests. Extends the real KafkaSpanDecorator to inherit all
     * Kafka-specific behavior (partition, offset, key tags) and adds SpanKind.
     */
    public static class MockKafkaSpanDecorator extends org.apache.camel.telemetry.decorators.KafkaSpanDecorator {

        @Override
        public String getComponent() {
            return "mock-kafka";
        }

        @Override
        public String getComponentClassName() {
            return MockKafkaComponent.class.getName();
        }
    }

}
