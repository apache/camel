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
import java.nio.file.Files;
import java.nio.file.Path;

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.dsl.jbang.core.commands.LlmClient.ApiType;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariables;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An endpoint the user names with --url is the only one the client uses: when it does not answer, the client does not
 * go on to another provider. Here there is always another one to find, an Ollama started with camel infra run.
 */
class LlmClientExplicitUrlTest {

    private static final String NOTHING_THERE = "http://127.0.0.1:1";

    @TempDir
    Path home;

    private Path previousHome;
    private FakeLlmServer infraOllama;

    @BeforeEach
    void start() throws Exception {
        previousHome = CommandLineHelper.getHomeDir();
        CommandLineHelper.useHomeDir(home.toString());
        Files.createDirectories(CommandLineHelper.getCamelDir());
        // what camel infra run ollama leaves for the CLI to find
        infraOllama = new FakeLlmServer("unused");
        Files.writeString(CommandLineHelper.getCamelDir().resolve("infra-ollama-4711.json"),
                "{\"baseUrl\":\"" + infraOllama.url() + "\"}");
    }

    @AfterEach
    void stop() {
        infraOllama.close();
        CommandLineHelper.useHomeDir(previousHome.toString());
    }

    @Test
    void withoutAUrlTheOllamaOfCamelInfraIsFound() {
        LlmClient client = LlmClient.create().withApiType(ApiType.ollama);

        assertThat(client.detectEndpoint()).isTrue();
        assertThat(client.endpointUrl()).isEqualTo(infraOllama.url());
    }

    @Test
    void anUnreachableUrlOfAnApiType() {
        LlmClient client = LlmClient.create().withApiType(ApiType.ollama).withUrl(NOTHING_THERE);

        assertThat(client.detectEndpoint()).isFalse();
        // not the url of another Ollama
        assertThat(client.endpointUrl()).isEqualTo(NOTHING_THERE);
    }

    @Test
    void anUnreachableUrlWithoutAnApiType() {
        LlmClient client = LlmClient.create().withUrl(NOTHING_THERE);

        assertThat(client.detectEndpoint()).isFalse();
        assertThat(client.endpointUrl()).isEqualTo(NOTHING_THERE);
        assertThat(client.apiType()).isNull();
    }

    @Test
    void aUrlThatAnswersIsUsed() throws Exception {
        try (FakeLlmServer server = new FakeLlmServer("unused")) {
            LlmClient client = LlmClient.create().withUrl(server.url());

            assertThat(client.detectEndpoint()).isTrue();
            assertThat(client.endpointUrl()).isEqualTo(server.url());
            assertThat(client.apiType()).isEqualTo(ApiType.ollama);
        }
    }

    /**
     * CAMEL-27514: a URL ending with /v1 (the common OpenAI base path) must not produce a double /v1/v1/models probe.
     * The server only knows /v1/models; /v1/v1/models returns 404 and would cause detection to fail.
     */
    @Test
    void urlEndingWithV1IsDetectedAsOpenAi() throws Exception {
        try (FakeLlmServer server = new FakeLlmServer("unused")) {
            // The user passes the OpenAI-style base URL that includes the /v1 segment.
            LlmClient client = LlmClient.create().withUrl(server.url() + "/v1");

            assertThat(client.detectEndpoint()).isTrue();
            assertThat(client.apiType()).isEqualTo(ApiType.openai);
        }
    }

