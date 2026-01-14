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

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
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
 * Integration tests for watsonx.ai deployment operations. These tests require a valid deployment ID and space ID to be
 * provided as system properties:
 * <ul>
 * <li>camel.ibm.watsonx.ai.deploymentId - The deployed model ID</li>
 * <li>camel.ibm.watsonx.ai.spaceId - The deployment space ID</li>
 * </ul>
 */
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "camel.ibm.watsonx.ai.apiKey", matches = ".+",
                                 disabledReason = "IBM watsonx.ai API Key not provided"),
        @EnabledIfSystemProperty(named = "camel.ibm.watsonx.ai.projectId", matches = ".+",
                                 disabledReason = "IBM watsonx.ai Project ID not provided"),
        @EnabledIfSystemProperty(named = "camel.ibm.watsonx.ai.deploymentId", matches = ".+",
                                 disabledReason = "IBM watsonx.ai Deployment ID not provided"),
        @EnabledIfSystemProperty(named = "camel.ibm.watsonx.ai.spaceId", matches = ".+",
                                 disabledReason = "IBM watsonx.ai Space ID not provided")
})
public class WatsonxAiDeploymentIT extends WatsonxAiTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(WatsonxAiDeploymentIT.class);
    private static String deploymentId;
    private static String spaceId;

    static {
        deploymentId = System.getProperty("camel.ibm.watsonx.ai.deploymentId");
        spaceId = System.getProperty("camel.ibm.watsonx.ai.spaceId");
    }

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint mockResult;

    @BeforeEach
    public void resetMocks() {
        mockResult.reset();
    }

    @Test
    public void testDeploymentGenerate() throws Exception {
        mockResult.expectedMessageCount(1);

        final String prompt = "What is 2 + 2?";

        template.send("direct:deploymentGenerate", exchange -> {
            exchange.getIn().setBody(prompt);
            exchange.getIn().setHeader(WatsonxAiConstants.DEPLOYMENT_ID, deploymentId);
        });

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        String body = exchange.getIn().getBody(String.class);

        assertNotNull(body, "Response body should not be null");
        assertFalse(body.isEmpty(), "Response should not be empty");

        LOG.info("Prompt: {}", prompt);
        LOG.info("Generated response: {}", body);

        // Verify metadata headers
        String generatedText = exchange.getIn().getHeader(WatsonxAiConstants.GENERATED_TEXT, String.class);
        Integer inputTokens = exchange.getIn().getHeader(WatsonxAiConstants.INPUT_TOKEN_COUNT, Integer.class);
        Integer outputTokens = exchange.getIn().getHeader(WatsonxAiConstants.OUTPUT_TOKEN_COUNT, Integer.class);

        assertNotNull(generatedText, "Generated text header should be set");
        assertEquals(body, generatedText, "Body and generated text header should match");

        LOG.info("Input tokens: {}, Output tokens: {}", inputTokens, outputTokens);
    }

    @Test
    public void testDeploymentChat() throws Exception {
        mockResult.expectedMessageCount(1);

        final String message = "What is the capital of France?";

        template.send("direct:deploymentChat", exchange -> {
            exchange.getIn().setBody(message);
            exchange.getIn().setHeader(WatsonxAiConstants.DEPLOYMENT_ID, deploymentId);
        });

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        String body = exchange.getIn().getBody(String.class);

        assertNotNull(body, "Response body should not be null");
        assertFalse(body.isEmpty(), "Response should not be empty");

        LOG.info("Message: {}", message);
        LOG.info("Chat response: {}", body);

        // Verify metadata headers
        String stopReason = exchange.getIn().getHeader(WatsonxAiConstants.STOP_REASON, String.class);
        assertNotNull(stopReason, "Stop reason header should be set");
        LOG.info("Stop reason: {}", stopReason);
    }

    @Test
    public void testDeploymentInfo() throws Exception {
        mockResult.expectedMessageCount(1);

        template.send("direct:deploymentInfo", exchange -> {
            exchange.getIn().setHeader(WatsonxAiConstants.DEPLOYMENT_ID, deploymentId);
            exchange.getIn().setHeader(WatsonxAiConstants.SPACE_ID, spaceId);
        });

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        Object body = exchange.getIn().getBody();

        assertNotNull(body, "Response body should not be null");

        // Verify response headers
        String deploymentName = exchange.getIn().getHeader(WatsonxAiConstants.DEPLOYMENT_NAME, String.class);
        String deploymentAssetType = exchange.getIn().getHeader(WatsonxAiConstants.DEPLOYMENT_ASSET_TYPE, String.class);
        String deploymentStatus = exchange.getIn().getHeader(WatsonxAiConstants.DEPLOYMENT_STATUS, String.class);

        assertNotNull(deploymentName, "Deployment name header should be set");
        assertNotNull(deploymentAssetType, "Deployment asset type header should be set");
        assertNotNull(deploymentStatus, "Deployment status header should be set");

        LOG.info("Deployment Name: {}", deploymentName);
        LOG.info("Deployment Asset Type: {}", deploymentAssetType);
        LOG.info("Deployment Status: {}", deploymentStatus);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:deploymentInfo")
                        .to(buildDeploymentEndpointUri("deploymentInfo"))
                        .to("mock:result");

                from("direct:deploymentGenerate")
                        .to(buildDeploymentEndpointUri("deploymentGenerate"))
                        .to("mock:result");

                from("direct:deploymentChat")
                        .to(buildDeploymentEndpointUri("deploymentChat"))
                        .to("mock:result");
            }
        };
    }

    private String buildDeploymentEndpointUri(String operation) {
        StringBuilder uri = new StringBuilder("ibm-watsonx-ai://default");
        uri.append("?apiKey=RAW(").append(apiKey).append(")");
        uri.append("&baseUrl=").append(baseUrl);
        uri.append("&operation=").append(operation);
        return uri.toString();
    }
}
