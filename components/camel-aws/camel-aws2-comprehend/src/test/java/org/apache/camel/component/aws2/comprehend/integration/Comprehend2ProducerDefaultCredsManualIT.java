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
package org.apache.camel.component.aws2.comprehend.integration;

import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.comprehend.Comprehend2Constants;
import org.apache.camel.component.aws2.comprehend.Comprehend2Operations;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import software.amazon.awssdk.services.comprehend.model.DetectSentimentResponse;
import software.amazon.awssdk.services.comprehend.model.DominantLanguage;
import software.amazon.awssdk.services.comprehend.model.Entity;
import software.amazon.awssdk.services.comprehend.model.KeyPhrase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test using default AWS credentials provider. Enable by setting -Daws.comprehend.manual.test=true Requires
 * AWS credentials to be configured in the environment (env vars, ~/.aws/credentials, etc.)
 */
@EnabledIfSystemProperty(named = "aws.comprehend.manual.test", matches = "true",
                         disabledReason = "Manual test - set -Daws.comprehend.manual.test=true to enable")
public class Comprehend2ProducerDefaultCredsManualIT extends CamelTestSupport {

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void detectDominantLanguageEnglishTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectDominantLanguage", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody("Apache Camel is a powerful open source integration framework.");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        @SuppressWarnings("unchecked")
        List<DominantLanguage> languages = exchange.getIn().getBody(List.class);
        assertNotNull(languages);
        assertFalse(languages.isEmpty());
        assertEquals("en", languages.get(0).languageCode());
        assertTrue(languages.get(0).score() > 0.9f);

