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
package org.apache.camel.component.springai.chat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ai.observability.GenAiAttributes;
import org.apache.camel.component.ai.observability.GenAiObservabilityProperties;
import org.apache.camel.telemetry.Span;
import org.apache.camel.telemetry.SpanContextPropagationExtractor;
import org.apache.camel.telemetry.SpanContextPropagationInjector;
import org.apache.camel.telemetry.SpanLifecycleManager;
import org.apache.camel.telemetry.Tracer;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.ObservedStubOpenAiChatModel;
import org.springframework.ai.openai.StubOpenAiChatModel;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A Spring AI model built with an ObservationRegistry records its own calls, so Camel does not record them again.
 */
class SpringAiChatFrameworkTracingTest extends CamelTestSupport {

    private final List<Map<String, String>> genAiSpans = new ArrayList<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        Tracer tracer = new Tracer() {
            @Override
            protected void initTracer() {
                setSpanLifecycleManager(new SpanLifecycleManager() {
                    @Override
                    public Span create(
                            String spanName, String spanKind, Span parent, SpanContextPropagationExtractor extractor) {
                        return new TagSpan(new HashMap<>());
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
                        Map<String, String> tags = ((TagSpan) span).tags();
                        if (tags.containsKey(GenAiAttributes.OPERATION_NAME)) {
                            genAiSpans.add(tags);
                        }
                    }

                    @Override
                    public void inject(Span span, SpanContextPropagationInjector injector, boolean includeTracing) {
                        // noop
                    }
                });
            }
        };
        CamelContextAware.trySetCamelContext(tracer, context);
        tracer.init(context);
        return context;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.getRegistry().bind("observedModel", new ObservedStubOpenAiChatModel());
                context.getRegistry().bind("plainModel", new StubOpenAiChatModel());
                from("direct:observed").to("spring-ai-chat:observed?chatModel=#observedModel");
                from("direct:plain").to("spring-ai-chat:plain?chatModel=#plainModel");
            }
        };
    }

    @Test
    void shouldLeaveTheCallsOfAnObservedModelToSpringAi() {
        assertThat(template.requestBody("direct:observed", "Hello", String.class)).isEqualTo("Hello back");

        assertThat(genAiSpans).isEmpty();
    }

    @Test
    void shouldRecordTheCallsOfAModelWithoutObservations() {
        template.requestBody("direct:plain", "Hello", String.class);

        assertThat(genAiSpans).hasSize(1);
    }

    @Test
    void shouldRecordTheCallsOfAnObservedModelWhenNotDeferring() {
        context.getPropertiesComponent().addOverrideProperty(GenAiObservabilityProperties.DEFER_TO_FRAMEWORK, "false");

        template.requestBody("direct:observed", "Hello", String.class);

        assertThat(genAiSpans).hasSize(1);
    }

    private record TagSpan(Map<String, String> tags) implements Span {
        @Override
        public void log(Map<String, String> fields) {
            // noop
        }

        @Override
        public void setTag(String key, String value) {
            tags.put(key, value);
        }

        @Override
        public void setComponent(String component) {
            tags.put("component", component);
        }

        @Override
        public void setError(boolean isError) {
            tags.put("error", Boolean.toString(isError));
        }
    }
}
