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
package org.apache.camel.opentelemetry;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.tracing.SpanDecorator;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CamelOpenTelemetryTestSupport extends CamelTestSupport {
    static final AttributeKey<String> CAMEL_URI_KEY = AttributeKey.stringKey("camel-uri");
    static final AttributeKey<String> COMPONENT_KEY = AttributeKey.stringKey("component");
    static final AttributeKey<String> PRE_KEY = AttributeKey.stringKey("pre");
    static final AttributeKey<String> POST_KEY = AttributeKey.stringKey("post");
    static final AttributeKey<String> MESSAGE_KEY = AttributeKey.stringKey("message");

    private static final Logger LOG = LoggerFactory.getLogger(CamelOpenTelemetryTestSupport.class);

    private InMemorySpanExporter inMemorySpanExporter = InMemorySpanExporter.create();
    private SpanTestData[] expected;
    private Tracer tracer;
    private OpenTelemetryTracer ottracer;
    private SdkTracerProvider tracerFactory;

    CamelOpenTelemetryTestSupport(SpanTestData[] expected) {
        this.expected = expected;
    }

    @AfterEach
    void noLeakingContext() {
        Assertions.assertSame(Context.root(), Context.current(), "There must be no leaking span after test");
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        ottracer = new OpenTelemetryTracer();

        tracerFactory = SdkTracerProvider.builder()
                .addSpanProcessor(new LoggingSpanProcessor())
                .addSpanProcessor(SimpleSpanProcessor.create(inMemorySpanExporter)).build();

        tracer = tracerFactory.get("tracerTest");
        ottracer.setTracer(tracer);
        ottracer.setExcludePatterns(getExcludePatterns());
        ottracer.addDecorator(new TestSEDASpanDecorator());
        ottracer.setTracingStrategy(getTracingStrategy().apply(ottracer));
        ottracer.init(context);
        return context;
    }

    protected String getExcludePatterns() {
        return null;
    }

    protected void verify() {
        verify(expected, false);
    }

    protected void verify(boolean async) {
        verify(expected, async);
    }

    protected List<SpanData> verify(SpanTestData[] expected, boolean async) {
        List<SpanData> spans = inMemorySpanExporter.getFinishedSpanItems();
        spans.forEach(mockSpan -> {
            LOG.info("Span: {}", mockSpan);
            LOG.info("Component: {}", mockSpan.getAttributes().get(COMPONENT_KEY));
            LOG.info("Tags: {}", mockSpan.getAttributes());
            LOG.info("Logs: ");

        });
        assertEquals(expected.length, spans.size(), "Incorrect number of spans");
        verifySameTrace();

        if (async) {
            final List<SpanData> unsortedSpans = spans;
            spans = Arrays.stream(expected)
                    .map(td -> findSpan(td, unsortedSpans)).distinct().toList();
            assertEquals(expected.length, spans.size(), "Incorrect number of spans after sorting");
        }

        for (int i = 0; i < expected.length; i++) {
            verifySpan(i, expected, spans);
        }

        return spans;
    }

    protected SpanData findSpan(SpanTestData testdata, List<SpanData> spans) {
        return spans.stream().filter(s -> {
            boolean matched = s.getName().equals(testdata.getOperation());
            if (s.getAttributes().get(CAMEL_URI_KEY) != null) {
                matched = matched && s.getAttributes().get(CAMEL_URI_KEY).equals(testdata.getUri());
            }
            matched = matched && s.getKind().equals(testdata.getKind());
            return matched;
        }).findFirst().orElse(null);
    }

    protected Tracer getTracer() {
        return tracer;
    }

    protected void verifyTraceSpanNumbers(int numOfTraces, int numSpansPerTrace) {
        Map<String, List<SpanData>> traces = new HashMap<>();
        Awaitility.await()
                .alias("inMemorySpanExporter.getFinishedSpanItems() should eventually contain all expected spans")
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(10, TimeUnit.MILLISECONDS)
                .pollDelay(0, TimeUnit.MILLISECONDS)
                .until(() -> inMemorySpanExporter.getFinishedSpanItems().size() >= (numOfTraces * numSpansPerTrace));

        List<SpanData> finishedSpans = inMemorySpanExporter.getFinishedSpanItems();
        // Sort spans into separate traces
        for (int i = 0; i < finishedSpans.size(); i++) {
            List<SpanData> spans = traces.computeIfAbsent(finishedSpans.get(i).getTraceId(), k -> new ArrayList<>());
            spans.add(finishedSpans.get(i));
        }

        LOG.info("Found traces: {}", traces);
        assertEquals(numOfTraces, traces.size());

        for (Map.Entry<String, List<SpanData>> spans : traces.entrySet()) {
            assertEquals(numSpansPerTrace, spans.getValue().size());
        }
    }

    protected void verifySpan(int index, SpanTestData[] testdata, List<SpanData> spans) {
        SpanData span = spans.get(index);
        SpanTestData td = testdata[index];

        String component = span.getAttributes().get(COMPONENT_KEY);
        assertNotNull(component);

        if (td.getUri() != null) {
            assertEquals(SpanDecorator.CAMEL_COMPONENT + URI.create(td.getUri()).getScheme(), component, td.getLabel());
        }

        if ("camel-seda".equals(component)) {
            assertNotNull(span.getAttributes().get(PRE_KEY));
            assertNotNull(span.getAttributes().get(POST_KEY));
        }

        assertEquals(td.getOperation(), span.getName(), td.getLabel());

        assertEquals(td.getKind(), span.getKind(), td.getLabel());

        if (!td.getLogMessages().isEmpty()) {
            assertEquals(td.getLogMessages().size(), span.getEvents().size(), td.getLabel());
            for (int i = 0; i < td.getLogMessages().size(); i++) {
                assertEquals(td.getLogMessages().get(i), span.getEvents().get(i).getAttributes().get(MESSAGE_KEY));
            }
        }

        if (td.getParentId() != -1) {
            assertEquals(spans.get(td.getParentId()).getSpanId(), span.getParentSpanId(), td.getLabel());
        }
        if (!td.getTags().isEmpty()) {
            for (Map.Entry<String, String> entry : td.getTags().entrySet()) {
                assertEquals(entry.getValue(), span.getAttributes().get(AttributeKey.stringKey(entry.getKey())));
            }
        }

    }

    protected void verifySameTrace() {
        assertEquals(1, inMemorySpanExporter.getFinishedSpanItems().stream().map(s -> s.getTraceId()).distinct().count());
    }

    protected Function<OpenTelemetryTracer, InterceptStrategy> getTracingStrategy() {
        return ottracer -> new NoopTracingStrategy();
    }

    private static class LoggingSpanProcessor implements SpanProcessor {
        private static final Logger LOG = LoggerFactory.getLogger(LoggingSpanProcessor.class);

        @Override
        public void onStart(Context context, ReadWriteSpan readWriteSpan) {
            LOG.debug("Span started: name - '{}', kind - '{}', id - '{}-{}", readWriteSpan.getName(), readWriteSpan.getKind(),
                    readWriteSpan.getSpanContext().getTraceId(), readWriteSpan.getSpanContext().getSpanId());
        }

        @Override
        public boolean isStartRequired() {
            return true;
        }

        @Override
        public void onEnd(ReadableSpan readableSpan) {
            LOG.debug("Span ended: name - '{}', kind - '{}', id - '{}-{}", readableSpan.getName(), readableSpan.getKind(),
                    readableSpan.getSpanContext().getTraceId(), readableSpan.getSpanContext().getSpanId());
        }

        @Override
        public boolean isEndRequired() {
            return true;
        }
    }
}
