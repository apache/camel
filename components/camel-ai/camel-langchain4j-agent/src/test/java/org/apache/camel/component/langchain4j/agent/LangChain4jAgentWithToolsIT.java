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
package org.apache.camel.component.langchain4j.agent;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static java.time.Duration.ofSeconds;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "OPENAI_API_KEY", matches = ".*", disabledReason = "OpenAI API key required")
public class LangChain4jAgentWithToolsIT extends CamelTestSupport {

    private static final String USER_DB_NAME = "John Doe";
    private static final String WEATHER_INFO = "sunny, 25°C";
    private static final String WEATHER_INFO_1 = "sunny";
    private static final String WEATHER_INFO_2 = "25";

    protected ChatModel chatModel;
    private String openAiApiKey;

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();

        openAiApiKey = System.getenv("OPENAI_API_KEY");
        if (openAiApiKey == null || openAiApiKey.trim().isEmpty()) {
            throw new IllegalStateException("OPENAI_API_KEY system property is required for testing");
        }
        chatModel = createModel();
    }

    protected ChatModel createModel() {
        return OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName(GPT_4_O_MINI)
                .temperature(1.0)
                .timeout(ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Test
    void testAgentWithUserDatabaseTools() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:agent-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBody(
                "direct:agent-with-user-tools",
                "What is the name of user ID 123?",
                String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");
        assertTrue(response.contains(USER_DB_NAME),
                "Response should contain the user name from the database tool");
    }

    @Test
    void testAgentWithWeatherTools() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:agent-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBody(
                "direct:agent-with-weather-tools",
                "What's the weather like in New York?",
                String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");
        System.out.println("response testAgentWithWeatherTools is :: " + response);
        assertTrue(response.toLowerCase().contains(WEATHER_INFO_1),
                "Response should contain weather information from the weather tool");
        assertTrue(response.toLowerCase().contains(WEATHER_INFO_2),
                "Response should contain weather information from the weather tool");
    }

    @Test
    void testAgentWithMultipleTagsAndChatMessages() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:agent-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        String systemMessage = "You are a helpful assistant that can access user database and weather information. " +
                               "Use the available tools to provide accurate information.";
        String userMessage = "Can you tell me the name of user 123 and the weather in New York?";

        AiAgentBody aiAgentBody = new AiAgentBody(systemMessage, userMessage, null);

        String response = template.requestBody(
                "direct:agent-with-multiple-tools",
                aiAgentBody,
                String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");
        assertTrue(response.contains(USER_DB_NAME),
                "Response should contain the user name from the database tool");
        assertTrue(response.toLowerCase().contains(WEATHER_INFO_1),
                "Response should contain weather information from the weather tool");
        assertTrue(response.toLowerCase().contains(WEATHER_INFO_2),
                "Response should contain weather information from the weather tool");
    }

    @Test
    void testAgentWithConfiguredTags() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:agent-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBody(
                "direct:agent-with-configured-tags",
                "What's the weather in Paris?",
                String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");
        System.out.println("response testAgentWithConfiguredTags is :: " + response);
        assertTrue(response.toLowerCase().contains(WEATHER_INFO_1),
                "Response should contain weather information from the weather tool");
        assertTrue(response.toLowerCase().contains(WEATHER_INFO_2),
                "Response should contain weather information from the weather tool");
    }

    @Test
    void testAgentWithoutToolsNoTagsProvided() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:agent-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBody(
                "direct:agent-without-tools",
                "What is Apache Camel?",
                String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");
        assertTrue(response.contains("Apache Camel"),
                "Response should contain information about Apache Camel");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        this.context.getRegistry().bind("chatModel", chatModel);

        return new RouteBuilder() {
            public void configure() {
                from("direct:agent-with-user-tools")
                        .to("langchain4j-agent:test-agent?chatModel=#chatModel&tags=users")
                        .to("mock:agent-response");

                from("direct:agent-with-weather-tools")
                        .to("langchain4j-agent:test-agent?chatModel=#chatModel&tags=weather")
                        .to("mock:agent-response");

                from("direct:agent-with-multiple-tools")
                        .to("langchain4j-agent:test-agent?chatModel=#chatModel&tags=users,weather")
                        .to("mock:agent-response");

                from("direct:agent-with-configured-tags")
                        .to("langchain4j-agent:test-agent?chatModel=#chatModel&tags=weather")
                        .to("mock:agent-response");

                from("direct:agent-without-tools")
                        .to("langchain4j-agent:test-agent?chatModel=#chatModel")
                        .to("mock:agent-response");

                from("direct:agent-check-no-tools")
                        .to("langchain4j-agent:test-agent?chatModel=#chatModel&tags=nonexistent")
                        .to("mock:check-no-tools");

                from("langchain4j-tools:userDb?tags=users&description=Query user database by user ID&parameter.userId=integer")
                        .setBody(constant("{\"name\": \"" + USER_DB_NAME + "\", \"id\": \"123\"}"));

                from("langchain4j-tools:weatherService?tags=weather&description=Get weather information for a city&parameter.city=string")
                        .setBody(constant("{\"weather\": \"" + WEATHER_INFO + "\", \"city\": \"New York\"}"));

                from("langchain4j-tools:parisWeather?tags=weather&description=Get weather information for Paris&parameter.location=string")
                        .setBody(constant("{\"weather\": \"" + WEATHER_INFO + "\", \"city\": \"Paris\"}"));
            }
        };
    }
}
