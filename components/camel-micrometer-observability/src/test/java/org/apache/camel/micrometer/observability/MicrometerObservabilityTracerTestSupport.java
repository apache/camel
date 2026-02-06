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
package org.apache.camel.micrometer.observability;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.micrometer.tracing.test.simple.SimpleSpan;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.telemetry.Op;
import org.apache.camel.telemetry.TagConstants;
import org.apache.camel.test.junit6.ExchangeTestSupport;

public class MicrometerObservabilityTracerTestSupport extends ExchangeTestSupport {

    protected SimpleTracer tracer = new SimpleTracer();
    protected MicrometerObservabilityTracer tst = new MicrometerObservabilityTracer();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("MicrometerObservabilityTracer", tracer);
        CamelContextAware.trySetCamelContext(tst, context);
        tst.init(context);
        return context;
    }

    protected Map<String, MicrometerObservabilityTrace> traces() {
        HashMap<String, MicrometerObservabilityTrace> map = new HashMap<>();
        for (SimpleSpan span : tracer.getSpans()) {
            String traceId = span.getTraceId();
            MicrometerObservabilityTrace trace = map.get(traceId);
            if (trace == null) {
                trace = new MicrometerObservabilityTrace(traceId);
                map.put(traceId, trace);
            }
            trace.add(span);
        }
        return map;
    }

    protected static SimpleSpan getSpan(List<SimpleSpan> trace, String uri, Op op) {
        for (SimpleSpan span : trace) {
            if (span.getTags().get("camel.uri") != null && span.getTags().get("camel.uri").equals(uri)) {
                if (span.getTags().get(TagConstants.OP).equals(op.toString())) {
                    return span;
                }
            }
        }
        throw new IllegalArgumentException("Trying to get a non existing span!");
    }

}
