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

import org.apache.camel.dsl.yaml.common.YamlDeserializationContext;
import org.apache.camel.dsl.yaml.common.exception.YamlDeserializationException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.semantic.SemanticExpert.InputType;
import org.apache.camel.semantic.SemanticExpert.Instructions;
import org.apache.camel.semantic.SemanticExpert.ResultType;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.ResourceHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.lowlevel.Compose;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticExpertYamlTest {
    private static final String DECLARATIONS = """
            - semantic:
                question:
                  injection:
                    expert: "{{security.expert:security}}"
                    type: boolean
                    threshold: "{{security.threshold:0.9}}"
            """;

    @Test
    void initialPreParseAcceptsAnExpertRegisteredBeforeExpressionInitialization() throws Exception {
        try (var context = new DefaultCamelContext()) {
            preParse(context, DECLARATIONS);
            var expert = new FixedExpert();
            context.getRegistry().bind("security", expert);
            context.start();
            var expression = context.resolveLanguage("semantic").createExpression("ref:injection");
            var exchange = new DefaultExchange(context);
            exchange.getMessage().setBody("text");
            assertThat(expression.evaluate(exchange, Boolean.class)).isFalse();
            var question = SemanticQuestions.get(context).get("injection");
            assertThat(question.getExpert()).isEqualTo("{{security.expert:security}}");
            assertThat(question.getInstructions()).isNull();
            assertThat(expert.calls).isEqualTo(1);
        }
    }

    @Test
    void invalidReloadRetainsPreviousDeclarationAndReportsYamlLocation() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var expert = new FixedExpert();
            context.getRegistry().bind("security", expert);
            preParse(context, DECLARATIONS);
            context.start();
            var expression = context.resolveLanguage("semantic").createExpression("ref:injection");
            var question = SemanticQuestions.get(context).get("injection");
            assertThatThrownBy(() -> preParse(context,
                    DECLARATIONS.replace("type: boolean", "type: boolean\n        instructions: unsupported")))
                    .isInstanceOfSatisfying(YamlDeserializationException.class, error -> {
                        assertThat(error).hasMessageContaining("injection").hasMessageContaining("security")
                                .hasMessageContaining("Instructions are unsupported");
                        assertThat(error.getProblemMark()).hasValueSatisfying(mark -> {
                            assertThat(mark.getName()).isEqualTo("questions.yaml");
                            assertThat(mark.getLine()).isZero();
                        });
                    });
            assertThat(SemanticQuestions.get(context).get("injection")).isSameAs(question);
            assertThat(expert.calls).isZero();
            preParse(context, DECLARATIONS.replace("threshold: \"{{security.threshold:0.9}}\"", "threshold: 0.7"));
            var exchange = new DefaultExchange(context);
            exchange.getMessage().setBody("text");
            assertThat(expression.evaluate(exchange, Boolean.class)).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "threshold: -0.1", "threshold: invalid", "uncertaintyPolicy: invalid", "thresholdd: 0.9",
            "instructions: {invalid: shape}", "state: [invalid]", "type: score\n        criteria: {invalid: shape}" })
    void invalidDeclarationsNameTheirExpert(String invalid) throws Exception {
        try (var context = new DefaultCamelContext()) {
            String yaml = DECLARATIONS.replace("{{security.expert:security}}", "security")
                    .replace("threshold: \"{{security.threshold:0.9}}\"", invalid);
            if (invalid.startsWith("type:")) {
                yaml = yaml.replace("        type: boolean\n", "");
            }
            String declaration = yaml;
            assertThatThrownBy(() -> preParse(context, declaration))
                    .isInstanceOf(YamlDeserializationException.class)
                    .hasMessageContaining("semantic question 'injection'").hasMessageContaining("expert 'security'");
        }
    }

    @Test
    void reloadRequiresNewExpertsBeforePreParse() throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("security", new FixedExpert());
            preParse(context, DECLARATIONS);
            context.start();
            var expression = context.resolveLanguage("semantic").createExpression("ref:injection");
            var previous = SemanticQuestions.get(context).get("injection");
            String replacement = DECLARATIONS.replace("{{security.expert:security}}", "newSecurity");
            assertThatThrownBy(() -> preParse(context, replacement))
                    .isInstanceOf(YamlDeserializationException.class).hasMessageContaining("newSecurity");
            assertThat(SemanticQuestions.get(context).get("injection")).isSameAs(previous);
            context.getRegistry().bind("newSecurity", new FixedExpert());
            preParse(context, replacement);
            var exchange = new DefaultExchange(context);
            exchange.getMessage().setBody("text");
            assertThat(expression.evaluate(exchange, Boolean.class)).isFalse();
        }
    }

    @Test
    void unusedDeclarationsDoNotMakeAnUnchangedReloadFail() throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("security", new FixedExpert());
            context.getRegistry().bind("other", new FixedExpert());
            String declarations = DECLARATIONS + "      draft: {type: boolean}\n";
            preParse(context, declarations);
            context.start();
            context.resolveLanguage("semantic").createExpression("ref:injection");
            preParse(context, declarations);
            assertThat(SemanticQuestions.get(context).get("draft").getExpert()).isNull();
        }
    }

    private void preParse(DefaultCamelContext context, String yaml) throws Exception {
        var settings = LoadSettings.builder().setLabel("questions.yaml").build();
        try (var deserialization = new YamlDeserializationContext(settings)) {
            deserialization.setCamelContext(context);
            deserialization.setResource(ResourceHelper.fromString("questions.yaml", yaml));
            deserialization.start();
            deserialization.preParse(new Compose(settings).composeString(yaml).orElseThrow());
        }
    }

    @SemanticExpert(name = "fixed", description = "Fixed test expert", provider = "test", artifactId = "test",
                    inputTypes = InputType.TEXT, resultTypes = ResultType.BOOLEAN,
                    instructions = Instructions.UNSUPPORTED, callerDefinedCriteria = false, booleanProbability = true)
    private static class FixedExpert implements SemanticAdapter {
        private int calls;

        @Override
        public void validate(SemanticQuestion question) {
        }

        @Override
        public SemanticResult evaluate(SemanticQuestion question, Object state) {
            calls++;
            return new SemanticResult(null, 0.8, null, null, null);
        }
    }
}
