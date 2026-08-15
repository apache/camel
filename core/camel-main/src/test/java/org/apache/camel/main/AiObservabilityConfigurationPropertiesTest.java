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

import org.apache.camel.component.ai.observability.GenAiObservability;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiObservabilityConfigurationPropertiesTest {

    @Test
    void shouldDisableGenAiObservabilityViaMainConfiguration() throws Exception {
        Main main = new Main();
        main.configure().ai().observability().withEnabled(false);

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
}
