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

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.ExchangeTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenAiObservabilityTest extends ExchangeTestSupport {

    @Test
    void shouldBeEnabledByDefault() {
        assertThat(GenAiObservability.isEnabled(context)).isTrue();
    }

    @Test
    void shouldDisableWhenPropertyIsFalse() {
        Properties properties = new Properties();
        properties.setProperty(GenAiObservabilityProperties.ENABLED, "false");
        context.getPropertiesComponent().setOverrideProperties(properties);
        assertThat(GenAiObservability.isEnabled(context)).isFalse();
    }

    @Test
    void shouldDisableWhenDashStylePropertyIsFalse() {
        Properties properties = new Properties();
        properties.setProperty(GenAiObservabilityProperties.ENABLED_DASH, "false");
        context.getPropertiesComponent().setOverrideProperties(properties);
        assertThat(GenAiObservability.isEnabled(context)).isFalse();
    }

    @Test
    void shouldRecordMicrometerMetricsWhenRegistryPresent() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        context.getRegistry().bind("metricsRegistry", registry);

        Exchange exchange = new DefaultExchange(context);
        GenAiObservationContext observationContext = GenAiObservationContext.builder()
                .operationName(GenAiOperationName.CHAT)
                .system("openai")
                .requestModel("gpt-4o")
                .componentScheme("langchain4j-chat")
                .build();

        GenAiObservation observation = GenAiObservability.start(exchange, observationContext);
        observation.recordSuccess(GenAiUsage.of(10, 5, "stop", "gpt-4o"));
        observation.close();

        assertThat(registry.find(GenAiMetrics.CLIENT_OPERATION).timer()).isNotNull();
        assertThat(registry.find(GenAiMetrics.CLIENT_TOKEN_USAGE)
                .tag(GenAiMetrics.TAG_TOKEN_TYPE, GenAiMetrics.TOKEN_TYPE_INPUT)
                .counter()
                .count()).isEqualTo(10);
        assertThat(registry.find(GenAiMetrics.CLIENT_TOKEN_USAGE)
                .tag(GenAiMetrics.TAG_TOKEN_TYPE, GenAiMetrics.TOKEN_TYPE_OUTPUT)
                .counter()
                .count()).isEqualTo(5);
    }

    @Test
    void shouldReturnNoopWhenDisabled() {
        Properties properties = new Properties();
        properties.setProperty(GenAiObservabilityProperties.ENABLED, "false");
        context.getPropertiesComponent().setOverrideProperties(properties);

        Exchange exchange = new DefaultExchange(context);
        GenAiObservation observation = GenAiObservability.start(exchange, GenAiObservationContext.builder()
                .operationName(GenAiOperationName.CHAT)
                .requestModel("gpt-4o")
                .build());

        observation.recordSuccess(GenAiUsage.of(1, 1, "stop", "gpt-4o"));
        observation.close();

        assertThat(registryBeanCount(context)).isZero();
    }

    private static int registryBeanCount(CamelContext context) {
        SimpleMeterRegistry registry = context.getRegistry().lookupByNameAndType("metricsRegistry", SimpleMeterRegistry.class);
        if (registry == null) {
            return 0;
        }
        return registry.getMeters().size();
    }
}
