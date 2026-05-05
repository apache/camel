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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.apache.camel.component.langchain4j.agent.api.AgentWithoutMemory;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.ollama.services.OllamaService;
import org.apache.camel.test.infra.ollama.services.OllamaServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;
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

    @RegisterExtension
    static OllamaService OLLAMA = OllamaServiceFactory.createSingletonService();

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();

        JsonRawSchema jsonRawSchema = JsonRawSchema.from(PERSON_SCHEMA);
        ResponseFormat responseFormat = ResponseFormat.builder()
                .type(JSON)
                .jsonSchema(JsonSchema.builder()
                        .name("person_schema")
                        .rootElement(jsonRawSchema)
                        .build())
                .build();

        chatModel = ModelHelper.loadChatModel(OLLAMA, responseFormat);
    }

    @Test
    void testStructuredOutputWithJsonSchema() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBody("direct:structured-output", TEST_PROMPT, String.class);

        mockEndpoint.assertIsSatisfied();

        assertNotNull(response);
        JsonNode jsonResponse = objectMapper.readTree(response);

        assertThat(jsonResponse.has("name")).isTrue();
        assertThat(jsonResponse.has("age")).isTrue();
        assertThat(jsonResponse.has("occupation")).isTrue();

        assertThat(jsonResponse.get("name").asText()).contains("Alice");
        assertThat(jsonResponse.get("age").asInt()).isEqualTo(30);
        assertThat(jsonResponse.get("occupation").asText().toLowerCase()).containsAnyOf("engineer", "developer");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        AgentConfiguration configuration = new AgentConfiguration()
                .withChatModel(chatModel);

        Agent agent = new AgentWithoutMemory(configuration);
        context.getRegistry().bind("structuredOutputAgent", agent);

        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:structured-output")
                        .to("langchain4j-agent:structured-output?agent=#structuredOutputAgent")
                        .to("json-validator:classpath:person-schema.json")
                        .to("mock:result");
            }
        };
    }

}
