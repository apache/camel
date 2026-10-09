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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticMetadataConsoleTest {
    @Test
    void inspectionDoesNotSelectAnAdapterAndErrorsUseTheResolvedName() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var language = (SemanticLanguage) context.resolveLanguage("semantic");
            context.getRegistry().bind("security", new Detector());
            Properties properties = new Properties();
            properties.setProperty("adapter.name", "security");
            context.getPropertiesComponent().setInitialProperties(properties);
            language.setAdapter("{{adapter.name}}");
            assertThat(language.describeExpert(null).reference()).isEqualTo("security");
            assertThat(language).extracting("selectedAdapterName", "selectedAdapter").containsExactly(null, null);
            language.setAdapter(null);
            assertThat(language.describeExpert(null).reference()).isEqualTo("security");
            assertThat(language).extracting("selectedAdapterName", "selectedAdapter").containsExactly(null, null);
            context.getRegistry().unbind("security");
            language.setAdapter("{{adapter.name}}");
            assertThatThrownBy(() -> language.describeExpert(null))
                    .hasMessage("No semantic adapter bean or class found: security");
            assertThatThrownBy(() -> language.evaluate(
                    new SemanticEvaluation("injection", null, null, Map.of()), "test"))
                    .hasStackTraceContaining("No semantic adapter bean or class found: security")
                    .hasStackTraceContaining("security");
        }
    }

    @Test
    void namedDefaultAndPlaceholderExpertsExposeTheirOwnOperations() throws Exception {
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
            var language = (SemanticLanguage) context.resolveLanguage("semantic");
            language.setDefaultExpert("decisions");
            context.start();
            DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("semantic-metadata");
            assertThat(console).isNotNull();
            assertThat(operations(console, Map.of("expert", "security")))
                    .extracting(op -> op.getString("name")).containsExactly("injection");
            assertThat(operations(console, Map.of("expert", "{{selected.expert}}")))
                    .extracting(op -> op.getString("name")).containsExactly("injection");
            assertThat(operations(console, Map.of())).extracting(op -> op.getString("name"))
                    .containsExactly("boolean", "choice", "score");
            assertThat(operations(console, Map.of("expert", "security")).get(0).getJsonObject("contract"))
                    .doesNotContainKey("scoreLevelsParameter");
            assertThat(operations(console, Map.of("expert", "missing"))).isEmpty();
            assertThat(operations(console, Map.of("expert", "{{missing}}"))).isEmpty();
            language.setDefaultExpert(null);
            assertThat(operations(console, Map.of())).isEmpty();
            context.getRegistry().unbind("decisions");
            assertThat(operations(console, Map.of())).extracting(op -> op.getString("name")).containsExactly("injection");
            assertThat(operations(console, Map.of()).get(0)).containsEntry("resultType", "boolean")
                    .containsEntry("description", "Detect injection");
        }
    }

    @Test
    void inspectingAConfiguredClassDoesNotConstructItOrCallTheProvider() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var language = (SemanticLanguage) context.resolveLanguage("semantic");
            language.setAdapter(UnconstructedDetector.class.getName());
            context.start();
            DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("semantic-metadata");
            assertThat(operations(console, Map.of())).extracting(op -> op.getString("name")).containsExactly("injection");
            assertThat(context.getRegistry().lookupByName(SemanticLanguage.ADAPTER_NAME)).isNull();
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

    public static class UnconstructedDetector extends Detector {
        public UnconstructedDetector() {
            throw new AssertionError("Metadata must not construct a class-discovered expert");
        }
    }
}
