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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.opentracing.Span;
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

        ottracer.init(context);

        return context;
    }

    protected MockTracer getTracer() {
        return tracer;
    }

    protected void verify() {
        assertEquals("Incorrect number of spans", testdata.length, tracer.finishedSpans().size());

        for (int i = 0; i < testdata.length; i++) {
            if (i > 0) {
                assertEquals(testdata[i].getLabel(), tracer.finishedSpans().get(0).context().traceId(),
                    tracer.finishedSpans().get(i).context().traceId());
            }

            String component = (String) tracer.finishedSpans().get(i).tags().get(Tags.COMPONENT.getKey());
            assertNotNull(component);
            assertEquals(testdata[i].getLabel(),
                SpanDecorator.CAMEL_COMPONENT + URI.create((String) testdata[i].getUri()).getScheme(),
                component);
            assertEquals(testdata[i].getLabel(), testdata[i].getUri(),
                tracer.finishedSpans().get(i).tags().get("camel.uri"));

            // If span associated with TestSEDASpanDecorator, check that pre/post tags have been defined
            if ("camel-seda".equals(component)) {
                assertTrue(tracer.finishedSpans().get(i).tags().containsKey("pre"));
                assertTrue(tracer.finishedSpans().get(i).tags().containsKey("post"));
            }

            assertEquals(testdata[i].getLabel(), testdata[i].getOperation(), tracer.finishedSpans().get(i).operationName());

            assertEquals(testdata[i].getLabel(), testdata[i].getKind(),
                tracer.finishedSpans().get(i).tags().get(Tags.SPAN_KIND.getKey()));

            if (testdata[i].getParentId() != -1) {
                assertEquals(testdata[i].getLabel(),
                    tracer.finishedSpans().get(testdata[i].getParentId()).context().spanId(),
                    tracer.finishedSpans().get(i).parentId());
            }

        }
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
