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

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Strings;
import com.google.genai.Client;
import org.apache.camel.CamelContext;
import org.apache.camel.support.ResourceHelper;

public final class GoogleVertexAIConnectionFactory {

    /**
     * OAuth scopes required for Vertex AI Generative AI API.
     */
    private static final List<String> VERTEX_AI_SCOPES = Arrays.asList(
            "https://www.googleapis.com/auth/cloud-platform",
            "https://www.googleapis.com/auth/generative-language");

    /**
     * Prevent instantiation.
     */
    private GoogleVertexAIConnectionFactory() {
    }

    public static Client create(
            CamelContext context, GoogleVertexAIConfiguration configuration)
            throws Exception {

        Client.Builder clientBuilder = Client.builder()
                .vertexAI(true)
                .project(configuration.getProjectId())
                .location(configuration.getLocation());

        if (!Strings.isNullOrEmpty(configuration.getServiceAccountKey())) {
            // Load credentials from the service account key file
            InputStream credentialsStream
                    = ResourceHelper.resolveMandatoryResourceAsInputStream(context, configuration.getServiceAccountKey());
            GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream)
                    .createScoped(VERTEX_AI_SCOPES);
            clientBuilder.credentials(credentials);
        }
        // If no service account key is provided, it will use Application Default Credentials
        // This requires the environment variable GOOGLE_APPLICATION_CREDENTIALS to be set
        // with the service account file path
        // More info at https://cloud.google.com/docs/authentication/production

        return clientBuilder.build();
    }
}
