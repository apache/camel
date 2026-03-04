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

import java.io.InputStream;
import java.util.Base64;
import java.util.List;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.model.chat.ChatModel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.apache.camel.component.langchain4j.agent.api.AgentWithoutMemory;
import org.apache.camel.component.langchain4j.agent.api.AiAgentBody;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.ollama.services.OllamaService;
import org.apache.camel.test.infra.ollama.services.OllamaServiceFactory;
import org.apache.camel.test.infra.ollama.services.OpenAIService;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Integration tests for multimodal content support in the LangChain4j Agent component. Tests the ability to send both
 * TextContent and ImageContent to AI models.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Requires too much network resources")
public class LangChain4jAgentMultimodalityIT extends CamelTestSupport {

    private static final String TEST_IMAGE_PATH = "camel-logo.png";

    protected ChatModel chatModel;

    @RegisterExtension
    static OllamaService OLLAMA = OllamaServiceFactory.createSingletonService();

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();

        chatModel = ModelHelper.loadChatModel(OLLAMA);
    }

    @BeforeEach
    void skipIfOllama() {
        // Skip if not using OpenAI - Ollama doesn't support multimodal content
        boolean isOpenAI = OLLAMA instanceof OpenAIService;
        assumeFalse(!isOpenAI,
                "Skipping multimodality tests with Ollama: LangChain4j's Ollama provider does not support " +
                               "multiple content blocks in a single UserMessage. The provider's InternalOllamaHelper.toText() "
                               +
                               "calls UserMessage.singleText() which requires exactly one TextContent. " +
                               "Use OpenAI or Gemini providers for multimodal content testing.");
    }

    /**
     * Tests sending a message with TextContent. This validates that the Content parameter works correctly with simple
     * text content.
     */
    @Test
    void testTextContent() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        TextContent textContent = TextContent.from("This is additional context about Apache Camel integration framework.");

        AiAgentBody<TextContent> body = new AiAgentBody<TextContent>()
                .withUserMessage("What can you tell me about the text I provided?")
                .withContent(textContent);

        String response = template.requestBody("direct:multimodal-agent", body, String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");
        assertTrue(response.length() > 0, "AI response should not be empty");
        assertTrue(response.contains("Camel"), "AI response should contain Camel " + response);
    }

    /**
     * Tests sending a message with ImageContent. This validates that the agent can process image content for
     * vision-capable models.
     */
    @Test
    void testImageContent() throws Exception {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        // Load the test image
        byte[] imageBytes;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(TEST_IMAGE_PATH)) {
            if (is == null) {
                throw new IllegalStateException("Test image not found: " + TEST_IMAGE_PATH);
            }
            imageBytes = is.readAllBytes();
        }

        // Create ImageContent from base64-encoded image
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        Image image = Image.builder()
                .base64Data(base64Image)
                .mimeType("image/png")
                .build();
        ImageContent imageContent = ImageContent.from(image);

        AiAgentBody<ImageContent> body = new AiAgentBody<ImageContent>()
                .withUserMessage("What do you see in this image? Describe it briefly.")
                .withContent(imageContent);

        String response = template.requestBody("direct:multimodal-agent", body, String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");
        assertTrue(response.length() > 0, "AI response should not be empty");
        assertTrue(response.contains("Camel"), "AI response should contain Camel " + response);
    }

    /**
     * Tests sending a message with TextContent and a system message. This validates that the Content parameter works
     * correctly alongside system messages.
     */
    @Test
    void testTextContentWithSystemMessage() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        TextContent textContent = TextContent.from("Apache Camel is an open-source integration framework.");

        AiAgentBody<TextContent> body = new AiAgentBody<TextContent>()
                .withUserMessage("Summarize the provided text in one sentence.")
                .withSystemMessage("You are a technical documentation assistant. Be concise and accurate.")
                .withContent(textContent);

        String response = template.requestBody("direct:multimodal-agent", body, String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");
        assertTrue(response.length() > 0, "AI response should not be empty");
        assertTrue(response.contains("Camel"), "AI response should contain Camel " + response);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        AgentConfiguration configuration = new AgentConfiguration()
                .withChatModel(chatModel)
                .withInputGuardrailClasses(List.of())
                .withOutputGuardrailClasses(List.of());

        Agent multimodalAgent = new AgentWithoutMemory(configuration);

        this.context.getRegistry().bind("multimodalAgent", multimodalAgent);

        return new RouteBuilder() {
            public void configure() {
                from("direct:multimodal-agent")
                        .to("langchain4j-agent:multimodal?agent=#multimodalAgent")
                        .to("mock:response");
            }
        };
    }
}
