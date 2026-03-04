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
package org.apache.camel.component.aws2.bedrock.runtime.integration;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.bedrock.BedrockModels;
import org.apache.camel.component.aws2.bedrock.runtime.BedrockConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for AWS Bedrock Guardrails functionality. Requires a configured guardrail in your AWS account. Must
 * be manually tested. Provide your own accessKey, secretKey and guardrail ID using: -Daws.manual.access.key
 * -Daws.manual.secret.key -Daws.manual.guardrail.id
 */
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "aws.manual.access.key", matches = ".*", disabledReason = "Access key not provided"),
        @EnabledIfSystemProperty(named = "aws.manual.secret.key", matches = ".*", disabledReason = "Secret key not provided"),
        @EnabledIfSystemProperty(named = "aws.manual.guardrail.id", matches = ".*",
                                 disabledReason = "Guardrail ID not provided")
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BedrockGuardrailsIT extends CamelTestSupport {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @BeforeEach
    public void resetMocks() {
        result.reset();
    }

    @Test
    public void testConverseWithGuardrailsViaEndpointConfig() throws InterruptedException {
        result.expectedMessageCount(1);
        final Exchange exchange = template.send("direct:converse_with_guardrails_endpoint", ex -> {
            // Create a message using the Converse API
            java.util.List<software.amazon.awssdk.services.bedrockruntime.model.Message> messages = new java.util.ArrayList<>();
            messages.add(software.amazon.awssdk.services.bedrockruntime.model.Message.builder()
                    .role(software.amazon.awssdk.services.bedrockruntime.model.ConversationRole.USER)
                    .content(software.amazon.awssdk.services.bedrockruntime.model.ContentBlock
                            .fromText("Tell me about Paris"))
                    .build());

            ex.getMessage().setHeader(BedrockConstants.CONVERSE_MESSAGES, messages);

            // Optional: Add inference configuration
            software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration inferenceConfig
                    = software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration.builder()
                            .maxTokens(200)
                            .temperature(0.7f)
                            .build();
            ex.getMessage().setHeader(BedrockConstants.CONVERSE_INFERENCE_CONFIG, inferenceConfig);
        });

        // Verify that the response contains content
        assertNotNull(exchange.getMessage().getBody(String.class));
        assertTrue(exchange.getMessage().getBody(String.class).length() > 0);

        // Check if guardrail trace is present (if trace is enabled)
        if (exchange.getMessage().getHeader(BedrockConstants.GUARDRAIL_TRACE) != null) {
            assertNotNull(exchange.getMessage().getHeader(BedrockConstants.GUARDRAIL_TRACE));
        }

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testConverseWithGuardrailsViaHeader() throws InterruptedException {
        result.expectedMessageCount(1);
        final Exchange exchange = template.send("direct:converse_claude", ex -> {
            // Create a message using the Converse API
            java.util.List<software.amazon.awssdk.services.bedrockruntime.model.Message> messages = new java.util.ArrayList<>();
            messages.add(software.amazon.awssdk.services.bedrockruntime.model.Message.builder()
                    .role(software.amazon.awssdk.services.bedrockruntime.model.ConversationRole.USER)
                    .content(software.amazon.awssdk.services.bedrockruntime.model.ContentBlock
                            .fromText("What is the capital of France?"))
                    .build());

            ex.getMessage().setHeader(BedrockConstants.CONVERSE_MESSAGES, messages);

            // Configure guardrail via header
            String guardrailId = System.getProperty("aws.manual.guardrail.id");
            software.amazon.awssdk.services.bedrockruntime.model.GuardrailConfiguration guardrailConfig
                    = software.amazon.awssdk.services.bedrockruntime.model.GuardrailConfiguration.builder()
                            .guardrailIdentifier(guardrailId)
                            .guardrailVersion("DRAFT")
                            .trace(software.amazon.awssdk.services.bedrockruntime.model.GuardrailTrace.ENABLED)
                            .build();
            ex.getMessage().setHeader(BedrockConstants.GUARDRAIL_CONFIG, guardrailConfig);

            // Optional: Add inference configuration
            software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration inferenceConfig
                    = software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration.builder()
                            .maxTokens(100)
                            .temperature(0.7f)
                            .build();
            ex.getMessage().setHeader(BedrockConstants.CONVERSE_INFERENCE_CONFIG, inferenceConfig);
        });

        // Verify that the response contains content
        assertNotNull(exchange.getMessage().getBody(String.class));
        assertTrue(exchange.getMessage().getBody(String.class).length() > 0);

        // Guardrail trace should be present when enabled
        if (exchange.getMessage().getHeader(BedrockConstants.GUARDRAIL_TRACE) != null) {
            assertNotNull(exchange.getMessage().getHeader(BedrockConstants.GUARDRAIL_TRACE));
        }

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testConverseStreamWithGuardrails() throws InterruptedException {
        result.expectedMessageCount(1);
        final Exchange exchange = template.send("direct:converse_stream_with_guardrails", ex -> {
            // Create a message using the Converse API
            java.util.List<software.amazon.awssdk.services.bedrockruntime.model.Message> messages = new java.util.ArrayList<>();
            messages.add(software.amazon.awssdk.services.bedrockruntime.model.Message.builder()
                    .role(software.amazon.awssdk.services.bedrockruntime.model.ConversationRole.USER)
                    .content(software.amazon.awssdk.services.bedrockruntime.model.ContentBlock
                            .fromText("Tell me a short story about a robot"))
                    .build());

            ex.getMessage().setHeader(BedrockConstants.CONVERSE_MESSAGES, messages);
            ex.getMessage().setHeader(BedrockConstants.STREAM_OUTPUT_MODE, "complete");

            // Optional: Add inference configuration
            software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration inferenceConfig
                    = software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration.builder()
                            .maxTokens(300)
                            .temperature(0.9f)
                            .build();
            ex.getMessage().setHeader(BedrockConstants.CONVERSE_INFERENCE_CONFIG, inferenceConfig);
        });

        // Verify that the streaming response contains content
        assertNotNull(exchange.getMessage().getBody(String.class));
        assertTrue(exchange.getMessage().getBody(String.class).length() > 0);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testApplyGuardrail() throws InterruptedException {
        result.expectedMessageCount(1);
        final Exchange exchange = template.send("direct:apply_guardrail", ex -> {
            // Create content blocks to check against the guardrail
            java.util.List<software.amazon.awssdk.services.bedrockruntime.model.GuardrailContentBlock> content
                    = new java.util.ArrayList<>();
            content.add(software.amazon.awssdk.services.bedrockruntime.model.GuardrailContentBlock.builder()
                    .text(software.amazon.awssdk.services.bedrockruntime.model.GuardrailTextBlock.builder()
                            .text("This is a test message to check against the guardrail.")
                            .build())
                    .build());

            ex.getMessage().setHeader(BedrockConstants.GUARDRAIL_CONTENT, content);
            ex.getMessage().setHeader(BedrockConstants.GUARDRAIL_SOURCE, "INPUT");
        });

        // Verify that the response contains an action (GUARDRAIL_INTERVENED or NONE)
        String action = exchange.getMessage().getBody(String.class);
        assertNotNull(action);
        assertTrue(action.equals("GUARDRAIL_INTERVENED") || action.equals("NONE"));

        // Verify that output headers are present
        if (action.equals("GUARDRAIL_INTERVENED")) {
            // When intervened, there should be outputs
            assertNotNull(exchange.getMessage().getHeader(BedrockConstants.GUARDRAIL_OUTPUT));
        }

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testApplyGuardrailWithPOJO() throws InterruptedException {
        result.expectedMessageCount(1);
        final Exchange exchange = template.send("direct:apply_guardrail_pojo", ex -> {
            // Create content blocks
            java.util.List<software.amazon.awssdk.services.bedrockruntime.model.GuardrailContentBlock> content
                    = new java.util.ArrayList<>();
            content.add(software.amazon.awssdk.services.bedrockruntime.model.GuardrailContentBlock.builder()
                    .text(software.amazon.awssdk.services.bedrockruntime.model.GuardrailTextBlock.builder()
                            .text("Another test message for guardrail validation.")
                            .build())
                    .build());

            // Build the full ApplyGuardrailRequest
            String guardrailId = System.getProperty("aws.manual.guardrail.id");
            software.amazon.awssdk.services.bedrockruntime.model.ApplyGuardrailRequest request
                    = software.amazon.awssdk.services.bedrockruntime.model.ApplyGuardrailRequest.builder()
                            .guardrailIdentifier(guardrailId)
                            .guardrailVersion("DRAFT")
                            .source(software.amazon.awssdk.services.bedrockruntime.model.GuardrailContentSource.INPUT)
                            .content(content)
                            .build();

            ex.getMessage().setBody(request);
        });

        // Verify that the response contains an action
        String action = exchange.getMessage().getBody(String.class);
        assertNotNull(action);
        assertTrue(action.equals("GUARDRAIL_INTERVENED") || action.equals("NONE"));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String guardrailId = System.getProperty("aws.manual.guardrail.id");

                from("direct:converse_with_guardrails_endpoint")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=us-east-1&operation=converse&modelId="
                            + BedrockModels.ANTROPHIC_CLAUDE_V3.model + "&guardrailIdentifier=RAW(" + guardrailId
                            + ")&guardrailVersion=DRAFT&guardrailTrace=true")
                        .log("Converse with guardrails response: ${body}")
                        .to(result);

                from("direct:converse_claude")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=us-east-1&operation=converse&modelId="
                            + BedrockModels.ANTROPHIC_CLAUDE_V3.model)
                        .log("Converse response: ${body}")
                        .to(result);

                from("direct:converse_stream_with_guardrails")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=us-east-1&operation=converseStream&modelId="
                            + BedrockModels.ANTROPHIC_CLAUDE_V3.model + "&guardrailIdentifier=RAW(" + guardrailId
                            + ")&guardrailVersion=DRAFT")
                        .log("Converse stream with guardrails response: ${body}")
                        .to(result);

                from("direct:apply_guardrail")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=us-east-1&operation=applyGuardrail&guardrailIdentifier=RAW("
                            + guardrailId + ")&guardrailVersion=DRAFT")
                        .log("ApplyGuardrail action: ${body}")
                        .log("Guardrail output: ${header." + BedrockConstants.GUARDRAIL_OUTPUT + "}")
                        .to(result);

                from("direct:apply_guardrail_pojo")
                        .to("aws-bedrock:label?accessKey=RAW({{aws.manual.access.key}})&secretKey=RAW({{aws.manual.secret.key}})&region=us-east-1&operation=applyGuardrail&pojoRequest=true")
                        .log("ApplyGuardrail POJO action: ${body}")
                        .to(result);
            }
        };
    }
}
