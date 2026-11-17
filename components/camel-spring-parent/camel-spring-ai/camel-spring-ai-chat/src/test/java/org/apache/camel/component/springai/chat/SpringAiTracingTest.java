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

import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.model.tool.ToolCallingManager;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiTracingTest {

    private static ObservationRegistry tracingRegistry() {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new DefaultTracingObservationHandler(new SimpleTracer()));
        return registry;
    }

    @Test
    void shouldNeedATracingHandler() {
        ObservationRegistry metricsOnly = ObservationRegistry.create();
        metricsOnly.observationConfig().observationHandler(new DefaultMeterObservationHandler(new SimpleMeterRegistry()));

        assertThat(SpringAiTracing.hasTracingHandler(ObservationRegistry.NOOP)).isFalse();
        assertThat(SpringAiTracing.hasTracingHandler(ObservationRegistry.create())).isFalse();
        assertThat(SpringAiTracing.hasTracingHandler(metricsOnly)).isFalse();
        assertThat(SpringAiTracing.hasTracingHandler(tracingRegistry())).isTrue();
    }

    @Test
    void shouldFindATracingHandlerInsideAComposite() {
        // Spring Boot groups the tracing handlers in a FirstMatchingCompositeObservationHandler
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationHandler.FirstMatchingCompositeObservationHandler(
                new DefaultTracingObservationHandler(new SimpleTracer())));

        assertThat(SpringAiTracing.hasTracingHandler(registry)).isTrue();
    }

    @Test
    void shouldReadTheRegistryOfTheModel() {
        assertThat(SpringAiTracing.isModelTraced(new ModelWithRegistries(tracingRegistry(), null))).isTrue();
        assertThat(SpringAiTracing.isModelTraced(new ModelWithRegistries(ObservationRegistry.NOOP, null))).isFalse();
        assertThat(SpringAiTracing.isModelTraced(new Object())).isFalse();
    }

    @Test
    void shouldReadTheToolCallsFromTheToolCallingManager() {
        // OllamaChatModel keeps a default ToolCallingManager without a registry even when the model has one
        ToolCallingManager withoutRegistry = ToolCallingManager.builder().build();
        ToolCallingManager withRegistry = ToolCallingManager.builder().observationRegistry(tracingRegistry()).build();

        assertThat(SpringAiTracing.areToolCallsTraced(new ModelWithRegistries(tracingRegistry(), withoutRegistry)))
                .isFalse();
        assertThat(SpringAiTracing.areToolCallsTraced(new ModelWithRegistries(ObservationRegistry.NOOP, withRegistry)))
                .isTrue();
    }

    @SuppressWarnings("unused")
    private static final class ModelWithRegistries {
        private final ObservationRegistry observationRegistry;
        private final ToolCallingManager toolCallingManager;

        private ModelWithRegistries(ObservationRegistry observationRegistry, ToolCallingManager toolCallingManager) {
            this.observationRegistry = observationRegistry;
            this.toolCallingManager = toolCallingManager;
        }
    }
}
