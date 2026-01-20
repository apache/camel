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

import java.net.URL;
import java.util.List;

import dev.langchain4j.model.chat.ChatModel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.apache.camel.component.langchain4j.agent.api.AgentWithoutMemory;
import org.apache.camel.component.langchain4j.agent.api.Headers;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for WrappedFile support in the LangChain4j Agent component. Tests the ability to process files from
 * the file component directly, with automatic Content type conversion based on MIME type.
 * <p>
 * Requires environment variables to be set:
 * <ul>
 * <li>API_KEY - The API key for the LLM provider</li>
 * <li>MODEL_PROVIDER - The provider name (e.g., "openai", "gemini", "ollama")</li>
 * <li>MODEL_BASE_URL - (Optional) Custom base URL for OpenAI-compatible endpoints</li>
 * <li>MODEL_NAME - (Optional) Custom model name</li>
 * </ul>
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Requires too much network resources")
public class LangChain4jAgentWrappedFileIT extends CamelTestSupport {

    private static final String IMAGE_ROUTE_ID = "image-route";
    private static final String PDF_ROUTE_ID = "pdf-route";

    protected ChatModel chatModel;
    private String resourcesPath;

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();

        if (!ModelHelper.hasEnvironmentConfiguration()) {
            throw new IllegalStateException(
                    "This test requires environment variables: API_KEY, MODEL_PROVIDER. "
                                            + "Optionally: MODEL_BASE_URL, MODEL_NAME");
        }

        chatModel = ModelHelper.loadFromEnv();

        // Get the path to test resources
        URL resourceUrl = getClass().getClassLoader().getResource("camel-logo.png");
        assertNotNull(resourceUrl, "Test resources not found");
        resourcesPath = resourceUrl.getPath().replace("/camel-logo.png", "");
    }

    /**
     * Tests that an image file from the file component is automatically converted to ImageContent and processed by the
     * agent.
     */
    @Test
    void testImageFileFromFileComponent() throws Exception {
        // Start only the image route
        context.getRouteController().startRoute(IMAGE_ROUTE_ID);

        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:image-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        // Wait for the file to be processed
        mockEndpoint.assertIsSatisfied(60000);

        // Verify the response
        String response = mockEndpoint.getExchanges().get(0).getIn().getBody(String.class);
        assertNotNull(response, "Response should not be null");
        assertTrue(response.length() > 0, "Response should not be empty");
    }

    /**
     * Tests that a PDF file from the file component is automatically converted to PdfFileContent and processed by the
     * agent.
     */
    @Test
    void testPdfFileFromFileComponent() throws Exception {
        // Start only the PDF route
        context.getRouteController().startRoute(PDF_ROUTE_ID);

        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:pdf-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        // Wait for the file to be processed
        mockEndpoint.assertIsSatisfied(60000);

        // Verify the response
        String response = mockEndpoint.getExchanges().get(0).getIn().getBody(String.class);
        assertNotNull(response, "Response should not be null");
        assertTrue(response.length() > 0, "Response should not be empty");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        AgentConfiguration configuration = new AgentConfiguration()
                .withChatModel(chatModel)
                .withInputGuardrailClasses(List.of())
                .withOutputGuardrailClasses(List.of());

        Agent agent = new AgentWithoutMemory(configuration);

        this.context.getRegistry().bind("fileAgent", agent);

        return new RouteBuilder() {
            public void configure() {
                // Route for processing image files from resources folder (starts stopped)
                from("file:" + resourcesPath + "?noop=true&include=.*\\.png")
                        .routeId(IMAGE_ROUTE_ID)
                        .autoStartup(false)
                        .setHeader(Headers.USER_MESSAGE, constant("What do you see in this image? Describe it briefly."))
                        .to("langchain4j-agent:describe?agent=#fileAgent")
                        .to("mock:image-response");

                // Route for processing PDF files from resources folder (starts stopped)
                from("file:" + resourcesPath + "?noop=true&include=.*\\.pdf")
                        .routeId(PDF_ROUTE_ID)
                        .autoStartup(false)
                        .setHeader(Headers.USER_MESSAGE, constant("What is this document about? Summarize it briefly."))
                        .to("langchain4j-agent:describe?agent=#fileAgent")
                        .to("mock:pdf-response");
            }
        };
    }
}
