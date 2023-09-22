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

import java.util.EnumMap;
import java.util.Map;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageBuilder;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.apache.camel.tracing.SpanAdapter;
import org.apache.camel.tracing.Tag;

public class OpenTelemetrySpanAdapter implements SpanAdapter {
    private static final String DEFAULT_EVENT_NAME = "log";
    private static Map<Tag, String> tagMap = new EnumMap<>(Tag.class);

    static {
        tagMap.put(Tag.COMPONENT, "component");
        tagMap.put(Tag.DB_TYPE, SemanticAttributes.DB_SYSTEM.getKey());
        tagMap.put(Tag.DB_STATEMENT, SemanticAttributes.DB_STATEMENT.getKey());
        tagMap.put(Tag.DB_INSTANCE, SemanticAttributes.DB_NAME.getKey());
        tagMap.put(Tag.HTTP_METHOD, SemanticAttributes.HTTP_METHOD.getKey());
        tagMap.put(Tag.HTTP_STATUS, SemanticAttributes.HTTP_STATUS_CODE.getKey());
        tagMap.put(Tag.HTTP_URL, SemanticAttributes.HTTP_URL.getKey());
        tagMap.put(Tag.MESSAGE_BUS_DESTINATION, "message_bus.destination");
    }

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
    public void setTag(Tag key, String value) {
        String attribute = tagMap.getOrDefault(key, key.getAttribute());
        this.span.setAttribute(attribute, value);
        if (!attribute.equals(key.getAttribute())) {
            this.span.setAttribute(key.getAttribute(), value);
        }
    }

    @Override
    public void setTag(Tag key, Number value) {
        this.span.setAttribute(tagMap.getOrDefault(key, key.getAttribute()), value.intValue());
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
