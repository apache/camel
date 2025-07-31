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
import static org.apache.camel.component.langchain4j.agent.LangChain4jAgent.Headers.SYSTEM_MESSAGE;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "OPENAI_API_KEY", matches = ".*", disabledReason = "OpenAI API key required")
public class LangChain4jSimpleAgentIT extends CamelTestSupport {

    // Test constants
    private static final String TEST_USER_MESSAGE_SIMPLE = "What is Apache Camel?";
    private static final String TEST_USER_MESSAGE_STORY = "Write a short story about a lost cat.";
    private static final String TEST_SYSTEM_MESSAGE
            = """
                    You are a whimsical storyteller. Your responses should be imaginative, descriptive, and always include a touch of magic. Start every story with 'Once upon a starlit night...'""";
    private static final String EXPECTED_STORY_START = "Once upon a starlit night";
    private static final String EXPECTED_STORY_CONTENT = "cat";

    protected ChatModel chatModel;

    private String openAiApiKey;

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();

        openAiApiKey = System.getenv("OPENAI_API_KEY");
        if (openAiApiKey == null || openAiApiKey.trim().isEmpty()) {
            throw new IllegalStateException("OPENAI_API_KEY environment variable is required for testing");
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
    void testSimpleUserMessage() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBody("direct:send-simple-user-message", TEST_USER_MESSAGE_SIMPLE, String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");
        assertNotEquals(TEST_USER_MESSAGE_SIMPLE, response, "AI response should be different from the input message");
        assertTrue(response.contains("Apache Camel"), "Response should contain information about 'Apache Camel'");
    }

    @Test
    void testSimpleUserMessageWithHeaderPrompt() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBodyAndHeader("direct:send-simple-user-message",
                TEST_USER_MESSAGE_STORY, SYSTEM_MESSAGE, TEST_SYSTEM_MESSAGE, String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");
        assertNotEquals(TEST_USER_MESSAGE_STORY, response, "AI response should be different from the input message");
        assertTrue(response.contains(EXPECTED_STORY_START), "Response should start with the expected magical opening phrase");
        assertTrue(response.contains(EXPECTED_STORY_CONTENT), "Response should contain content about a cat as requested");
    }

    @Test
    void testSimpleUserMessageWithBodyBean() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        AiAgentBody body = new AiAgentBody()
                .withSystemMessage(TEST_SYSTEM_MESSAGE)
                .withUserMessage(TEST_USER_MESSAGE_STORY);

        String response = template.requestBody("direct:send-simple-user-message", body, String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");
        assertNotEquals(TEST_USER_MESSAGE_STORY, response, "AI response should be different from the input message");
        assertTrue(response.contains(EXPECTED_STORY_START), "Response should start with the expected magical opening phrase");
        assertTrue(response.contains(EXPECTED_STORY_CONTENT), "Response should contain content about a cat as requested");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        this.context.getRegistry().bind("chatModel", chatModel);

        return new RouteBuilder() {
            public void configure() {
                from("direct:send-simple-user-message")
                        .to("langchain4j-agent:test-agent?chatModel=#chatModel")
                        .to("mock:response");
            }
        };
    }
}
