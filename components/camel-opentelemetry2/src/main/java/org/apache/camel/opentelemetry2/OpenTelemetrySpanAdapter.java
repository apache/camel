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
package org.apache.camel.opentelemetry2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import org.apache.camel.telemetry.Op;
import org.apache.camel.telemetry.TagConstants;

public class OpenTelemetrySpanAdapter implements org.apache.camel.telemetry.Span {

    private static final String DEFAULT_EVENT_NAME = "log";
    static final String BAGGAGE_CAMEL_FLAG = "camelScope";

    private Span otelSpan;
    private final Baggage baggage;
    private Scope scope;
    private Scope baggageScope;

    // For deferred span creation
    private SpanBuilder spanBuilder;
    private final Map<String, String> pendingTags = new HashMap<>();
    private final List<Map<String, String>> pendingLogs = new ArrayList<>();
    private boolean pendingError;
    private String pendingComponent;

    protected OpenTelemetrySpanAdapter(SpanBuilder spanBuilder, Baggage baggage) {
        this.spanBuilder = spanBuilder;
        this.baggage = baggage != null
                ? baggage.toBuilder().put(BAGGAGE_CAMEL_FLAG, "true").build()
                : Baggage.current().toBuilder().put(BAGGAGE_CAMEL_FLAG, "true").build();
    }

    protected Span getSpan() {
        return this.otelSpan;
    }

    protected void makeCurrent() {
        // Start the span if it was deferred
        if (spanBuilder != null) {
            // Determine and apply span kind
            try {
                spanBuilder.setSpanKind(SpanKind.valueOf(determineKind()));
            } catch (IllegalArgumentException e) {
                // Invalid kind, use default
                spanBuilder.setSpanKind(SpanKind.INTERNAL);
            }
            // Start the span
            this.otelSpan = spanBuilder.startSpan();
            this.spanBuilder = null;

            // Apply pending operations
            if (pendingComponent != null) {
                this.otelSpan.setAttribute(TagConstants.COMPONENT, pendingComponent);
            }
            for (Map.Entry<String, String> entry : pendingTags.entrySet()) {
                this.otelSpan.setAttribute(entry.getKey(), entry.getValue());
            }
            for (Map<String, String> logFields : pendingLogs) {
                this.otelSpan.addEvent(getEventNameFromFields(logFields), convertToAttributes(logFields));
            }
            if (pendingError) {
                this.otelSpan.setStatus(StatusCode.ERROR);
            }

            // Clear pending state
            pendingTags.clear();
            pendingLogs.clear();
        }
        this.scope = this.otelSpan.makeCurrent();
        this.baggageScope = this.baggage.makeCurrent();
    }

    protected void end() {
        this.otelSpan.end();
    }

    protected void close() {
        if (baggageScope != null) {
            this.baggageScope.close();
        }
        if (scope != null) {
            this.scope.close();
        }
    }

    protected Baggage getBaggage() {
        return this.baggage;
    }

    private String determineKind() {
        var kind = pendingTags.get("kind");
        if (kind != null) {
            return kind;
        }
        var operation = pendingTags.get(TagConstants.OP);
        boolean isMessaging = pendingTags.containsKey(TagConstants.MESSAGE_BUS_DESTINATION);
        boolean isHttp = pendingTags.containsKey(TagConstants.HTTP_METHOD);
        return switch (Op.valueOf(operation)) {
            case EVENT_RECEIVED -> isMessaging ? "CONSUMER" : isHttp ? "SERVER" : "INTERNAL";
            case EVENT_SENT -> isMessaging ? "PRODUCER" : isHttp ? "CLIENT" : "INTERNAL";
            default -> "INTERNAL";
        };

    }

    @Override
    public void log(Map<String, String> fields) {
        if (otelSpan != null) {
            this.otelSpan.addEvent(getEventNameFromFields(fields), convertToAttributes(fields));
        } else {
            pendingLogs.add(new HashMap<>(fields));
        }
    }

    @Override
    public void setTag(String key, String value) {
        if (otelSpan != null) {
            this.otelSpan.setAttribute(key, value);
        } else {
            pendingTags.put(key, value);
        }
    }

    @Override
    public void setComponent(String component) {
        if (otelSpan != null) {
            this.otelSpan.setAttribute(TagConstants.COMPONENT, component);
        } else {
            pendingComponent = component;
        }
    }

    @Override
    public void setError(boolean isError) {
        if (otelSpan != null) {
            this.otelSpan.setAttribute(TagConstants.ERROR, "" + isError);
            this.otelSpan.setStatus(isError ? StatusCode.ERROR : StatusCode.OK);
        } else {
            pendingTags.put(TagConstants.ERROR, "" + isError);
            pendingError = isError;
        }
    }

    private String getEventNameFromFields(Map<String, ?> fields) {
        Object eventValue = fields == null ? null : fields.get("event");
        if (eventValue != null) {
            return eventValue.toString();
        }

        return DEFAULT_EVENT_NAME;
    }

    private Attributes convertToAttributes(Map<String, ?> fields) {
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
            } else if (value instanceof Boolean b) {
                attributesBuilder.put(key, b);
            } else {
                attributesBuilder.put(key, value.toString());
            }
        }
        return attributesBuilder.build();
    }

    @Override
    public String toString() {
        return "OpenTelemetrySpanAdapter [span=" + otelSpan + ", baggage=" + baggage + "]";
    }

}
