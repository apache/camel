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

import java.util.List;

import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.telemetry.Op;
import org.apache.camel.telemetry.TagConstants;
import org.apache.camel.test.junit6.ExchangeTestSupport;

/**
 * This test is special as it requires a different setting to inherit the Opentelemetry propagation mechanism.
 */
public class MicrometerObservabilityTracerPropagationTestSupport extends ExchangeTestSupport {

    protected CamelOpenTelemetryExtension otelExtension = CamelOpenTelemetryExtension.create();
    protected MicrometerObservabilityTracer tst = new MicrometerObservabilityTracer();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        ContextPropagators propagators = otelExtension.getPropagators();
        io.opentelemetry.api.trace.Tracer otelTracer = otelExtension.getOpenTelemetry().getTracer("traceTest");

        OtelPropagator otelPropagator = new OtelPropagator(propagators, otelTracer);
        OtelCurrentTraceContext currentTraceContext = new OtelCurrentTraceContext();
        // We must convert the Otel Tracer into a micrometer Tracer
        io.micrometer.tracing.Tracer micrometerTracer = new OtelTracer(
                otelTracer,
                currentTraceContext,
                null);

        context.getRegistry().bind("MicrometerObservabilityTracer", micrometerTracer);
        context.getRegistry().bind("OpentelemetryPropagators", otelPropagator);

        CamelContextAware.trySetCamelContext(tst, context);
        tst.init(context);
        return context;
    }

    protected static SpanData getSpan(List<SpanData> trace, String uri, Op op) {
        for (SpanData span : trace) {
            String camelURI = span.getAttributes().get(AttributeKey.stringKey("camel.uri"));
            if (camelURI != null && camelURI.equals(uri)) {
                String operation = span.getAttributes().get(AttributeKey.stringKey(TagConstants.OP));
                if (operation != null && operation.equals(op.toString())) {
                    return span;
                }
            }
        }
        throw new IllegalArgumentException("Trying to get a non existing span!");
    }

}
