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
package org.apache.camel.observation;

import java.util.Map;

import io.micrometer.observation.Observation;
import io.micrometer.tracing.Baggage;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.TracingObservationHandler.TracingContext;
import org.apache.camel.tracing.SpanAdapter;
import org.apache.camel.tracing.Tag;

public class MicrometerObservationSpanAdapter implements SpanAdapter {
    private static final String DEFAULT_EVENT_NAME = "log";

    private final Observation observation;

    private final Tracer tracer;

    MicrometerObservationSpanAdapter(Observation observation, Tracer tracer) {
        this.observation = observation;
        this.tracer = tracer;
    }

    Observation getMicrometerObservation() {
        return this.observation;
    }

    @Override
    public void setComponent(String component) {
        this.observation.lowCardinalityKeyValue("component", component);
    }

    @Override
    public void setError(boolean error) {
        this.observation.lowCardinalityKeyValue("error", String.valueOf(error));
    }

    @Override
    public void setTag(Tag key, String value) {
        this.observation.highCardinalityKeyValue(key.toString(), value);
        this.observation.highCardinalityKeyValue(key.getAttribute(), value);
    }

    @Override
    public void setTag(Tag key, Number value) {
        setTag(key, value.toString());
    }

    @Override
    public void setTag(String key, String value) {
        this.observation.highCardinalityKeyValue(key, value);
    }

    @Override
    public void setTag(String key, Number value) {
        setTag(key, value.toString());
    }

    @Override
    public void setTag(String key, Boolean value) {
        setTag(key, value.toString());
    }

    @Override
    public void setLowCardinalityTag(Tag key, String value) {
        observation.lowCardinalityKeyValue(key.toString(), value);
        observation.lowCardinalityKeyValue(key.getAttribute(), value);
    }

    @Override
    public void setLowCardinalityTag(Tag key, Number value) {
        observation.lowCardinalityKeyValue(key.toString(), value.toString());
        observation.lowCardinalityKeyValue(key.getAttribute(), value.toString());
    }

    @Override
    public void setLowCardinalityTag(String key, String value) {
        observation.lowCardinalityKeyValue(key, value);
    }

    @Override
    public void setLowCardinalityTag(String key, Number value) {
        observation.lowCardinalityKeyValue(key, value.toString());
    }

    @Override
    public void setLowCardinalityTag(String key, Boolean value) {
        observation.lowCardinalityKeyValue(key, value.toString());
    }

    @Override
    public void log(Map<String, String> fields) {
        String event = fields.get("event");
        if ("error".equalsIgnoreCase(event)) {
            if (fields.containsKey("message")) {
                observation.error(new RuntimeException(fields.get("message")));
            } else {
                setError(true);
            }
        } else {
            observation.event(() -> getMessageNameFromFields(fields));
        }
    }

    @Override
    public String traceId() {
        TracingContext tracingContext = getTracingContext();
        return tracingContext.getSpan() != null ? tracingContext.getSpan().context().traceId() : null;
    }

    private TracingContext getTracingContext() {
        return observation.getContextView().getOrDefault(TracingContext.class, new TracingContext());
    }

    @Override
    public String spanId() {
        TracingContext tracingContext = getTracingContext();
        return tracingContext.getSpan() != null ? tracingContext.getSpan().context().spanId() : null;
    }

    @Override
    public AutoCloseable makeCurrent() {
        return observation.openScope();
    }

    String getMessageNameFromFields(Map<String, ?> fields) {
        Object eventValue = fields == null ? null : fields.get("message");
        if (eventValue != null) {
            return eventValue.toString();
        }

        return DEFAULT_EVENT_NAME;
    }

    public void setCorrelationContextItem(String key, String value) {
        Baggage baggage = tracer.createBaggage(key);
        Span span = getTracingContext().getSpan();
        if (span == null) {
            return;
        }
        baggage.set(span.context(), value);
    }

    public String getContextPropagationItem(String key) {
        Span span = getTracingContext().getSpan();
        if (span == null) {
            return null;
        }
        Baggage baggage = tracer.getBaggage(span.context(), key);
        if (baggage != null) {
            return baggage.get(span.context());
        }
        return null;
    }

}
