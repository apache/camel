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

package org.apache.camel.component.springai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * Integration test demonstrating interaction between camel-spring-ai-chat and camel-spring-ai-tools components.
 *
 * This test shows how Camel routes can be exposed as tools/functions that LLMs can call during conversations.
 *
 * NOTE: Tool invocation depends on the LLM's capability and willingness to use tools. Some models or configurations may
 * not invoke tools even when available. The tests verify that: 1. Tools are properly registered in the cache 2. The
 * chat endpoint has access to the tools 3. If tools are invoked, they execute correctly
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Disabled unless running in CI")
public class SpringAiChatToolsIntegrationIT extends OllamaTestSupport {

    private static final String WEATHER_PARIS = "The weather in Paris is sunny with a temperature of 22°C";
    private static final String WEATHER_LONDON = "The weather in London is cloudy with a temperature of 15°C";
    private static final String CALCULATOR_RESULT = "The result is: 8";

    @Test
    public void testToolsAreRegistered() throws Exception {
        // Verify that tools are registered in the cache before testing
        var toolCache = org.apache.camel.component.springai.tools.spec.CamelToolExecutorCache.getInstance();
        var tools = toolCache.getTools();

        // Give routes time to start and register tools
        Thread.sleep(1000);

        assertThat(tools).isNotEmpty();
        assertThat(tools).containsKey("weather");
        assertThat(tools).containsKey("math");
        assertThat(tools.get("weather")).hasSize(1);
        assertThat(tools.get("math")).hasSize(1);
    }

    @Test
    public void testChatWithSingleTool() throws InterruptedException {
        MockEndpoint weatherMock = getMockEndpoint("mock:weather");
        weatherMock.reset();
        weatherMock.expectedMinimumMessageCount(1);
        weatherMock.expectedHeaderReceived("city", "Paris");

        // Test that the LLM can call a single tool (weather) during conversation
        String response = template().requestBody("direct:weatherChat", "What's the weather in Paris?", String.class);

        assertThat(response).isNotNull();
        assertThat(response.toLowerCase()).containsAnyOf("paris", "sunny", "22", "weather");

        // Verify the weather tool was actually invoked
        weatherMock.assertIsSatisfied();
        assertThat(weatherMock.getReceivedCounter()).isGreaterThanOrEqualTo(1);
    }

    @Test
    public void testChatWithMultipleTools() throws InterruptedException {
        // Test that the LLM can choose between multiple tools based on the question

        // Test weather tool
        MockEndpoint weatherMock = getMockEndpoint("mock:weather");
        weatherMock.reset();
        weatherMock.expectedMinimumMessageCount(1);

        String weatherResponse =
                template().requestBody("direct:multiToolChat", "What's the weather in London?", String.class);

        assertThat(weatherResponse).isNotNull();
        assertThat(weatherResponse.toLowerCase()).containsAnyOf("london", "cloudy", "15", "weather");

        // Verify the weather tool was actually invoked
        weatherMock.assertIsSatisfied();
        assertThat(weatherMock.getReceivedCounter()).isGreaterThanOrEqualTo(1);

        // Test calculator tool
        MockEndpoint calculatorMock = getMockEndpoint("mock:calculator");
        calculatorMock.reset();
        calculatorMock.expectedMinimumMessageCount(1);

        String mathResponse = template().requestBody("direct:multiToolChat", "What is 5 plus 3?", String.class);

        assertThat(mathResponse).isNotNull();
        assertThat(mathResponse).containsAnyOf("8", "eight");

        // Verify the calculator tool was actually invoked
        calculatorMock.assertIsSatisfied();
        assertThat(calculatorMock.getReceivedCounter()).isGreaterThanOrEqualTo(1);
    }

    @Test
    public void testToolWithParameters() throws InterruptedException {
        MockEndpoint weatherMock = getMockEndpoint("mock:weather");
        weatherMock.reset();
        weatherMock.expectedMinimumMessageCount(1);

        // Test that tool parameters are properly passed from LLM to Camel route
        String response =
                template().requestBody("direct:weatherChat", "Tell me about the weather in Paris", String.class);

        assertThat(response).isNotNull();
        // The LLM should have called the weather tool with city="Paris"
        assertThat(response.toLowerCase()).containsAnyOf("paris", "sunny", "22");

        // Verify the tool was invoked and received the correct parameter
        weatherMock.assertIsSatisfied();
        assertThat(weatherMock.getReceivedCounter()).isGreaterThanOrEqualTo(1);

        // Verify the city parameter was passed correctly
        String cityParam = weatherMock.getExchanges().get(0).getIn().getHeader("city", String.class);
        assertThat(cityParam).isNotNull();
        assertThat(cityParam.toLowerCase()).contains("paris");
    }

