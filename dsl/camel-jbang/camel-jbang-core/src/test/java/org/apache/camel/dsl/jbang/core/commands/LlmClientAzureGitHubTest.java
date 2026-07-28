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
package org.apache.camel.dsl.jbang.core.commands;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmClientAzureGitHubTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void isAzureOpenAiEndpointDetectsResourceHostAndDeploymentPaths() {
        assertThat(LlmClient.isAzureOpenAiEndpoint("https://myresource.openai.azure.com")).isTrue();
        assertThat(LlmClient.isAzureOpenAiEndpoint("http://127.0.0.1:8080/openai/deployments/gpt-4o")).isTrue();
        assertThat(LlmClient.isAzureOpenAiEndpoint("https://models.inference.ai.azure.com")).isFalse();
        assertThat(LlmClient.isAzureOpenAiEndpoint("https://api.openai.com")).isFalse();
    }

    @Test
    void resolveAzureDeploymentNameForChatUrlPrefersModelThenEnvThenFallback() {
        assertThat(LlmClient.resolveAzureDeploymentNameForChatUrl("my-deploy", "from-env")).isEqualTo("my-deploy");
        assertThat(LlmClient.resolveAzureDeploymentNameForChatUrl(null, "from-env")).isEqualTo("from-env");
        assertThat(LlmClient.resolveAzureDeploymentNameForChatUrl(null, null)).isEqualTo("gpt-4o");
    }

    @Test
    void normalizeOpenAiUrlBuildsAzureDeploymentChatPathWithApiVersion() {
        LlmClient client = LlmClient.create()
                .withApiType(LlmClient.ApiType.openai)
                .withModel("gpt-4o-deployment");

        String chatUrl = client.normalizeOpenAiUrl("https://myresource.openai.azure.com/");

        assertThat(chatUrl).isEqualTo(
                "https://myresource.openai.azure.com/openai/deployments/gpt-4o-deployment/chat/completions?api-version=2024-10-21");
    }

    @Test
    void normalizeOpenAiUrlPreservesExistingAzureChatCompletionsUrl() {
        LlmClient client = LlmClient.create().withApiType(LlmClient.ApiType.openai);
        String input = "https://myresource.openai.azure.com/openai/deployments/gpt-4o/chat/completions?api-version=2024-08-01";

        assertThat(client.normalizeOpenAiUrl(input)).isEqualTo(input);
    }

    @Test
    void normalizeOpenAiUrlStillNormalizesStandardOpenAiHost() {
        LlmClient client = LlmClient.create().withApiType(LlmClient.ApiType.openai);

        assertThat(client.normalizeOpenAiUrl("https://api.openai.com"))
                .isEqualTo("https://api.openai.com/v1/chat/completions");
    }

    @Test
    void normalizeOpenAiUrlNormalizesGitHubModelsHostLikeOpenAiCompatibleApi() {
        LlmClient client = LlmClient.create().withApiType(LlmClient.ApiType.openai);

        assertThat(client.normalizeOpenAiUrl("https://models.github.ai/inference"))
                .isEqualTo("https://models.github.ai/inference/chat/completions");
    }

    @Test
    void isGitHubModelsAutoDetectRequiresExplicitOptIn() {
        assertThat(LlmClient.isGitHubModelsAutoDetectEnabled()).isFalse();
    }

    @Test
    void isGitHubModelsOptInFlagRejectsDisabledValues() {
        assertThat(LlmClient.isGitHubModelsOptInFlag(null)).isFalse();
        assertThat(LlmClient.isGitHubModelsOptInFlag("")).isFalse();
        assertThat(LlmClient.isGitHubModelsOptInFlag("0")).isFalse();
        assertThat(LlmClient.isGitHubModelsOptInFlag("false")).isFalse();
        assertThat(LlmClient.isGitHubModelsOptInFlag("FALSE")).isFalse();
        assertThat(LlmClient.isGitHubModelsOptInFlag("1")).isTrue();
        assertThat(LlmClient.isGitHubModelsOptInFlag("true")).isTrue();
    }

    @Test
    void detectEndpointReplacesLlamaPlaceholderWithFirstAzureDeploymentFromModelsApi() throws IOException {
        AtomicReference<String> apiKeyHeader = new AtomicReference<>();
        String baseUrl = startAzureModelsServer(apiKeyHeader);
        LlmClient client = LlmClient.create()
                .withApiType(LlmClient.ApiType.openai)
                .withUrl(baseUrl + "/openai/deployments/ignored")
                .withApiKey("azure-secret")
                .withModel("llama3.2");

        assertThat(client.detectEndpoint()).isTrue();
        assertThat(client.model()).isEqualTo("deployment-a");
    }

    @Test
    void normalizeOpenAiModelsUrlBuildsAzureModelsPathWithApiVersion() {
        LlmClient client = LlmClient.create().withApiType(LlmClient.ApiType.openai);

        assertThat(client.normalizeOpenAiModelsUrl("https://myresource.openai.azure.com"))
                .isEqualTo("https://myresource.openai.azure.com/openai/models?api-version=2024-10-21");
    }

    @Test
    void buildOpenAiAuthHeadersUsesBearerForStandardOpenAi() {
        LlmClient client = LlmClient.create().withApiType(LlmClient.ApiType.openai).withApiKey("sk-test");

        assertThat(client.buildOpenAiAuthHeaders("sk-test"))
                .containsEntry("Authorization", "Bearer sk-test")
                .doesNotContainKey("api-key");
    }

    @Test
    void listsAzureOpenAiModelsWithApiKeyHeader() throws IOException {
        AtomicReference<String> apiKeyHeader = new AtomicReference<>();
        String baseUrl = startAzureModelsServer(apiKeyHeader);
        LlmClient client = LlmClient.create()
                .withApiType(LlmClient.ApiType.openai)
                .withUrl(baseUrl + "/openai/deployments/list-test")
                .withApiKey("azure-secret");

        assertThat(client.detectEndpoint()).isTrue();
        assertThat(client.listModels()).containsExactly("deployment-a", "deployment-b");
        assertThat(apiKeyHeader.get()).isEqualTo("azure-secret");
        assertThat(client.buildOpenAiAuthHeaders("azure-secret")).containsEntry("api-key", "azure-secret");
    }

    @Test
    void listsGitHubModelsWithBearerAuth() throws IOException {
        AtomicReference<String> authHeader = new AtomicReference<>();
        String baseUrl = startOpenAiCompatibleModelsServer(authHeader);
        LlmClient client = LlmClient.create()
                .withApiType(LlmClient.ApiType.openai)
                .withUrl(baseUrl)
                .withApiKey("ghp_test");

        assertThat(client.detectEndpoint()).isTrue();
        assertThat(client.listModels()).containsExactly("gpt-4o", "gpt-4o-mini");
        assertThat(authHeader.get()).isEqualTo("Bearer ghp_test");
    }

    private String startAzureModelsServer(AtomicReference<String> capturedApiKey) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/openai/models", exchange -> {
            if (capturedApiKey != null) {
                capturedApiKey.set(exchange.getRequestHeaders().getFirst("api-key"));
            }
            String query = exchange.getRequestURI().getQuery();
            if (query == null || !query.contains("api-version=")) {
                exchange.sendResponseHeaders(400, -1);
                exchange.close();
                return;
            }
            byte[] bytes = "{\"data\":[{\"id\":\"deployment-a\"},{\"id\":\"deployment-b\"}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private String startOpenAiCompatibleModelsServer(AtomicReference<String> capturedAuth) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/models", exchange -> {
            if (capturedAuth != null) {
                capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            }
            byte[] bytes = "{\"data\":[{\"id\":\"gpt-4o\"},{\"id\":\"gpt-4o-mini\"}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
