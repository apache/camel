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

import org.apache.camel.test.junit6.ExchangeTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenAiObservabilityDiagnosticsTest extends ExchangeTestSupport {

    private LogCapture capture;

    @BeforeEach
    void resetDiagnostics() {
        GenAiObservabilityDiagnostics.resetForTesting();
        capture = LogCapture.attach(GenAiObservabilityDiagnostics.class);
    }

    @AfterEach
    void detachLogCapture() {
        if (capture != null) {
            capture.close();
        }
        GenAiObservabilityDiagnostics.resetForTesting();
    }

    @Test
    void shouldLogInfoOnceForMissingImplementation() {
        GenAiObservabilityDiagnostics.warnMissingImplementation(context);
        GenAiObservabilityDiagnostics.warnMissingImplementation(context);

        assertThat(capture.infoMessages()).hasSize(1);
        assertThat(capture.infoMessages().get(0)).contains("camel-ai-observability is not on the classpath");
        assertThat(capture.warnMessages()).isEmpty();
    }

    @Test
    void shouldLogWarnOnceForMissingImplementationWhenExplicitlyEnabled() {
        Properties properties = new Properties();
        properties.setProperty(GenAiObservabilityProperties.ENABLED, "true");
        context.getPropertiesComponent().setOverrideProperties(properties);

        GenAiObservabilityDiagnostics.warnMissingImplementation(context);
        GenAiObservabilityDiagnostics.warnMissingImplementation(context);

        assertThat(capture.warnMessages()).hasSize(1);
        assertThat(capture.warnMessages().get(0)).contains("camel-ai-observability is not on the classpath");
        assertThat(capture.infoMessages()).isEmpty();
    }

    @Test
    void shouldLogInfoOnceForObservationWithoutTracingHandler() {
        GenAiObservabilityDiagnostics.warnObservationWithoutTracingHandler(context);
        GenAiObservabilityDiagnostics.warnObservationWithoutTracingHandler(context);

        assertThat(capture.infoMessages()).hasSize(1);
        assertThat(capture.infoMessages().get(0)).contains("ObservationRegistry without a tracing handler");
        assertThat(capture.warnMessages()).isEmpty();
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
