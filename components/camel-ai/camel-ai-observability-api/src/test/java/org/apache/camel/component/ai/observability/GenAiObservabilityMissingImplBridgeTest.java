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
import java.util.concurrent.ConcurrentMap;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.ExchangeTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link GenAiObservability#start} triggers missing-implementation diagnostics when the bridge cannot resolve
 * {@code GenAiObservabilityImpl}.
 */
class GenAiObservabilityMissingImplBridgeTest extends ExchangeTestSupport {

    @AfterEach
    void resetBridgeCache() {
        GenAiObservability.resetBridgeForTesting();
        GenAiObservabilityDiagnostics.resetForTesting();
    }

    @Test
    void shouldWarnWhenImplementationBridgeIsUnavailable() throws Exception {
        GenAiObservabilityDiagnostics.resetForTesting();
        try (LogCapture capture = LogCapture.attach(GenAiObservabilityDiagnostics.class)) {
            installUnavailableBridge();

            Exchange exchange = new DefaultExchange(context);
            GenAiObservationContext observationContext = GenAiObservationContext.builder()
                    .operationName(GenAiOperationName.CHAT)
                    .requestModel("gpt-4o")
                    .build();
            GenAiObservability.start(exchange, observationContext).close();
            GenAiObservability.start(exchange, observationContext).close();

            assertThat(capture.infoMessages()).hasSize(1);
            assertThat(capture.infoMessages().get(0)).contains("camel-ai-observability is not on the classpath");
        }
    }

    @Test
    void shouldWarnWhenImplementationBridgeIsUnavailableAndExplicitlyEnabled() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(GenAiObservabilityProperties.ENABLED, "true");
        context.getPropertiesComponent().setOverrideProperties(properties);

        try (LogCapture capture = LogCapture.attach(GenAiObservabilityDiagnostics.class)) {
            installUnavailableBridge();

            Exchange exchange = new DefaultExchange(context);
            GenAiObservationContext observationContext = GenAiObservationContext.builder()
                    .operationName(GenAiOperationName.CHAT)
                    .requestModel("gpt-4o")
                    .build();
            GenAiObservability.start(exchange, observationContext).close();
            GenAiObservability.start(exchange, observationContext).close();

            assertThat(capture.warnMessages()).hasSize(1);
            assertThat(capture.warnMessages().get(0)).contains("camel-ai-observability is not on the classpath");
            assertThat(capture.infoMessages()).isEmpty();
        }
    }

    @Test
    void shouldNotWarnWhenObservabilityDisabledAndBridgeUnavailable() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(GenAiObservabilityProperties.ENABLED, "false");
        context.getPropertiesComponent().setOverrideProperties(properties);

        GenAiObservabilityDiagnostics.resetForTesting();
        try (LogCapture capture = LogCapture.attach(GenAiObservabilityDiagnostics.class)) {
            installUnavailableBridge();

            Exchange exchange = new DefaultExchange(context);
            GenAiObservability.start(exchange, GenAiObservationContext.builder()
                    .operationName(GenAiOperationName.CHAT)
                    .requestModel("gpt-4o")
                    .build()).close();

            assertThat(capture.infoMessages()).isEmpty();
            assertThat(capture.warnMessages()).isEmpty();
        }
    }

    private void installUnavailableBridge() throws Exception {
        var bridgesField = GenAiObservability.class.getDeclaredField("BRIDGES");
        bridgesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentMap<CamelContext, Object> bridges
                = (ConcurrentMap<CamelContext, Object>) bridgesField.get(null);
        bridges.clear();

        var unavailableBridgeField = GenAiObservability.class.getDeclaredField("UNAVAILABLE_BRIDGE");
        unavailableBridgeField.setAccessible(true);
        bridges.put(context, unavailableBridgeField.get(null));
    }
}
