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
package org.apache.camel.component.openai;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAIJavaAgentTracingTest {

    private static final List<String> WITH_AGENT = List.of("-Xmx512m", "-javaagent:/opt/opentelemetry-javaagent.jar");

    @Test
    void shouldBeActiveWhenTheAgentIsAttached() {
        assertThat(OpenAIJavaAgentTracing.isActive(WITH_AGENT, key -> null)).isTrue();
    }

    @Test
    void shouldBeInactiveWithoutTheAgent() {
        assertThat(OpenAIJavaAgentTracing.isActive(List.of("-Xmx512m"), key -> null)).isFalse();
        assertThat(OpenAIJavaAgentTracing.isActive(List.of("-javaagent:/opt/other-agent.jar"), key -> null)).isFalse();
    }

    @Test
    void shouldFollowTheAgentSettings() {
        assertThat(OpenAIJavaAgentTracing.isActive(WITH_AGENT,
                Map.of("otel.instrumentation.openai-java.enabled", "false")::get)).isFalse();
        assertThat(OpenAIJavaAgentTracing.isActive(WITH_AGENT,
                Map.of("otel.instrumentation.common.default-enabled", "false")::get)).isFalse();
        assertThat(OpenAIJavaAgentTracing.isActive(WITH_AGENT,
                Map.of("otel.instrumentation.common.default-enabled", "false",
                        "otel.instrumentation.openai-java.enabled", "true")::get))
                .isTrue();
        assertThat(OpenAIJavaAgentTracing.isActive(WITH_AGENT, Map.of("otel.javaagent.enabled", "false")::get))
                .isFalse();
    }
}
