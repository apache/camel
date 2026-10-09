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

class SemanticScoreMetadataTest {
    @Test
    void discoversAndPublishesScoreLevelsWithoutConstructingAnExpert() throws Exception {
        var operation = SemanticCapabilities.from(OrderedExpert.class).operation("rate");
        assertThat(operation.getScoreLevelsParameter()).isEqualTo("rubric");
        try (var context = new DefaultCamelContext()) {
            var language = (SemanticLanguage) context.resolveLanguage("semantic");
            language.setAdapter(OrderedExpert.class.getName());
            context.start();
            DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("semantic-metadata");
            JsonObject response = (JsonObject) console.call(DevConsole.MediaType.JSON, Map.of());
            List<JsonObject> operations = response.getCollection("operations");
            assertThat(operations).hasSize(1);
            assertThat(operations.get(0).getJsonObject("contract")).containsEntry("scoreLevelsParameter", "rubric");
            assertThat(context.getRegistry().lookupByName(SemanticLanguage.ADAPTER_NAME)).isNull();
        }
    }

    @ParameterizedTest
    @ValueSource(classes = { MissingParameter.class, WrongParameterType.class, WrongItemType.class, WrongResultType.class })
    void rejectsInvalidScoreLevelRelationships(Class<?> expert) {
        assertThatThrownBy(() -> SemanticCapabilities.from(expert))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Score levels require");
    }

    @SemanticExpert(name = "ordered", description = "Ordered score expert", provider = "test", artifactId = "test",
                    operations = @SemanticOperation(name = "rate", description = "Rate the input",
                                                    inputTypes = InputType.TEXT, inputRequirements = "Text",
                                                    resultType = ResultType.SCORE, resultMeaning = "Position on the rubric",
                                                    scoreLevelsParameter = "rubric",
                                                    parameters = @SemanticParameter(name = "rubric", description = "Levels",
                                                                                    type = List.class, itemType = String.class,
                                                                                    required = true, minSize = 1)))
    public static class OrderedExpert implements SemanticAdapter {
        public OrderedExpert() {
            throw new AssertionError("Metadata must not construct the expert");
        }

        @Override
        public void validate(SemanticEvaluation evaluation) {
            throw new AssertionError("Metadata must not validate an evaluation");
        }

        @Override
        public SemanticResult evaluate(SemanticEvaluation evaluation, Object state) {
            throw new AssertionError("Metadata must not call the expert");
        }
    }

    @SemanticExpert(name = "invalid", description = "Invalid expert", provider = "test", artifactId = "test",
                    operations = @SemanticOperation(name = "rate", description = "Rate the input",
                                                    inputTypes = InputType.TEXT, inputRequirements = "Text",
                                                    resultType = ResultType.SCORE, resultMeaning = "Invalid",
                                                    scoreLevelsParameter = "rubric"))
    private static class MissingParameter {
    }

    @SemanticExpert(name = "invalid", description = "Invalid expert", provider = "test", artifactId = "test",
                    operations = @SemanticOperation(name = "rate", description = "Rate the input",
                                                    inputTypes = InputType.TEXT, inputRequirements = "Text",
                                                    resultType = ResultType.SCORE, resultMeaning = "Invalid",
                                                    scoreLevelsParameter = "rubric",
                                                    parameters = @SemanticParameter(name = "rubric", description = "Levels",
                                                                                    required = true)))
    private static class WrongParameterType {
    }

    @SemanticExpert(name = "invalid", description = "Invalid expert", provider = "test", artifactId = "test",
                    operations = @SemanticOperation(name = "rate", description = "Rate the input",
                                                    inputTypes = InputType.TEXT, inputRequirements = "Text",
                                                    resultType = ResultType.SCORE, resultMeaning = "Invalid",
                                                    scoreLevelsParameter = "rubric",
                                                    parameters = @SemanticParameter(name = "rubric", description = "Levels",
                                                                                    type = List.class, itemType = Number.class,
                                                                                    required = true)))
    private static class WrongItemType {
    }

    @SemanticExpert(name = "invalid", description = "Invalid expert", provider = "test", artifactId = "test",
                    operations = @SemanticOperation(name = "rate", description = "Rate the input",
                                                    inputTypes = InputType.TEXT, inputRequirements = "Text",
                                                    resultType = ResultType.CHOICE, resultMeaning = "Invalid",
                                                    scoreLevelsParameter = "rubric",
                                                    parameters = @SemanticParameter(name = "rubric", description = "Levels",
                                                                                    type = List.class, itemType = String.class,
                                                                                    required = true)))
    private static class WrongResultType {
    }
}
