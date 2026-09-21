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
package org.apache.camel.component.jev;

import java.net.URI;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.util.ObjectHelper;

@UriParams
public class JevConfiguration implements Cloneable {
    @UriParam(label = "security")
    @Metadata(required = true, secret = true)
    private String apiKey;
    @UriParam(defaultValue = "https://api.typesafe.ai")
    private String baseUrl = "https://api.typesafe.ai";
    @UriParam(defaultValue = "jev-latest")
    private String model = "jev-latest";
    @UriParam(defaultValue = "30000")
    private long requestTimeout = 30000;

    @UriParam
    private String questions;
    @UriParam(defaultValue = "${body}")
    private String state = "${body}";
    @UriParam
    private String resultProperty;
    @UriParam
    private Double threshold;
    @UriParam(defaultValue = "0")
    private double uncertainty;
    @UriParam(defaultValue = "NonMatch")
    private JevPredicate.UncertaintyPolicy uncertaintyPolicy = JevPredicate.UncertaintyPolicy.NonMatch;

    public String getQuestions() {
        return questions;
    }

    /**
     * A JSON object mapping question names to Noul, Choice or Score question objects. When set, producers evaluate the
     * selected message state; otherwise the body must contain a complete request map. The Jev language requires exactly
     * one configured Noul question.
     */
    public void setQuestions(String questions) {
        this.questions = questions;
    }

    public String getState() {
        return state;
    }

    /** The Simple expression selecting state for configured questions. Defaults to the message body. */
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

    public Double getThreshold() {
        return threshold;
    }

    /** The explicit Noul probability threshold for the Jev language. Required when using the language. */
    public void setThreshold(Double threshold) {
        this.threshold = threshold;
    }

    public double getUncertainty() {
        return uncertainty;
    }

    /** Half-width of the inclusive uncertainty band around the predicate threshold. Zero disables the band. */
    public void setUncertainty(double uncertainty) {
        this.uncertainty = uncertainty;
    }

    public JevPredicate.UncertaintyPolicy getUncertaintyPolicy() {
        return uncertaintyPolicy;
    }

    /** Whether the Jev language returns a non-match or raises an exception for an uncertain result. */
    public void setUncertaintyPolicy(JevPredicate.UncertaintyPolicy uncertaintyPolicy) {
        this.uncertaintyPolicy = uncertaintyPolicy;
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

    public JevConfiguration copy() {
        try {
            return (JevConfiguration) clone();
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
    }
}
