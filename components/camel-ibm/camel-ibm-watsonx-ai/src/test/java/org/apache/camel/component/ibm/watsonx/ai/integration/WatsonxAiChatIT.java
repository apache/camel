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
package org.apache.camel.component.ibm.watsonx.ai.integration;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.ibm.watsonx.ai.chat.model.SystemMessage;
import com.ibm.watsonx.ai.chat.model.UserMessage;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for watsonx.ai chat operations. These tests require valid IBM Cloud credentials to be provided as
 * system properties:
 * <ul>
 * <li>camel.ibm.watsonx.ai.apiKey - IBM Cloud API key</li>
 * <li>camel.ibm.watsonx.ai.projectId - watsonx.ai project ID</li>
 * <li>camel.ibm.watsonx.ai.baseUrl - watsonx.ai base URL (optional, defaults to us-south)</li>
 * </ul>
 *
 * To run these tests, execute:
 *
 * <pre>
 * mvn verify -Dcamel.ibm.watsonx.ai.apiKey=YOUR_API_KEY -Dcamel.ibm.watsonx.ai.projectId=YOUR_PROJECT_ID
 * </pre>
 */
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "camel.ibm.watsonx.ai.apiKey", matches = ".+",
                                 disabledReason = "IBM watsonx.ai API Key not provided"),
        @EnabledIfSystemProperty(named = "camel.ibm.watsonx.ai.projectId", matches = ".+",
                                 disabledReason = "IBM watsonx.ai Project ID not provided")
})
public class WatsonxAiChatIT extends WatsonxAiTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(WatsonxAiChatIT.class);

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint mockResult;

    @BeforeEach
    public void resetMocks() {
        mockResult.reset();
    }

    @Test
    public void testSimpleChat() throws Exception {
        mockResult.expectedMessageCount(1);

        // Simple string body is converted to UserMessage
        template.sendBody("direct:chat", "What is 2 + 2? Answer with just the number.");

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        String response = exchange.getIn().getBody(String.class);

        assertNotNull(response, "Response should not be null");
        assertFalse(response.isEmpty(), "Response should not be empty");

        LOG.info("Simple chat response: {}", response);

        // Verify metadata headers are set
        Integer inputTokens = exchange.getIn().getHeader(WatsonxAiConstants.INPUT_TOKEN_COUNT, Integer.class);
        Integer outputTokens = exchange.getIn().getHeader(WatsonxAiConstants.OUTPUT_TOKEN_COUNT, Integer.class);

        assertNotNull(inputTokens, "Input token count should be set");
        assertNotNull(outputTokens, "Output token count should be set");

        LOG.info("Input tokens: {}, Output tokens: {}", inputTokens, outputTokens);
    }

    @Test
    public void testChatWithMessages() throws Exception {
        mockResult.expectedMessageCount(1);

        template.send("direct:chat", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(WatsonxAiConstants.MESSAGES, List.of(
                        SystemMessage.of("You are a helpful assistant. Keep your answers brief."),
                        UserMessage.text("What is the capital of Italy?")));
            }
        });

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        String response = exchange.getIn().getBody(String.class);

        assertNotNull(response, "Response should not be null");
        assertFalse(response.isEmpty(), "Response should not be empty");

        LOG.info("Chat with messages response: {}", response);
    }

    @Test
    public void testStreamingChat() throws Exception {
        mockResult.expectedMessageCount(1);

        final StringBuilder streamedContent = new StringBuilder();
        final AtomicInteger chunkCount = new AtomicInteger(0);

        Consumer<String> streamHandler = chunk -> {
            streamedContent.append(chunk);
            chunkCount.incrementAndGet();
            LOG.debug("Received chunk {}: {}", chunkCount.get(), chunk);
        };

        template.send("direct:chatStreaming", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(WatsonxAiConstants.MESSAGES, List.of(
                        UserMessage.text("Tell me a very short joke about programming.")));
                exchange.getIn().setHeader(WatsonxAiConstants.STREAM_CONSUMER, streamHandler);
            }
        });

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        String fullResponse = exchange.getIn().getBody(String.class);

        assertNotNull(fullResponse, "Full response should not be null");
        assertFalse(fullResponse.isEmpty(), "Full response should not be empty");
        assertEquals(streamedContent.toString(), fullResponse, "Streamed content should match full response");
        assertTrue(chunkCount.get() > 0, "Should have received at least one chunk");

        LOG.info("Streaming chat response ({} chunks): {}", chunkCount.get(), fullResponse);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:chat")
                        .to(buildEndpointUri("chat"))
                        .to("mock:result");

                from("direct:chatStreaming")
                        .to(buildEndpointUri("chatStreaming"))
                        .to("mock:result");
            }
        };
    }
}
