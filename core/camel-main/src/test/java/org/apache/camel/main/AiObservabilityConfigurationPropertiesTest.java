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
package org.apache.camel.main;

import org.apache.camel.PropertyBindingException;
import org.apache.camel.component.ai.observability.GenAiObservability;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.OrderedLocationProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiObservabilityConfigurationPropertiesTest {

    @Test
    void shouldDisableGenAiObservabilityViaMainConfiguration() throws Exception {
        Main main = new Main();
        main.configure().aiObservability().withEnabled(false);

        main.start();

        try {
            assertThat(GenAiObservability.isEnabled(main.getCamelContext())).isFalse();
        } finally {
            main.stop();
        }
    }

    @Test
    void shouldDisableGenAiObservabilityViaApplicationProperties() throws Exception {
        Main main = new Main();
        main.setDefaultPropertyPlaceholderLocation("classpath:ai-observability.properties");

        main.start();

        try {
            assertThat(GenAiObservability.isEnabled(main.getCamelContext())).isFalse();
        } finally {
            main.stop();
        }
    }

    @Test
    void shouldEnableGenAiObservabilityByDefault() throws Exception {
        Main main = new Main();
        main.start();

        try {
            assertThat(GenAiObservability.isEnabled(main.getCamelContext())).isTrue();
        } finally {
            main.stop();
        }
    }

    @Test
    void shouldLeaveUnknownAiObservabilityPropertyWhenFailFastIsDisabled() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        MainConfigurationProperties mainConfig = new MainConfigurationProperties();
        AiObservabilityConfigurationProperties config = mainConfig.aiObservability();
        OrderedLocationProperties properties = new OrderedLocationProperties();
        properties.put("test", "enable", "false");
        OrderedLocationProperties autoConfiguredProperties = new OrderedLocationProperties();

        MainHelper.setPropertiesOnTarget(context, config, properties, "camel.aiObservability.",
                false, true, autoConfiguredProperties);

        assertThat(properties.asMap()).containsEntry("enable", "false");
        assertThat(autoConfiguredProperties.asMap()).doesNotContainKey("camel.aiObservability.enable");
    }

    @Test
    void shouldLeaveOnlyUnknownAiObservabilityPropertyWhenKnownPropertyIsPresent() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        MainConfigurationProperties mainConfig = new MainConfigurationProperties();
        AiObservabilityConfigurationProperties config = mainConfig.aiObservability();
        OrderedLocationProperties properties = new OrderedLocationProperties();
        properties.put("test", "enabled", "false");
        properties.put("test", "enable", "true");
        OrderedLocationProperties autoConfiguredProperties = new OrderedLocationProperties();

        MainHelper.setPropertiesOnTarget(context, config, properties, "camel.aiObservability.",
                false, true, autoConfiguredProperties);

        assertThat(properties.asMap()).containsEntry("enable", "true");
        assertThat(config.isEnabled()).isFalse();
        assertThat(autoConfiguredProperties.asMap()).containsEntry("camel.aiObservability.enabled", "false");
    }

    @Test
    void shouldFailFastWhenUnknownAiObservabilityPropertyIsPresent() {
        Main main = new Main();
        try {
            main.addInitialProperty("camel.aiObservability.enable", "true");
            assertThatThrownBy(main::start)
                    .hasRootCauseInstanceOf(PropertyBindingException.class)
                    .rootCause()
                    .hasMessageContaining("enable");
        } finally {
            main.stop();
        }
    }

    @Test
    void shouldIgnoreUnknownAiObservabilityPropertyWhenFailFastIsDisabled() throws Exception {
        Main main = new Main();
        main.configure().withAutoConfigurationFailFast(false);
        main.addInitialProperty("camel.aiObservability.enabled", "false");
        main.addInitialProperty("camel.aiObservability.enable", "true");

        main.start();

        try {
            assertThat(GenAiObservability.isEnabled(main.getCamelContext())).isFalse();
        } finally {
            main.stop();
        }
    }

    @Test
    void shouldIgnoreUnknownAiObservabilityPropertyFromPropertiesFileWhenFailFastIsDisabled() throws Exception {
        Main main = new Main();
        main.configure().withAutoConfigurationFailFast(false);
        main.setDefaultPropertyPlaceholderLocation("classpath:ai-observability-unknown.properties");

        main.start();

        try {
            assertThat(GenAiObservability.isEnabled(main.getCamelContext())).isTrue();
        } finally {
            main.stop();
        }
    }

    @Test
    void shouldConsumeKnownAiObservabilityPropertyWithoutLeavingItForWarning() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        MainConfigurationProperties mainConfig = new MainConfigurationProperties();
        AiObservabilityConfigurationProperties config = mainConfig.aiObservability();
        OrderedLocationProperties properties = new OrderedLocationProperties();
        properties.put("test", "enabled", "false");
        OrderedLocationProperties autoConfiguredProperties = new OrderedLocationProperties();

        MainHelper.setPropertiesOnTarget(context, config, properties, "camel.aiObservability.",
                false, true, autoConfiguredProperties);

        assertThat(properties.asMap()).isEmpty();
        assertThat(config.isEnabled()).isFalse();
        assertThat(autoConfiguredProperties.asMap()).containsEntry("camel.aiObservability.enabled", "false");
    }
}
