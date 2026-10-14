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
package org.apache.camel.semantic;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.console.DevConsole;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.language.semantic.SemanticLanguage;
import org.apache.camel.semantic.SemanticExpert.InputType;
import org.apache.camel.semantic.SemanticExpert.ResultType;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticMetadataConsoleTest {
    @ParameterizedTest
    @ValueSource(ints = { 0, 1, 2 })
    void overviewListsRegisteredExpertsWithoutSelectingADefault(int expertCount) throws Exception {
        try (var context = new DefaultCamelContext()) {
            for (int i = 0; i < expertCount; i++) {
                context.getRegistry().bind("security" + i, new Detector());
            }
            context.start();
            DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("semantic-metadata");
            JsonObject response = (JsonObject) console.call(DevConsole.MediaType.JSON, Map.of("overview", true));
            assertThat(response).doesNotContainKeys("defaultExpert", "defaultError", "defaultErrorCode");
            List<JsonObject> experts = response.getCollection("experts");
            assertThat(experts).hasSize(expertCount);
            assertThat(operations(console, Map.of())).isEmpty();
        }
    }

    @Test
    void overviewReportsAnInvalidRegisteredContractAlongsideValidExperts() throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("security", new Detector());
            context.getRegistry().bind("invalid", new InvalidDetector());
            context.start();
            DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("semantic-metadata");
            JsonObject response = (JsonObject) console.call(DevConsole.MediaType.JSON, Map.of("overview", true));
            List<JsonObject> experts = response.getCollection("experts");
            assertThat(experts).hasSize(2);
            assertThat(experts.get(0)).containsEntry("reference", "invalid");
            assertThat(experts.get(0).getString("error")).isNotBlank();
            assertThat(experts.get(1)).containsEntry("reference", "security").doesNotContainKey("error");
        }
    }

    @Test
    void inspectionAndEvaluationErrorsUseTheResolvedExpertName() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var language = (SemanticLanguage) context.resolveLanguage("semantic");
            context.getRegistry().bind("security", new Detector());
            Properties properties = new Properties();
            properties.setProperty("expert.name", "security");
            context.getPropertiesComponent().setInitialProperties(properties);
            assertThat(language.describeExpert("{{expert.name}}").reference()).isEqualTo("security");
            context.getRegistry().unbind("security");
            assertThatThrownBy(() -> language.describeExpert("{{expert.name}}"))
                    .hasMessage("Unknown expert or bean does not implement SemanticAdapter: security");
            assertThatThrownBy(() -> language.evaluate(
                    new SemanticEvaluation("injection", "{{expert.name}}", null, Map.of()), "test"))
                    .hasStackTraceContaining("Unknown expert or bean does not implement SemanticAdapter: security")
                    .hasStackTraceContaining("expert 'security'");
        }
    }

    @Test
    void namedAndPlaceholderExpertsExposeTheirOwnOperations() throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("security", new Detector());
            context.getRegistry().bind("decisions", new TestSemanticAdapter() {
                @Override
                public SemanticResult evaluate(SemanticEvaluation evaluation, Object state) {
                    throw new AssertionError("Metadata must not perform inference");
                }
            });
            Properties properties = new Properties();
            properties.setProperty("selected.expert", "security");
            context.getPropertiesComponent().setInitialProperties(properties);
            context.start();
            DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("semantic-metadata");
            assertThat(operations(console, Map.of("expert", "security")))
                    .extracting(op -> op.getString("name")).containsExactly("injection");
            assertThat(operations(console, Map.of("expert", "{{selected.expert}}")))
                    .extracting(op -> op.getString("name")).containsExactly("injection");
            assertThat(operations(console, Map.of("expert", "decisions"))).extracting(op -> op.getString("name"))
                    .containsExactly("boolean", "choice", "score");
            assertThat(operations(console, Map.of("expert", "security")).get(0).getJsonObject("contract"))
                    .doesNotContainKey("scoreLevelsParameter");
            assertThat(operations(console, Map.of("expert", "missing"))).isEmpty();
            assertThat(operations(console, Map.of("expert", "{{missing}}"))).isEmpty();
            context.getRegistry().unbind("decisions");
            assertThat(operations(console, Map.of())).isEmpty();
        }
    }

    private static List<JsonObject> operations(DevConsole console, Map<String, Object> options) {
        JsonObject answer = (JsonObject) console.call(DevConsole.MediaType.JSON, options);
        return answer.getCollection("operations");
    }

    @SemanticExpert(name = "detector", description = "Injection detector", provider = "test", artifactId = "test",
                    operations = @SemanticOperation(name = "injection", description = "Detect injection",
                                                    inputTypes = InputType.TEXT, inputRequirements = "Text",
                                                    resultType = ResultType.BOOLEAN, resultMeaning = "Injection detected"))
    public static class Detector implements SemanticAdapter {
        @Override
        public void validate(SemanticEvaluation evaluation) {
            throw new AssertionError("Metadata must not validate a declaration");
        }

        @Override
        public SemanticResult evaluate(SemanticEvaluation evaluation, Object state) {
            throw new AssertionError("Metadata must not perform inference");
        }
    }

    @SemanticExpert(name = "invalid", description = "Invalid contract", provider = "test", artifactId = "test",
                    operations = {})
    public static class InvalidDetector extends Detector {
    }
}
