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
package org.apache.camel.component.ai.observability;

import java.util.Properties;

import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.ExchangeTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenAiObservabilityFallbackDiagnosticsTest extends ExchangeTestSupport {

    private LogCapture capture;

    @BeforeEach
    void resetState() {
        GenAiObservabilityDiagnostics.resetForTesting();
        GenAiObservabilityImpl.resetBackendsForTesting();
        capture = LogCapture.attach(GenAiObservabilityDiagnostics.class);
    }

    @AfterEach
    void detachLogCapture() {
        if (capture != null) {
            capture.close();
        }
        GenAiObservabilityDiagnostics.resetForTesting();
        GenAiObservabilityImpl.resetBackendsForTesting();
    }

    @Test
    void shouldLogInfoOnceWhenObservationRegistryHasNoTracingHandler() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        observationRegistry.observationConfig()
                .observationHandler(new DefaultMeterObservationHandler(meters));
        context.getRegistry().bind("observationRegistry", observationRegistry);

        observeTwice();

        assertThat(capture.infoMessages()).hasSize(1);
        assertThat(capture.infoMessages().get(0)).contains("ObservationRegistry without a tracing handler");
        assertThat(capture.warnMessages()).isEmpty();
    }

    @Test
    void shouldLogWarnOnceWhenObservationRegistryHasNoTracingHandlerAndExplicitlyEnabled() {
        Properties properties = new Properties();
        properties.setProperty(GenAiObservabilityProperties.ENABLED, "true");
        context.getPropertiesComponent().setOverrideProperties(properties);

        ObservationRegistry observationRegistry = ObservationRegistry.create();
        observationRegistry.observationConfig().observationHandler(new RecordingObservationHandler());
        context.getRegistry().bind("observationRegistry", observationRegistry);

        observeTwice();

        assertThat(capture.warnMessages()).hasSize(1);
        assertThat(capture.warnMessages().get(0)).contains("ObservationRegistry without a tracing handler");
        assertThat(capture.infoMessages()).isEmpty();
    }

    @Test
    void shouldNotLogWhenObservationRegistryHasTracingHandler() {
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        observationRegistry.observationConfig()
                .observationHandler(new DefaultTracingObservationHandler(new SimpleTracer()));
        context.getRegistry().bind("observationRegistry", observationRegistry);

        observeTwice();

        assertThat(capture.infoMessages()).isEmpty();
        assertThat(capture.warnMessages()).isEmpty();
    }

    @Test
    void shouldNotLogWhenMeterHandlerIsRegisteredBeforeTracingHandler() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        observationRegistry.observationConfig()
                .observationHandler(new DefaultMeterObservationHandler(meters))
                .observationHandler(new DefaultTracingObservationHandler(new SimpleTracer()));
        context.getRegistry().bind("observationRegistry", observationRegistry);

        observeTwice();

        assertThat(capture.infoMessages()).isEmpty();
        assertThat(capture.warnMessages()).isEmpty();
    }

    private void observeTwice() {
        GenAiObservationContext context = GenAiObservationContext.builder()
                .operationName(GenAiOperationName.CHAT)
                .system("openai")
                .requestModel("test-model")
                .componentScheme("langchain4j-chat")
                .build();
        Exchange exchange = new DefaultExchange(this.context);
        GenAiObservability.start(exchange, context).close();
        GenAiObservability.start(exchange, context).close();
    }

    private static final class RecordingObservationHandler implements ObservationHandler<Observation.Context> {

        @Override
        public void onStart(Observation.Context context) {
            // noop
        }

        @Override
        public void onStop(Observation.Context context) {
            // noop
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }
    }
}
