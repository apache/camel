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

import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GoogleVertexAIComponentConfigurationTest extends CamelTestSupport {

    @Test
    public void createEndpointWithMinimalConfiguration() throws Exception {
        final String projectId = "my-gcp-project";
        final String location = "us-central1";
        final String modelId = "gemini-1.5-pro";

        GoogleVertexAIComponent component = context.getComponent("google-vertexai", GoogleVertexAIComponent.class);
        GoogleVertexAIEndpoint endpoint = (GoogleVertexAIEndpoint) component
                .createEndpoint(String.format("google-vertexai:%s:%s:%s", projectId, location, modelId));

        assertEquals(projectId, endpoint.getConfiguration().getProjectId());
        assertEquals(location, endpoint.getConfiguration().getLocation());
        assertEquals(modelId, endpoint.getConfiguration().getModelId());
    }

    @Test
    public void createEndpointWithFullConfiguration() throws Exception {
        final String projectId = "my-gcp-project";
        final String location = "us-central1";
        final String modelId = "text-bison";
        final String serviceAccountKeyFile = "credentials.json";
        final float temperature = 0.5f;
        final int maxOutputTokens = 2048;

        GoogleVertexAIComponent component = context.getComponent("google-vertexai", GoogleVertexAIComponent.class);
        GoogleVertexAIEndpoint endpoint = (GoogleVertexAIEndpoint) component.createEndpoint(
                String.format(
                        "google-vertexai:%s:%s:%s?serviceAccountKey=file:%s&temperature=%s&maxOutputTokens=%s&operation=generateText",
                        projectId, location, modelId, serviceAccountKeyFile, temperature, maxOutputTokens));

        GoogleVertexAIConfiguration configuration = endpoint.getConfiguration();
        assertEquals(projectId, configuration.getProjectId());
        assertEquals(location, configuration.getLocation());
        assertEquals(modelId, configuration.getModelId());
        assertEquals("file:" + serviceAccountKeyFile, configuration.getServiceAccountKey());
        assertEquals(temperature, configuration.getTemperature());
        assertEquals(maxOutputTokens, configuration.getMaxOutputTokens());
        assertEquals(GoogleVertexAIOperations.generateText, configuration.getOperation());
    }
}
