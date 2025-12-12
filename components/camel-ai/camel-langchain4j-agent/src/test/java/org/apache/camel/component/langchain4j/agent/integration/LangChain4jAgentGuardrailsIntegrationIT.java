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

import java.util.List;

import dev.langchain4j.model.chat.ChatModel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.apache.camel.component.langchain4j.agent.api.AgentWithoutMemory;
import org.apache.camel.component.langchain4j.agent.pojos.TestFailingInputGuardrail;
import org.apache.camel.component.langchain4j.agent.pojos.TestJsonOutputGuardrail;
import org.apache.camel.component.langchain4j.agent.pojos.TestSuccessInputGuardrail;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.ollama.services.OllamaService;
import org.apache.camel.test.infra.ollama.services.OllamaServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Requires too much network resources")
public class LangChain4jAgentGuardrailsIntegrationIT extends CamelTestSupport {

    protected ChatModel chatModel;

    @RegisterExtension
    static OllamaService OLLAMA = OllamaServiceFactory.createSingletonService();

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();
        chatModel = ModelHelper.loadChatModel(OLLAMA);
    }

    @BeforeEach
    void setup() {
        // Reset all guardrails before each test
        TestSuccessInputGuardrail.reset();
        TestFailingInputGuardrail.reset();
        TestJsonOutputGuardrail.reset();
    }

    @Test
    void testAgentWithInputGuardrails() throws InterruptedException {

        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:agent-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBody(
                "direct:agent-with-input-guardrails",
                "What is Apache Camel?",
                String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");
        assertTrue(TestSuccessInputGuardrail.wasValidated(),
                "Input guardrail should have been called to validate the user message");
        assertTrue(response.toLowerCase().contains("camel") || response.toLowerCase().contains("integration"),
                "Response should contain information about Apache Camel");
    }

    @Test
    void testAgentWithMultipleInputGuardrails() throws InterruptedException {

        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:agent-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBody(
                "direct:agent-with-multiple-input-guardrails",
                "What is integration?",
                String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");
        assertTrue(TestSuccessInputGuardrail.wasValidated(),
                "First input guardrail should have been called");
        assertTrue(TestFailingInputGuardrail.wasValidated(),
                "Second input guardrail should have been called");
        assertEquals(1, TestFailingInputGuardrail.getCallCount(),
                "Second guardrail should have been called exactly once");
        assertTrue(response.toLowerCase().contains("integration") || response.toLowerCase().contains("connect"),
                "Response should contain information about integration");
    }

    @Test
    void testAgentWithJsonOutputGuardrail() throws InterruptedException {

        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:agent-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        // Request a specific JSON format for person information
        String jsonRequest = """
                Please return information about a software engineer named John Doe in the following JSON format:
                {
                  "name": "string",
                  "profession": "string",
                  "experience": "number",
                  "skills": ["array", "of", "strings"]
                }
                """;

        String response = template.requestBody(
                "direct:agent-with-json-output-guardrail",
                jsonRequest,
                String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");

        // Verify the output guardrail was called
        assertTrue(TestJsonOutputGuardrail.wasValidated(),
                "Output guardrail should have been called to validate the AI response");

        assertTrue(response.trim().startsWith("{"), "Response should start with JSON object");
        assertTrue(response.trim().endsWith("}"), "Response should end with JSON object");
        assertTrue(response.contains("\"name\""), "Response should contain name field");
        assertTrue(response.contains("\"profession\""), "Response should contain profession field");
        assertTrue(response.contains("John Doe"), "Response should contain the requested name");
    }

    @Test
    void testAgentWithJsonOutputGuardrailFailure() throws InterruptedException {
        // Disable reprompting so the guardrail fails immediately with fatal error
        TestJsonOutputGuardrail.setAllowReprompt(false);

        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:agent-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(0); // No message should reach the endpoint due to guardrail failure

        String nonJsonRequest = "Tell me a simple story about a cat in one sentence";

        // Expect an exception due to guardrail failure
        Exception exception = assertThrows(Exception.class, () -> {
            template.requestBody(
                    "direct:agent-with-json-output-guardrail",
                    nonJsonRequest,
                    Object.class);
        });

        assertTrue(exception.getMessage().contains("Output validation failed") ||
                exception.getCause() != null && exception.getCause().getMessage().contains("Output validation failed"),
                "Exception should be related to output guardrail validation failure");

        assertTrue(TestJsonOutputGuardrail.wasValidated(),
                "Output guardrail should have been called to validate the AI response");

        mockEndpoint.assertIsSatisfied();
    }

    @Test
    void testAgentWithBothInputAndOutputGuardrails() throws InterruptedException {

        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:agent-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        String jsonRequest = """
                Create a JSON profile for a data scientist named Alice Smith with 5 years of experience.
                Use this exact format:
                {
                  "name": "string",
                  "title": "string",
                  "yearsExperience": number,
                  "department": "string"
                }
                """;

        String response = template.requestBody(
                "direct:agent-with-mixed-guardrails",
                jsonRequest,
                String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");

        assertTrue(TestSuccessInputGuardrail.wasValidated(),
                "Input guardrail should have been called to validate the user message");
        assertTrue(TestJsonOutputGuardrail.wasValidated(),
                "Output guardrail should have been called to validate the AI response");
        assertTrue(response.trim().startsWith("{"), "Response should start with JSON object");
        assertTrue(response.trim().endsWith("}"), "Response should end with JSON object");
        assertTrue(response.contains("\"name\""), "Response should contain name field");
        assertTrue(response.contains("Alice Smith"), "Response should contain the requested name");
        assertTrue(response.contains("\"title\"") || response.contains("\"department\""),
                "Response should contain professional information (title or department)");
        assertTrue(response.contains("\"yearsExperience\"") || response.contains("experience"),
                "Response should contain experience");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        // Create agent with input guardrails
        AgentConfiguration inputGuardrailsConfig = new AgentConfiguration()
                .withChatModel(chatModel)
                .withInputGuardrailClassesList("org.apache.camel.component.langchain4j.agent.pojos.TestSuccessInputGuardrail")
                .withOutputGuardrailClasses(List.of());
        Agent agentWithInputGuardrails = new AgentWithoutMemory(inputGuardrailsConfig);

        // Create agent with multiple input guardrails
        AgentConfiguration multipleInputGuardrailsConfig = new AgentConfiguration()
                .withChatModel(chatModel)
                .withInputGuardrailClassesList(
                        "org.apache.camel.component.langchain4j.agent.pojos.TestSuccessInputGuardrail,org.apache.camel.component.langchain4j.agent.pojos.TestFailingInputGuardrail")
                .withOutputGuardrailClasses(List.of());
        Agent agentWithMultipleInputGuardrails = new AgentWithoutMemory(multipleInputGuardrailsConfig);

        // Create agent with output guardrails
        AgentConfiguration outputGuardrailsConfig = new AgentConfiguration()
                .withChatModel(chatModel)
                .withInputGuardrailClasses(List.of())
                .withOutputGuardrailClassesList("org.apache.camel.component.langchain4j.agent.pojos.TestJsonOutputGuardrail");
        Agent agentWithOutputGuardrails = new AgentWithoutMemory(outputGuardrailsConfig);

        // Create agent with mixed guardrails
        AgentConfiguration mixedGuardrailsConfig = new AgentConfiguration()
                .withChatModel(chatModel)
                .withInputGuardrailClassesList("org.apache.camel.component.langchain4j.agent.pojos.TestSuccessInputGuardrail")
                .withOutputGuardrailClassesList("org.apache.camel.component.langchain4j.agent.pojos.TestJsonOutputGuardrail");
        Agent agentWithMixedGuardrails = new AgentWithoutMemory(mixedGuardrailsConfig);

        // Register agents in the context
        this.context.getRegistry().bind("agentWithInputGuardrails", agentWithInputGuardrails);
        this.context.getRegistry().bind("agentWithMultipleInputGuardrails", agentWithMultipleInputGuardrails);
        this.context.getRegistry().bind("agentWithOutputGuardrails", agentWithOutputGuardrails);
        this.context.getRegistry().bind("agentWithMixedGuardrails", agentWithMixedGuardrails);

        return new RouteBuilder() {
            public void configure() {
                from("direct:agent-with-input-guardrails")
                        .to("langchain4j-agent:test-agent?agent=#agentWithInputGuardrails")
                        .to("mock:agent-response");

                from("direct:agent-with-multiple-input-guardrails")
                        .to("langchain4j-agent:test-agent?agent=#agentWithMultipleInputGuardrails")
                        .to("mock:agent-response");

                from("direct:agent-with-json-output-guardrail")
                        .to("langchain4j-agent:test-agent?agent=#agentWithOutputGuardrails")
                        .to("mock:agent-response");

                from("direct:agent-with-mixed-guardrails")
                        .to("langchain4j-agent:test-agent?agent=#agentWithMixedGuardrails")
                        .to("mock:agent-response");
            }
        };
    }
}
