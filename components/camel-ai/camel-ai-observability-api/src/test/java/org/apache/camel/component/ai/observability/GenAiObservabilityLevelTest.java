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

import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenAiObservabilityLevelTest {

    @Test
    void shouldParseLevelNamesIgnoringCase() {
        assertThat(GenAiObservabilityLevel.parse("basic")).isEqualTo(GenAiObservabilityLevel.BASIC);
        assertThat(GenAiObservabilityLevel.parse(" Detailed ")).isEqualTo(GenAiObservabilityLevel.DETAILED);
        assertThat(GenAiObservabilityLevel.parse("FULL")).isEqualTo(GenAiObservabilityLevel.FULL);
        assertThat(GenAiObservabilityLevel.parse(null)).isEqualTo(GenAiObservabilityLevel.BASIC);
        assertThat(GenAiObservabilityLevel.parse("")).isEqualTo(GenAiObservabilityLevel.BASIC);
    }

    @Test
    void shouldRejectUnknownLevel() {
        assertThatThrownBy(() -> GenAiObservabilityLevel.parse("verbose"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("verbose")
                .hasMessageContaining("basic, detailed, full");
    }

    @Test
    void shouldIncludeLowerLevels() {
        assertThat(GenAiObservabilityLevel.FULL.includes(GenAiObservabilityLevel.DETAILED)).isTrue();
        assertThat(GenAiObservabilityLevel.DETAILED.includes(GenAiObservabilityLevel.BASIC)).isTrue();
        assertThat(GenAiObservabilityLevel.DETAILED.includes(GenAiObservabilityLevel.FULL)).isFalse();
        assertThat(GenAiObservabilityLevel.BASIC.includes(GenAiObservabilityLevel.DETAILED)).isFalse();
    }

    @Test
    void shouldResolveLevelFromProperties() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            assertThat(GenAiObservability.level(context)).isEqualTo(GenAiObservabilityLevel.BASIC);
            assertThat(GenAiObservability.contentMaxLength(context))
                    .isEqualTo(GenAiObservabilityProperties.DEFAULT_CONTENT_MAX_LENGTH);

            context.getPropertiesComponent().addOverrideProperty(GenAiObservabilityProperties.LEVEL, "full");
            context.getPropertiesComponent().addOverrideProperty(GenAiObservabilityProperties.CONTENT_MAX_LENGTH, "100");

            assertThat(GenAiObservability.level(context)).isEqualTo(GenAiObservabilityLevel.FULL);
            assertThat(GenAiObservability.isLevel(context, GenAiObservabilityLevel.DETAILED)).isTrue();
            assertThat(GenAiObservability.contentMaxLength(context)).isEqualTo(100);
        }
    }

    @Test
    void shouldUseTheDefaultContentMaxLengthWhenNotPositive() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.getPropertiesComponent().addOverrideProperty(GenAiObservabilityProperties.CONTENT_MAX_LENGTH, "0");

            assertThat(GenAiObservability.contentMaxLength(context))
                    .isEqualTo(GenAiObservabilityProperties.DEFAULT_CONTENT_MAX_LENGTH);
        }
    }

    @Test
    void shouldFallBackToBasicForUnknownLevelOrWhenDisabled() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.getPropertiesComponent().addOverrideProperty(GenAiObservabilityProperties.LEVEL, "verbose");
            assertThat(GenAiObservability.level(context)).isEqualTo(GenAiObservabilityLevel.BASIC);

            context.getPropertiesComponent().addOverrideProperty(GenAiObservabilityProperties.LEVEL, "full");
            context.getPropertiesComponent().addOverrideProperty(GenAiObservabilityProperties.ENABLED, "false");
            assertThat(GenAiObservability.isLevel(context, GenAiObservabilityLevel.DETAILED)).isFalse();
        }
    }
}
