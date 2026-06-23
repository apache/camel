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
package org.apache.camel.component.langchain4j.agent.integration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.ollama.services.OllamaService;
import org.apache.camel.test.infra.ollama.services.OllamaServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration test for structured outputs with JSON Schema using the langchain4j-agent component.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Requires too much network resources")
public class LangChain4jAgentStructuredOutputIT extends CamelTestSupport {

    private static final String PERSON_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "name": {
                  "type": "string",
                  "description": "The person's full name"
                },
                "age": {
                  "type": "integer",
                  "description": "The person's age in years"
                },
                "occupation": {
                  "type": "string",
                  "description": "The person's job or profession"
                }
              },
              "required": ["name", "age", "occupation"],
              "additionalProperties": false
            }
            """;

    private static final String TEST_PROMPT
            = "Generate information about a software engineer named Alice who is 30 years old.";

    private final ObjectMapper objectMapper = new ObjectMapper();

    protected ChatModel chatModel;

    @TempDir
    Path tempDir;

    @RegisterExtension
    static OllamaService OLLAMA = OllamaServiceFactory.createSingletonService();

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();

        chatModel = ModelHelper.loadChatModel(OLLAMA);
    }

    @Test
    void testStructuredOutputWithClasspathSchema() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:classpath-result");
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBody("direct:classpath-schema", TEST_PROMPT, String.class);

        mockEndpoint.assertIsSatisfied();
        assertValidPersonResponse(response);
    }

    @Test
    void testStructuredOutputWithInlineSchema() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:inline-result");
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBody("direct:inline-schema", TEST_PROMPT, String.class);

        mockEndpoint.assertIsSatisfied();
        assertValidPersonResponse(response);
    }

    @Test
    void testStructuredOutputWithFileSchema() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:file-result");
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBody("direct:file-schema", TEST_PROMPT, String.class);

        mockEndpoint.assertIsSatisfied();
        assertValidPersonResponse(response);
    }

    private void assertValidPersonResponse(String response) throws Exception {
        assertNotNull(response);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertThat(jsonResponse.has("name")).isTrue();
        assertThat(jsonResponse.has("age")).isTrue();
        assertThat(jsonResponse.has("occupation")).isTrue();

        assertThat(jsonResponse.get("name").asText()).contains("Alice");
        assertThat(jsonResponse.get("age").asInt()).isEqualTo(30);
        assertThat(jsonResponse.get("occupation").asText().toLowerCase()).containsAnyOf("engineer", "developer");
    }

    private String loadSchemaFromClasspath() throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("person-schema.json")) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        String inlineSchema = loadSchemaFromClasspath();

        Path schemaFile = tempDir.resolve("person-schema.json");
        Files.writeString(schemaFile, inlineSchema);

        AgentConfiguration agentConfiguration = new AgentConfiguration().withChatModel(chatModel);
        context.getRegistry().bind("myAgentConfig", agentConfiguration);

        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:classpath-schema")
                        .to("langchain4j-agent:classpath-test?agentConfiguration=#myAgentConfig"
                            + "&jsonSchema=classpath:person-schema.json")
                        .to("json-validator:classpath:person-schema.json")
                        .to("mock:classpath-result");

                from("direct:inline-schema")
                        .to("langchain4j-agent:inline-test?agentConfiguration=#myAgentConfig"
                            + "&jsonSchema=RAW(" + inlineSchema + ")")
                        .to("json-validator:classpath:person-schema.json")
                        .to("mock:inline-result");

                from("direct:file-schema")
                        .to("langchain4j-agent:file-test?agentConfiguration=#myAgentConfig"
                            + "&jsonSchema=file:" + schemaFile.toAbsolutePath())
                        .to("json-validator:classpath:person-schema.json")
                        .to("mock:file-result");

            }
        };
    }

}
