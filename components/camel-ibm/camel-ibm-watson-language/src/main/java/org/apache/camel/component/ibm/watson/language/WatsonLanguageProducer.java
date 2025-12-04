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

package org.apache.camel.component.ibm.watson.language;

import com.ibm.watson.natural_language_understanding.v1.NaturalLanguageUnderstanding;
import com.ibm.watson.natural_language_understanding.v1.model.AnalysisResults;
import com.ibm.watson.natural_language_understanding.v1.model.AnalyzeOptions;
import com.ibm.watson.natural_language_understanding.v1.model.CategoriesOptions;
import com.ibm.watson.natural_language_understanding.v1.model.ConceptsOptions;
import com.ibm.watson.natural_language_understanding.v1.model.EmotionOptions;
import com.ibm.watson.natural_language_understanding.v1.model.EntitiesOptions;
import com.ibm.watson.natural_language_understanding.v1.model.Features;
import com.ibm.watson.natural_language_understanding.v1.model.KeywordsOptions;
import com.ibm.watson.natural_language_understanding.v1.model.SentimentOptions;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WatsonLanguageProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(WatsonLanguageProducer.class);

    public WatsonLanguageProducer(WatsonLanguageEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        WatsonLanguageOperations operation = determineOperation(exchange);

        switch (operation) {
            case analyzeText:
                analyzeText(exchange);
                break;
            case analyzeUrl:
                analyzeUrl(exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation: " + operation);
        }
    }

    @Override
    public WatsonLanguageEndpoint getEndpoint() {
        return (WatsonLanguageEndpoint) super.getEndpoint();
    }

    private WatsonLanguageOperations determineOperation(Exchange exchange) {
        WatsonLanguageOperations operation =
                exchange.getIn().getHeader(WatsonLanguageConstants.OPERATION, WatsonLanguageOperations.class);

        if (operation == null) {
            operation = getEndpoint().getConfiguration().getOperation();
        }

        if (operation == null) {
            throw new IllegalArgumentException("Operation must be specified");
        }

        return operation;
    }

    private void analyzeText(Exchange exchange) {
        NaturalLanguageUnderstanding nlu = getEndpoint().getNluClient();
        if (nlu == null) {
            throw new IllegalStateException("NLU client not initialized. Use service=nlu");
        }

        String text = exchange.getIn().getHeader(WatsonLanguageConstants.TEXT, String.class);
        if (text == null) {
            text = exchange.getIn().getBody(String.class);
        }

        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text to analyze must be specified");
        }

        LOG.trace("Analyzing text with NLU");

        Features features = buildFeatures(exchange);

        AnalyzeOptions options =
                new AnalyzeOptions.Builder().text(text).features(features).build();

        AnalysisResults result = nlu.analyze(options).execute().getResult();

        Message message = getMessageForResponse(exchange);

        // Set the full analysis results as body (contains sentiment, entities, keywords, etc.)
        message.setBody(result);

        // Set convenience headers for commonly used values
        if (result.getSentiment() != null && result.getSentiment().getDocument() != null) {
            message.setHeader(
                    WatsonLanguageConstants.SENTIMENT_SCORE,
                    result.getSentiment().getDocument().getScore());
            message.setHeader(
                    WatsonLanguageConstants.SENTIMENT_LABEL,
                    result.getSentiment().getDocument().getLabel());
        }
        if (result.getLanguage() != null) {
            message.setHeader(WatsonLanguageConstants.LANGUAGE, result.getLanguage());
        }
    }

    private void analyzeUrl(Exchange exchange) {
        NaturalLanguageUnderstanding nlu = getEndpoint().getNluClient();
        if (nlu == null) {
            throw new IllegalStateException("NLU client not initialized. Use service=nlu");
        }

        String url = exchange.getIn().getHeader(WatsonLanguageConstants.URL, String.class);
        if (url == null) {
            url = exchange.getIn().getBody(String.class);
        }

        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL to analyze must be specified");
        }

        LOG.trace("Analyzing URL with NLU: {}", url);

        Features features = buildFeatures(exchange);

        AnalyzeOptions options =
                new AnalyzeOptions.Builder().url(url).features(features).build();

        AnalysisResults result = nlu.analyze(options).execute().getResult();

        Message message = getMessageForResponse(exchange);

        // Set the full analysis results as body (contains sentiment, entities, keywords, etc.)
        message.setBody(result);

        // Set convenience headers for commonly used values
        if (result.getSentiment() != null && result.getSentiment().getDocument() != null) {
            message.setHeader(
                    WatsonLanguageConstants.SENTIMENT_SCORE,
                    result.getSentiment().getDocument().getScore());
            message.setHeader(
                    WatsonLanguageConstants.SENTIMENT_LABEL,
                    result.getSentiment().getDocument().getLabel());
        }
        if (result.getLanguage() != null) {
            message.setHeader(WatsonLanguageConstants.LANGUAGE, result.getLanguage());
        }
    }

    private Features buildFeatures(Exchange exchange) {
        Features.Builder builder = new Features.Builder();

        boolean analyzeSentiment = exchange.getIn()
                .getHeader(
                        WatsonLanguageConstants.ANALYZE_SENTIMENT,
                        getEndpoint().getConfiguration().isAnalyzeSentiment(),
                        Boolean.class);
        boolean analyzeEmotion = exchange.getIn()
                .getHeader(
                        WatsonLanguageConstants.ANALYZE_EMOTION,
                        getEndpoint().getConfiguration().isAnalyzeEmotion(),
                        Boolean.class);
        boolean analyzeEntities = exchange.getIn()
                .getHeader(
                        WatsonLanguageConstants.ANALYZE_ENTITIES,
                        getEndpoint().getConfiguration().isAnalyzeEntities(),
                        Boolean.class);
        boolean analyzeKeywords = exchange.getIn()
                .getHeader(
                        WatsonLanguageConstants.ANALYZE_KEYWORDS,
                        getEndpoint().getConfiguration().isAnalyzeKeywords(),
                        Boolean.class);
        boolean analyzeConcepts = exchange.getIn()
                .getHeader(
                        WatsonLanguageConstants.ANALYZE_CONCEPTS,
                        getEndpoint().getConfiguration().isAnalyzeConcepts(),
                        Boolean.class);
        boolean analyzeCategories = exchange.getIn()
                .getHeader(
                        WatsonLanguageConstants.ANALYZE_CATEGORIES,
                        getEndpoint().getConfiguration().isAnalyzeCategories(),
                        Boolean.class);

        if (analyzeSentiment) {
            builder.sentiment(new SentimentOptions.Builder().build());
        }
        if (analyzeEmotion) {
            builder.emotion(new EmotionOptions.Builder().build());
        }
        if (analyzeEntities) {
            builder.entities(new EntitiesOptions.Builder().build());
        }
        if (analyzeKeywords) {
            builder.keywords(new KeywordsOptions.Builder().build());
        }
        if (analyzeConcepts) {
            builder.concepts(new ConceptsOptions.Builder().build());
        }
        if (analyzeCategories) {
            builder.categories(new CategoriesOptions.Builder().build());
        }

        return builder.build();
    }

    private Message getMessageForResponse(Exchange exchange) {
        return exchange.getMessage();
    }
}
