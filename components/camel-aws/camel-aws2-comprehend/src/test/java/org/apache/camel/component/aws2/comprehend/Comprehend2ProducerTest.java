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
package org.apache.camel.component.aws2.comprehend;

import java.util.List;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.comprehend.model.DetectDominantLanguageRequest;
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

public class Comprehend2ProducerTest extends CamelTestSupport {

    @BindToRegistry("amazonComprehendClient")
    AmazonAWSComprehendMock clientMock = new AmazonAWSComprehendMock();

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void detectDominantLanguageTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectDominantLanguage", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody("This is a sample text in English.");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        @SuppressWarnings("unchecked")
        List<DominantLanguage> languages = exchange.getIn().getBody(List.class);
        assertNotNull(languages);
        assertFalse(languages.isEmpty());
        assertEquals("en", languages.get(0).languageCode());

        // Verify headers are set
        assertEquals("en", exchange.getIn().getHeader(Comprehend2Constants.DETECTED_LANGUAGE, String.class));
        assertNotNull(exchange.getIn().getHeader(Comprehend2Constants.DETECTED_LANGUAGE_SCORE, Float.class));
    }

    @Test
    public void detectDominantLanguagePojoTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectDominantLanguagePojo", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody(DetectDominantLanguageRequest.builder()
                        .text("This is a sample text in English.")
                        .build());
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        @SuppressWarnings("unchecked")
        List<DominantLanguage> languages = exchange.getIn().getBody(List.class);
        assertNotNull(languages);
        assertFalse(languages.isEmpty());
        assertEquals("en", languages.get(0).languageCode());
    }

    @Test
    public void detectEntitiesTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectEntities", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody("John Doe lives in New York.");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        @SuppressWarnings("unchecked")
        List<Entity> entities = exchange.getIn().getBody(List.class);
        assertNotNull(entities);
        assertFalse(entities.isEmpty());
        assertEquals("John Doe", entities.get(0).text());
    }

    @Test
    public void detectKeyPhrasesTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectKeyPhrases", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody("We have an important meeting next week.");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        @SuppressWarnings("unchecked")
        List<KeyPhrase> keyPhrases = exchange.getIn().getBody(List.class);
        assertNotNull(keyPhrases);
        assertFalse(keyPhrases.isEmpty());
        assertEquals("important meeting", keyPhrases.get(0).text());
    }

    @Test
    public void detectSentimentTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectSentiment", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody("I love this product! It's amazing.");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DetectSentimentResponse response = exchange.getIn().getBody(DetectSentimentResponse.class);
        assertNotNull(response);
        assertEquals("POSITIVE", exchange.getIn().getHeader(Comprehend2Constants.DETECTED_SENTIMENT, String.class));
        assertNotNull(exchange.getIn().getHeader(Comprehend2Constants.DETECTED_SENTIMENT_SCORE));
    }

    @Test
    public void detectSentimentPojoTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectSentimentPojo", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody(DetectSentimentRequest.builder()
                        .text("I love this product! It's amazing.")
                        .languageCode("en")
                        .build());
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DetectSentimentResponse response = exchange.getIn().getBody(DetectSentimentResponse.class);
        assertNotNull(response);
        assertEquals("POSITIVE", exchange.getIn().getHeader(Comprehend2Constants.DETECTED_SENTIMENT, String.class));
    }

    @Test
    public void detectSyntaxTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectSyntax", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody("The quick brown fox.");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        @SuppressWarnings("unchecked")
        List<SyntaxToken> tokens = exchange.getIn().getBody(List.class);
        assertNotNull(tokens);
        assertFalse(tokens.isEmpty());
        assertEquals("The", tokens.get(0).text());
    }

    @Test
    public void detectPiiEntitiesTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectPiiEntities", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody("Contact me at john.doe@example.com or 555-123-4567.");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        @SuppressWarnings("unchecked")
        List<PiiEntity> entities = exchange.getIn().getBody(List.class);
        assertNotNull(entities);
        assertFalse(entities.isEmpty());
    }

    @Test
    public void detectToxicContentTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectToxicContent", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody("This is a normal, polite message.");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        @SuppressWarnings("unchecked")
        List<?> results = exchange.getIn().getBody(List.class);
        assertNotNull(results);
        assertFalse(results.isEmpty());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:detectDominantLanguage")
                        .to("aws2-comprehend://test?comprehendClient=#amazonComprehendClient&operation=detectDominantLanguage")
                        .to("mock:result");
                from("direct:detectDominantLanguagePojo")
                        .to("aws2-comprehend://test?comprehendClient=#amazonComprehendClient&operation=detectDominantLanguage&pojoRequest=true")
                        .to("mock:result");
                from("direct:detectEntities")
                        .to("aws2-comprehend://test?comprehendClient=#amazonComprehendClient&operation=detectEntities&languageCode=en")
                        .to("mock:result");
                from("direct:detectKeyPhrases")
                        .to("aws2-comprehend://test?comprehendClient=#amazonComprehendClient&operation=detectKeyPhrases&languageCode=en")
                        .to("mock:result");
                from("direct:detectSentiment")
                        .to("aws2-comprehend://test?comprehendClient=#amazonComprehendClient&operation=detectSentiment&languageCode=en")
                        .to("mock:result");
                from("direct:detectSentimentPojo")
                        .to("aws2-comprehend://test?comprehendClient=#amazonComprehendClient&operation=detectSentiment&pojoRequest=true")
                        .to("mock:result");
                from("direct:detectSyntax")
                        .to("aws2-comprehend://test?comprehendClient=#amazonComprehendClient&operation=detectSyntax&languageCode=en")
                        .to("mock:result");
                from("direct:detectPiiEntities")
                        .to("aws2-comprehend://test?comprehendClient=#amazonComprehendClient&operation=detectPiiEntities&languageCode=en")
                        .to("mock:result");
                from("direct:detectToxicContent")
                        .to("aws2-comprehend://test?comprehendClient=#amazonComprehendClient&operation=detectToxicContent&languageCode=en")
                        .to("mock:result");
            }
        };
    }
}
