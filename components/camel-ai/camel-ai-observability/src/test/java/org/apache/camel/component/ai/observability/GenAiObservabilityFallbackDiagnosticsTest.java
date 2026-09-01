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

import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.ExchangeTestSupport;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenAiObservabilityFallbackDiagnosticsTest extends ExchangeTestSupport {

    private AbstractAppender appender;
    private Logger logger;
    private final List<String> infoMessages = new CopyOnWriteArrayList<>();
    private final List<String> warnMessages = new CopyOnWriteArrayList<>();

    @BeforeEach
    void attachLogCapture() {
        GenAiObservabilityDiagnostics.resetForTesting();
        GenAiObservabilityImpl.resetBackendsForTesting();
        appender = new AbstractAppender("GenAiObservabilityCapture", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (event.getLevel() == Level.INFO) {
                    infoMessages.add(event.getMessage().getFormattedMessage());
                } else if (event.getLevel() == Level.WARN) {
                    warnMessages.add(event.getMessage().getFormattedMessage());
                }
            }
        };
        appender.start();
        logger = (Logger) LogManager.getLogger(GenAiObservabilityDiagnostics.class);
        logger.addAppender(appender);
    }

    @AfterEach
    void detachLogCapture() {
        if (logger != null && appender != null) {
            logger.removeAppender(appender);
            appender.stop();
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

        assertThat(infoMessages).hasSize(1);
        assertThat(infoMessages.get(0)).contains("ObservationRegistry without a tracing handler");
        assertThat(warnMessages).isEmpty();
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

        assertThat(warnMessages).hasSize(1);
        assertThat(warnMessages.get(0)).contains("ObservationRegistry without a tracing handler");
        assertThat(infoMessages).isEmpty();
    }

    @Test
    void shouldNotLogWhenObservationRegistryHasTracingHandler() {
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        observationRegistry.observationConfig()
                .observationHandler(new RecordingObservationHandler())
                .observationHandler(new FakeTracingObservationHandler());
        context.getRegistry().bind("observationRegistry", observationRegistry);

        observeTwice();

        assertThat(infoMessages).isEmpty();
        assertThat(warnMessages).isEmpty();
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

    /**
     * Handler class name matches the TracingObservationHandler detection used in production code.
     */
    private static final class FakeTracingObservationHandler implements ObservationHandler<Observation.Context> {

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
