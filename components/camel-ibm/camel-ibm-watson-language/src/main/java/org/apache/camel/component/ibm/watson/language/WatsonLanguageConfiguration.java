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
public class WatsonLanguageConfiguration implements Cloneable {

    @UriParam(label = "security", secret = true)
    @Metadata(required = true)
    private String apiKey;

    @UriParam(label = "common")
    private String serviceUrl;

    @UriParam(label = "producer")
    private WatsonLanguageOperations operation;

    @UriParam(label = "producer", defaultValue = "true")
    private boolean analyzeSentiment = true;

    @UriParam(label = "producer")
    private boolean analyzeEmotion;

    @UriParam(label = "producer", defaultValue = "true")
    private boolean analyzeEntities = true;

    @UriParam(label = "producer", defaultValue = "true")
    private boolean analyzeKeywords = true;

    @UriParam(label = "producer")
    private boolean analyzeConcepts;

    @UriParam(label = "producer")
    private boolean analyzeCategories;

    public String getApiKey() {
        return apiKey;
    }

    /**
     * The IBM Cloud API key for authentication
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getServiceUrl() {
        return serviceUrl;
    }

    /**
     * The service endpoint URL. If not specified, the default URL will be used.
     */
    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    public WatsonLanguageOperations getOperation() {
        return operation;
    }

    /**
     * The operation to perform
     */
    public void setOperation(WatsonLanguageOperations operation) {
        this.operation = operation;
    }

    public boolean isAnalyzeSentiment() {
        return analyzeSentiment;
    }

    /**
     * Enable sentiment analysis
     */
    public void setAnalyzeSentiment(boolean analyzeSentiment) {
        this.analyzeSentiment = analyzeSentiment;
    }

    public boolean isAnalyzeEmotion() {
        return analyzeEmotion;
    }

    /**
     * Enable emotion analysis
     */
    public void setAnalyzeEmotion(boolean analyzeEmotion) {
        this.analyzeEmotion = analyzeEmotion;
    }

    public boolean isAnalyzeEntities() {
        return analyzeEntities;
    }

    /**
     * Enable entity extraction
     */
    public void setAnalyzeEntities(boolean analyzeEntities) {
        this.analyzeEntities = analyzeEntities;
    }

    public boolean isAnalyzeKeywords() {
        return analyzeKeywords;
    }

    /**
     * Enable keyword extraction
     */
    public void setAnalyzeKeywords(boolean analyzeKeywords) {
        this.analyzeKeywords = analyzeKeywords;
    }

    public boolean isAnalyzeConcepts() {
        return analyzeConcepts;
    }

    /**
     * Enable concept extraction
     */
    public void setAnalyzeConcepts(boolean analyzeConcepts) {
        this.analyzeConcepts = analyzeConcepts;
    }

    public boolean isAnalyzeCategories() {
        return analyzeCategories;
    }

    /**
     * Enable category classification
     */
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
