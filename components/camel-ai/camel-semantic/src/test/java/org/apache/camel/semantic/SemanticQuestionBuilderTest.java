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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.apache.camel.semantic.SemanticQuestionsBuilder.semanticQuestions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticQuestionBuilderTest {
    static Stream<Consumer<SemanticQuestionBuilder>> conflicts() {
        return Stream.of(
                b -> b.criterion("a", "A").level("B"),
                b -> b.level("B").criterion("a", "A"),
                b -> b.parameter("criteria", Map.of("a", "A")).criterion("b", "B"),
                b -> b.criterion("b", "B").parameter("criteria", Map.of("a", "A")),
                b -> b.parameter("criteria", Map.of("a", "A")).level("B"));
    }

    @ParameterizedTest
    @MethodSource("conflicts")
    void criteriaConflictsHaveDeclarationContext(Consumer<SemanticQuestionBuilder> configure) throws Exception {
        try (var context = new DefaultCamelContext()) {
            assertThatThrownBy(() -> context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    var question = semanticQuestions(this).expert("selected").evaluation("q").operation("choice");
                    configure.accept(question);
                    question.register();
                }
            })).hasMessageContaining("'q'").hasMessageContaining("expert 'selected'")
                    .hasMessageContaining("Duplicate parameter 'criteria'");
        }
    }

    @Test
    void callerCriteriaAreNeverMutated() throws Exception {
        try (var context = new DefaultCamelContext()) {
            Map<String, String> supplied = new LinkedHashMap<>(Map.of("a", "A"));
            var builder = new SemanticQuestionBuilder().operation("choice").parameter("criteria", supplied);
            builder.criterion("b", "B");
            assertThatThrownBy(() -> builder.build(context)).hasMessageContaining("Duplicate parameter 'criteria'");
            assertThat(supplied).containsExactlyEntriesOf(Map.of("a", "A"));
        }
    }

    @Test
    void xmlCriteriaConflictsAndStructuralErrorsNameTheBlockExpert() throws Exception {
        try (var context = new DefaultCamelContext()) {
            for (String children : new String[] {
                    "<criterion key=\"a\" value=\"A\"/><level>B</level>",
                    "<parameters><parameter name=\"criteria\"><list><string>A</string></list></parameter></parameters><level>B</level>",
                    "<unknown/>" }) {
                assertThatThrownBy(() -> PluginHelper.getRoutesLoader(context)
                        .loadRoutes(ResourceHelper.fromString("invalid.xml",
                                "<semantic expert=\"selected\"><evaluation name=\"q\" operation=\"choice\">" + children
                                                                             + "</evaluation></semantic>")))
                        .hasStackTraceContaining("'q'").hasStackTraceContaining("expert 'selected'");
            }
        }
    }

    @Test
    void policyPlaceholdersResolveBeforeShorthandNormalization() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var properties = new Properties();
            properties.setProperty("myPolicy", "NON_MATCH");
            context.getPropertiesComponent().setInitialProperties(properties);
            var shorthand = new SemanticQuestionBuilder().operation("boolean").uncertaintyPolicy("{{myPolicy}}")
                    .build(context);
            var generic = new SemanticQuestionBuilder().operation("custom").parameter("uncertaintyPolicy", "{{myPolicy}}")
                    .build(context);
            assertThat(shorthand.getParameters()).containsEntry("uncertaintyPolicy", "non-match");
            assertThat(generic.getParameters()).containsEntry("uncertaintyPolicy", "NON_MATCH");
        }
    }

    @Test
    void nullPolicyIsRejectedWithContractContext() throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("fixed", new FixedSemanticExpert());
            context.start();
            assertThatThrownBy(() -> context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    semanticQuestions(this).expert("fixed").evaluation("q").operation("boolean")
                            .uncertaintyPolicy(null).register();
                }
            })).hasMessageContaining("'q'").hasMessageContaining("'fixed'")
                    .hasMessageContaining("uncertaintyPolicy").hasMessageContaining("must not be null");
        }
    }
}
