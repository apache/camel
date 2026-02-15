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
package org.apache.camel.component.ibm.watsonx.ai.integration;

import org.apache.camel.test.junit6.CamelTestSupport;

/**
 * Base class for watsonx.ai integration tests.
 */
public abstract class WatsonxAiTestSupport extends CamelTestSupport {

    protected static String apiKey;
    protected static String projectId;
    protected static String baseUrl;

    // COS (Cloud Object Storage) configuration for text extraction/classification
    protected static String cosUrl;
    protected static String documentConnectionId;
    protected static String documentBucket;
    protected static String resultConnectionId;
    protected static String resultBucket;

    static {
        apiKey = System.getProperty("camel.ibm.watsonx.ai.apiKey");
        projectId = System.getProperty("camel.ibm.watsonx.ai.projectId");
        baseUrl = System.getProperty("camel.ibm.watsonx.ai.baseUrl", "https://us-south.ml.cloud.ibm.com");

        // COS configuration
        cosUrl = System.getProperty("camel.ibm.watsonx.ai.cosUrl");
        documentConnectionId = System.getProperty("camel.ibm.watsonx.ai.documentConnectionId");
        documentBucket = System.getProperty("camel.ibm.watsonx.ai.documentBucket");
        resultConnectionId = System.getProperty("camel.ibm.watsonx.ai.resultConnectionId");
        resultBucket = System.getProperty("camel.ibm.watsonx.ai.resultBucket");
    }

    protected String buildEndpointUri(String operation, String modelId) {
        StringBuilder uri = new StringBuilder("ibm-watsonx-ai://default");
        uri.append("?apiKey=RAW(").append(apiKey).append(")");
        uri.append("&baseUrl=").append(baseUrl);
        uri.append("&projectId=").append(projectId);

        if (modelId != null && !modelId.isEmpty()) {
            uri.append("&modelId=").append(modelId);
        }

        if (operation != null && !operation.isEmpty()) {
            uri.append("&operation=").append(operation);
        }

        return uri.toString();
    }

    protected String buildEndpointUri(String operation) {
        return buildEndpointUri(operation, "ibm/granite-4-h-small");
    }

    /**
     * Builds endpoint URI for text extraction operations that require COS configuration.
     */
    protected String buildTextExtractionEndpointUri(String operation) {
        StringBuilder uri = new StringBuilder("ibm-watsonx-ai://extraction");
        uri.append("?apiKey=RAW(").append(apiKey).append(")");
        uri.append("&baseUrl=").append(baseUrl);
        uri.append("&projectId=").append(projectId);
        uri.append("&cosUrl=").append(cosUrl);
        uri.append("&documentConnectionId=").append(documentConnectionId);
        uri.append("&documentBucket=").append(documentBucket);
        uri.append("&resultConnectionId=").append(resultConnectionId);
        uri.append("&resultBucket=").append(resultBucket);

        if (operation != null && !operation.isEmpty()) {
            uri.append("&operation=").append(operation);
        }

        return uri.toString();
    }

    /**
     * Builds endpoint URI for text classification operations that require COS configuration.
     */
    protected String buildTextClassificationEndpointUri(String operation) {
        StringBuilder uri = new StringBuilder("ibm-watsonx-ai://classification");
        uri.append("?apiKey=RAW(").append(apiKey).append(")");
        uri.append("&baseUrl=").append(baseUrl);
        uri.append("&projectId=").append(projectId);
        uri.append("&cosUrl=").append(cosUrl);
        uri.append("&documentConnectionId=").append(documentConnectionId);
        uri.append("&documentBucket=").append(documentBucket);

        if (operation != null && !operation.isEmpty()) {
            uri.append("&operation=").append(operation);
        }

        return uri.toString();
    }
}
