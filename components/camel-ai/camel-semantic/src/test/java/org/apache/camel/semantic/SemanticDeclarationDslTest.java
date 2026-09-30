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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.language.semantic.SemanticLanguage;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.RouteWatcherReloadStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.apache.camel.semantic.SemanticQuestionsBuilder.semanticQuestions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticDeclarationDslTest {
    @TempDir
    Path directory;
    private DefaultCamelContext context;
    private final List<Object> states = new ArrayList<>();

    @BeforeEach
    void setup() throws Exception {
        context = new DefaultCamelContext();
        context.getRegistry().bind("adapter", new SemanticAdapter() {
            @Override
            public void validate(SemanticQuestion question) {
            }

            @Override
            public SemanticResult evaluate(SemanticQuestion question, Object state) {
                states.add(state);
                return switch (question.getType()) {
                    case BOOLEAN -> new SemanticResult(null, 0.82, null, null, null);
                    case CHOICE -> new SemanticResult("billing", null, null, null, null);
                    case SCORE -> new SemanticResult(1.2, null, null, null, null);
                };
            }
        });
        ((SemanticLanguage) context.resolveLanguage("semantic")).setAdapter("adapter");
        context.start();
    }

    @AfterEach
    void close() throws Exception {
        context.close();
    }

    private static void questions(SemanticQuestionsBuilder semantic) {
        semantic.question("urgent").type("boolean").instructions("Is this urgent?")
                .state("${header.myState}").threshold(0.8).uncertainty(0.1).uncertaintyPolicy("non-match");
        semantic.question("department").type("choice").instructions("Which department?")
                .state("${header.myState}").criterion("billing", "Invoices and refunds")
                .criterion("technical", "Bugs and outages");
        semantic.question("priority").type("score").instructions("How urgent?")
                .state("${header.myState}").level("Routine").level("Urgent").level("Critical");
        semantic.register();
    }

    private static String xmlQuestions() {
        return """
                <semantic>
                  <question name="urgent" type="boolean" state="${header.myState}"
                            threshold="0.8" uncertainty="0.1" uncertaintyPolicy="non-match">
                    <instructions>Is this urgent?</instructions>
                  </question>
                  <question name="department" type="choice" state="${header.myState}">
                    <instructions>Which department?</instructions>
                    <criterion key="billing" value="Invoices and refunds"/>
                    <criterion key="technical" value="Bugs and outages"/>
                  </question>
                  <question name="priority" type="score" state="${header.myState}">
                    <instructions>How urgent?</instructions>
                    <level>Routine</level><level>Urgent</level><level>Critical</level>
                  </question>
                </semantic>
                """;
    }

    private static String document(String root, String contents) {
        return "<" + root + " xmlns=\"http://camel.apache.org/schema/spring\">" + contents + "</" + root + ">";
    }

    @ParameterizedTest
    @ValueSource(strings = { "java", "routes", "standalone", "no-namespace", "component-namespace", "xml-io-namespace" })
    void componentDeclarationsEvaluateSingleQuestionsAndMixedBatches(String dsl) throws Exception {
        if (dsl.equals("java")) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:batch").setProperty("decision").language("semantic", "refs:urgent,department,priority")
                            .setHeader("department", simple("${exchangeProperty.decision[department]}"));
                    // Declarations are collected before route initialization, including those after the route.
                    questions(semanticQuestions(this));
                }
            });
        } else {
            String route = """
                    <route>
                      <from uri="direct:batch"/>
                      <setProperty name="decision">
                        <language language="semantic">refs:urgent,department,priority</language>
                      </setProperty>
                      <setHeader name="department"><simple>${exchangeProperty.decision[department]}</simple></setHeader>
                    </route>
                    """;
            if (dsl.equals("standalone")) {
                PluginHelper.getRoutesLoader(context).loadRoutes(List.of(
                        ResourceHelper.fromString("use.xml", document("routes", route)),
                        ResourceHelper.fromString("questions.semantic.xml", xmlQuestions())));
            } else {
                String xml = document("routes", route + xmlQuestions());
                if (dsl.equals("no-namespace")) {
                    xml = xml.replace(" xmlns=\"http://camel.apache.org/schema/spring\"", "");
                } else if (dsl.equals("component-namespace")) {
                    xml = xml.replace("schema/spring", "schema/semantic");
                } else if (dsl.equals("xml-io-namespace")) {
                    xml = xml.replace("schema/spring", "schema/xml-io");
                }
                load("questions.semantic.xml", xml);
            }
        }
        assertThat(states).isEmpty();
        SemanticQuestion urgent = SemanticQuestions.get(context).get("urgent");
        assertThat(urgent.getThreshold()).isEqualTo(0.8);
        assertThat(urgent.getUncertaintyPolicy()).isEqualTo(SemanticQuestion.UncertaintyPolicy.NON_MATCH);
        assertThat(SemanticQuestions.get(context).get("priority").getLevels()).containsExactly("Routine", "Urgent", "Critical");
        try (var template = context.createProducerTemplate()) {
            Exchange exchange = template.request("direct:batch", e -> {
                e.getMessage().setBody("original");
                e.getMessage().setHeader("myState", "invoice");
            });
            assertThat(exchange.getException()).isNull();
            assertThat(exchange.getProperty("decision", Map.class))
                    .containsEntry("urgent", false).containsEntry("department", "billing").containsEntry("priority", 1.2);
            assertThat(exchange.getMessage().getHeader("department")).isEqualTo("billing");
            assertThat(exchange.getMessage().getBody()).isEqualTo("original");
            assertThat(exchange.getProperty(SemanticLanguage.RESULTS, Map.class)).hasSize(3);
            for (String name : List.of("urgent", "department", "priority")) {
                Object value
                        = context.resolveLanguage("semantic").createExpression("ref:" + name).evaluate(exchange, Object.class);
                assertThat(value).isEqualTo(exchange.getProperty("decision", Map.class).get(name));
            }
            assertThat(states).containsExactly("invoice", "invoice", "invoice", "invoice", "invoice", "invoice");
        }
    }

    @ParameterizedTest
    @MethodSource("invalidXml")
    void invalidDeclarationsLeaveThePreviousDefinitionsIntact(String declaration, String message) throws Exception {
        load("questions.semantic.xml", document("routes", xmlQuestions()));
        SemanticQuestion previous = SemanticQuestions.get(context).get("department");
        assertThatThrownBy(() -> PluginHelper.getRoutesLoader(context).updateRoutes(
                ResourceHelper.fromString("questions.semantic.xml",
                        document("routes", "<semantic>" + declaration + "</semantic>"))))
                .hasMessageContaining(message);
        assertThat(SemanticQuestions.get(context).get("department")).isSameAs(previous);
        assertThat(states).isEmpty();
        PluginHelper.getRoutesLoader(context).updateRoutes(ResourceHelper.fromString("questions.semantic.xml",
                document("routes", xmlQuestions().replace("header.myState", "header.corrected"))));
        assertThat(SemanticQuestions.get(context).get("department").getState()).isEqualTo("${header.corrected}");
    }

    static Stream<Arguments> invalidXml() {
        String question = "<question name=\"q\" type=\"boolean\"><instructions>Valid?</instructions></question>";
        return Stream.of(
                Arguments.of(question + question, "Duplicate semantic question"),
                Arguments.of(question.replace("name=\"q\"", "name=\" \""), "nonblank name"),
                Arguments.of(question.replace("type=\"boolean\"", ""), "type is required"),
                Arguments.of(question.replace("boolean", "unknown"), "Invalid semantic question"),
                Arguments.of(question.replace("Valid?", " "), "instructions must not be blank"),
                Arguments.of(question.replace("type=\"boolean\"", "type=\"choice\" threshold=\"0.8\""), "require a boolean"),
                Arguments.of(
                        question.replace("</question>",
                                "<criterion key=\"true\" value=\"A\"/><criterion key=\"true\" value=\"B\"/></question>"),
                        "Duplicate semantic criterion"),
                Arguments.of(question.replace("type=\"boolean\"", "type=\"score\""), "score needs ordered levels"),
                Arguments.of(question.replace("type=\"boolean\"", "type=\"boolean\" threshold=\"NaN\""), "within [0,1]"),
                Arguments.of(question.replace("type=\"boolean\"", "type=\"boolean\" threshold=\"abc\""),
                        "Invalid semantic question 'q': threshold must be a valid number: abc"),
                Arguments.of(question.replace("type=\"boolean\"", "type=\"boolean\" uncertainty=\"abc\""),
                        "Invalid semantic question 'q': uncertainty must be a valid number: abc"),
                Arguments.of(question.replace("type=\"boolean\"", "type=\"boolean\" state=\" \""),
                        "state selector must not be blank"),
                Arguments.of(question.replace("name=\"q\"", "unknown=\"q\""), "Unexpected attribute"));
    }

    @Test
    void javaResourceReloadReplacesAndRemovesDeclarations() throws Exception {
        class Questions extends RouteBuilder {
            private final String name;

            Questions(String name) {
                this.name = name;
                setResource(ResourceHelper.fromString("Questions.java", ""));
            }

            @Override
            public void configure() {
                if (name != null) {
                    semanticQuestions(this).question(name).type("boolean").instructions("Valid?").register();
                }
            }
        }
        context.addRoutes(new Questions("first"));
        context.addRoutes(new Questions("second"));
        assertThatThrownBy(() -> SemanticQuestions.get(context).get("first")).hasMessageContaining("Unknown");
        assertThat(SemanticQuestions.get(context).get("second")).isNotNull();
        context.addRoutes(new Questions(null));
        assertThatThrownBy(() -> SemanticQuestions.get(context).get("second")).hasMessageContaining("Unknown");
        assertThat(states).isEmpty();
    }

    @Test
    void embeddedBuilderInstancesOwnSeparateDeclarations() throws Exception {
        class Questions extends RouteBuilder {
            private final String name;

            Questions(String name) {
                this.name = name;
            }

            @Override
            public void configure() {
                if (name != null) {
                    semanticQuestions(this).question(name).type("boolean").instructions("Valid?").register();
                }
            }
        }
        context.addRoutes(new Questions("first"));
        context.addRoutes(new Questions("second"));
        context.addRoutes(new Questions(null));
        assertThat(SemanticQuestions.get(context).get(List.of("first", "second"))).hasSize(2);
        assertThatThrownBy(() -> context.addRoutes(new Questions("first"))).hasMessageContaining("Duplicate semantic question");
        assertThat(states).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "non-match", "nonMatch", "NON_MATCH", "NonMatch" })
    void policySpellingsMatchYaml(String policy) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                semanticQuestions(this).question("urgent").type("BOOLEAN").instructions("Urgent?").uncertaintyPolicy(policy)
                        .register();
            }
        });
        assertThat(SemanticQuestions.get(context).get("urgent").getUncertaintyPolicy())
                .isEqualTo(SemanticQuestion.UncertaintyPolicy.NON_MATCH);
    }

    @Test
    void duplicateNamesAcrossJavaAndXmlAreRejected() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                questions(semanticQuestions(this));
            }
        });
        assertThatThrownBy(() -> load("duplicate.semantic.xml", document("routes", xmlQuestions())))
                .hasMessageContaining("Duplicate semantic question");
        assertThat(states).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "java", "xml" })
    void numericPlaceholdersResolveBeforeValidation(String dsl) throws Exception {
        if (dsl.equals("java")) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    semanticQuestions(this).question("urgent").type("boolean").instructions("Urgent?")
                            .threshold("{{threshold:0.8}}").uncertainty("{{uncertainty:0.1}}").register();
                }
            });
        } else {
            load("questions.semantic.xml", document("routes", xmlQuestions()
                    .replace("threshold=\"0.8\"", "threshold=\"{{threshold:0.8}}\"")
                    .replace("uncertainty=\"0.1\"", "uncertainty=\"{{uncertainty:0.1}}\"")));
        }
        assertThat(SemanticQuestions.get(context).get("urgent").getThreshold()).isEqualTo(0.8);
        assertThat(SemanticQuestions.get(context).get("urgent").getUncertainty()).isEqualTo(0.1);
        assertThatThrownBy(() -> load("invalid.semantic.xml", document("routes", """
                <semantic><question name="invalid" type="boolean" threshold="{{threshold:abc}}">
                  <instructions>Urgent?</instructions>
                </question></semantic>
                """)))
                .hasMessageContaining("Invalid semantic question 'invalid': threshold must be a valid number")
                .hasRootCauseInstanceOf(NumberFormatException.class);
    }

    @Test
    void failedResourceBatchDoesNotReuseEarlierCachedDeclarationsOnRetry() throws Exception {
        String second = "<semantic><question name=\"second\" type=\"boolean\">"
                        + "<instructions>Valid?</instructions></question></semantic>";
        var loader = PluginHelper.getRoutesLoader(context);
        assertThatThrownBy(() -> loader.loadRoutes(List.of(
                ResourceHelper.fromString("first.semantic.xml", document("routes", xmlQuestions())),
                ResourceHelper.fromString("second.semantic.xml", document("routes", second.replace("boolean", "unknown"))))))
                .hasMessageContaining("Invalid semantic question");
        loader.loadRoutes(List.of(
                ResourceHelper.fromString("first.semantic.xml",
                        document("routes", xmlQuestions().replace("header.myState", "header.new"))),
                ResourceHelper.fromString("second.semantic.xml", document("routes", second))));
        assertThat(SemanticQuestions.get(context).get("department").getState()).isEqualTo("${header.new}");
        assertThat(SemanticQuestions.get(context).get("second")).isNotNull();
        assertThat(states).isEmpty();
    }

    @Test
    void declarationsFromAnotherResourceAreInstalledBeforeReferences() throws Exception {
        PluginHelper.getRoutesLoader(context).loadRoutes(List.of(
                ResourceHelper.fromString("use.xml", document("routes", """
                        <route><from uri="direct:use"/>
                          <setBody><language language="semantic">ref:department</language></setBody>
                        </route>
                        """)),
                ResourceHelper.fromString("definitions.semantic.xml", document("routes", xmlQuestions()))));
        assertThat(states).isEmpty();
        try (var template = context.createProducerTemplate()) {
            assertThat(template.requestBodyAndHeader("direct:use", "original", "myState", "invoice")).isEqualTo("billing");
        }
    }

    @Test
    void unknownReferenceFailsAtRouteInitialization() {
        assertThatThrownBy(() -> load("unknown.xml", document("routes", """
                <route><from uri="direct:unknown"/>
                  <setBody><language language="semantic">ref:missing</language></setBody>
                </route>
                """))).hasRootCauseMessage("Unknown semantic question: missing");
        assertThat(states).isEmpty();
    }

    @Test
    void reloadingReplacesAndRemovesDeclarationsUsedByExistingExpressions() throws Exception {
        load("questions.semantic.xml", document("routes", xmlQuestions()));
        var expression = context.resolveLanguage("semantic").createExpression("ref:department");
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader("myState", "old");
        assertThat(expression.evaluate(exchange, String.class)).isEqualTo("billing");
        PluginHelper.getRoutesLoader(context).updateRoutes(ResourceHelper.fromString("questions.semantic.xml",
                document("routes", xmlQuestions().replace("header.myState", "header.updated"))));
        exchange.getMessage().setHeader("updated", "new");
        assertThat(expression.evaluate(exchange, String.class)).isEqualTo("billing");
        assertThat(states).containsExactly("old", "new");
        PluginHelper.getRoutesLoader(context)
                .updateRoutes(ResourceHelper.fromString("questions.semantic.xml", document("routes", "")));
        assertThatThrownBy(() -> expression.evaluate(exchange, Object.class)).hasMessageContaining("Unknown semantic question");
    }

    @Test
    void watcherRemovesDeletedAndRenamedDeclarationResources() throws Exception {
        Path original = directory.resolve("questions.semantic.xml");
        Files.writeString(original, document("routes", xmlQuestions()));
        Resource source = ResourceHelper.resolveResource(context, original.toUri().toString());
        PluginHelper.getRoutesLoader(context).loadRoutes(source);
        TestWatcher watcher = new TestWatcher();
        watcher.setCamelContext(context);
        Path renamed = Files.move(original, directory.resolve("renamed.semantic.xml"));
        watcher.reload(source);
        assertThatThrownBy(() -> SemanticQuestions.get(context).get("department")).hasMessageContaining("Unknown");
        Resource replacement = ResourceHelper.resolveResource(context, renamed.toUri().toString());
        watcher.reload(replacement);
        assertThat(watcher.getLastError()).isNull();
        assertThat(SemanticQuestions.get(context).get("department")).isNotNull();
        Files.delete(renamed);
        watcher.reload(replacement);
        assertThatThrownBy(() -> SemanticQuestions.get(context).get("department")).hasMessageContaining("Unknown");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "http://camel.apache.org/schema/semantic", "http://camel.apache.org/schema/xml-io" })
    void standaloneXmlSupportsOptionalNamespace(String namespace) throws Exception {
        load("questions.semantic.xml", xmlQuestions().replace("<semantic>", "<semantic xmlns=\"" + namespace + "\">"));
        assertThat(SemanticQuestions.get(context).get("department").getCriteria()).containsKey("billing");
        load("questions.semantic.xml", "<semantic/>");
        assertThat(SemanticQuestions.get(context).isEmpty()).isTrue();
    }

    @Test
    void failedJavaReplacementKeepsPreviousDefinitions() throws Exception {
        class Questions extends RouteBuilder {
            private final String instructions;

            Questions(String instructions) {
                this.instructions = instructions;
                setResource(ResourceHelper.fromString("Questions.java", ""));
            }

            @Override
            public void configure() {
                semanticQuestions(this).question("urgent").type("boolean").instructions(instructions).end()
                        .question("other").type("boolean").instructions("Other?").register();
            }
        }
        context.addRoutes(new Questions("Urgent?"));
        var previous = SemanticQuestions.get(context).get("urgent");
        assertThatThrownBy(() -> context.addRoutes(new Questions(" "))).hasMessageContaining("instructions must not be blank");
        assertThat(SemanticQuestions.get(context).get("urgent")).isSameAs(previous);
        assertThat(SemanticQuestions.get(context).get("other")).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<routes><semantic/><semantic/></routes>",
            "<routes><semantic xmlns='urn:unexpected'/></routes>",
            "<semantic><question name='q' type='boolean'><instructions>Hi</instructions><unexpected/></question></semantic>",
            "<!DOCTYPE semantic [<!ENTITY external SYSTEM 'file:///nonexistent'>]><semantic>&external;</semantic>",
            "<routes><semantic><question name='q' type='boolean'><instructions>Hi</instructions></question></semantic><route><wrong/></route></routes>"
    })
    void malformedXmlDoesNotReplacePreviousDefinitions(String xml) throws Exception {
        load("questions.semantic.xml", xmlQuestions());
        var previous = SemanticQuestions.get(context).get("department");
        assertThatThrownBy(() -> load("questions.semantic.xml", xml)).isInstanceOf(Exception.class);
        assertThat(SemanticQuestions.get(context).get("department")).isSameAs(previous);
    }

    @ParameterizedTest
    @ValueSource(strings = { "java", "xml" })
    void loadingPlainBuilderDiscardsDeletedDeclarationResources(String dsl) throws Exception {
        Path file = directory.resolve(dsl.equals("java") ? "Questions.java" : "questions.semantic.xml");
        Files.writeString(file, xmlQuestions());
        Resource resource = ResourceHelper.resolveResource(context, file.toUri().toString());
        if (dsl.equals("java")) {
            RouteBuilder builder = new RouteBuilder() {
                @Override
                public void configure() {
                    semanticQuestions(this).question("urgent").type("boolean").instructions("Urgent?").register();
                }
            };
            builder.setResource(resource);
            context.addRoutes(builder);
        } else {
            PluginHelper.getRoutesLoader(context).loadRoutes(resource);
        }
        assertThat(SemanticQuestions.get(context).isEmpty()).isFalse();
        Files.delete(file);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
            }
        });
        assertThat(SemanticQuestions.get(context).isEmpty()).isTrue();
    }

    private void load(String location, String xml) throws Exception {
        PluginHelper.getRoutesLoader(context).loadRoutes(ResourceHelper.fromString(location, xml));
    }

    private static class TestWatcher extends RouteWatcherReloadStrategy {
        void reload(Resource resource) {
            onRouteReload(List.of(resource), false);
        }
    }
}
