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

import com.ibm.watsonx.ai.rerank.RerankResponse;
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
 * Integration tests for watsonx.ai rerank operations. These tests require valid IBM Cloud credentials to be provided as
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
public class WatsonxAiRerankIT extends WatsonxAiTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(WatsonxAiRerankIT.class);
    private static final String RERANK_MODEL = "cross-encoder/ms-marco-minilm-l-12-v2";

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
    public void testBasicRerank() throws Exception {
        mockResult.expectedMessageCount(1);

        final String query = "What is the best programming language for enterprise applications?";
        final List<String> documents = List.of(
                "Java is widely used in enterprise applications due to its stability and extensive ecosystem.",
                "Python is popular for machine learning and data science applications.",
                "JavaScript is the primary language for web development.",
                "Go is designed for cloud-native applications and microservices.",
                "Rust provides memory safety without garbage collection.");

        template.send("direct:rerank", exchange -> {
            exchange.getIn().setHeader(WatsonxAiConstants.RERANK_QUERY, query);
            exchange.getIn().setBody(documents);
        });

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        Object body = exchange.getIn().getBody();

        assertNotNull(body, "Response body should not be null");
        assertTrue(body instanceof List, "Response should be a list");

        List<RerankResponse.RerankResult> results = (List<RerankResponse.RerankResult>) body;
        assertFalse(results.isEmpty(), "Results should not be empty");
        assertEquals(documents.size(), results.size(), "Should have same number of results as documents");

        LOG.info("Query: {}", query);
        LOG.info("Rerank results:");
        for (RerankResponse.RerankResult result : results) {
            LOG.info("  Index: {}, Score: {}", result.index(), result.score());
        }

        // Verify results are sorted by score (descending)
        for (int i = 1; i < results.size(); i++) {
            assertTrue(results.get(i - 1).score() >= results.get(i).score(),
                    "Results should be sorted by score descending");
        }

        // Verify metadata headers
        Integer inputTokens = exchange.getIn().getHeader(WatsonxAiConstants.INPUT_TOKEN_COUNT, Integer.class);
        assertNotNull(inputTokens, "Input token count should be set");
        assertTrue(inputTokens > 0, "Input token count should be > 0");

        LOG.info("Input tokens: {}", inputTokens);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testRerankWithTopN() throws Exception {
        mockResult.expectedMessageCount(1);

        final String query = "Integration framework for enterprise";
        final List<String> documents = List.of(
                "Apache Camel is an open-source integration framework.",
                "Spring Integration provides integration patterns.",
                "MuleSoft is an integration platform.",
                "Apache Kafka is a distributed streaming platform.",
                "Docker is a containerization platform.");

        template.send("direct:rerankTopN", exchange -> {
            exchange.getIn().setHeader(WatsonxAiConstants.RERANK_QUERY, query);
            exchange.getIn().setHeader(WatsonxAiConstants.RERANK_TOP_N, 3);
            exchange.getIn().setBody(documents);
        });

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        List<RerankResponse.RerankResult> results = (List<RerankResponse.RerankResult>) exchange.getIn().getBody();

        assertNotNull(results, "Results should not be null");
        assertEquals(3, results.size(), "Should return only top 3 results");

        LOG.info("Query: {}", query);
        LOG.info("Top 3 rerank results:");
        for (RerankResponse.RerankResult result : results) {
            LOG.info("  Index: {}, Score: {}", result.index(), result.score());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testRerankWithInputsHeader() throws Exception {
        mockResult.expectedMessageCount(1);

        final String query = "Cloud computing services";
        final List<String> documents = List.of(
                "AWS provides cloud computing services.",
                "Database management systems store data.");

        template.send("direct:rerank", exchange -> {
            exchange.getIn().setHeader(WatsonxAiConstants.RERANK_QUERY, query);
            exchange.getIn().setHeader(WatsonxAiConstants.INPUTS, documents);
        });

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        List<RerankResponse.RerankResult> results = (List<RerankResponse.RerankResult>) exchange.getIn().getBody();

        assertNotNull(results, "Results should not be null");
        assertEquals(2, results.size(), "Should have 2 results");

        // The AWS document should score higher for "Cloud computing services"
        RerankResponse.RerankResult topResult = results.get(0);
        assertEquals(0, topResult.index(), "AWS document should be ranked first");

        LOG.info("Top result index: {} with score: {}", topResult.index(), topResult.score());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:rerank")
                        .to(buildEndpointUri("rerank", RERANK_MODEL))
                        .to("mock:result");

                from("direct:rerankTopN")
                        .to(buildEndpointUri("rerank", RERANK_MODEL))
                        .to("mock:result");
            }
        };
    }
}
