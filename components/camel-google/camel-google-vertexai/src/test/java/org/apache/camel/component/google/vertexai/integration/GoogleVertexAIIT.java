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

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.vertexai.GoogleVertexAIConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for Google Vertex AI component.
 *
 * To run this test, set the following system properties: -
 * google.vertexai.serviceAccountKey=/path/to/service-account-key.json - google.vertexai.project=your-project-id -
 * google.vertexai.location=us-central1 (optional, defaults to us-central1) - google.vertexai.model=gemini-2.5-flash
 * (optional, defaults to gemini-2.5-flash)
 *
 * Example Maven command: mvn test -Dtest=GoogleVertexAIIT \ -Dgoogle.vertexai.serviceAccountKey=/path/to/key.json \
 * -Dgoogle.vertexai.project=my-project
 */
@EnabledIfSystemProperty(named = "google.vertexai.serviceAccountKey", matches = ".*",
                         disabledReason = "System property google.vertexai.serviceAccountKey not provided")
public class GoogleVertexAIIT extends CamelTestSupport {

    final String serviceAccountKeyFile = System.getProperty("google.vertexai.serviceAccountKey");
    final String project = System.getProperty("google.vertexai.project");
    final String location = System.getProperty("google.vertexai.location", "us-central1");
    final String model = System.getProperty("google.vertexai.model", "gemini-2.5-flash");

    @EndpointInject("mock:generateText")
    private MockEndpoint mockGenerateText;

    @EndpointInject("mock:generateChat")
    private MockEndpoint mockGenerateChat;

    @EndpointInject("mock:generateCode")
    private MockEndpoint mockGenerateCode;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {

                // Generate text
                from("direct:generateText")
                        .to("google-vertexai:" + project + ":" + location + ":" + model
                            + "?serviceAccountKey=file:" + serviceAccountKeyFile
                            + "&operation=generateText")
                        .to("mock:generateText");

                // Generate chat
                from("direct:generateChat")
                        .to("google-vertexai:" + project + ":" + location + ":" + model
                            + "?serviceAccountKey=file:" + serviceAccountKeyFile
                            + "&operation=generateChat")
                        .to("mock:generateChat");

                // Generate code
                from("direct:generateCode")
                        .to("google-vertexai:" + project + ":" + location + ":" + model
                            + "?serviceAccountKey=file:" + serviceAccountKeyFile
                            + "&operation=generateCode")
                        .to("mock:generateCode");
            }
        };
    }

    @Test
    public void testGenerateText() throws Exception {
        mockGenerateText.expectedMessageCount(1);

        Exchange exchange = template.request("direct:generateText", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getMessage().setBody("Write a haiku about Apache Camel");
            }
        });

        String response = exchange.getMessage().getBody(String.class);
        assertNotNull(response, "Response should not be null");
        assertTrue(response.length() > 0, "Response should not be empty");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testGenerateChat() throws Exception {
        mockGenerateChat.expectedMessageCount(1);

        Exchange exchange = template.request("direct:generateChat", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getMessage().setBody("What is Apache Camel?");
            }
        });

        String response = exchange.getMessage().getBody(String.class);
        assertNotNull(response, "Response should not be null");
        assertTrue(response.length() > 0, "Response should not be empty");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testGenerateCode() throws Exception {
        mockGenerateCode.expectedMessageCount(1);

        Exchange exchange = template.request("direct:generateCode", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getMessage().setBody("Write a Java method to reverse a string");
            }
        });

        String response = exchange.getMessage().getBody(String.class);
        assertNotNull(response, "Response should not be null");
        assertTrue(response.length() > 0, "Response should not be empty");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testGenerateTextWithHeaders() throws Exception {
        mockGenerateText.expectedMessageCount(1);

        Exchange exchange = template.request("direct:generateText", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getMessage().setHeader(GoogleVertexAIConstants.TEMPERATURE, 0.5f);
                exchange.getMessage().setHeader(GoogleVertexAIConstants.MAX_OUTPUT_TOKENS, 512);
                exchange.getMessage().setBody("Explain quantum computing in one sentence");
            }
        });

        String response = exchange.getMessage().getBody(String.class);
        assertNotNull(response, "Response should not be null");
        assertTrue(response.length() > 0, "Response should not be empty");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testMultipleRequests() throws Exception {
        mockGenerateText.expectedMessageCount(3);

        String[] prompts = {
                "What is 2+2?",
                "Name a primary color",
                "What is the capital of France?"
        };

        for (String prompt : prompts) {
            Exchange exchange = template.request("direct:generateText", new Processor() {
                @Override
                public void process(Exchange exchange) {
                    exchange.getMessage().setBody(prompt);
                }
            });

            String response = exchange.getMessage().getBody(String.class);
            assertNotNull(response, "Response should not be null for prompt: " + prompt);
            assertTrue(response.length() > 0, "Response should not be empty for prompt: " + prompt);
        }

        MockEndpoint.assertIsSatisfied(context);
    }
}
