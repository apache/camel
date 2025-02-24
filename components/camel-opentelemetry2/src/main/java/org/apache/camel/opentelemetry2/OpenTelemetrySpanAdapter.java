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

import java.util.Map;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import org.apache.camel.telemetry.TagConstants;

public class OpenTelemetrySpanAdapter implements org.apache.camel.telemetry.Span {

    private static final String DEFAULT_EVENT_NAME = "log";

    private final Span otelSpan;
    private final Baggage baggage;
    private Scope scope;

    protected OpenTelemetrySpanAdapter(Span otelSpan, Baggage baggage) {
        this.otelSpan = otelSpan;
        this.baggage = baggage;
    }

    protected Span getSpan() {
        return this.otelSpan;
    }

    protected void makeCurrent() {
        this.scope = this.otelSpan.makeCurrent();
    }

    protected void end() {
        this.otelSpan.end();
    }

    protected void close() {
        if (scope != null) {
            this.scope.close();
        }
    }

    protected Baggage getBaggage() {
        return this.baggage;
    }

    @Override
    public void log(Map<String, String> fields) {
        this.otelSpan.addEvent(getEventNameFromFields(fields), convertToAttributes(fields));
    }

    @Override
    public void setTag(String key, String value) {
        this.otelSpan.setAttribute(key, value);
    }

    @Override
    public void setComponent(String component) {
        this.setTag(TagConstants.COMPONENT, component);
    }

    @Override
    public void setError(boolean isError) {
        this.setTag(TagConstants.ERROR, "" + isError);
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
            } else if (value instanceof Boolean) {
                attributesBuilder.put(key, (Boolean) value);
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
