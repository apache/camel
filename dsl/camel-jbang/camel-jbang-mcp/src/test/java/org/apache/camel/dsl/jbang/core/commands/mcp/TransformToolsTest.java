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

import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransformToolsTest {

    private TransformTools createTools() {
        CatalogService catalogService = new CatalogService();
        catalogService.catalogRepos = Optional.empty();

        TransformTools tools = new TransformTools();
        tools.catalogService = catalogService;
        return tools;
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
