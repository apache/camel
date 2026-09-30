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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.FilterDefinition;
import org.apache.camel.model.SetBodyDefinition;
import org.apache.camel.semantic.SemanticQuestions;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransformToolsTest {

    private TransformTools createTools() {
        return new TransformTools();
    }

    @Test
    void transformXmlToYaml() {
        TransformTools tools = createTools();
        String xml = """
                <routes xmlns="http://camel.apache.org/schema/spring">
                  <route>
                    <from uri="timer:hello"/>
                    <log message="Hello World"/>
                  </route>
                </routes>
                """;

        TransformTools.TransformResult result = tools.camel_transform_route(xml, "xml", "yaml");

        assertThat(result.supported).isTrue();
        assertThat(result.result).contains("timer");
        assertThat(result.result).contains("log");
    }

    @Test
    void transformXmlToYamlPreservesSemanticDeclarations() {
        var result = createTools().camel_transform_route(
                """
                        <routes xmlns="http://camel.apache.org/schema/spring">
                          <semantic><question name="urgent" type="boolean"><instructions>Urgent?</instructions></question></semantic>
                          <route><from uri="direct:input"/><setBody><language language="semantic">ref:urgent</language></setBody></route>
                        </routes>
                        """,
                "xml", "yaml");
        assertThat(result.supported).isTrue();
        assertThat(result.result).contains("semantic:", "question:", "urgent:", "Urgent?", "ref:urgent");
    }

    @ParameterizedTest
    @CsvSource({ "yaml,xml", "java,xml", "java,yaml" })
    void semanticDeclarationsSurviveConversionAndReload(String source, String target) throws Exception {
        String yaml = """
                - semantic:
                    question:
                      urgent:
                        type: boolean
                        instructions: Urgent?
                        threshold: "{{semantic.export.threshold:0.8}}"
                        uncertainty: "{{semantic.export.uncertainty:0.1}}"
                        uncertaintyPolicy: non-match
                      department:
                        type: choice
                        state: ${header.selected}
                        instructions: Which department?
                        criteria:
                          billing: Invoices
                          technical: Bugs
                      priority:
                        type: score
                        instructions: Priority?
                        criteria: [Low, High]
                - route:
                    from:
                      uri: direct:input
                      steps:
                        - setBody:
                            expression:
                              language:
                                language: semantic
                                expression: ref:department
                """;
        String java = """
                import org.apache.camel.builder.RouteBuilder;
                public class SemanticRoute extends RouteBuilder {
                    public void configure() {
                        semanticQuestions().question("urgent").type("boolean").instructions("Urgent?")
                            .threshold("{{semantic.export.threshold:0.8}}")
                            .uncertainty("{{semantic.export.uncertainty:0.1}}").uncertaintyPolicy("non-match");
                        semanticQuestions().question("department").type("choice").instructions("Which department?")
                            .state("${header.selected}").criterion("billing", "Invoices").criterion("technical", "Bugs");
                        semanticQuestions().question("priority").type("score").instructions("Priority?")
                            .level("Low").level("High");
                        from("direct:input").setBody().language("semantic", "ref:department");
                    }
                }
                """;
        var result = createTools().camel_transform_route(source.equals("java") ? java : yaml, source, target);
        assertThat(result.supported).isTrue();
        assertThat(result.result).contains("ref:department").doesNotContain("{{semantic.export.");
        try (var context = new DefaultCamelContext()) {
            context.build();
            PluginHelper.getRoutesLoader(context).loadRoutes(ResourceHelper.fromString("result." + target, result.result));
            var questions = SemanticQuestions.get(context);
            assertThat(questions.get("urgent").getThreshold()).isEqualTo(0.8);
            assertThat(questions.get("urgent").getUncertainty()).isEqualTo(0.1);
            assertThat(questions.get("urgent").getUncertaintyPolicy().name()).isEqualTo("NON_MATCH");
            assertThat(questions.get("department").getCriteria()).containsEntry("billing", "Invoices")
                    .containsEntry("technical", "Bugs");
            assertThat(questions.get("department").getState()).isEqualTo("${header.selected}");
            assertThat(questions.get("priority").getLevels()).containsExactly("Low", "High");
        }
    }

    @ParameterizedTest
    @CsvSource({ "yaml,xml", "java,xml", "java,yaml" })
    void semanticConversionOmitsDefaultPolicies(String source, String target) throws Exception {
        var result = createTools().camel_transform_route(semanticRoute(source, "0.5"), source, target);

        assertThat(result.supported).isTrue();
        assertThat(result.result).contains("urgent", "Urgent?").doesNotContain("threshold", "uncertainty");
        try (var context = new DefaultCamelContext()) {
            context.build();
            PluginHelper.getRoutesLoader(context).loadRoutes(ResourceHelper.fromString("result." + target, result.result));
            var question = SemanticQuestions.get(context).get("urgent");
            assertThat(question.getThreshold()).isEqualTo(0.5);
            assertThat(question.getUncertainty()).isZero();
            assertThat(question.getUncertaintyPolicy().name()).isEqualTo("FAIL");
        }
    }

    @ParameterizedTest
    @CsvSource({ "yaml,xml", "java,xml", "java,yaml" })
    void semanticConversionReportsMissingNumericProperty(String source, String target) {
        String route = semanticRoute(source, "{{semantic.export.missing.threshold}}");

        assertThatThrownBy(() -> createTools().camel_transform_route(route, source, target))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("Property with key [semantic.export.missing.threshold] not found");
    }

    private static String semanticRoute(String source, String threshold) {
        if (source.equals("java")) {
            return """
                    semanticQuestions().question("urgent").type("boolean").instructions("Urgent?")
                        .threshold("%s").uncertainty(0).uncertaintyPolicy("fail");
                    from("direct:input").setBody().language("semantic", "ref:urgent");
                    """.formatted(threshold);
        }
        return """
                - semantic:
                    question:
                      urgent:
                        type: boolean
                        instructions: Urgent?
                        threshold: "%s"
                        uncertainty: 0
                        uncertaintyPolicy: fail
                - route:
                    from:
                      uri: direct:input
                      steps:
                        - setBody:
                            expression:
                              language:
                                language: semantic
                                expression: ref:urgent
                """.formatted(threshold);
    }

    @ParameterizedTest
    @ValueSource(strings = { "yaml", "xml" })
    void nonSemanticExpressionClausesSurviveConversionAndReload(String target) throws Exception {
        String route = """
                from("direct:input")
                    .filter().simple("${body} == 'hello'")
                        .setBody().simple("${body.toUpperCase()}")
                    .end();
                """;
        var result = createTools().camel_transform_route(route, "java", target);

        assertThat(result.supported).isTrue();
        assertThat(result.result).doesNotContain("semantic");
        try (var context = new DefaultCamelContext()) {
            context.build();
            PluginHelper.getRoutesLoader(context).loadRoutes(ResourceHelper.fromString("result." + target, result.result));
            var filter = (FilterDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
            assertThat(filter.getExpression().getLanguage()).isEqualTo("simple");
            assertThat(filter.getExpression().getExpression()).isEqualTo("${body} == 'hello'");
            var setBody = (SetBodyDefinition) filter.getOutputs().get(0);
            assertThat(setBody.getExpression().getLanguage()).isEqualTo("simple");
            assertThat(setBody.getExpression().getExpression()).isEqualTo("${body.toUpperCase()}");
        }
    }

    @Test
    void transformYamlToXml() {
        TransformTools tools = createTools();
        String yaml = """
                - route:
                    from:
                      uri: timer:hello
                      steps:
                        - log:
                            message: Hello World
                """;

        TransformTools.TransformResult result = tools.camel_transform_route(yaml, "yaml", "xml");

        assertThat(result.supported).isTrue();
        assertThat(result.result).contains("timer:hello");
        assertThat(result.result).contains("<log");
    }

    @Test
    void transformJavaToYaml() {
        TransformTools tools = createTools();
        String java = """
                import org.apache.camel.builder.RouteBuilder;

                public class MyRoute extends RouteBuilder {
                    @Override
                    public void configure() {
                        from("timer:hello")
                            .log("Hello World");
                    }
                }
                """;

        TransformTools.TransformResult result = tools.camel_transform_route(java, "java", "yaml");

        assertThat(result.supported).isTrue();
        assertThat(result.result).contains("timer");
        assertThat(result.result).contains("log");
    }

    @Test
    void transformJavaToXml() {
        TransformTools tools = createTools();
        String java = """
                import org.apache.camel.builder.RouteBuilder;

                public class MyRoute extends RouteBuilder {
                    @Override
                    public void configure() {
                        from("timer:hello")
                            .log("Hello World");
                    }
                }
                """;

        TransformTools.TransformResult result = tools.camel_transform_route(java, "java", "xml");

        assertThat(result.supported).isTrue();
        assertThat(result.result).contains("timer:hello");
        assertThat(result.result).contains("<log");
    }

    @Test
    void transformJavaSnippetToYaml() {
        TransformTools tools = createTools();
        String snippet = """
                from("timer:hello")
                    .log("Hello World");
                """;

        TransformTools.TransformResult result = tools.camel_transform_route(snippet, "java", "yaml");

        assertThat(result.supported).isTrue();
        assertThat(result.result).contains("timer");
        assertThat(result.result).contains("log");
    }

    @Test
    void transformJavaSnippetToXml() {
        TransformTools tools = createTools();
        String snippet = """
                from("timer:hello")
                    .to("log:foo");
                """;

        TransformTools.TransformResult result = tools.camel_transform_route(snippet, "java", "xml");

        assertThat(result.supported).isTrue();
        assertThat(result.result).contains("timer:hello");
        assertThat(result.result).contains("log:foo");
    }

    @Test
    void sameFormatReturnsInput() {
        TransformTools tools = createTools();
        String yaml = "- route:\n    from:\n      uri: timer:hello\n";

        TransformTools.TransformResult result = tools.camel_transform_route(yaml, "yaml", "yaml");

        assertThat(result.supported).isTrue();
        assertThat(result.result).isEqualTo(yaml);
    }

    @Test
    void unsupportedFormatReturnsNotSupported() {
        TransformTools tools = createTools();

        TransformTools.TransformResult result = tools.camel_transform_route("some route", "groovy", "yaml");

        assertThat(result.supported).isFalse();
        assertThat(result.note).contains("Unsupported");
    }
}