        // Verify headers are set
        assertEquals("en", exchange.getIn().getHeader(Comprehend2Constants.DETECTED_LANGUAGE, String.class));
        assertNotNull(exchange.getIn().getHeader(Comprehend2Constants.DETECTED_LANGUAGE_SCORE, Float.class));
    }

    @Test
    public void detectDominantLanguageSpanishTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectDominantLanguage", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody("Hola, buenos dias. Como estas hoy?");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        @SuppressWarnings("unchecked")
        List<DominantLanguage> languages = exchange.getIn().getBody(List.class);
        assertNotNull(languages);
        assertFalse(languages.isEmpty());
        assertEquals("es", languages.get(0).languageCode());
    }

    @Test
    public void detectDominantLanguageFrenchTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectDominantLanguage", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody("Bonjour, comment allez-vous aujourd'hui?");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        @SuppressWarnings("unchecked")
        List<DominantLanguage> languages = exchange.getIn().getBody(List.class);
        assertNotNull(languages);
        assertFalse(languages.isEmpty());
        assertEquals("fr", languages.get(0).languageCode());
    }

    @Test
    public void detectEntitiesTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectEntities", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody(
                        "Jeff Bezos founded Amazon in Seattle in 1994. The company is now worth over a trillion dollars.");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        @SuppressWarnings("unchecked")
        List<Entity> entities = exchange.getIn().getBody(List.class);
        assertNotNull(entities);
        assertFalse(entities.isEmpty());
        assertTrue(entities.size() >= 4); // Jeff Bezos, Amazon, Seattle, 1994
    }

    @Test
    public void detectKeyPhrasesTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectKeyPhrases", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn()
                        .setBody("Machine learning and artificial intelligence are transforming the technology industry.");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        @SuppressWarnings("unchecked")
        List<KeyPhrase> keyPhrases = exchange.getIn().getBody(List.class);
        assertNotNull(keyPhrases);
        assertFalse(keyPhrases.isEmpty());
    }

    @Test
    public void detectSentimentPositiveTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectSentiment", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody("I love using Apache Camel! It makes integration so easy and enjoyable.");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DetectSentimentResponse response = exchange.getIn().getBody(DetectSentimentResponse.class);
        assertNotNull(response);
        assertEquals("POSITIVE", exchange.getIn().getHeader(Comprehend2Constants.DETECTED_SENTIMENT, String.class));
        assertNotNull(exchange.getIn().getHeader(Comprehend2Constants.DETECTED_SENTIMENT_SCORE));
    }

    @Test
    public void detectSentimentNegativeTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectSentiment", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody("This is the worst experience ever. I am very disappointed and frustrated.");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DetectSentimentResponse response = exchange.getIn().getBody(DetectSentimentResponse.class);
        assertNotNull(response);
        assertEquals("NEGATIVE", exchange.getIn().getHeader(Comprehend2Constants.DETECTED_SENTIMENT, String.class));
    }

    @Test
    public void detectSentimentNeutralTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectSentiment", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody("The meeting is scheduled for 3 PM in conference room B.");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DetectSentimentResponse response = exchange.getIn().getBody(DetectSentimentResponse.class);
        assertNotNull(response);
        // Neutral or mixed sentiment expected for factual statements
        String sentiment = exchange.getIn().getHeader(Comprehend2Constants.DETECTED_SENTIMENT, String.class);
        assertTrue("NEUTRAL".equals(sentiment) || "MIXED".equals(sentiment));
    }

    @Test
    public void detectPiiEntitiesWithEmailTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectPiiEntities", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody("Please send the report to john.doe@example.com by Friday.");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        @SuppressWarnings("unchecked")
        List<?> entities = exchange.getIn().getBody(List.class);
        assertNotNull(entities);
        assertFalse(entities.isEmpty());
    }

    @Test
    public void operationViaHeaderTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:operationViaHeader", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Comprehend2Constants.OPERATION, Comprehend2Operations.detectDominantLanguage);
                exchange.getIn().setBody("Testing operation via header.");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        @SuppressWarnings("unchecked")
        List<DominantLanguage> languages = exchange.getIn().getBody(List.class);
        assertNotNull(languages);
        assertFalse(languages.isEmpty());
    }

    @Test
    public void languageCodeViaHeaderTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:languageCodeViaHeader", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Comprehend2Constants.LANGUAGE_CODE, "en");
                exchange.getIn().setBody("Testing language code via header for entity detection.");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        @SuppressWarnings("unchecked")
        List<Entity> entities = exchange.getIn().getBody(List.class);
        assertNotNull(entities);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String baseEndpoint = "aws2-comprehend://test?useDefaultCredentialsProvider=true&region=us-east-1";

                from("direct:detectDominantLanguage")
                        .log("Detecting dominant language for: ${body}")
                        .to(baseEndpoint + "&operation=detectDominantLanguage")
                        .log("Detected language: ${header.CamelAwsComprehendDetectedLanguage} with score: ${header.CamelAwsComprehendDetectedLanguageScore}")
                        .to("mock:result");

                from("direct:detectEntities")
                        .log("Detecting entities for: ${body}")
                        .to(baseEndpoint + "&operation=detectEntities&languageCode=en")
                        .log("Detected entities count: ${body.size()}")
                        .log("Detected entities: ${body}")
                        .to("mock:result");

                from("direct:detectKeyPhrases")
                        .log("Detecting key phrases for: ${body}")
                        .to(baseEndpoint + "&operation=detectKeyPhrases&languageCode=en")
                        .log("Detected key phrases count: ${body.size()}")
                        .log("Detected key phrases: ${body}")
                        .to("mock:result");

                from("direct:detectSentiment")
                        .log("Detecting sentiment for: ${body}")
                        .to(baseEndpoint + "&operation=detectSentiment&languageCode=en")
                        .log("Detected sentiment: ${header.CamelAwsComprehendDetectedSentiment}")
                        .log("Sentiment scores: ${header.CamelAwsComprehendDetectedSentimentScore}")
                        .to("mock:result");

                from("direct:detectPiiEntities")
                        .log("Detecting PII entities for: ${body}")
                        .to(baseEndpoint + "&operation=detectPiiEntities&languageCode=en")
                        .log("Detected PII entities count: ${body.size()}")
                        .log("Detected PII entities: ${body}")
                        .to("mock:result");

                from("direct:operationViaHeader")
                        .log("Processing with operation from header: ${header.CamelAwsComprehendOperation}")
                        .log("Input text: ${body}")
                        .to(baseEndpoint)
                        .log("Result: ${body}")
                        .to("mock:result");

                from("direct:languageCodeViaHeader")
                        .log("Detecting entities with language code from header: ${header.CamelAwsComprehendLanguageCode}")
                        .log("Input text: ${body}")
                        .to(baseEndpoint + "&operation=detectEntities")
                        .log("Detected entities: ${body}")
                        .to("mock:result");
            }
        };
    }
}
