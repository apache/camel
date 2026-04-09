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
package org.apache.camel.component.google.vertexai;

import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests to verify that all operations are properly configured and routable.
 */
public class GoogleVertexAIProducerOperationsTest extends CamelTestSupport {

    @Test
    public void testEndpointWithStreamingOperation() throws Exception {
        GoogleVertexAIComponent component = context.getComponent("google-vertexai", GoogleVertexAIComponent.class);
        GoogleVertexAIEndpoint endpoint = (GoogleVertexAIEndpoint) component.createEndpoint(
                "google-vertexai:my-project:us-central1:gemini-2.0-flash?operation=generateChatStreaming");

        assertNotNull(endpoint.getConfiguration());
        assertEquals(GoogleVertexAIOperations.generateChatStreaming, endpoint.getConfiguration().getOperation());
    }

    @Test
    public void testEndpointWithImageOperation() throws Exception {
        GoogleVertexAIComponent component = context.getComponent("google-vertexai", GoogleVertexAIComponent.class);
        GoogleVertexAIEndpoint endpoint = (GoogleVertexAIEndpoint) component.createEndpoint(
                "google-vertexai:my-project:us-central1:imagen-3.0-generate-002?operation=generateImage");

        assertNotNull(endpoint.getConfiguration());
        assertEquals(GoogleVertexAIOperations.generateImage, endpoint.getConfiguration().getOperation());
    }

    @Test
    public void testEndpointWithEmbeddingsOperation() throws Exception {
        GoogleVertexAIComponent component = context.getComponent("google-vertexai", GoogleVertexAIComponent.class);
        GoogleVertexAIEndpoint endpoint = (GoogleVertexAIEndpoint) component.createEndpoint(
                "google-vertexai:my-project:us-central1:text-embedding-005?operation=generateEmbeddings");

        assertNotNull(endpoint.getConfiguration());
        assertEquals(GoogleVertexAIOperations.generateEmbeddings, endpoint.getConfiguration().getOperation());
    }

    @Test
    public void testEndpointWithMultimodalOperation() throws Exception {
        GoogleVertexAIComponent component = context.getComponent("google-vertexai", GoogleVertexAIComponent.class);
        GoogleVertexAIEndpoint endpoint = (GoogleVertexAIEndpoint) component.createEndpoint(
                "google-vertexai:my-project:us-central1:gemini-2.0-flash?operation=generateMultimodal");

        assertNotNull(endpoint.getConfiguration());
        assertEquals(GoogleVertexAIOperations.generateMultimodal, endpoint.getConfiguration().getOperation());
    }

    @Test
    public void testEndpointWithStreamRawPredictOperation() throws Exception {
        GoogleVertexAIComponent component = context.getComponent("google-vertexai", GoogleVertexAIComponent.class);
        GoogleVertexAIEndpoint endpoint = (GoogleVertexAIEndpoint) component.createEndpoint(
                "google-vertexai:my-project:us-east5:claude-sonnet-4@20250514"
                                                                                            + "?operation=streamRawPredict&publisher=anthropic");

        assertNotNull(endpoint.getConfiguration());
        assertEquals(GoogleVertexAIOperations.streamRawPredict, endpoint.getConfiguration().getOperation());
        assertEquals("anthropic", endpoint.getConfiguration().getPublisher());
    }

    @Test
    public void testEndpointWithStreamOutputMode() throws Exception {
        GoogleVertexAIComponent component = context.getComponent("google-vertexai", GoogleVertexAIComponent.class);
        GoogleVertexAIEndpoint endpoint = (GoogleVertexAIEndpoint) component.createEndpoint(
                "google-vertexai:my-project:us-central1:gemini-2.0-flash"
                                                                                            + "?operation=generateChatStreaming&streamOutputMode=chunks");

        assertNotNull(endpoint.getConfiguration());
        assertEquals("chunks", endpoint.getConfiguration().getStreamOutputMode());
    }

    @Test
    public void testAllOperationEnumValues() {
        // Verify all operations exist in the enum
        assertEquals(9, GoogleVertexAIOperations.values().length);
        assertNotNull(GoogleVertexAIOperations.valueOf("generateText"));
        assertNotNull(GoogleVertexAIOperations.valueOf("generateChat"));
        assertNotNull(GoogleVertexAIOperations.valueOf("generateChatStreaming"));
        assertNotNull(GoogleVertexAIOperations.valueOf("generateImage"));
        assertNotNull(GoogleVertexAIOperations.valueOf("generateEmbeddings"));
        assertNotNull(GoogleVertexAIOperations.valueOf("generateCode"));
        assertNotNull(GoogleVertexAIOperations.valueOf("generateMultimodal"));
        assertNotNull(GoogleVertexAIOperations.valueOf("rawPredict"));
        assertNotNull(GoogleVertexAIOperations.valueOf("streamRawPredict"));
    }
}
