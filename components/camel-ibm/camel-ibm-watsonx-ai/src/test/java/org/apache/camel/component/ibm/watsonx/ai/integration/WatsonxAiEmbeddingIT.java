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
 * Integration tests for watsonx.ai embedding operations. These tests require valid IBM Cloud credentials to be provided
 * as system properties:
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
public class WatsonxAiEmbeddingIT extends WatsonxAiTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(WatsonxAiEmbeddingIT.class);
    private static final String EMBEDDING_MODEL = "ibm/granite-embedding-278m-multilingual";

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
    public void testSingleTextEmbedding() throws Exception {
        mockResult.expectedMessageCount(1);

        final String text = "Apache Camel is a versatile open-source integration framework";

        template.sendBody("direct:embed", text);

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        Object body = exchange.getIn().getBody();

        assertNotNull(body, "Response body should not be null");
        assertTrue(body instanceof List, "Response should be a list");

        List<List<Float>> embeddings = (List<List<Float>>) body;
        assertFalse(embeddings.isEmpty(), "Embeddings should not be empty");
        assertFalse(embeddings.get(0).isEmpty(), "First embedding vector should not be empty");

        LOG.info("Input text: {}", text);
        LOG.info("Embedding dimensions: {}", embeddings.get(0).size());

        // Verify metadata headers are set
        List<List<Float>> embeddingsHeader = exchange.getIn().getHeader(WatsonxAiConstants.EMBEDDINGS, List.class);
        Integer inputTokens = exchange.getIn().getHeader(WatsonxAiConstants.INPUT_TOKEN_COUNT, Integer.class);

        assertNotNull(embeddingsHeader, "Embeddings header should be set");
        assertNotNull(inputTokens, "Input token count should be set");
        assertTrue(inputTokens > 0, "Input token count should be > 0");

        LOG.info("Input tokens: {}", inputTokens);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testBatchEmbedding() throws Exception {
        mockResult.expectedMessageCount(1);

        final List<String> texts = List.of(
                "Apache Camel is an integration framework",
                "Spring Boot is a Java framework",
                "Kubernetes is a container orchestration platform");

        template.sendBody("direct:embed", texts);

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        List<List<Float>> embeddings = (List<List<Float>>) exchange.getIn().getBody();

        assertNotNull(embeddings, "Embeddings should not be null");
        assertEquals(3, embeddings.size(), "Should have 3 embeddings for 3 inputs");

        for (int i = 0; i < embeddings.size(); i++) {
            assertFalse(embeddings.get(i).isEmpty(), "Embedding " + i + " should not be empty");
            LOG.info("Text {}: {} chars -> {} dimensions", i, texts.get(i).length(), embeddings.get(i).size());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testEmbeddingViaInputsHeader() throws Exception {
        mockResult.expectedMessageCount(1);

        final List<String> texts = List.of("Hello world", "Goodbye world");

        template.send("direct:embed", exchange -> {
            exchange.getIn().setHeader(WatsonxAiConstants.INPUTS, texts);
        });

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        List<List<Float>> embeddings = (List<List<Float>>) exchange.getIn().getBody();

        assertNotNull(embeddings, "Embeddings should not be null");
        assertEquals(2, embeddings.size(), "Should have 2 embeddings");

        LOG.info("Embeddings generated via INPUTS header: {} vectors", embeddings.size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:embed")
                        .to(buildEndpointUri("embedding", EMBEDDING_MODEL))
                        .to("mock:result");
            }
        };
    }
}
