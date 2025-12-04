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

package org.apache.camel.component.ibm.watson.language.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.ibm.watson.natural_language_understanding.v1.model.AnalysisResults;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ibm.watson.language.WatsonLanguageConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for Watson Natural Language Understanding operations.
 */
@EnabledIfSystemProperties({
    @EnabledIfSystemProperty(
            named = "camel.ibm.watson.apiKey",
            matches = ".*",
            disabledReason = "IBM Watson API Key not provided"),
    @EnabledIfSystemProperty(
            named = "camel.ibm.watson.serviceUrl",
            matches = ".*",
            disabledReason = "IBM Watson NLU Service URL not provided")
})
public class WatsonNluIT extends WatsonLanguageTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(WatsonNluIT.class);

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint mockResult;

    @BeforeEach
    public void resetMocks() {
        mockResult.reset();
    }

    @Test
    public void testAnalyzeTextSentimentPositive() throws Exception {
        mockResult.expectedMessageCount(1);

        final String positiveText = "I absolutely love this product! It's amazing and works perfectly!";

        template.send("direct:analyzeSentiment", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody(positiveText);
            }
        });

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        AnalysisResults results = exchange.getIn().getBody(AnalysisResults.class);

        assertNotNull(results);
        assertNotNull(results.getSentiment());
        assertNotNull(results.getSentiment().getDocument());

        String sentimentLabel = results.getSentiment().getDocument().getLabel();
        Double sentimentScore = results.getSentiment().getDocument().getScore();

        assertEquals("positive", sentimentLabel);
        assertTrue(sentimentScore > 0, "Positive text should have positive score");

        // Verify headers
        assertEquals(sentimentLabel, exchange.getIn().getHeader(WatsonLanguageConstants.SENTIMENT_LABEL));
        assertEquals(sentimentScore, exchange.getIn().getHeader(WatsonLanguageConstants.SENTIMENT_SCORE));

        LOG.info("Sentiment: {} (score: {})", sentimentLabel, sentimentScore);
    }

    @Test
    public void testAnalyzeTextSentimentNegative() throws Exception {
        mockResult.expectedMessageCount(1);

        final String negativeText = "This is terrible. I hate it and it doesn't work at all!";

        template.sendBody("direct:analyzeSentiment", negativeText);

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        AnalysisResults results = exchange.getIn().getBody(AnalysisResults.class);

        assertNotNull(results);
        String sentimentLabel = results.getSentiment().getDocument().getLabel();
        Double sentimentScore = results.getSentiment().getDocument().getScore();

        assertEquals("negative", sentimentLabel);
        assertTrue(sentimentScore < 0, "Negative text should have negative score");

        LOG.info("Sentiment: {} (score: {})", sentimentLabel, sentimentScore);
    }

    @Test
    public void testAnalyzeTextWithEntities() throws Exception {
        mockResult.expectedMessageCount(1);

        final String textWithEntities =
                "IBM Watson is an artificial intelligence platform developed by IBM. It was created in Yorktown Heights, New York.";

        template.send("direct:analyzeEntities", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody(textWithEntities);
            }
        });

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        AnalysisResults results = exchange.getIn().getBody(AnalysisResults.class);

        assertNotNull(results);
        assertNotNull(results.getEntities());
        assertFalse(results.getEntities().isEmpty(), "Should extract entities");

        LOG.info("Extracted entities: {}", results.getEntities().size());
        results.getEntities().forEach(entity -> {
            LOG.info("  - {} ({})", entity.getText(), entity.getType());
        });
    }

    @Test
    public void testAnalyzeTextWithKeywords() throws Exception {
        mockResult.expectedMessageCount(1);

        final String textWithKeywords =
                "Machine learning and artificial intelligence are transforming the technology industry. "
                        + "Natural language processing is a key component of AI systems.";

        template.sendBody("direct:analyzeKeywords", textWithKeywords);

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        AnalysisResults results = exchange.getIn().getBody(AnalysisResults.class);

        assertNotNull(results);
        assertNotNull(results.getKeywords());
        assertFalse(results.getKeywords().isEmpty(), "Should extract keywords");

        LOG.info("Extracted keywords: {}", results.getKeywords().size());
        results.getKeywords().forEach(keyword -> {
            LOG.info("  - {} (relevance: {})", keyword.getText(), keyword.getRelevance());
        });
    }

    @Test
    public void testAnalyzeTextComprehensive() throws Exception {
        mockResult.expectedMessageCount(1);

        final String comprehensiveText = "Apple Inc. announced a new iPhone with advanced AI capabilities. "
                + "The CEO Tim Cook praised the innovation during the launch event in Cupertino, California. "
                + "Customers are excited about the new features and technology.";

        template.sendBody("direct:analyzeComprehensive", comprehensiveText);

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        AnalysisResults results = exchange.getIn().getBody(AnalysisResults.class);

        assertNotNull(results);

        // Verify sentiment was analyzed
        assertNotNull(results.getSentiment());
        LOG.info("Sentiment: {}", results.getSentiment().getDocument().getLabel());

        // Verify entities were extracted
        assertNotNull(results.getEntities());
        assertFalse(results.getEntities().isEmpty());
        LOG.info("Entities: {}", results.getEntities().size());

        // Verify keywords were extracted
        assertNotNull(results.getKeywords());
        assertFalse(results.getKeywords().isEmpty());
        LOG.info("Keywords: {}", results.getKeywords().size());
    }

    @Test
    public void testAnalyzeUrl() throws Exception {
        mockResult.expectedMessageCount(1);

        // Using IBM's public Watson page
        final String url = "https://www.ibm.com/watson";

        template.send("direct:analyzeUrl", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(WatsonLanguageConstants.URL, url);
            }
        });

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        AnalysisResults results = exchange.getIn().getBody(AnalysisResults.class);

        assertNotNull(results);
        assertNotNull(results.getLanguage(), "Should detect page language");

        LOG.info("URL analyzed: {}", url);
        LOG.info("Detected language: {}", results.getLanguage());

        if (results.getSentiment() != null) {
            LOG.info("Page sentiment: {}", results.getSentiment().getDocument().getLabel());
        }
    }

    @Test
    public void testAnalyzeWithDynamicFeatures() throws Exception {
        mockResult.expectedMessageCount(1);

        final String text = "This is a wonderful day! The weather is perfect.";

        template.send("direct:analyzeDynamic", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody(text);
                exchange.getIn().setHeader(WatsonLanguageConstants.ANALYZE_SENTIMENT, true);
                exchange.getIn().setHeader(WatsonLanguageConstants.ANALYZE_EMOTION, true);
                exchange.getIn().setHeader(WatsonLanguageConstants.ANALYZE_KEYWORDS, false);
            }
        });

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        AnalysisResults results = exchange.getIn().getBody(AnalysisResults.class);

        assertNotNull(results);
        assertNotNull(results.getSentiment(), "Sentiment should be analyzed");
        assertNotNull(results.getEmotion(), "Emotion should be analyzed");

        LOG.info("Sentiment: {}", results.getSentiment().getDocument().getLabel());
        if (results.getEmotion() != null && results.getEmotion().getDocument() != null) {
            LOG.info("Emotions detected: {}", results.getEmotion().getDocument().getEmotion());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:analyzeSentiment")
                        .to(buildEndpointUri("analyzeText")
                                + "&analyzeSentiment=true&analyzeEntities=false&analyzeKeywords=false")
                        .to("mock:result");

                from("direct:analyzeEntities")
                        .to(buildEndpointUri("analyzeText")
                                + "&analyzeSentiment=false&analyzeEntities=true&analyzeKeywords=false")
                        .to("mock:result");

                from("direct:analyzeKeywords")
                        .to(buildEndpointUri("analyzeText")
                                + "&analyzeSentiment=false&analyzeEntities=false&analyzeKeywords=true")
                        .to("mock:result");

                from("direct:analyzeComprehensive")
                        .to(buildEndpointUri("analyzeText")
                                + "&analyzeSentiment=true&analyzeEntities=true&analyzeKeywords=true")
                        .to("mock:result");

                from("direct:analyzeUrl")
                        .to(buildEndpointUri("analyzeUrl") + "&analyzeSentiment=true&analyzeKeywords=true")
                        .to("mock:result");

                from("direct:analyzeDynamic")
                        .to(buildEndpointUri("analyzeText"))
                        .to("mock:result");
            }
        };
    }
}
