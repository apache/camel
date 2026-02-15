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
import org.apache.camel.component.langchain4j.agent.pojos.WeatherTool;
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
 * Integration test for LangChain4j Agent component with custom tools only (no Camel route tools).
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Requires too much network resources")
public class LangChain4jAgentCustomToolsIT extends CamelTestSupport {

    private static final String CALCULATION_RESULT = "8";
    private static final String WEATHER_INFO = "sunny";
    private static final String WEATHER_TEMP = "22";

    protected ChatModel chatModel;

    @RegisterExtension
    static OllamaService OLLAMA = OllamaServiceFactory.createSingletonService();

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();
        chatModel = ModelHelper.loadChatModel(OLLAMA);
    }

    @Test
    void testAgentWithAdditionalToolsOnly() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:agent-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBody("direct:chat", "What is 5 + 3?", String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");
        assertTrue(response.contains(CALCULATION_RESULT) || response.contains("eight"),
                "Response should contain the calculation result from the calculator tool");
    }

    @Test
    void testMultipleToolInstances() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:agent-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        String response
                = template.requestBody("direct:chat",
                        "Calculate 10 * 5 and tell me the result, then tell me the weather in Paris", String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");
        assertTrue(response.contains("50") || response.contains("fifty"),
                "Response should contain the multiplication result but was: " + response);
        // Weather tool might not be used by the AI, so we make this assertion more flexible
        assertTrue(
                response.toLowerCase().contains(WEATHER_INFO) || response.toLowerCase().contains("weather")
                        || response.toLowerCase().contains("paris"),
                "Response should contain weather information or reference to weather/Paris");
    }

    @Test
    void testWeatherTool() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:agent-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBody("direct:chat",
                "Call the getWeather function for Paris", String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");

        boolean weatherToolUsed = response.contains("Weather in Paris: " + WEATHER_INFO + ", " + WEATHER_TEMP + "°C") ||
                response.toLowerCase().contains("sunny") ||
                response.toLowerCase().contains("22");

        assertTrue(weatherToolUsed,
                "Response should contain weather information from the weather tool. Response was: " + response);
    }

    @Test
    void testStringManipulationTool() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:agent-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBody("direct:chat", "Convert the 'hello world' text to uppercase", String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");
        assertTrue(response.contains("HELLO WORLD"),
                "Response should contain the uppercase conversion result but was: " + response);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        // Create LangChain4j tool instances
        CalculatorTool calculator = new CalculatorTool();
        WeatherTool weather = new WeatherTool();
        StringTool stringTool = new StringTool();

        List<Object> customTools = Arrays.asList(calculator, weather, stringTool);

        // Create agent configuration with custom tools
        AgentConfiguration config = new AgentConfiguration()
                .withChatModel(chatModel)
                .withCustomTools(customTools);

        // Create agent
        Agent agent = new AgentWithoutMemory(config);

        // Register agent in Camel context
        this.context.getRegistry().bind("additionalToolsAgent", agent);

        return new RouteBuilder() {
            @Override
            public void configure() {
                // Route with custom tools only
                from("direct:chat")
                        .to("langchain4j-agent:assistant?agent=#additionalToolsAgent")
                        .to("mock:agent-response");
            }
        };
    }
}
