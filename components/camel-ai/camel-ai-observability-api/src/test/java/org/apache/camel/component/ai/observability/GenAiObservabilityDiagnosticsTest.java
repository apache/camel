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

class GenAiObservabilityDiagnosticsTest extends ExchangeTestSupport {

    private AbstractAppender appender;
    private Logger logger;
    private final List<String> infoMessages = new CopyOnWriteArrayList<>();
    private final List<String> warnMessages = new CopyOnWriteArrayList<>();

    @BeforeEach
    void attachLogCapture() {
        GenAiObservabilityDiagnostics.resetForTesting();
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
    }

    @Test
    void shouldLogInfoOnceForMissingImplementation() {
        GenAiObservabilityDiagnostics.warnMissingImplementation(context);
        GenAiObservabilityDiagnostics.warnMissingImplementation(context);

        assertThat(infoMessages).hasSize(1);
        assertThat(infoMessages.get(0)).contains("camel-ai-observability is not on the classpath");
        assertThat(warnMessages).isEmpty();
    }

    @Test
    void shouldLogWarnOnceForMissingImplementationWhenExplicitlyEnabled() {
        Properties properties = new Properties();
        properties.setProperty(GenAiObservabilityProperties.ENABLED, "true");
        context.getPropertiesComponent().setOverrideProperties(properties);

        GenAiObservabilityDiagnostics.warnMissingImplementation(context);
        GenAiObservabilityDiagnostics.warnMissingImplementation(context);

        assertThat(warnMessages).hasSize(1);
        assertThat(warnMessages.get(0)).contains("camel-ai-observability is not on the classpath");
        assertThat(infoMessages).isEmpty();
    }

    @Test
    void shouldLogInfoOnceForObservationWithoutTracingHandler() {
        GenAiObservabilityDiagnostics.warnObservationWithoutTracingHandler(context);
        GenAiObservabilityDiagnostics.warnObservationWithoutTracingHandler(context);

        assertThat(infoMessages).hasSize(1);
        assertThat(infoMessages.get(0)).contains("ObservationRegistry without a tracing handler");
        assertThat(warnMessages).isEmpty();
    }

    @Test
    void shouldDetectExplicitlyEnabledProperty() {
        assertThat(GenAiObservabilityDiagnostics.isExplicitlyEnabled(context)).isFalse();

        Properties properties = new Properties();
        properties.setProperty(GenAiObservabilityProperties.ENABLED, "true");
        context.getPropertiesComponent().setOverrideProperties(properties);

        assertThat(GenAiObservabilityDiagnostics.isExplicitlyEnabled(context)).isTrue();
    }
}