    /**
     * CAMEL-27514: an OpenAI-compatible server that requires auth (returns 401 without a key) must be treated as
     * reachable when the user has supplied an API key, because the health probe now forwards the Authorization header.
     * <p>
     * The negative assertion (no-key → detection fails) is skipped when {@code OPENAI_API_KEY} or {@code LLM_API_KEY}
     * is set in the environment: {@code resolveApiKey()} falls back to those variables, so a developer machine with
     * either variable set would send a Bearer header and make detection succeed even without an explicit key.
     */
    @Test
    @DisabledIfEnvironmentVariables({
            @DisabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+"),
            @DisabledIfEnvironmentVariable(named = "LLM_API_KEY", matches = ".+")
    })
    void openAiCompatibleEndpointRequiringAuthIsDetectedWhenKeyIsConfigured() throws IOException {
        // A server that replies 401 to /v1/models without an Authorization header, 200 with one.
        HttpServer authServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        authServer.createContext("/v1/models", exchange -> {
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            int status = (auth != null && auth.startsWith("Bearer ")) ? 200 : 401;
            String body = status == 200 ? "{\"data\":[{\"id\":\"test\"}]}" : "";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        authServer.start();
        String base = "http://127.0.0.1:" + authServer.getAddress().getPort();
        try {
            // Without a key the server returns 401, endpoint detection must fail.
            LlmClient noKey = LlmClient.create().withUrl(base);
            assertThat(noKey.detectEndpoint()).isFalse();

            // With a key the Authorization header is forwarded; detection succeeds.
            LlmClient withKey = LlmClient.create().withUrl(base).withApiKey("sk-test");
            assertThat(withKey.detectEndpoint()).isTrue();
            assertThat(withKey.apiType()).isEqualTo(ApiType.openai);
        } finally {
            authServer.stop(0);
        }
    }

    /**
     * CAMEL-27514: when {@code --api-type=openai} is set explicitly, detection goes through
     * {@code isEndpointReachable()} rather than {@code tryExplicitUrl()}. The same normalisation must apply: a URL
     * ending with {@code /v1} or {@code /v1/} must not produce a double {@code /v1/v1/models} probe.
     * <p>
     * {@code /api/tags} is overridden to 404 so that the Ollama probe fails and the assertions confirm that detection
     * succeeds via the {@code /v1/models} probe, not via a coincidental Ollama hit.
     */
    @Test
    void withApiTypeOpenAiAndV1UrlVariants() throws Exception {
        try (FakeLlmServer server = new FakeLlmServer("unused")) {
            // Fail the Ollama /api/tags probe so the assertions exercise the /v1/models OpenAI probe.
            server.reply("/api/tags", 404, "");

            // Plain base URL — sanity check that the path still works without a /v1 suffix.
            LlmClient base = LlmClient.create().withApiType(ApiType.openai).withUrl(server.url());
            assertThat(base.detectEndpoint()).isTrue();

            // URL already ending with /v1 — must not produce /v1/v1/models.
            LlmClient v1 = LlmClient.create().withApiType(ApiType.openai).withUrl(server.url() + "/v1");
            assertThat(v1.detectEndpoint()).isTrue();

            // URL ending with /v1/ (trailing slash) — normalisation must strip it too.
            LlmClient v1Slash = LlmClient.create().withApiType(ApiType.openai).withUrl(server.url() + "/v1/");
            assertThat(v1Slash.detectEndpoint()).isTrue();
        }
    }

    /**
     * CAMEL-27514: with {@code --api-type=ollama} the client calls {@code <url>/api/generate}, so a URL ending with
     * {@code /v1} is not an Ollama endpoint, even though Ollama also answers {@code /v1/models} (its OpenAI-compatible
     * API). Detection must fail up front instead of failing later on {@code /v1/api/generate}.
     */
    @Test
    void withApiTypeOllamaAndV1UrlIsNotReachable() throws Exception {
        try (FakeLlmServer server = new FakeLlmServer("unused")) {
            // the server answers /api/tags and /v1/models at its root, as Ollama does
            LlmClient root = LlmClient.create().withApiType(ApiType.ollama).withUrl(server.url());
            assertThat(root.detectEndpoint()).isTrue();

            LlmClient v1 = LlmClient.create().withApiType(ApiType.ollama).withUrl(server.url() + "/v1");
            assertThat(v1.detectEndpoint()).isFalse();

            LlmClient v1Slash = LlmClient.create().withApiType(ApiType.ollama).withUrl(server.url() + "/v1/");
            assertThat(v1Slash.detectEndpoint()).isFalse();
        }
    }
}
