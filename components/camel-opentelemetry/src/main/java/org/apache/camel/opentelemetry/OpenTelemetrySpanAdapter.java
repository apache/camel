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

import java.util.Map;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageBuilder;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import org.apache.camel.tracing.SpanAdapter;

public class OpenTelemetrySpanAdapter implements SpanAdapter {
    private static final String DEFAULT_EVENT_NAME = "log";

    private Baggage baggage;
    private io.opentelemetry.api.trace.Span span;

    OpenTelemetrySpanAdapter(io.opentelemetry.api.trace.Span span) {
        this.span = span;
    }

    OpenTelemetrySpanAdapter(io.opentelemetry.api.trace.Span span, Baggage baggage) {
        this.span = span;
        this.baggage = baggage;
    }

    io.opentelemetry.api.trace.Span getOpenTelemetrySpan() {
        return this.span;
    }

    @Override
    public void setComponent(String component) {
        this.span.setAttribute("component", component);
    }

    @Override
    public void setError(boolean error) {
        this.span.setAttribute("error", error);
    }

    @Override
    public void setTag(String key, String value) {
        this.span.setAttribute(key, value);
    }

    @Override
    public void setTag(String key, Number value) {
        this.span.setAttribute(key, value.intValue());
    }

    @Override
    public void setTag(String key, Boolean value) {
        this.span.setAttribute(key, value);
    }

    @Override
    public void log(Map<String, String> fields) {
        span.addEvent(getEventNameFromFields(fields), convertToAttributes(fields));
    }

    @Override
    public String traceId() {
        return span.getSpanContext().getTraceId();
    }

    @Override
    public String spanId() {
        return span.getSpanContext().getSpanId();
    }

    @Override
    public AutoCloseable makeCurrent() {
        return span.makeCurrent();
    }

    String getEventNameFromFields(Map<String, ?> fields) {
        Object eventValue = fields == null ? null : fields.get("event");
        if (eventValue != null) {
            return eventValue.toString();
        }

        return DEFAULT_EVENT_NAME;
    }

    Attributes convertToAttributes(Map<String, ?> fields) {
        AttributesBuilder attributesBuilder = Attributes.builder();

        for (Map.Entry<String, ?> entry : fields.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            if (value instanceof Byte
                    || value instanceof Short
                    || value instanceof Integer
                    || value instanceof Long) {
                attributesBuilder.put(key, ((Number) value).longValue());
            } else if (value instanceof Float || value instanceof Double) {
                attributesBuilder.put(key, ((Number) value).doubleValue());
            } else if (value instanceof Boolean) {
                attributesBuilder.put(key, (Boolean) value);
            } else {
                attributesBuilder.put(key, value.toString());
            }
        }
        return attributesBuilder.build();
    }

    public Baggage getBaggage() {
        return this.baggage;
    }

    public void setBaggage(Baggage baggage) {
        this.baggage = baggage;
    }

    public void setCorrelationContextItem(String key, String value) {
        BaggageBuilder builder = Baggage.builder();
        if (baggage != null) {
            builder = Baggage.current().toBuilder();
        }
        baggage = builder.put(key, value).build();
    }

    public String getContextPropagationItem(String key) {
        if (baggage != null) {
            return baggage.getEntryValue(key);
        }
        return null;
    }
}
