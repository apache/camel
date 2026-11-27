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

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
@Deprecated(since = "4.24")
public class WatsonLanguageConfiguration implements Cloneable {

    @UriParam(label = "security", security = "secret",
              description = "The IBM Cloud API key of the Natural Language Understanding service instance. The IBM Cloud IAM"
                            + " authenticator of the SDK exchanges it for an access token. The endpoint fails to start if"
                            + " it is not set.")
    @Metadata(required = true)
    private String apiKey;

    @UriParam(label = "common",
              description = "The URL of the Natural Language Understanding service instance, as given in its service"
                            + " credentials. If not set, the SDK uses"
                            + " https://api.us-south.natural-language-understanding.watson.cloud.ibm.com (Dallas).")
    private String serviceUrl;

    @UriParam(label = "producer",
              description = "The operation that the producer performs: analyzeText analyzes the given text, and analyzeUrl"
                            + " the web page at the given URL, which the service retrieves. The"
                            + " CamelIBMWatsonLanguageOperation header replaces this option. The producer fails if neither"
                            + " is set.")
    private WatsonLanguageOperations operation;

    @UriParam(label = "producer", defaultValue = "true",
              description = "Whether to request the sentiment of the whole document. The producer then sets the"
                            + " CamelIBMWatsonLanguageSentimentScore and CamelIBMWatsonLanguageSentimentLabel headers. The"
                            + " CamelIBMWatsonLanguageAnalyzeSentiment header replaces this option.")
    private boolean analyzeSentiment = true;

    @UriParam(label = "producer",
              description = "Whether to request the emotions of the whole document: a score from 0 to 1 for anger,"
                            + " disgust, fear, joy and sadness. The service supports it for English only. The"
                            + " CamelIBMWatsonLanguageAnalyzeEmotion header replaces this option.")
    private boolean analyzeEmotion;

    @UriParam(label = "producer", defaultValue = "true",
              description = "Whether to request the entities, such as people, cities and organizations, that the content"
                            + " mentions. The CamelIBMWatsonLanguageAnalyzeEntities header replaces this option.")
    private boolean analyzeEntities = true;

    @UriParam(label = "producer", defaultValue = "true",
              description = "Whether to request the important keywords of the content. The"
                            + " CamelIBMWatsonLanguageAnalyzeKeywords header replaces this option.")
    private boolean analyzeKeywords = true;

    @UriParam(label = "producer",
              description = "Whether to request the high-level concepts of the content, which can include concepts that"
                            + " the text does not name. The CamelIBMWatsonLanguageAnalyzeConcepts header replaces this"
                            + " option.")
    private boolean analyzeConcepts;

    @UriParam(label = "producer",
              description = "Whether to request the categories of the content in a hierarchical taxonomy, by default the"
                            + " top three. The CamelIBMWatsonLanguageAnalyzeCategories header replaces this option.")
    private boolean analyzeCategories;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getServiceUrl() {
        return serviceUrl;
    }

    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    public WatsonLanguageOperations getOperation() {
        return operation;
    }

    public void setOperation(WatsonLanguageOperations operation) {
        this.operation = operation;
    }

    public boolean isAnalyzeSentiment() {
        return analyzeSentiment;
    }

    public void setAnalyzeSentiment(boolean analyzeSentiment) {
        this.analyzeSentiment = analyzeSentiment;
    }

    public boolean isAnalyzeEmotion() {
        return analyzeEmotion;
    }

    public void setAnalyzeEmotion(boolean analyzeEmotion) {
        this.analyzeEmotion = analyzeEmotion;
    }

    public boolean isAnalyzeEntities() {
        return analyzeEntities;
    }

    public void setAnalyzeEntities(boolean analyzeEntities) {
        this.analyzeEntities = analyzeEntities;
    }

    public boolean isAnalyzeKeywords() {
        return analyzeKeywords;
    }

    public void setAnalyzeKeywords(boolean analyzeKeywords) {
        this.analyzeKeywords = analyzeKeywords;
    }

    public boolean isAnalyzeConcepts() {
        return analyzeConcepts;
    }

    public void setAnalyzeConcepts(boolean analyzeConcepts) {
        this.analyzeConcepts = analyzeConcepts;
    }

    public boolean isAnalyzeCategories() {
        return analyzeCategories;
    }

    public void setAnalyzeCategories(boolean analyzeCategories) {
        this.analyzeCategories = analyzeCategories;
    }

    public WatsonLanguageConfiguration copy() {
        try {
            return (WatsonLanguageConfiguration) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
