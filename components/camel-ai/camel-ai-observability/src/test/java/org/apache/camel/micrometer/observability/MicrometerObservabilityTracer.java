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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.telemetry.Span;
import org.apache.camel.telemetry.SpanContextPropagationExtractor;
import org.apache.camel.telemetry.SpanContextPropagationInjector;
import org.apache.camel.telemetry.SpanLifecycleManager;
import org.apache.camel.telemetry.Tracer;

/**
 * Test stand-in for the camel-micrometer-observability tracer. It carries the real class name, which GenAI
 * observability recognises: it would clash if camel-micrometer-observability were ever added to the test classpath of
 * this module.
 */
public class MicrometerObservabilityTracer extends Tracer {

    private final List<Span> closedSpans = new ArrayList<>();

    @Override
    protected void initTracer() {
        setSpanLifecycleManager(new SpanLifecycleManager() {
            @Override
            public Span create(String spanName, String spanKind, Span parent, SpanContextPropagationExtractor extractor) {
                return new Span() {
                    @Override
                    public void log(Map<String, String> fields) {
                        // noop
                    }

                    @Override
                    public void setTag(String key, String value) {
                        // noop
                    }

                    @Override
                    public void setComponent(String component) {
                        // noop
                    }

                    @Override
                    public void setError(boolean isError) {
                        // noop
                    }
                };
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
                closedSpans.add(span);
            }

            @Override
            public void inject(Span span, SpanContextPropagationInjector injector, boolean includeTracing) {
                // noop
            }
        });
    }

    public List<Span> closedSpans() {
        return closedSpans;
    }
}
