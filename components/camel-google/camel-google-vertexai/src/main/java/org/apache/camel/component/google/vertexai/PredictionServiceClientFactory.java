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

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.aiplatform.v1.PredictionServiceClient;
import com.google.cloud.aiplatform.v1.PredictionServiceSettings;
import com.google.common.base.Strings;
import org.apache.camel.CamelContext;
import org.apache.camel.support.ResourceHelper;

/**
 * Factory for creating {@link PredictionServiceClient} instances for Vertex AI Prediction Services.
 * <p>
 * This client is used for rawPredict operations to call partner models (Claude, Llama, Mistral) and custom deployed
 * models on Vertex AI.
 * </p>
 */
public final class PredictionServiceClientFactory {

    /**
     * OAuth scopes required for Vertex AI Prediction API.
     */
    private static final List<String> VERTEX_AI_SCOPES = Arrays.asList(
            "https://www.googleapis.com/auth/cloud-platform");

    /**
     * Prevent instantiation.
     */
    private PredictionServiceClientFactory() {
    }

    /**
     * Default regional endpoint for partner models when "global" is specified. The gRPC client doesn't support the
     * "global" endpoint directly, so we fall back to a regional endpoint.
     */
    private static final String DEFAULT_REGIONAL_ENDPOINT = "us-east5";

    /**
     * Creates a PredictionServiceClient for the specified configuration.
     *
     * @param  context       the Camel context for resource loading
     * @param  configuration the Vertex AI configuration
     * @return               a configured PredictionServiceClient
     * @throws Exception     if client creation fails
     */
    public static PredictionServiceClient create(
            CamelContext context, GoogleVertexAIConfiguration configuration)
            throws Exception {

        String location = configuration.getLocation();

        // The gRPC client doesn't support "global" endpoint directly.
        // For global, we use a default regional endpoint.
        // Note: Global endpoints are primarily supported via REST API, not gRPC.
        if ("global".equalsIgnoreCase(location)) {
            location = DEFAULT_REGIONAL_ENDPOINT;
        }

        String endpoint = String.format("%s-aiplatform.googleapis.com:443", location);

        PredictionServiceSettings.Builder settingsBuilder = PredictionServiceSettings.newBuilder()
                .setEndpoint(endpoint);

        // Configure credentials
        GoogleCredentials credentials = loadCredentials(context, configuration);
        if (credentials != null) {
            settingsBuilder.setCredentialsProvider(FixedCredentialsProvider.create(credentials));
        }

        return PredictionServiceClient.create(settingsBuilder.build());
    }

    /**
     * Loads Google credentials from configuration or environment.
     *
     * @param  context       the Camel context
     * @param  configuration the configuration containing service account key path
     * @return               GoogleCredentials or null if using ADC
     * @throws IOException   if credential loading fails
     */
    private static GoogleCredentials loadCredentials(
            CamelContext context, GoogleVertexAIConfiguration configuration)
            throws Exception {

        if (!Strings.isNullOrEmpty(configuration.getServiceAccountKey())) {
            InputStream credentialsStream = ResourceHelper.resolveMandatoryResourceAsInputStream(
                    context, configuration.getServiceAccountKey());
            return GoogleCredentials.fromStream(credentialsStream)
                    .createScoped(VERTEX_AI_SCOPES);
        }

        // Return null to use Application Default Credentials
        return null;
    }

    /**
     * Builds the endpoint name for publisher models (partner models like Claude, Llama).
     * <p>
     * Format: {@code projects/{project}/locations/{location}/publishers/{publisher}/models/{model}}
     * </p>
     *
     * @param  projectId the GCP project ID
     * @param  location  the GCP region (e.g., us-central1)
     * @param  publisher the model publisher (e.g., anthropic, meta, mistralai)
     * @param  modelId   the model ID with version (e.g., claude-3-5-sonnet-v2@20241022)
     * @return           the formatted endpoint name
     */
    public static String buildPublisherModelEndpoint(
            String projectId, String location, String publisher, String modelId) {
        return String.format(
                "projects/%s/locations/%s/publishers/%s/models/%s",
                projectId, location, publisher, modelId);
    }

    /**
     * Builds the endpoint name for custom deployed models.
     * <p>
     * Format: {@code projects/{project}/locations/{location}/endpoints/{endpoint}}
     * </p>
     *
     * @param  projectId  the GCP project ID
     * @param  location   the GCP region
     * @param  endpointId the deployed endpoint ID
     * @return            the formatted endpoint name
     */
    public static String buildCustomEndpoint(
            String projectId, String location, String endpointId) {
        return String.format(
                "projects/%s/locations/%s/endpoints/%s",
                projectId, location, endpointId);
    }
}
