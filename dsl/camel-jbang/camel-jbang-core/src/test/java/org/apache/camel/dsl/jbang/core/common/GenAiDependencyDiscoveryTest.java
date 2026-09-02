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
package org.apache.camel.dsl.jbang.core.common;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class GenAiDependencyDiscoveryTest {

    private static final String LANGCHAIN4J_ROUTE = """
            - beans:
              - name: chatModel
                type: dev.langchain4j.model.ollama.OllamaChatModel
                properties:
                  baseUrl: http://localhost:11434
                  modelName: llama3.2
            - from:
                uri: timer:tick
                steps:
                  - to: langchain4j-chat:myModel
            """;

    private static final String OPENAI_ROUTE = """
            - from:
                uri: timer:tick
                steps:
                  - to: openai:completion
            """;

    private static final String XML_ROUTE = """
            <routes xmlns="http://camel.apache.org/schema/spring">
              <route>
                <from uri="timer:tick"/>
                <to uri="langchain4j-chat:model"/>
              </route>
            </routes>
            """;

    private static final String JAVA_ROUTE = """
            public class AiRoute {
                public void configure() {
                    from("timer:tick").to("langchain4j-chat:model");
                }
            }
            """;

    @TempDir
    Path tempDir;

    @Test
    void shouldDiscoverLangChain4jComponentAndProvider() throws Exception {
        Path route = tempDir.resolve("ai-route.yaml");
        Files.writeString(route, LANGCHAIN4J_ROUTE);

        Collection<String> deps = GenAiDependencyDiscovery.discover(List.of(route.toString()), new Properties(), false);

        assertThat(deps).contains("camel:langchain4j-chat");
        assertThat(deps).contains("mvn:dev.langchain4j:langchain4j-ollama:${langchain4j-version}");
        assertThat(deps).contains("camel:ai-observability");
    }

    @Test
    void shouldDiscoverOpenAiComponent() throws Exception {
        Path route = tempDir.resolve("openai-route.yaml");
        Files.writeString(route, OPENAI_ROUTE);

        Collection<String> deps = GenAiDependencyDiscovery.discover(List.of(route.toString()), new Properties(), false);

        assertThat(deps).contains("camel:openai");
        assertThat(deps).contains("camel:ai-observability");
    }

    @Test
    void shouldDiscoverFromXmlRoute() throws Exception {
        Path route = tempDir.resolve("ai-route.xml");
        Files.writeString(route, XML_ROUTE);

        Collection<String> deps = GenAiDependencyDiscovery.discover(List.of(route.toString()), new Properties(), false);

        assertThat(deps).contains("camel:langchain4j-chat");
        assertThat(deps).contains("camel:ai-observability");
    }

    @Test
    void shouldDiscoverFromJavaRoute() throws Exception {
        Path route = tempDir.resolve("AiRoute.java");
        Files.writeString(route, JAVA_ROUTE);

        Collection<String> deps = GenAiDependencyDiscovery.discover(List.of(route.toString()), new Properties(), false);

        assertThat(deps).contains("camel:langchain4j-chat");
        assertThat(deps).contains("camel:ai-observability");
    }

    @Test
    void shouldSkipAiObservabilityWhenExplicitlyDisabled() throws Exception {
        Path route = tempDir.resolve("ai-route.yaml");
        Files.writeString(route, OPENAI_ROUTE);
        Properties properties = new Properties();
        properties.setProperty(GenAiDependencyDiscovery.AI_OBSERVABILITY_ENABLED, "false");

        Collection<String> deps = GenAiDependencyDiscovery.discover(List.of(route.toString()), properties, true);

        assertThat(deps).contains("camel:openai");
        assertThat(deps).doesNotContain("camel:ai-observability");
    }

    @Test
    void shouldIncludeAiObservabilityWhenExplicitlyEnabled() {
        Properties properties = new Properties();
        properties.setProperty(GenAiDependencyDiscovery.AI_OBSERVABILITY_ENABLED, "true");

        assertThat(GenAiDependencyDiscovery.includeAiObservability(properties, false)).isTrue();
    }

    @Test
    void shouldIncludeAiObservabilityWithObserveFlag() {
        assertThat(GenAiDependencyDiscovery.includeAiObservability(new Properties(), true)).isTrue();
    }

    @Test
    void shouldNotDiscoverForNonGenAiRoute() throws Exception {
        Path route = tempDir.resolve("route.yaml");
        Files.writeString(route, """
                - from:
                    uri: timer:tick
                    steps:
                      - to: log:info
                """);

        Collection<String> deps = GenAiDependencyDiscovery.discover(List.of(route.toString()), new Properties(), false);

        assertThat(deps).isEmpty();
    }

    @Test
    void shouldDiscoverFromClasspathResource() {
        Collection<String> deps = GenAiDependencyDiscovery.discover(
                List.of("classpath:genai/langchain4j-route.yaml"), new Properties(), false);

        assertThat(deps).contains("camel:langchain4j-chat");
        assertThat(deps).contains("camel:ai-observability");
    }

    @Test
    void shouldDiscoverFromSettingsFile() throws Exception {
        Path route = tempDir.resolve("genai-route.yaml");
        Files.writeString(route, OPENAI_ROUTE);

        Path settings = tempDir.resolve("camel-runner.properties");
        Files.writeString(settings, "yaml=" + route + "\n");

        Path profile = tempDir.resolve("application.properties");
        Files.writeString(profile, "camel.aiObservability.enabled=true\n");

        Collection<String> deps = GenAiDependencyDiscovery.discoverFromSettings(settings, profile, false, List.of());

        assertThat(deps).contains("camel:openai");
        assertThat(deps).contains("camel:ai-observability");
    }

    @Test
    void shouldExtractSchemesFromYamlAndXml() {
        assertThat(GenAiDependencyDiscovery.extractSchemes(OPENAI_ROUTE)).containsExactly("timer", "openai");
        assertThat(GenAiDependencyDiscovery.extractSchemes(XML_ROUTE)).containsExactly("timer", "langchain4j-chat");
    }

    @Test
    void shouldDiscoverOpenAiProviderFromJavaContent() {
        String content = "dev.langchain4j.model.openai.OpenAiChatModel model = OpenAiChatModel.builder().apiKey(key).build();";
        assertThat(GenAiDependencyDiscovery.discoverProviderDependencies(content))
                .contains("mvn:dev.langchain4j:langchain4j-open-ai:${langchain4j-version}");
    }

    @Test
    void shouldIgnoreUnknownGenAiSchemeWhenNotInCatalog() throws Exception {
        Path route = tempDir.resolve("future-route.yaml");
        Files.writeString(route, """
                - from:
                    uri: openai:completion
                """);

        CamelCatalog catalog = new DefaultCamelCatalog() {
            @Override
            public org.apache.camel.tooling.model.ComponentModel componentModel(String name) {
                return null;
            }
        };

        Collection<String> deps = GenAiDependencyDiscovery.discover(
                List.of(route.toString()), new Properties(), false, catalog);

        assertThat(deps).isEmpty();
    }
}
