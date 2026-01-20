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
package org.apache.camel.component.google.vertexai.integration;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.vertexai.GoogleVertexAIConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for Google Vertex AI rawPredict operation with partner models (Claude, Llama, Mistral).
 * <p>
 * This test demonstrates how to use the rawPredict operation to call partner models on Vertex AI.
 * </p>
 *
 * <h2>Prerequisites</h2>
 * <ul>
 * <li>Enable Vertex AI API in your GCP project</li>
 * <li>Enable access to partner models in Vertex AI Model Garden</li>
 * <li>Create a service account with appropriate permissions</li>
 * </ul>
 *
 * <h2>Required System Properties</h2>
 * <ul>
 * <li>{@code google.vertexai.serviceAccountKey} - Path to service account JSON key file</li>
 * <li>{@code google.vertexai.project} - GCP project ID</li>
 * <li>{@code google.vertexai.location} - GCP region (default: us-east5 for Claude)</li>
 * <li>{@code google.vertexai.claude.model} - Claude model ID (default: claude-3-5-sonnet-v2@20241022)</li>
 * </ul>
 *
 * <h2>Example Maven Command</h2>
 *
 * <pre>
 * mvn test -Dtest=GoogleVertexAIRawPredictIT \
 *   -Dgoogle.vertexai.serviceAccountKey=/path/to/key.json \
 *   -Dgoogle.vertexai.project=my-project \
 *   -Dgoogle.vertexai.location=global
 * </pre>
 */
@EnabledIfSystemProperty(named = "google.vertexai.serviceAccountKey", matches = ".*",
                         disabledReason = "System property google.vertexai.serviceAccountKey not provided")
public class GoogleVertexAIRawPredictIT extends CamelTestSupport {

    // System properties
    final String serviceAccountKeyFile = System.getProperty("google.vertexai.serviceAccountKey");
    final String project = System.getProperty("google.vertexai.project");
    // Use a regional endpoint for partner models (us-east5 is recommended for Claude)
    // Note: "global" is automatically mapped to us-east5 for gRPC-based rawPredict
    final String location = System.getProperty("google.vertexai.location", "us-east5");
    // Use Claude Sonnet 4 as default
    final String claudeModel = System.getProperty("google.vertexai.claude.model", "claude-sonnet-4@20250514");

    @EndpointInject("mock:rawPredict")
    private MockEndpoint mockRawPredict;