    @Test
    public void testChatWithSpecificTagsOnly() throws InterruptedException {
        // Test that only tools with matching tags are available

        // Weather chat should only have access to weather tools
        MockEndpoint weatherMock = getMockEndpoint("mock:weather");
        weatherMock.reset();
        weatherMock.expectedMinimumMessageCount(1);

        String weatherResponse =
                template().requestBody("direct:weatherChat", "What's the weather in Paris?", String.class);
        assertThat(weatherResponse).isNotNull();

        weatherMock.assertIsSatisfied();

        // Math chat should only have access to calculator tools
        MockEndpoint calculatorMock = getMockEndpoint("mock:calculator");
        calculatorMock.reset();
        calculatorMock.expectedMinimumMessageCount(1);

        String mathResponse = template()
                .requestBody(
                        "direct:mathChat", "Calculate the following mathematical expression 5 plus 3", String.class);
        assertThat(mathResponse).isNotNull();
        assertThat(mathResponse).containsAnyOf("8", "eight");

        calculatorMock.assertIsSatisfied();
    }

    @Test
    public void testConversationWithToolCalls() throws InterruptedException {
        MockEndpoint weatherMock = getMockEndpoint("mock:weather");
        weatherMock.reset();
        weatherMock.expectedMinimumMessageCount(1);

        // Test a conversation where tools might be called
        String response = template()
                .requestBody(
                        "direct:weatherChat",
                        "What's the weather in Paris and London? Please check both cities.",
                        String.class);

        assertThat(response).isNotNull();
        // The LLM should respond with something about weather
        assertThat(response.toLowerCase())
                .containsAnyOf("paris", "london", "weather", "sunny", "cloudy", "temperature");

        // Verify the weather tool was invoked at least once
        weatherMock.assertIsSatisfied();
        int actualCalls = weatherMock.getReceivedCounter();
        assertThat(actualCalls).isGreaterThanOrEqualTo(1);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                bindChatModel(getCamelContext());

                // Define weather tool - gets weather for a specified city
                from("spring-ai-tools:weather?tags=weather&description=Get current weather for a city&parameter.city=string")
                        .log("Weather tool called with city: ${header.city}")
                        .process(exchange -> {
                            String city = exchange.getIn().getHeader("city", String.class);
                            String weather;

                            if (city != null && city.toLowerCase().contains("paris")) {
                                weather = WEATHER_PARIS;
                            } else if (city != null && city.toLowerCase().contains("london")) {
                                weather = WEATHER_LONDON;
                            } else {
                                weather = "Weather information not available for " + city;
                            }

                            exchange.getIn().setBody(weather);
                        })
                        .log("Weather tool returning: ${body}")
                        .to("mock:weather");

                // Define calculator tool - performs simple calculations
                from("spring-ai-tools:calculator?tags=math&description=Calculate mathematical expressions&parameter.expression=string")
                        .process(exchange -> {
                            String expression = exchange.getIn().getHeader("expression", String.class);
                            String result;

                            // Simple calculator for demonstration
                            if (expression != null && expression.contains("5") && expression.contains("3")) {
                                result = CALCULATOR_RESULT;
                            } else {
                                result = "Unable to calculate: " + expression;
                            }

                            exchange.getIn().setBody(result);
                        })
                        .to("mock:calculator");

                // Chat endpoint with weather tools only
                from("direct:weatherChat").to("spring-ai-chat:weatherChat?tags=weather&chatModel=#chatModel");

                // Chat endpoint with math tools only
                from("direct:mathChat").to("spring-ai-chat:mathChat?tags=math&chatModel=#chatModel");

                // Chat endpoint with multiple tools (weather + math)
                from("direct:multiToolChat").to("spring-ai-chat:multiToolChat?tags=weather,math&chatModel=#chatModel");
            }
        };
    }
}
