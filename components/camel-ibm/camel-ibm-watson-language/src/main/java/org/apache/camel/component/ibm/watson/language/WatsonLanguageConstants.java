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
@Deprecated(since = "4.24")
public interface WatsonLanguageConstants {

    @Metadata(description = "The operation to perform, analyzeText or analyzeUrl. It replaces the operation option.",
              javaType = "String")
    String OPERATION = "CamelIBMWatsonLanguageOperation";

    // Natural Language Understanding headers
    @Metadata(description = "The text to analyze for analyzeText. If not set, the producer analyzes the message body.",
              javaType = "String")
    String TEXT = "CamelIBMWatsonLanguageText";
    @Metadata(description = "The URL of the web page to analyze for analyzeUrl, which the service retrieves. If not set, the"
                            + " producer reads the URL from the message body.",
              javaType = "String")
    String URL = "CamelIBMWatsonLanguageUrl";
    @Metadata(description = "Whether to request the sentiment of the document. It replaces the analyzeSentiment option.",
              javaType = "Boolean")
    String ANALYZE_SENTIMENT = "CamelIBMWatsonLanguageAnalyzeSentiment";
    @Metadata(description = "Whether to request the emotions of the document. It replaces the analyzeEmotion option.",
              javaType = "Boolean")
    String ANALYZE_EMOTION = "CamelIBMWatsonLanguageAnalyzeEmotion";
    @Metadata(description = "Whether to request the entities of the content. It replaces the analyzeEntities option.",
              javaType = "Boolean")
    String ANALYZE_ENTITIES = "CamelIBMWatsonLanguageAnalyzeEntities";
    @Metadata(description = "Whether to request the keywords of the content. It replaces the analyzeKeywords option.",
              javaType = "Boolean")
    String ANALYZE_KEYWORDS = "CamelIBMWatsonLanguageAnalyzeKeywords";
    @Metadata(description = "Whether to request the concepts of the content. It replaces the analyzeConcepts option.",
              javaType = "Boolean")
    String ANALYZE_CONCEPTS = "CamelIBMWatsonLanguageAnalyzeConcepts";
    @Metadata(description = "Whether to request the categories of the content. It replaces the analyzeCategories option.",
              javaType = "Boolean")
    String ANALYZE_CATEGORIES = "CamelIBMWatsonLanguageAnalyzeCategories";
    @Metadata(description = "The ISO 639-1 code of the language that the service detected and used for the analysis, for"
                            + " example en. The producer sets it after each analysis.",
              javaType = "String")
    String LANGUAGE = "CamelIBMWatsonLanguageLanguage";
    @Metadata(description = "The sentiment score of the whole document, from -1 (negative) to 1 (positive). The producer"
                            + " sets it when sentiment is requested.",
              javaType = "Double")
    String SENTIMENT_SCORE = "CamelIBMWatsonLanguageSentimentScore";
    @Metadata(description = "The sentiment label of the whole document: positive, neutral or negative. The producer sets it"
                            + " when sentiment is requested.",
              javaType = "String")
    String SENTIMENT_LABEL = "CamelIBMWatsonLanguageSentimentLabel";
}
