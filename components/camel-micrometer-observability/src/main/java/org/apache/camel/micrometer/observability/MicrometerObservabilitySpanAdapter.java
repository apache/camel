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

import java.util.Map;

import io.micrometer.tracing.Tracer;
import org.apache.camel.telemetry.Span;

public class MicrometerObservabilitySpanAdapter implements Span {

    private static final String DEFAULT_EVENT_NAME = "log";

    private final io.micrometer.tracing.Span span;
    private final Tracer tracer;
    private Tracer.SpanInScope spanInScope;

    public MicrometerObservabilitySpanAdapter(io.micrometer.tracing.Span span, Tracer tracer) {
        this.span = span;
        this.tracer = tracer;
    }

    @Override
    public void log(Map<String, String> fields) {
        String event = fields.get("event");
        if ("error".equalsIgnoreCase(event)) {
            setError(true);
        } else if (fields.get("message") != null) {
            // this is really the only expected event for logging
            this.span.event("message=" + fields.get("message"));
        } else {
            this.span.event(DEFAULT_EVENT_NAME);
        }
    }

    @Override
    public void setComponent(String component) {
        this.span.tag("component", component);
    }

    @Override
    public void setError(boolean isError) {
        this.span.tag("error", isError);
    }

    @Override
    public void setTag(String key, String value) {
        this.span.tag(key, value);
    }

    protected io.micrometer.tracing.Span getSpan() {
        return this.span;
    }

    protected void activate() {
        this.span.start();
        this.spanInScope = this.tracer.withSpan(this.span);
    }

    protected void deactivate() {
        if (this.spanInScope != null) {
            this.spanInScope.close();
            this.spanInScope = null;
        }
    }

    protected void close() {
        this.span.end();
    }

    @Override
    public String toString() {
        return "MicrometerObservabilitySpanAdapter [traceId=" +
               span.context().traceId() + " spanId=" + span.context().spanId() + "]";
    }

}
