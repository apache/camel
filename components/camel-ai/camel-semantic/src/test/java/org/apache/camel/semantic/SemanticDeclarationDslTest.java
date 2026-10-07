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
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.apache.camel.semantic.SemanticEvaluationsBuilder.semanticEvaluations;
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
        context.getRegistry().bind("adapter", new TestSemanticAdapter() {
            @Override
            public void validate(SemanticEvaluation evaluation) {
            }

            @Override
            public SemanticResult evaluate(SemanticEvaluation evaluation, Object state) {
                states.add(state);
                return applyPolicy(evaluation, switch (evaluation.getOperation()) {
                    case "boolean" -> new SemanticResult(null, 0.82, null, null, null);
                    case "choice" -> new SemanticResult("billing", null, null, null, null);
                    case "score" -> new SemanticResult(1.2, null, null, null, null);
                    default -> throw new IllegalArgumentException("Unsupported fixture operation");
                });
            }
        });
        ((SemanticLanguage) context.resolveLanguage("semantic")).setAdapter("adapter");
        context.start();
    }

    @AfterEach
    void close() throws Exception {
        context.close();
    }

    private static void evaluations(SemanticEvaluationsBuilder semantic) {
        semantic.evaluation("urgent").type("boolean").instructions("Is this urgent?")
                .state("${header.myState}").threshold(0.8).uncertainty(0.1).uncertaintyPolicy("non-match");
        semantic.evaluation("department").type("choice").instructions("Which department?")
                .state("${header.myState}").criterion("billing", "Invoices and refunds")
                .criterion("technical", "Bugs and outages");
        semantic.evaluation("priority").type("score").instructions("How urgent?")
                .state("${header.myState}").level("Routine").level("Urgent").level("Critical");
        semantic.register();
    }

    private static String xmlEvaluations() {
        return """
                <semantic>
                  <evaluation name="urgent" type="boolean" state="${header.myState}"
                            threshold="0.8" uncertainty="0.1" uncertaintyPolicy="non-match">
                    <instructions>Is this urgent?</instructions>
                  </evaluation>
                  <evaluation name="department" type="choice" state="${header.myState}">
                    <instructions>Which department?</instructions>
                    <criterion key="billing" value="Invoices and refunds"/>
                    <criterion key="technical" value="Bugs and outages"/>
                  </evaluation>
                  <evaluation name="priority" type="score" state="${header.myState}">
                    <instructions>How urgent?</instructions>
                    <level>Routine</level><level>Urgent</level><level>Critical</level>
                  </evaluation>
                </semantic>
                """;
    }

    private static String document(String root, String contents) {
        return "<" + root + " xmlns=\"http://camel.apache.org/schema/spring\">" + contents + "</" + root + ">";
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "java", "routes", "standalone", "no-namespace", "component-namespace", "xml-io-namespace", "semantic-extension" })
    void componentDeclarationsEvaluateSingleEvaluationsAndMixedBatches(String dsl) throws Exception {
        if (dsl.equals("java")) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:batch").setProperty("decision").language("semantic", "refs:urgent,department,priority")
                            .setHeader("department", simple("${exchangeProperty.decision[department]}"));
                    // Declarations are collected before route initialization, including those after the route.
                    evaluations(semanticEvaluations(this));
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
                        ResourceHelper.fromString("evaluations.xml", xmlEvaluations())));
            } else {
                String xml = document("routes", route + xmlEvaluations());
                if (dsl.equals("no-namespace")) {
                    xml = xml.replace(" xmlns=\"http://camel.apache.org/schema/spring\"", "");
                } else if (dsl.equals("component-namespace")) {
                    xml = xml.replace("schema/spring", "schema/semantic");
                } else if (dsl.equals("xml-io-namespace")) {
                    xml = xml.replace("schema/spring", "schema/xml-io");
                }
                load(dsl.equals("semantic-extension") ? "evaluations.semantic.xml" : "evaluations.xml", xml);
            }
        }
        assertThat(states).isEmpty();
        SemanticEvaluation urgent = SemanticEvaluations.get(context).get("urgent");
        assertThat(urgent.getParameters().get("threshold")).isEqualTo(0.8);
        assertThat(urgent.getParameters().get("uncertaintyPolicy")).isEqualTo("non-match");
        assertThat(SemanticEvaluations.get(context).get("priority").getParameters().get("criteria"))
                .isEqualTo(List.of("Routine", "Urgent", "Critical"));
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
        load("evaluations.xml", document("routes", xmlEvaluations()));
        SemanticEvaluation previous = SemanticEvaluations.get(context).get("department");
        assertThatThrownBy(() -> PluginHelper.getRoutesLoader(context).updateRoutes(
                ResourceHelper.fromString("evaluations.xml",
                        document("routes", "<semantic>" + declaration + "</semantic>"))))
                .hasMessageContaining(message);
        assertThat(SemanticEvaluations.get(context).get("department")).isSameAs(previous);
        assertThat(states).isEmpty();
        PluginHelper.getRoutesLoader(context).updateRoutes(ResourceHelper.fromString("evaluations.xml",
                document("routes", xmlEvaluations().replace("header.myState", "header.corrected"))));
        assertThat(SemanticEvaluations.get(context).get("department").getState()).isEqualTo("${header.corrected}");
    }

    static Stream<Arguments> invalidXml() {
        String evaluation = "<evaluation name=\"q\" type=\"boolean\"><instructions>Valid?</instructions></evaluation>";
        return Stream.of(
                Arguments.of(evaluation + evaluation, "Duplicate semantic evaluation"),
                Arguments.of(evaluation.replace("name=\"q\"", "name=\" \""), "nonblank name"),
                Arguments.of(evaluation.replace("type=\"boolean\"", ""), "operation is required"),
                Arguments.of(evaluation.replace("boolean", "unknown"), "Unknown operation"),
                Arguments.of(evaluation.replace("Valid?", " "), "Parameter 'instructions' is outside its size constraints"),
                Arguments.of(evaluation.replace("type=\"boolean\"", "type=\"choice\" threshold=\"0.8\""),
                        "Unknown parameter 'threshold'"),
                Arguments.of(
                        evaluation.replace("</evaluation>",
                                "<criterion key=\"true\" value=\"A\"/><criterion key=\"true\" value=\"B\"/></evaluation>"),
                        "Duplicate semantic criterion"),
                Arguments.of(evaluation.replace("type=\"boolean\"", "type=\"score\""), "Parameter 'criteria' is required"),
                Arguments.of(evaluation.replace("type=\"boolean\"", "type=\"boolean\" threshold=\"NaN\""),
                        "numeric constraints"),
                Arguments.of(evaluation.replace("type=\"boolean\"", "type=\"boolean\" threshold=\"abc\""),
                        "Invalid semantic evaluation 'q': Parameter 'threshold' must be a valid number"),
                Arguments.of(evaluation.replace("type=\"boolean\"", "type=\"boolean\" uncertainty=\"abc\""),
                        "Invalid semantic evaluation 'q': Parameter 'uncertainty' must be a valid number"),
                Arguments.of(evaluation.replace("type=\"boolean\"", "type=\"boolean\" state=\" \""),
                        "state selector must not be blank"),
                Arguments.of(evaluation.replace("name=\"q\"", "unknown=\"q\""), "Unexpected attribute"));
    }

    @Test
    void javaResourceReloadReplacesAndRemovesDeclarations() throws Exception {
        class Evaluations extends RouteBuilder {
            private final String name;

            Evaluations(String name) {
                this.name = name;
                setResource(ResourceHelper.fromString("Evaluations.java", ""));
            }

            @Override
            public void configure() {
                if (name != null) {
                    semanticEvaluations(this).evaluation(name).type("boolean").instructions("Valid?").register();
                }
            }
        }
        context.addRoutes(new Evaluations("first"));
        context.addRoutes(new Evaluations("second"));
        assertThatThrownBy(() -> SemanticEvaluations.get(context).get("first")).hasMessageContaining("Unknown");
        assertThat(SemanticEvaluations.get(context).get("second")).isNotNull();
        context.addRoutes(new Evaluations(null));
        assertThatThrownBy(() -> SemanticEvaluations.get(context).get("second")).hasMessageContaining("Unknown");
        assertThat(states).isEmpty();
    }

    @Test
    void embeddedBuilderInstancesOwnSeparateDeclarations() throws Exception {
        class Evaluations extends RouteBuilder {
            private final String name;

            Evaluations(String name) {
                this.name = name;
            }

            @Override
            public void configure() {
                if (name != null) {
                    semanticEvaluations(this).evaluation(name).type("boolean").instructions("Valid?").register();
                }
            }
        }
        context.addRoutes(new Evaluations("first"));
        context.addRoutes(new Evaluations("second"));
        context.addRoutes(new Evaluations(null));
        assertThat(SemanticEvaluations.get(context).get(List.of("first", "second"))).hasSize(2);
        assertThatThrownBy(() -> context.addRoutes(new Evaluations("first")))
                .hasMessageContaining("Duplicate semantic evaluation");
        assertThat(states).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "non-match", "nonMatch", "NON_MATCH", "NonMatch" })
    void policySpellingsMatchYaml(String policy) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                semanticEvaluations(this).evaluation("urgent").type("BOOLEAN").instructions("Urgent?").uncertaintyPolicy(policy)
                        .register();
            }
        });
        assertThat(SemanticEvaluations.get(context).get("urgent").getParameters().get("uncertaintyPolicy"))
                .isEqualTo("non-match");
    }

    @Test
    void duplicateNamesAcrossJavaAndXmlAreRejected() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                evaluations(semanticEvaluations(this));
            }
        });
        assertThatThrownBy(() -> load("duplicate.xml", document("routes", xmlEvaluations())))
                .hasMessageContaining("Duplicate semantic evaluation");
        assertThat(states).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "java", "xml" })
    void numericPlaceholdersResolveBeforeValidation(String dsl) throws Exception {
        if (dsl.equals("java")) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    semanticEvaluations(this).evaluation("urgent").type("boolean").instructions("Urgent?")
                            .threshold("{{threshold:0.8}}").uncertainty("{{uncertainty:0.1}}").register();
                }
            });
        } else {
            load("evaluations.xml", document("routes", xmlEvaluations()
                    .replace("threshold=\"0.8\"", "threshold=\"{{threshold:0.8}}\"")
                    .replace("uncertainty=\"0.1\"", "uncertainty=\"{{uncertainty:0.1}}\"")));
        }
        assertThat(SemanticEvaluations.get(context).get("urgent").getParameters().get("threshold")).isEqualTo(0.8);
        assertThat(SemanticEvaluations.get(context).get("urgent").getParameters().get("uncertainty")).isEqualTo(0.1);
        assertThatThrownBy(() -> load("invalid.xml", document("routes", """
                <semantic><evaluation name="invalid" type="boolean" threshold="{{threshold:abc}}">
                  <instructions>Urgent?</instructions>
                </evaluation></semantic>
                """)))
                .hasMessageContaining("Invalid semantic evaluation 'invalid': Parameter 'threshold' must be a valid number")
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failedResourceBatchDoesNotReuseEarlierCachedDeclarationsOnRetry() throws Exception {
        String second = "<semantic><evaluation name=\"second\" type=\"boolean\">"
                        + "<instructions>Valid?</instructions></evaluation></semantic>";
        var loader = PluginHelper.getRoutesLoader(context);
        assertThatThrownBy(() -> loader.loadRoutes(List.of(
                ResourceHelper.fromString("first.xml", document("routes", xmlEvaluations())),
                ResourceHelper.fromString("second.xml", document("routes", second.replace("boolean", "unknown"))))))
                .hasMessageContaining("Unknown operation");
        loader.loadRoutes(List.of(
                ResourceHelper.fromString("first.xml",
                        document("routes", xmlEvaluations().replace("header.myState", "header.new"))),
                ResourceHelper.fromString("second.xml", document("routes", second))));
        assertThat(SemanticEvaluations.get(context).get("department").getState()).isEqualTo("${header.new}");
        assertThat(SemanticEvaluations.get(context).get("second")).isNotNull();
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
                ResourceHelper.fromString("definitions.xml", document("routes", xmlEvaluations()))));
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
                """))).hasRootCauseMessage("Unknown semantic evaluation: missing");
        assertThat(states).isEmpty();
    }

    @Test
    void reloadingReplacesAndRemovesDeclarationsUsedByExistingExpressions() throws Exception {
        load("evaluations.xml", document("routes", xmlEvaluations()));
        var expression = context.resolveLanguage("semantic").createExpression("ref:department");
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader("myState", "old");
        assertThat(expression.evaluate(exchange, String.class)).isEqualTo("billing");
        PluginHelper.getRoutesLoader(context).updateRoutes(ResourceHelper.fromString("evaluations.xml",
                document("routes", xmlEvaluations().replace("header.myState", "header.updated"))));
        exchange.getMessage().setHeader("updated", "new");
        assertThat(expression.evaluate(exchange, String.class)).isEqualTo("billing");
        assertThat(states).containsExactly("old", "new");
        PluginHelper.getRoutesLoader(context)
                .updateRoutes(ResourceHelper.fromString("evaluations.xml", document("routes", "")));
        assertThatThrownBy(() -> expression.evaluate(exchange, Object.class))
                .hasMessageContaining("Unknown semantic evaluation");
    }

    @Test
    void watcherRemovesDeletedAndRenamedDeclarationResources() throws Exception {
        Path original = directory.resolve("evaluations.xml");
        Files.writeString(original, document("routes", xmlEvaluations()));
        Resource source = ResourceHelper.resolveResource(context, original.toUri().toString());
        PluginHelper.getRoutesLoader(context).loadRoutes(source);
        TestWatcher watcher = new TestWatcher();
        watcher.setCamelContext(context);
        Path renamed = Files.move(original, directory.resolve("renamed.xml"));
        watcher.reload(source);
        assertThatThrownBy(() -> SemanticEvaluations.get(context).get("department")).hasMessageContaining("Unknown");
        Resource replacement = ResourceHelper.resolveResource(context, renamed.toUri().toString());
        watcher.reload(replacement);
        assertThat(watcher.getLastError()).isNull();
        assertThat(SemanticEvaluations.get(context).get("department")).isNotNull();
        Files.delete(renamed);
        watcher.reload(replacement);
        assertThatThrownBy(() -> SemanticEvaluations.get(context).get("department")).hasMessageContaining("Unknown");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "http://camel.apache.org/schema/semantic", "http://camel.apache.org/schema/xml-io" })
    void standaloneXmlSupportsOptionalNamespace(String namespace) throws Exception {
        load("evaluations.xml", xmlEvaluations().replace("<semantic>", "<semantic xmlns=\"" + namespace + "\">"));
        assertThat(SemanticEvaluations.get(context).get("department").getParameters().get("criteria"))
                .asInstanceOf(InstanceOfAssertFactories.MAP).containsKey("billing");
        load("evaluations.xml", "<semantic/>");
        assertThat(SemanticEvaluations.get(context).isEmpty()).isTrue();
    }

    @Test
    void failedJavaReplacementKeepsPreviousDefinitions() throws Exception {
        class Evaluations extends RouteBuilder {
            private final String instructions;

            Evaluations(String instructions) {
                this.instructions = instructions;
                setResource(ResourceHelper.fromString("Evaluations.java", ""));
            }

            @Override
            public void configure() {
                semanticEvaluations(this).evaluation("urgent").type("boolean").instructions(instructions).end()
                        .evaluation("other").type("boolean").instructions("Other?").register();
            }
        }
        context.addRoutes(new Evaluations("Urgent?"));
        var previous = SemanticEvaluations.get(context).get("urgent");
        assertThatThrownBy(() -> context.addRoutes(new Evaluations(" ")))
                .hasMessageContaining("Parameter 'instructions' is outside its size constraints");
        assertThat(SemanticEvaluations.get(context).get("urgent")).isSameAs(previous);
        assertThat(SemanticEvaluations.get(context).get("other")).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<routes><semantic/><semantic/></routes>",
            "<routes><semantic xmlns='urn:unexpected'/></routes>",
            "<semantic><evaluation name='q' type='boolean'><instructions>Hi</instructions><unexpected/></evaluation></semantic>",
            "<!DOCTYPE semantic [<!ENTITY external SYSTEM 'file:///nonexistent'>]><semantic>&external;</semantic>",
            "<routes><semantic><evaluation name='q' type='boolean'><instructions>Hi</instructions></evaluation></semantic><route><wrong/></route></routes>"
    })
    void malformedXmlDoesNotReplacePreviousDefinitions(String xml) throws Exception {
        load("evaluations.xml", xmlEvaluations());
        var previous = SemanticEvaluations.get(context).get("department");
        assertThatThrownBy(() -> load("evaluations.xml", xml)).isInstanceOf(Exception.class);
        assertThat(SemanticEvaluations.get(context).get("department")).isSameAs(previous);
    }

    @ParameterizedTest
    @ValueSource(strings = { "java", "xml" })
    void loadingPlainBuilderDiscardsDeletedDeclarationResources(String dsl) throws Exception {
        Path file = directory.resolve(dsl.equals("java") ? "Evaluations.java" : "evaluations.xml");
        Files.writeString(file, xmlEvaluations());
        Resource resource = ResourceHelper.resolveResource(context, file.toUri().toString());
        if (dsl.equals("java")) {
            RouteBuilder builder = new RouteBuilder() {
                @Override
                public void configure() {
                    semanticEvaluations(this).evaluation("urgent").type("boolean").instructions("Urgent?").register();
                }
            };
            builder.setResource(resource);
            context.addRoutes(builder);
        } else {
            PluginHelper.getRoutesLoader(context).loadRoutes(resource);
        }
        assertThat(SemanticEvaluations.get(context).isEmpty()).isFalse();
        Files.delete(file);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
            }
        });
        assertThat(SemanticEvaluations.get(context).isEmpty()).isTrue();
    }

    @Test
    void semanticNamespaceCanBeUsedInsideStandardXmlRoutes() throws Exception {
        load("evaluations.xml", document("routes", xmlEvaluations().replace("<semantic>",
                "<semantic xmlns=\"http://camel.apache.org/schema/semantic\">")));
        assertThat(SemanticEvaluations.get(context).get("department").getParameters().get("criteria"))
                .asInstanceOf(InstanceOfAssertFactories.MAP).containsKey("billing");
    }

    @Test
    void failedOrdinaryReplacementKeepsPreviousDeclarations() throws Exception {
        load("evaluations.xml", xmlEvaluations());
        var previous = SemanticEvaluations.get(context).get("department");
        assertThatThrownBy(() -> load("evaluations.xml", "<routes><route><wrong/></route></routes>"))
                .isInstanceOf(Exception.class);
        assertThat(SemanticEvaluations.get(context).get("department")).isSameAs(previous);
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
