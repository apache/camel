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
    void genericXmlConversionRejectsComponentDeclarations() {
        assertThatThrownBy(() -> createTools().camel_transform_route(
                "<routes><semantic/><route><from uri='direct:input'/><log message='Hello'/></route></routes>", "xml", "yaml"))
                .isInstanceOf(ToolCallException.class).hasMessageContaining("semantic");
    }

    @ParameterizedTest
    @CsvSource({ "yaml,xml", "java,xml", "java,yaml" })
    void genericConversionDoesNotSilentlyLoseSemanticDeclarations(String source, String target) {
        assertThatThrownBy(() -> createTools().camel_transform_route(semanticRoute(source, "0.8"), source, target))
                .isInstanceOf(ToolCallException.class).hasMessageContaining("Keep them in a separate declaration resource");
    }

    @ParameterizedTest
    @ValueSource(strings = { "xml", "yaml" })
    void emptySemanticRegistryDoesNotPreventConversion(String target) {
        String route = """
                import org.apache.camel.builder.RouteBuilder;
                import static org.apache.camel.semantic.SemanticQuestionsBuilder.semanticQuestions;
                public class EmptySemanticRoute extends RouteBuilder {
                    public void configure() {
                        semanticQuestions(this).register();
                        from("direct:input").log("Hello");
                    }
                }
                """;
        var result = createTools().camel_transform_route(route, "java", target);

        assertThat(result.supported).isTrue();
        assertThat(result.result).contains("direct:input").doesNotContain("semantic");
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
                    import org.apache.camel.builder.RouteBuilder;
                    import static org.apache.camel.semantic.SemanticQuestionsBuilder.semanticQuestions;
                    public class SemanticRoute extends RouteBuilder {
                        public void configure() {
                            semanticQuestions(this).question("urgent").type("boolean").instructions("Urgent?")
                                .threshold("%s").uncertainty(0).uncertaintyPolicy("fail").register();
                            from("direct:input").setBody().language("semantic", "ref:urgent");
                        }
                    }
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
