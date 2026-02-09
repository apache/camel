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
 * Integration tests for watsonx.ai tokenization operations. These tests require valid IBM Cloud credentials to be
 * provided as system properties:
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
public class WatsonxAiTokenizationIT extends WatsonxAiTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(WatsonxAiTokenizationIT.class);

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint mockResult;

    @BeforeEach
    public void resetMocks() {
        mockResult.reset();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSimpleTokenization() throws Exception {
        mockResult.expectedMessageCount(1);

        final String text = "Hello, world!";

        template.sendBody("direct:tokenize", text);

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        Integer tokenCount = exchange.getIn().getBody(Integer.class);

        assertNotNull(tokenCount, "Token count should not be null");
        assertTrue(tokenCount > 0, "Token count should be > 0");

        LOG.info("Input text: '{}'", text);
        LOG.info("Token count: {}", tokenCount);

        // Verify headers
        Integer tokenCountHeader = exchange.getIn().getHeader(WatsonxAiConstants.TOKEN_COUNT, Integer.class);
        List<String> tokens = exchange.getIn().getHeader(WatsonxAiConstants.TOKENS, List.class);

        assertNotNull(tokenCountHeader, "Token count header should be set");
        assertEquals(tokenCount, tokenCountHeader, "Token count should match header");
        assertNotNull(tokens, "Tokens list should be set");
        assertFalse(tokens.isEmpty(), "Tokens list should not be empty");
        assertEquals(tokenCount.intValue(), tokens.size(), "Token count should match tokens list size");

        LOG.info("Tokens: {}", tokens);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testTokenizationWithLongerText() throws Exception {
        mockResult.expectedMessageCount(1);

        final String text = "Apache Camel is an open-source integration framework that empowers you "
                            + "to quickly and easily integrate various systems consuming or producing data. "
                            + "It is based on Enterprise Integration Patterns.";

        template.sendBody("direct:tokenize", text);

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        Integer tokenCount = exchange.getIn().getBody(Integer.class);
        List<String> tokens = exchange.getIn().getHeader(WatsonxAiConstants.TOKENS, List.class);

        assertNotNull(tokenCount, "Token count should not be null");
        assertTrue(tokenCount > 10, "Longer text should have more tokens");

        LOG.info("Input text length: {} chars", text.length());
        LOG.info("Token count: {}", tokenCount);
        LOG.info("First 10 tokens: {}", tokens.subList(0, Math.min(10, tokens.size())));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testTokenizationViaInputHeader() throws Exception {
        mockResult.expectedMessageCount(1);

        final String text = "Testing tokenization via header";

        template.send("direct:tokenize", exchange -> {
            exchange.getIn().setHeader(WatsonxAiConstants.INPUT, text);
        });

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        Integer tokenCount = exchange.getIn().getBody(Integer.class);

        assertNotNull(tokenCount, "Token count should not be null");
        assertTrue(tokenCount > 0, "Token count should be > 0");

        LOG.info("Tokenization via header - Token count: {}", tokenCount);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testTokenizationWithSpecialCharacters() throws Exception {
        mockResult.expectedMessageCount(1);

        final String text = "Special chars: @#$%^&*() and emojis would go here";

        template.sendBody("direct:tokenize", text);

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        Integer tokenCount = exchange.getIn().getBody(Integer.class);
        List<String> tokens = exchange.getIn().getHeader(WatsonxAiConstants.TOKENS, List.class);

        assertNotNull(tokenCount, "Token count should not be null");
        assertTrue(tokenCount > 0, "Token count should be > 0");

        LOG.info("Text with special chars: '{}'", text);
        LOG.info("Token count: {}", tokenCount);
        LOG.info("Tokens: {}", tokens);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:tokenize")
                        .to(buildEndpointUri("tokenize"))
                        .to("mock:result");
            }
        };
    }
}
