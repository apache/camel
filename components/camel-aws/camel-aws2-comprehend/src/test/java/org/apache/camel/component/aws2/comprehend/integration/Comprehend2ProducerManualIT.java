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
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import software.amazon.awssdk.services.comprehend.model.DetectDominantLanguageRequest;
import software.amazon.awssdk.services.comprehend.model.DetectEntitiesRequest;
import software.amazon.awssdk.services.comprehend.model.DetectSentimentRequest;
import software.amazon.awssdk.services.comprehend.model.DetectSentimentResponse;
import software.amazon.awssdk.services.comprehend.model.DominantLanguage;
import software.amazon.awssdk.services.comprehend.model.Entity;
import software.amazon.awssdk.services.comprehend.model.KeyPhrase;
import software.amazon.awssdk.services.comprehend.model.PiiEntity;
import software.amazon.awssdk.services.comprehend.model.SyntaxToken;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Must be manually tested. Provide your own accessKey and secretKey using -Daws.access.key and -Daws.secret.key
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "aws.access.key", matches = ".*", disabledReason = "Access key not provided"),
        @EnabledIfSystemProperty(named = "aws.secret.key", matches = ".*", disabledReason = "Secret key not provided")
})
public class Comprehend2ProducerManualIT extends CamelTestSupport {

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void detectDominantLanguageTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectDominantLanguage", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Comprehend2Constants.OPERATION, Comprehend2Operations.detectDominantLanguage);
                exchange.getIn().setBody("This is a sample text written in English for testing purposes.");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        @SuppressWarnings("unchecked")
        List<DominantLanguage> languages = exchange.getIn().getBody(List.class);
        assertNotNull(languages);
        assertFalse(languages.isEmpty());
        assertEquals("en", languages.get(0).languageCode());
        assertTrue(languages.get(0).score() > 0.9f);
    }

    @Test
    public void detectDominantLanguagePojoTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectDominantLanguagePojo", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody(DetectDominantLanguageRequest.builder()
                        .text("Questo e un testo scritto in italiano per testare il riconoscimento della lingua.")
                        .build());
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        @SuppressWarnings("unchecked")
        List<DominantLanguage> languages = exchange.getIn().getBody(List.class);
        assertNotNull(languages);
        assertFalse(languages.isEmpty());
        assertEquals("it", languages.get(0).languageCode());
    }

    @Test
    public void detectEntitiesTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectEntities", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Comprehend2Constants.OPERATION, Comprehend2Operations.detectEntities);
                exchange.getIn().setBody("Amazon was founded by Jeff Bezos in Seattle, Washington in 1994.");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        @SuppressWarnings("unchecked")
        List<Entity> entities = exchange.getIn().getBody(List.class);
        assertNotNull(entities);
        assertFalse(entities.isEmpty());
        // Should detect entities like "Amazon", "Jeff Bezos", "Seattle", "Washington", "1994"
        assertTrue(entities.size() >= 3);
    }

    @Test
    public void detectEntitiesPojoTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectEntitiesPojo", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody(DetectEntitiesRequest.builder()
                        .text("Apple Inc. is headquartered in Cupertino, California.")
                        .languageCode("en")
                        .build());
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        @SuppressWarnings("unchecked")
        List<Entity> entities = exchange.getIn().getBody(List.class);
        assertNotNull(entities);
        assertFalse(entities.isEmpty());
    }

    @Test
    public void detectKeyPhrasesTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectKeyPhrases", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Comprehend2Constants.OPERATION, Comprehend2Operations.detectKeyPhrases);
                exchange.getIn().setBody(
                        "Apache Camel is an open source integration framework that provides a rule-based routing and mediation engine.");
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
                exchange.getIn().setHeader(Comprehend2Constants.OPERATION, Comprehend2Operations.detectSentiment);
                exchange.getIn().setBody("I absolutely love this product! It's amazing and works perfectly.");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DetectSentimentResponse response = exchange.getIn().getBody(DetectSentimentResponse.class);
        assertNotNull(response);
        assertEquals("POSITIVE", exchange.getIn().getHeader(Comprehend2Constants.DETECTED_SENTIMENT, String.class));
    }

    @Test
    public void detectSentimentNegativeTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectSentiment", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Comprehend2Constants.OPERATION, Comprehend2Operations.detectSentiment);
                exchange.getIn().setBody("This is terrible. I hate it and it doesn't work at all. Very disappointed.");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DetectSentimentResponse response = exchange.getIn().getBody(DetectSentimentResponse.class);
        assertNotNull(response);
        assertEquals("NEGATIVE", exchange.getIn().getHeader(Comprehend2Constants.DETECTED_SENTIMENT, String.class));
    }

    @Test
    public void detectSentimentPojoTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectSentimentPojo", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody(DetectSentimentRequest.builder()
                        .text("The weather today is nice and sunny.")
                        .languageCode("en")
                        .build());
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DetectSentimentResponse response = exchange.getIn().getBody(DetectSentimentResponse.class);
        assertNotNull(response);
        assertNotNull(response.sentiment());
    }

    @Test
    public void detectSyntaxTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectSyntax", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Comprehend2Constants.OPERATION, Comprehend2Operations.detectSyntax);
                exchange.getIn().setBody("The quick brown fox jumps over the lazy dog.");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        @SuppressWarnings("unchecked")
        List<SyntaxToken> tokens = exchange.getIn().getBody(List.class);
        assertNotNull(tokens);
        assertFalse(tokens.isEmpty());
        assertEquals(9, tokens.size()); // 9 words in the sentence
    }

    @Test
    public void detectPiiEntitiesTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectPiiEntities", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Comprehend2Constants.OPERATION, Comprehend2Operations.detectPiiEntities);
                exchange.getIn().setBody(
                        "My email is john.doe@example.com and my phone number is 555-123-4567. My SSN is 123-45-6789.");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        @SuppressWarnings("unchecked")
        List<PiiEntity> entities = exchange.getIn().getBody(List.class);
        assertNotNull(entities);
        assertFalse(entities.isEmpty());
        // Should detect EMAIL, PHONE, SSN
        assertTrue(entities.size() >= 3);
    }

    @Test
    public void detectToxicContentCleanTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectToxicContent", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Comprehend2Constants.OPERATION, Comprehend2Operations.detectToxicContent);
                exchange.getIn().setBody("This is a normal, friendly, and polite message.");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        @SuppressWarnings("unchecked")
        List<?> results = exchange.getIn().getBody(List.class);
        assertNotNull(results);
        assertFalse(results.isEmpty());
    }

    @Test
    public void containsPiiEntitiesTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:containsPiiEntities", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Comprehend2Constants.OPERATION, Comprehend2Operations.containsPiiEntities);
                exchange.getIn().setBody("Please contact me at john.smith@example.org for more information.");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        @SuppressWarnings("unchecked")
        List<?> labels = exchange.getIn().getBody(List.class);
        assertNotNull(labels);
        assertFalse(labels.isEmpty());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String baseEndpoint
                        = "aws2-comprehend://test?accessKey=RAW({{aws.access.key}})&secretKey=RAW({{aws.secret.key}})&region=us-east-1";

                from("direct:detectDominantLanguage")
                        .log("Detecting dominant language for: ${body}")
                        .to(baseEndpoint + "&operation=detectDominantLanguage")
                        .log("Detected languages: ${body}")
                        .to("mock:result");

                from("direct:detectDominantLanguagePojo")
                        .log("Detecting dominant language (POJO mode)")
                        .to(baseEndpoint + "&operation=detectDominantLanguage&pojoRequest=true")
                        .log("Detected languages: ${body}")
                        .to("mock:result");

                from("direct:detectEntities")
                        .log("Detecting entities for: ${body}")
                        .to(baseEndpoint + "&operation=detectEntities&languageCode=en")
                        .log("Detected entities: ${body}")
                        .to("mock:result");

                from("direct:detectEntitiesPojo")
                        .log("Detecting entities (POJO mode)")
                        .to(baseEndpoint + "&operation=detectEntities&pojoRequest=true")
                        .log("Detected entities: ${body}")
                        .to("mock:result");

                from("direct:detectKeyPhrases")
                        .log("Detecting key phrases for: ${body}")
                        .to(baseEndpoint + "&operation=detectKeyPhrases&languageCode=en")
                        .log("Detected key phrases: ${body}")
                        .to("mock:result");

                from("direct:detectSentiment")
                        .log("Detecting sentiment for: ${body}")
                        .to(baseEndpoint + "&operation=detectSentiment&languageCode=en")
                        .log("Detected sentiment: ${header.CamelAwsComprehendDetectedSentiment}")
                        .to("mock:result");

                from("direct:detectSentimentPojo")
                        .log("Detecting sentiment (POJO mode)")
                        .to(baseEndpoint + "&operation=detectSentiment&pojoRequest=true")
                        .log("Detected sentiment: ${header.CamelAwsComprehendDetectedSentiment}")
                        .to("mock:result");

                from("direct:detectSyntax")
                        .log("Detecting syntax for: ${body}")
                        .to(baseEndpoint + "&operation=detectSyntax&languageCode=en")
                        .log("Detected syntax tokens: ${body}")
                        .to("mock:result");

                from("direct:detectPiiEntities")
                        .log("Detecting PII entities for: ${body}")
                        .to(baseEndpoint + "&operation=detectPiiEntities&languageCode=en")
                        .log("Detected PII entities: ${body}")
                        .to("mock:result");

                from("direct:detectToxicContent")
                        .log("Detecting toxic content for: ${body}")
                        .to(baseEndpoint + "&operation=detectToxicContent&languageCode=en")
                        .log("Toxic content analysis: ${body}")
                        .to("mock:result");

                from("direct:containsPiiEntities")
                        .log("Checking for PII entities in: ${body}")
                        .to(baseEndpoint + "&operation=containsPiiEntities&languageCode=en")
                        .log("PII entity labels: ${body}")
                        .to("mock:result");
            }
        };
    }
}
