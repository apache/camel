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
package org.apache.camel.component.ibm.watsonx.ai;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for the WatsonxAiComponent.
 */
class WatsonxAiComponentTest {

    @Test
    void testComponentCreation() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            WatsonxAiComponent component = new WatsonxAiComponent();
            context.addComponent("ibm-watsonx-ai", component);

            assertNotNull(context.getComponent("ibm-watsonx-ai"));
        }
    }

    @Test
    void testEndpointCreation() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            WatsonxAiComponent component = new WatsonxAiComponent();
            context.addComponent("ibm-watsonx-ai", component);

            WatsonxAiEndpoint endpoint = (WatsonxAiEndpoint) context.getEndpoint(
                    "ibm-watsonx-ai:myLabel?apiKey=test-key&baseUrl=https://test.example.com&modelId=test-model&projectId=test-project&operation=textGeneration");

            assertNotNull(endpoint);
            assertEquals("myLabel", endpoint.getLabel());
            assertEquals("test-key", endpoint.getConfiguration().getApiKey());
            assertEquals("https://test.example.com", endpoint.getConfiguration().getBaseUrl());
            assertEquals("test-model", endpoint.getConfiguration().getModelId());
            assertEquals("test-project", endpoint.getConfiguration().getProjectId());
            assertEquals(WatsonxAiOperations.textGeneration, endpoint.getConfiguration().getOperation());
        }
    }

    @Test
    void testConfigurationCopy() {
        WatsonxAiConfiguration config = new WatsonxAiConfiguration();
        config.setApiKey("original-key");
        config.setBaseUrl("https://original.com");
        config.setModelId("original-model");
        config.setTemperature(0.7);
        config.setMaxNewTokens(100);

        WatsonxAiConfiguration copy = config.copy();

        assertNotNull(copy);
        assertEquals("original-key", copy.getApiKey());
        assertEquals("https://original.com", copy.getBaseUrl());
        assertEquals("original-model", copy.getModelId());
        assertEquals(0.7, copy.getTemperature());
        assertEquals(100, copy.getMaxNewTokens());

        // Modify copy and verify original is unchanged
        copy.setApiKey("modified-key");
        assertEquals("original-key", config.getApiKey());
        assertEquals("modified-key", copy.getApiKey());
    }

    @Test
    void testOperationsEnum() {
        // Verify all operations are defined
        assertEquals(33, WatsonxAiOperations.values().length);

        // Verify key operations
        assertNotNull(WatsonxAiOperations.valueOf("textGeneration"));
        assertNotNull(WatsonxAiOperations.valueOf("textGenerationStreaming"));
        assertNotNull(WatsonxAiOperations.valueOf("chat"));
        assertNotNull(WatsonxAiOperations.valueOf("chatStreaming"));
        assertNotNull(WatsonxAiOperations.valueOf("embedding"));
        assertNotNull(WatsonxAiOperations.valueOf("rerank"));
        assertNotNull(WatsonxAiOperations.valueOf("tokenize"));

        // Verify new text extraction operations
        assertNotNull(WatsonxAiOperations.valueOf("textExtractionUpload"));
        assertNotNull(WatsonxAiOperations.valueOf("textExtractionUploadAndFetch"));
        assertNotNull(WatsonxAiOperations.valueOf("textExtractionUploadFile"));
        assertNotNull(WatsonxAiOperations.valueOf("textExtractionReadFile"));
        assertNotNull(WatsonxAiOperations.valueOf("textExtractionDeleteFile"));
        assertNotNull(WatsonxAiOperations.valueOf("textExtractionDeleteRequest"));

        // Verify new text classification operations
        assertNotNull(WatsonxAiOperations.valueOf("textClassificationUpload"));
        assertNotNull(WatsonxAiOperations.valueOf("textClassificationUploadAndFetch"));
        assertNotNull(WatsonxAiOperations.valueOf("textClassificationUploadFile"));
        assertNotNull(WatsonxAiOperations.valueOf("textClassificationDeleteFile"));
        assertNotNull(WatsonxAiOperations.valueOf("textClassificationDeleteRequest"));
    }

    @Test
    void testEndpointValidation() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            WatsonxAiComponent component = new WatsonxAiComponent();
            context.addComponent("ibm-watsonx-ai", component);

            // Missing apiKey should fail on start
            WatsonxAiEndpoint endpoint = (WatsonxAiEndpoint) context.getEndpoint(
                    "ibm-watsonx-ai:test?baseUrl=https://test.example.com");

            assertThrows(IllegalArgumentException.class, endpoint::start);
        }
    }

    @Test
    void testEndpointValidationMissingBaseUrl() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            WatsonxAiComponent component = new WatsonxAiComponent();
            context.addComponent("ibm-watsonx-ai", component);

            // Missing baseUrl should fail on start
            WatsonxAiEndpoint endpoint = (WatsonxAiEndpoint) context.getEndpoint(
                    "ibm-watsonx-ai:test?apiKey=test-key");

            assertThrows(IllegalArgumentException.class, endpoint::start);
        }
    }

    @Test
    void testServiceLocation() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            WatsonxAiComponent component = new WatsonxAiComponent();
            context.addComponent("ibm-watsonx-ai", component);

            WatsonxAiEndpoint endpoint = (WatsonxAiEndpoint) context.getEndpoint(
                    "ibm-watsonx-ai:test?apiKey=test-key&baseUrl=https://us-south.ml.cloud.ibm.com");

            assertEquals("https://us-south.ml.cloud.ibm.com", endpoint.getServiceUrl());
            assertEquals("https", endpoint.getServiceProtocol());
        }
    }

    @Test
    void testConsumerNotSupported() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            WatsonxAiComponent component = new WatsonxAiComponent();
            context.addComponent("ibm-watsonx-ai", component);

            WatsonxAiEndpoint endpoint = (WatsonxAiEndpoint) context.getEndpoint(
                    "ibm-watsonx-ai:test?apiKey=test-key&baseUrl=https://test.example.com");

            assertThrows(UnsupportedOperationException.class, () -> endpoint.createConsumer(null));
        }
    }
}
