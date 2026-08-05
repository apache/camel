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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenAiModelResolverTest {

    @Test
    void shouldResolveOpenAiProvider() {
        assertThat(GenAiModelResolver.resolveSystem(new FakeOpenAiModel())).isEqualTo("openai");
    }

    @Test
    void shouldResolveOllamaProvider() {
        assertThat(GenAiModelResolver.resolveSystem(new FakeOllamaModel())).isEqualTo("ollama");
    }

    @Test
    void shouldResolveModelNameFromMethod() {
        assertThat(GenAiModelResolver.resolveModelName(new FakeOpenAiModel())).isEqualTo("gpt-4o");
    }

    @Test
    void shouldReturnUnknownForNullModel() {
        assertThat(GenAiModelResolver.resolveSystem(null)).isEqualTo("unknown");
        assertThat(GenAiModelResolver.resolveModelName(null)).isEqualTo("unknown");
    }

    static class FakeOpenAiModel {
        public String modelName() {
            return "gpt-4o";
        }
    }

    static class FakeOllamaModel {
        public String getModelName() {
            return "llama3";
        }
    }
}
