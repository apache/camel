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
package org.apache.camel.component.langchain4j.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.component.ai.observability.GenAiAttributes;
import org.apache.camel.telemetry.Span;
import org.apache.camel.telemetry.SpanContextPropagationExtractor;
import org.apache.camel.telemetry.SpanContextPropagationInjector;
import org.apache.camel.telemetry.SpanLifecycleManager;
import org.apache.camel.telemetry.Tracer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A camel-telemetry tracer that keeps the closed spans with their parent and typed tags.
 */
final class RecordingGenAiTracer extends Tracer {

    private final List<RecordingSpan> closedSpans = new ArrayList<>();

    @Override
    protected void initTracer() {
        setSpanLifecycleManager(new SpanLifecycleManager() {
            @Override
            public Span create(String spanName, String spanKind, Span parent, SpanContextPropagationExtractor extractor) {
                return new RecordingSpan(spanName, parent);
            }

            @Override
            public void activate(Span span) {
                // noop
            }

            @Override
            public void deactivate(Span span) {
                // noop
            }

            @Override
            public void close(Span span) {
                synchronized (closedSpans) {
                    closedSpans.add((RecordingSpan) span);
                }
            }

            @Override
            public void inject(Span span, SpanContextPropagationInjector injector, boolean includeTracing) {
                // noop
            }
        });
    }

    List<RecordingSpan> genAiSpans() {
        return closedSpans.stream().filter(span -> span.tags().containsKey(GenAiAttributes.OPERATION_NAME)).toList();
    }

    List<RecordingSpan> spans(String name) {
        return closedSpans.stream().filter(span -> span.name().equals(name)).toList();
    }

    RecordingSpan span(String name) {
        assertThat(spans(name)).as("spans named %s", name).hasSize(1);
        return spans(name).get(0);
    }

    boolean order(String first, String second) {
        List<String> names = closedSpans.stream().map(RecordingSpan::name).toList();
        return names.indexOf(first) >= 0 && names.indexOf(first) < names.indexOf(second);
    }

    record RecordingSpan(String name, Span parent, Map<String, Object> tags) implements Span {

        RecordingSpan(String name, Span parent) {
            this(name, parent, new HashMap<>());
        }

        @Override
        public void log(Map<String, String> fields) {
            // noop
        }

        @Override
        public void setTag(String key, String value) {
            tags.put(key, value);
        }

        @Override
        public void setTag(String key, long value) {
            tags.put(key, value);
        }

        @Override
        public void setTag(String key, double value) {
            tags.put(key, value);
        }

        @Override
        public void setComponent(String component) {
            tags.put("component", component);
        }

        @Override
        public void setError(boolean isError) {
            tags.put("error", isError);
        }
    }
}