    @EndpointInject("mock:rawPredictJson")
    private MockEndpoint mockRawPredictJson;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {

                // Route for rawPredict with simple text prompt (auto-wrapped)
                // Use low maxOutputTokens to minimize quota usage
                from("direct:rawPredictClaude")
                        .to("google-vertexai:" + project + ":" + location + ":" + claudeModel
                            + "?serviceAccountKey=file:" + serviceAccountKeyFile
                            + "&operation=rawPredict"
                            + "&publisher=anthropic"
                            + "&maxOutputTokens=50")
                        .to("mock:rawPredict");

                // Route for rawPredict with custom JSON body
                from("direct:rawPredictClaudeJson")
                        .to("google-vertexai:" + project + ":" + location + ":" + claudeModel
                            + "?serviceAccountKey=file:" + serviceAccountKeyFile
                            + "&operation=rawPredict"
                            + "&publisher=anthropic"
                            + "&maxOutputTokens=50")
                        .to("mock:rawPredictJson");
            }
        };
    }

    /**
     * Test rawPredict with a simple text prompt. The component automatically wraps the prompt in the required Anthropic
     * format.
     */
    @Test
    public void testRawPredictClaudeWithSimplePrompt() throws Exception {
        mockRawPredict.expectedMessageCount(1);

        Exchange exchange = template.request("direct:rawPredictClaude", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getMessage().setBody("What is Apache Camel? Answer in one sentence.");
            }
        });

        String response = exchange.getMessage().getBody(String.class);
        assertNotNull(response, "Response should not be null");
        assertFalse(response.isEmpty(), "Response should not be empty");

        // Verify headers
        String publisher = exchange.getMessage().getHeader(GoogleVertexAIConstants.PUBLISHER, String.class);
        assertNotNull(publisher, "Publisher header should be set");

        // Raw JSON response should also be available
        String rawResponse = exchange.getMessage().getHeader(GoogleVertexAIConstants.RAW_RESPONSE, String.class);
        assertNotNull(rawResponse, "Raw response header should be set");
        assertTrue(rawResponse.contains("content"), "Raw response should contain 'content' field");

        MockEndpoint.assertIsSatisfied(context);
    }

    /**
     * Test rawPredict with a custom JSON request body. This gives full control over the request format.
     */
    @Test
    public void testRawPredictClaudeWithJsonBody() throws Exception {
        mockRawPredictJson.expectedMessageCount(1);

        Exchange exchange = template.request("direct:rawPredictClaudeJson", new Processor() {
            @Override
            public void process(Exchange exchange) {
                // Build custom request body with minimal tokens to avoid quota issues
                String jsonRequest = """
                        {
                            "anthropic_version": "vertex-2023-10-16",
                            "max_tokens": 30,
                            "temperature": 0.5,
                            "messages": [
                                {
                                    "role": "user",
                                    "content": "What is 1+1? Answer with just the number."
                                }
                            ]
                        }
                        """;
                exchange.getMessage().setBody(jsonRequest);
            }
        });

        String response = exchange.getMessage().getBody(String.class);
        assertNotNull(response, "Response should not be null");
        assertFalse(response.isEmpty(), "Response should not be empty");

        MockEndpoint.assertIsSatisfied(context);
    }

    /**
     * Test rawPredict with a Map request body. The component converts it to JSON.
     */
    @Test
    public void testRawPredictClaudeWithMapBody() throws Exception {
        mockRawPredict.reset();
        mockRawPredict.expectedMessageCount(1);

        Exchange exchange = template.request("direct:rawPredictClaude", new Processor() {
            @Override
            public void process(Exchange exchange) {
                Map<String, Object> request = new HashMap<>();
                request.put("max_tokens", 20);
                request.put("messages", new Object[] {
                        Map.of("role", "user", "content", "What is 2+2? Just answer with the number.")
                });
                exchange.getMessage().setBody(request);
            }
        });

        String response = exchange.getMessage().getBody(String.class);
        assertNotNull(response, "Response should not be null");
        // Response should contain "4"
        assertTrue(response.contains("4"), "Response should contain the answer '4'");

        MockEndpoint.assertIsSatisfied(context);
    }

    /**
     * Test rawPredict with system prompt (Claude supports system messages).
     */
    @Test
    public void testRawPredictClaudeWithSystemPrompt() throws Exception {
        mockRawPredictJson.reset();
        mockRawPredictJson.expectedMessageCount(1);

        Exchange exchange = template.request("direct:rawPredictClaudeJson", new Processor() {
            @Override
            public void process(Exchange exchange) {
                String jsonRequest = """
                        {
                            "anthropic_version": "vertex-2023-10-16",
                            "max_tokens": 50,
                            "system": "You are a helpful assistant. Be brief.",
                            "messages": [
                                {
                                    "role": "user",
                                    "content": "Say hello"
                                }
                            ]
                        }
                        """;
                exchange.getMessage().setBody(jsonRequest);
            }
        });

        String response = exchange.getMessage().getBody(String.class);
        assertNotNull(response, "Response should not be null");
        assertFalse(response.isEmpty(), "Response should not be empty");

        MockEndpoint.assertIsSatisfied(context);
    }

    /**
     * Test multiple sequential requests to verify connection pooling works correctly.
     */
    @Test
    public void testMultipleRawPredictRequests() throws Exception {
        mockRawPredict.reset();
        mockRawPredict.expectedMessageCount(3);

        String[] prompts = {
                "What is 1+1?",
                "What is 2+2?",
                "What is 3+3?"
        };

        for (String prompt : prompts) {
            Exchange exchange = template.request("direct:rawPredictClaude", new Processor() {
                @Override
                public void process(Exchange exchange) {
                    exchange.getMessage().setBody(prompt + " Answer with just the number.");
                }
            });

            String response = exchange.getMessage().getBody(String.class);
            assertNotNull(response, "Response should not be null for prompt: " + prompt);
            assertFalse(response.isEmpty(), "Response should not be empty for prompt: " + prompt);
        }

        MockEndpoint.assertIsSatisfied(context);
    }
}
