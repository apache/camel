/**
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
import org.apache.camel.test.junit4.CamelTestSupport;

public class CamelOpenTracingTestSupport extends CamelTestSupport {

    private MockTracer tracer;

    private SpanTestData[] testdata;

    public CamelOpenTracingTestSupport(SpanTestData[] testdata) {
        this.testdata = testdata;
    }

    @Override
    protected void doPostSetup() throws Exception {
        tracer.reset();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        tracer = new MockTracer(Propagator.TEXT_MAP);

        OpenTracingTracer ottracer = new OpenTracingTracer();
        ottracer.setTracer(tracer);
        ottracer.setExcludePatterns(getExcludePatterns());

        ottracer.init(context);

        return context;
    }

    protected MockTracer getTracer() {
        return tracer;
    }

    protected Set<String> getExcludePatterns() {
        return new HashSet<String>();
    }

    protected void verify() {
        verify(false);
    }

    protected void verify(boolean async) {
        assertEquals("Incorrect number of spans", testdata.length, tracer.finishedSpans().size());

        verifySameTrace();

        List<MockSpan> spans = tracer.finishedSpans();
        if (async) {
            final List<MockSpan> unsortedSpans = spans;
            spans = Arrays.asList(testdata).stream()
                    .map(td -> findSpan(td, unsortedSpans)).distinct().collect(Collectors.toList());
            assertEquals("Incorrect number of spans after sorting", testdata.length, spans.size());
        }

        for (int i = 0; i < testdata.length; i++) {
            verifySpan(i, testdata, spans);
        }
    }

    protected MockSpan findSpan(SpanTestData testdata, List<MockSpan> spans) {
        return spans.stream().filter(s -> s.operationName().equals(testdata.getOperation())
                && s.tags().get("camel.uri").equals(testdata.getUri())
                && s.tags().get(Tags.SPAN_KIND.getKey()).equals(testdata.getKind())).findFirst().orElse(null);
    }

    protected void verifySpan(int index, SpanTestData[] testdata, List<MockSpan> spans) {
        MockSpan span = spans.get(index);
        SpanTestData td = testdata[index];

        String component = (String) span.tags().get(Tags.COMPONENT.getKey());
        assertNotNull(component);
        assertEquals(td.getLabel(),
            SpanDecorator.CAMEL_COMPONENT + URI.create((String) td.getUri()).getScheme(),
            component);
        assertEquals(td.getLabel(), td.getUri(), span.tags().get("camel.uri"));

        // If span associated with TestSEDASpanDecorator, check that pre/post tags have been defined
        if ("camel-seda".equals(component)) {
            assertTrue(span.tags().containsKey("pre"));
            assertTrue(span.tags().containsKey("post"));
        }

        assertEquals(td.getLabel(), td.getOperation(), span.operationName());

        assertEquals(td.getLabel(), td.getKind(),
                span.tags().get(Tags.SPAN_KIND.getKey()));

        if (td.getParentId() != -1) {
            assertEquals(td.getLabel(),
                spans.get(td.getParentId()).context().spanId(),
                span.parentId());
        }

        if (!td.getLogMessages().isEmpty()) {
            assertEquals("Number of log messages", td.getLogMessages().size(), span.logEntries().size());
            for (int i = 0; i < td.getLogMessages().size(); i++) {
                assertEquals(td.getLogMessages().get(i), span.logEntries().get(i).fields().get("message"));
            }
        }
    }

    protected void verifySameTrace() {
        assertEquals(1, tracer.finishedSpans().stream().map(s -> s.context().traceId()).distinct().count());
    }

    protected void verifyTraceSpanNumbers(int numOfTraces, int numSpansPerTrace) {
        Map<Long, List<Span>> traces = new HashMap<Long, List<Span>>();

        // Sort spans into separate traces
        for (int i = 0; i < getTracer().finishedSpans().size(); i++) {
            List<Span> spans = traces.get(getTracer().finishedSpans().get(i).context().traceId());
            if (spans == null) {
                spans = new ArrayList<Span>();
                traces.put(getTracer().finishedSpans().get(i).context().traceId(), spans);
            }
            spans.add(getTracer().finishedSpans().get(i));
        }

        assertEquals(numOfTraces, traces.size());

        for (Map.Entry<Long, List<Span>> spans : traces.entrySet()) {
            assertEquals(numSpansPerTrace, spans.getValue().size());
        }
    }

}
