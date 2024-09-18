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
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.ThreadPoolFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junitpioneer.jupiter.SetSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tags({ @Tag("not-parallel") })
@SetSystemProperty(key = "io.opentelemetry.context.enableStrictContext", value = "true")
class CamelOpenTelemetryTestSupport extends CamelTestSupport {
    static final AttributeKey<String> CAMEL_URI_KEY = AttributeKey.stringKey("camel-uri");
    static final AttributeKey<String> COMPONENT_KEY = AttributeKey.stringKey("component");
    static final AttributeKey<String> CAMEL_SCHEME_KEY = AttributeKey.stringKey("url.scheme");
    static final AttributeKey<String> PRE_KEY = AttributeKey.stringKey("pre");
    static final AttributeKey<String> POST_KEY = AttributeKey.stringKey("post");
    static final AttributeKey<String> MESSAGE_KEY = AttributeKey.stringKey("message");

    private static final Logger LOG = LoggerFactory.getLogger(CamelOpenTelemetryTestSupport.class);

    @RegisterExtension
    public final CamelOpenTelemetryExtension otelExtension = CamelOpenTelemetryExtension.create();

    @BindToRegistry
    ThreadPoolFactory threadPoolFactory = new OpenTelemetryInstrumentedThreadPoolFactory();

    SpanTestData[] expected;
    Tracer tracer;
    OpenTelemetryTracer otTracer;

    CamelOpenTelemetryTestSupport(SpanTestData[] expected) {
        this.expected = expected;
    }

    @AfterEach
    void noLeakingContext() {
        Assertions.assertSame(Context.root(), Context.current(), "There must be no leaking span after test");
    }

    protected void initTracer(CamelContext context) {
        otTracer = new OpenTelemetryTracer();
        CamelContextAware.trySetCamelContext(otTracer, context);

        tracer = otelExtension.getOpenTelemetry().getTracer("tracerTest");
        otTracer.setTracer(tracer);
        otTracer.setExcludePatterns(getExcludePatterns());
        otTracer.addDecorator(new TestSEDASpanDecorator());
        if (isTraceProcessor()) {
            otTracer.setTraceProcessors(true);
            otTracer.initTracer();
        } else {
            otTracer.setTracingStrategy(getTracingStrategy().apply(otTracer));
        }
        otTracer.init(context);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        initTracer(context);
        return context;
    }

    protected boolean isTraceProcessor() {
        return false;
    }

    protected String getExcludePatterns() {
        return null;
    }

    protected OpenTelemetryTracer getOtTracer() {
        return otTracer;
    }

    protected void verify() {
        verify(expected, false);
    }

    protected void verify(boolean async) {
        verify(expected, async);
    }

    protected List<SpanData> verify(SpanTestData[] expected, boolean async) {
        List<SpanData> spans = otelExtension.getSpans();
        SpanTreePrinter.printSpanTree(spans);
        spans.forEach(mockSpan -> {
            LOG.info("Span: {}", mockSpan);
            LOG.info("\tComponent: {}", mockSpan.getAttributes().get(COMPONENT_KEY));
            LOG.info("\tTags: {}", mockSpan.getAttributes());
        });

        List<LogRecordData> logRecords = otelExtension.getLogRecords();
        if (!logRecords.isEmpty()) {
            LOG.info("Log records:");
            logRecords.forEach(logRecord -> {
                SpanContext spanContext = logRecord.getSpanContext();
                LOG.info("\tLog: [{},{}] {}", spanContext.getTraceId(), spanContext.getSpanId(), logRecord);
            });
        }

        assertEquals(expected.length, spans.size(), "Incorrect number of spans");
        int numberOfLogs = spans.size() * 2;
        assertEquals(numberOfLogs, logRecords.size(), "Incorrect number of log records");

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
                matched = matched && Objects.equals(s.getAttributes().get(CAMEL_URI_KEY), testdata.getUri());
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
                .until(() -> otelExtension.getSpans().size() >= (numOfTraces * numSpansPerTrace));

        List<SpanData> finishedSpans = otelExtension.getSpans();
        // Sort spans into separate traces
        for (SpanData finishedSpan : finishedSpans) {
            List<SpanData> spans = traces.computeIfAbsent(finishedSpan.getTraceId(), k -> new ArrayList<>());
            spans.add(finishedSpan);
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
        Attributes attributes = span.getAttributes();
        String component = attributes.get(COMPONENT_KEY);
        assertNotNull(component);

        String scheme = attributes.get(CAMEL_SCHEME_KEY);

        if (td.getUri() != null) {
            assertEquals(URI.create(td.getUri()).getScheme(), scheme);
        }

        if ("camel-seda".equals(component)) {
            assertNotNull(attributes.get(PRE_KEY));
            assertNotNull(attributes.get(POST_KEY));
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
                assertEquals(entry.getValue(), attributes.get(AttributeKey.stringKey(entry.getKey())));
            }
        }
    }

    protected void verifySameTrace() {
        assertEquals(1, otelExtension.getSpans().stream().map(SpanData::getTraceId).distinct().count());
    }

    protected Function<OpenTelemetryTracer, InterceptStrategy> getTracingStrategy() {
        return openTelemetryTracer -> new NoopTracingStrategy();
    }
}
