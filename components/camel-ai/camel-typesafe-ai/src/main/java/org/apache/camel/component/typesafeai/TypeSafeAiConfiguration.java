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
package org.apache.camel.component.typesafeai;

import java.net.URI;

import org.apache.camel.language.typesafeai.TypeSafeAiLanguage.UncertaintyPolicy;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.util.ObjectHelper;

@UriParams
public class TypeSafeAiConfiguration implements Cloneable {
    @UriParam(label = "security", security = "secret")
    @Metadata(required = true)
    private String apiKey;
    @UriParam(label = "common", defaultValue = "https://api.typesafe.ai")
    private String baseUrl = "https://api.typesafe.ai";
    @UriParam(label = "common", defaultValue = "jev-latest")
    private String model = "jev-latest";
    @UriParam(label = "common", defaultValue = "30000")
    private long requestTimeout = 30000;
    @UriParam(label = "common", defaultValue = "64")
    private int maxConcurrentRequests = 64;

    @UriParam(label = "common")
    private String questions;
    @UriParam(label = "common")
    private String state = "${body}";
    @UriParam(label = "producer")
    private String resultProperty;

    @UriParam(label = "advanced", defaultValue = "0.5")
    private double threshold = 0.5;
    @UriParam(label = "advanced", defaultValue = "0")
    private double uncertainty;
    @UriParam(label = "advanced", defaultValue = "NonMatch")
    private UncertaintyPolicy uncertaintyPolicy = UncertaintyPolicy.NonMatch;

    public double getThreshold() {
        return threshold;
    }

    /** Default inclusive probability threshold for the TypeSafe AI language. Must be within [0,1]. */
    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    public double getUncertainty() {
        return uncertainty;
    }

    /** Default half-width of the inclusive uncertainty band for the TypeSafe AI language. Zero disables the band. */
    public void setUncertainty(double uncertainty) {
        this.uncertainty = uncertainty;
    }

    public UncertaintyPolicy getUncertaintyPolicy() {
        return uncertaintyPolicy;
    }

    /** Default action for the TypeSafe AI language within the uncertainty band: NonMatch or Fail. */
    public void setUncertaintyPolicy(UncertaintyPolicy uncertaintyPolicy) {
        this.uncertaintyPolicy = uncertaintyPolicy;
    }

    public String getQuestions() {
        return questions;
    }

    /**
     * A JSON object mapping question names to Noul, Choice or Score question objects. When set, producers evaluate the
     * selected message state; otherwise the body must contain a complete request map.
     */
    public void setQuestions(String questions) {
        this.questions = questions;
    }

    public String getState() {
        return state;
    }

    /**
     * The Simple expression selecting state for configured producer questions and the TypeSafe AI language. If not set,
     * the message body is used.
     */
    public void setState(String state) {
        this.state = state;
    }

    public String getResultProperty() {
        return resultProperty;
    }

    /** Store the producer response in this exchange property, preserving the original message body. */
    public void setResultProperty(String resultProperty) {
        this.resultProperty = resultProperty;
    }

    public String getApiKey() {
        return apiKey;
    }

    /** The API key used for Bearer authentication. */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    /** The API base URL. The client appends /v1/systemone. Redirects are not followed. */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    /** The model ID or alias. Use a versioned ID to pin decision behavior. */
    public void setModel(String model) {
        this.model = model;
    }

    public long getRequestTimeout() {
        return requestTimeout;
    }

    /** The timeout in milliseconds for the complete HTTP request and response body. Must be positive. */
    public void setRequestTimeout(long requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public int getMaxConcurrentRequests() {
        return maxConcurrentRequests;
    }

    /**
     * Maximum concurrent evaluations per endpoint, shared by producers and predicates. Excess requests fail immediately
     * with RejectedExecutionException without being queued or sent. Must be positive.
     */
    public void setMaxConcurrentRequests(int maxConcurrentRequests) {
        this.maxConcurrentRequests = maxConcurrentRequests;
    }

    public TypeSafeAiConfiguration copy() {
        try {
            return (TypeSafeAiConfiguration) clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
    }

    void validate() {
        ObjectHelper.notNull(apiKey, "apiKey");
        ObjectHelper.notNull(model, "model");
        ObjectHelper.notNull(baseUrl, "baseUrl");
        if (apiKey.isBlank() || model.isBlank()) {
            throw new IllegalArgumentException("apiKey and model must not be blank");
        }
        if (apiKey.chars().anyMatch(c -> c < 33 || c > 126)) {
            throw new IllegalArgumentException("apiKey must contain only visible ASCII characters");
        }
        URI uri = URI.create(baseUrl);
        if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null) {
            throw new IllegalArgumentException("baseUrl must be an HTTP(S) URL without user information, query or fragment");
        }
        if (requestTimeout <= 0) {
            throw new IllegalArgumentException("requestTimeout must be positive");
        }
        if (maxConcurrentRequests <= 0) {
            throw new IllegalArgumentException("maxConcurrentRequests must be positive");
        }
    }
}
