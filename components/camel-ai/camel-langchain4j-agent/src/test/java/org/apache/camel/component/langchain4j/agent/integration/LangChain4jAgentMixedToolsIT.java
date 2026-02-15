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

import java.util.Arrays;
import java.util.List;

import dev.langchain4j.model.chat.ChatModel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.apache.camel.component.langchain4j.agent.api.AgentWithoutMemory;
import org.apache.camel.component.langchain4j.agent.pojos.CalculatorTool;
import org.apache.camel.component.langchain4j.agent.pojos.StringTool;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.ollama.services.OllamaService;
import org.apache.camel.test.infra.ollama.services.OllamaServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for LangChain4j Agent component mixing Camel route tools (tags) and custom LangChain4j tools.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Requires too much network resources")
public class LangChain4jAgentMixedToolsIT extends CamelTestSupport {

    private static final String USER_DATABASE = """
            {"id": "123", "name": "John Smith", "membership": "Gold", "rentals": 15, "preferredVehicle": "SUV"}
            """;

    private static final String USER_DB_NAME = "John Smith";
    private static final String WEATHER_INFO = "sunny";
    private static final String CALCULATION_RESULT = "10";

    protected ChatModel chatModel;

    @RegisterExtension
    static OllamaService OLLAMA = OllamaServiceFactory.createSingletonService();

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();
        chatModel = ModelHelper.loadChatModel(OLLAMA);
    }

    @Test
    void testAgentWithMixedTools() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:agent-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBody("direct:mixedTools", "Calculate 7 + 3", String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");
        assertTrue(response.contains(CALCULATION_RESULT) || response.contains("ten"),
                "Response should contain the calculation result from the additional calculator tool");
    }

    @Test
    void testAgentWithMultipleTagsAndAdditionalTools() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:agent-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBody("direct:mixedTools",
                "Calculate 15 * 4 and convert 'hello' to uppercase",
                String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");
        assertTrue(response.contains("60") || response.contains("sixty"),
                "Response should contain the multiplication result from additional tools");
        assertTrue(response.contains("HELLO"),
                "Response should contain the uppercase conversion result from additional tools");
    }

    @Test
    void testAgentWithCamelAndAdditionalTools() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:agent-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBody("direct:mixedTools",
                "What is the name of user ID 123 and calculate 5 * 6?",
                String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");
        assertTrue(response.contains(USER_DB_NAME),
                "Response should contain the user name from the Camel route tool");
        assertTrue(response.contains("30") || response.contains("thirty"),
                "Response should contain the calculation result from the additional calculator tool");
    }

    @Test
    void testAgentWithOnlyCamelRouteTools() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:agent-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBody("direct:mixedTools", "What's the weather in New York?", String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");
        assertTrue(response.toLowerCase().contains(WEATHER_INFO),
                "Response should contain weather information from the Camel route tool");

    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        // Create LangChain4jtool instances
        CalculatorTool calculator = new CalculatorTool();
        StringTool stringTool = new StringTool();

        List<Object> customTools = Arrays.asList(calculator, stringTool);

        // Create agent configuration with custom tools
        AgentConfiguration config = new AgentConfiguration()
                .withChatModel(chatModel)
                .withCustomTools(customTools);

        // Create agent
        Agent agent = new AgentWithoutMemory(config);

        // Register agent in Camel context
        this.context.getRegistry().bind("mixedToolsAgent", agent);

        return new RouteBuilder() {
            @Override
            public void configure() {
                // Route with mixed tools : custom tools (via agent) + camel routes
                from("direct:mixedTools")
                        .to("langchain4j-agent:assistant?agent=#mixedToolsAgent&tags=users,weather")
                        .to("mock:agent-response");

                // Tool routes for function calling
                from("langchain4j-tools:userDb?tags=users&description=Query user database by user ID&parameter.userId=string")
                        .setBody(constant(USER_DATABASE));

                from("langchain4j-tools:weatherService?tags=weather&description=Get current weather information&parameter.location=string")
                        .setBody(constant("{\"weather\": \"" + WEATHER_INFO + "\", \"location\": \"Current Location\"}"));
            }
        };
    }
}
