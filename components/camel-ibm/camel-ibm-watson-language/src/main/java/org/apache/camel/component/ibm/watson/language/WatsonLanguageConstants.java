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

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel IBM Watson Language (NLU) module
 */
public interface WatsonLanguageConstants {

    @Metadata(description = "The operation to perform", javaType = "String")
    String OPERATION = "CamelIBMWatsonLanguageOperation";

    // Natural Language Understanding headers
    @Metadata(description = "The text to analyze", javaType = "String")
    String TEXT = "CamelIBMWatsonLanguageText";
    @Metadata(description = "The URL to analyze", javaType = "String")
    String URL = "CamelIBMWatsonLanguageUrl";
    @Metadata(description = "Enable sentiment analysis", javaType = "Boolean")
    String ANALYZE_SENTIMENT = "CamelIBMWatsonLanguageAnalyzeSentiment";
    @Metadata(description = "Enable emotion analysis", javaType = "Boolean")
    String ANALYZE_EMOTION = "CamelIBMWatsonLanguageAnalyzeEmotion";
    @Metadata(description = "Enable entity extraction", javaType = "Boolean")
    String ANALYZE_ENTITIES = "CamelIBMWatsonLanguageAnalyzeEntities";
    @Metadata(description = "Enable keyword extraction", javaType = "Boolean")
    String ANALYZE_KEYWORDS = "CamelIBMWatsonLanguageAnalyzeKeywords";
    @Metadata(description = "Enable concept extraction", javaType = "Boolean")
    String ANALYZE_CONCEPTS = "CamelIBMWatsonLanguageAnalyzeConcepts";
    @Metadata(description = "Enable category classification", javaType = "Boolean")
    String ANALYZE_CATEGORIES = "CamelIBMWatsonLanguageAnalyzeCategories";
    @Metadata(description = "The language of the text", javaType = "String")
    String LANGUAGE = "CamelIBMWatsonLanguageLanguage";
    @Metadata(description = "The sentiment score", javaType = "Double")
    String SENTIMENT_SCORE = "CamelIBMWatsonLanguageSentimentScore";
    @Metadata(description = "The sentiment label (positive, negative, neutral)", javaType = "String")
    String SENTIMENT_LABEL = "CamelIBMWatsonLanguageSentimentLabel";
}
