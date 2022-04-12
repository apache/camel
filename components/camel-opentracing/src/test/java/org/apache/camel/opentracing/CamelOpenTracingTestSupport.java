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
package org.apache.camel.opentracing;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.opentracing.Span;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.mock.MockTracer.Propagator;
import io.opentracing.tag.Tags;
import org.apache.camel.CamelContext;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.tracing.SpanDecorator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CamelOpenTracingTestSupport extends CamelTestSupport {

    protected OpenTracingTracer ottracer;
    private MockTracer tracer;
    private SpanTestData[] testdata;

    public CamelOpenTracingTestSupport(SpanTestData[] testdata) {
        this.testdata = testdata;
    }

    @Override
    protected void doPostSetup() {
        tracer.reset();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        tracer = new MockTracer(Propagator.TEXT_MAP);

        this.ottracer = new OpenTracingTracer();
        ottracer.setTracer(tracer);
        ottracer.setExcludePatterns(getExcludePatterns());
        ottracer.setTracingStrategy(getTracingStrategy());
        ottracer.addDecorator(new TestSEDASpanDecorator());
        ottracer.init(context);
        return context;
    }

    protected MockTracer getTracer() {
        return tracer;
    }

    protected Set<String> getExcludePatterns() {
        return new HashSet<>();
    }

    protected void verify() {
        verify(false);
    }

    protected void verify(boolean async) {
        List<MockSpan> spans = tracer.finishedSpans();
        spans.forEach(mockSpan -> {
            System.out.println("Span: " + mockSpan);
            System.out.println("\tComponent: " + mockSpan.tags().get(Tags.COMPONENT.getKey()));
            System.out.println("\tTags: " + mockSpan.tags());
            System.out.println("\tLogs: ");
            for (final MockSpan.LogEntry logEntry : mockSpan.logEntries()) {
                System.out.println("\t" + logEntry.fields());
            }
        });

        assertEquals(testdata.length, tracer.finishedSpans().size(), "Incorrect number of spans");

        verifySameTrace();

        if (async) {
            final List<MockSpan> unsortedSpans = spans;
            spans = Arrays.stream(testdata)
                    .map(td -> findSpan(td, unsortedSpans)).distinct().collect(Collectors.toList());
            assertEquals(testdata.length, spans.size(), "Incorrect number of spans after sorting");
        }

        for (int i = 0; i < testdata.length; i++) {
            verifySpan(i, testdata, spans);
        }
    }

    protected MockSpan findSpan(SpanTestData testdata, List<MockSpan> spans) {
        return spans.stream().filter(s -> {
            boolean matched = s.operationName().equals(testdata.getOperation());

            if (s.tags().containsKey("camel-uri")) {
                matched = matched && s.tags().get("camel.uri").equals(testdata.getUri());
            }

            if (s.tags().containsKey(Tags.SPAN_KIND.getKey())) {
                matched = matched && s.tags().get(Tags.SPAN_KIND.getKey()).equals(testdata.getKind());
            }

            return matched;
        }).findFirst().orElse(null);
    }

    protected void verifySpan(int index, SpanTestData[] testdata, List<MockSpan> spans) {
        MockSpan span = spans.get(index);
        SpanTestData td = testdata[index];

        String component = (String) span.tags().get(Tags.COMPONENT.getKey());
        assertNotNull(component);

        if (td.getUri() != null) {
            assertEquals(SpanDecorator.CAMEL_COMPONENT + URI.create(td.getUri()).getScheme(), component, td.getLabel());
        }
        assertEquals(td.getUri(), span.tags().get("camel.uri"), td.getLabel());

        // If span associated with org.apache.camel.opentracing.TestSEDASpanDecorator, check that pre/post tags have been defined
        if ("camel-seda".equals(component)) {
            assertTrue(span.tags().containsKey("pre"));
            assertTrue(span.tags().containsKey("post"));
        }

        assertEquals(td.getOperation(), span.operationName(), td.getLabel());

        assertEquals(td.getKind(), span.tags().get(Tags.SPAN_KIND.getKey()), td.getLabel());

        if (td.getParentId() != -1) {
            assertEquals(spans.get(td.getParentId()).context().spanId(), span.parentId(), td.getLabel());
        }

        if (!td.getLogMessages().isEmpty()) {
            assertEquals(td.getLogMessages().size(), span.logEntries().size(), td.getLabel());
            for (int i = 0; i < td.getLogMessages().size(); i++) {
                assertEquals(td.getLogMessages().get(i), span.logEntries().get(i).fields().get("message"));
            }
        }

        if (!td.getTags().isEmpty()) {
            for (Map.Entry<String, String> entry : td.getTags().entrySet()) {
                assertEquals(entry.getValue(), String.valueOf(span.tags().get(entry.getKey())));
            }
        }

        if (!td.getBaggage().isEmpty()) {
            for (Map.Entry<String, String> entry : td.getBaggage().entrySet()) {
                assertEquals(entry.getValue(), span.getBaggageItem(entry.getKey()));
            }
        }
    }

    protected void verifySameTrace() {
        assertEquals(1, tracer.finishedSpans().stream().map(s -> s.context().traceId()).distinct().count());
    }

    protected void verifyTraceSpanNumbers(int numOfTraces, int numSpansPerTrace) {
        Map<Long, List<Span>> traces = new HashMap<>();

        // Sort spans into separate traces
        for (int i = 0; i < getTracer().finishedSpans().size(); i++) {
            List<Span> spans = traces.get(getTracer().finishedSpans().get(i).context().traceId());
            if (spans == null) {
                spans = new ArrayList<>();
                traces.put(getTracer().finishedSpans().get(i).context().traceId(), spans);
            }
            spans.add(getTracer().finishedSpans().get(i));
        }

        assertEquals(numOfTraces, traces.size());

        for (Map.Entry<Long, List<Span>> spans : traces.entrySet()) {
            assertEquals(numSpansPerTrace, spans.getValue().size());
        }
    }

    protected InterceptStrategy getTracingStrategy() {
        return new NoopTracingStrategy();
    }
}
