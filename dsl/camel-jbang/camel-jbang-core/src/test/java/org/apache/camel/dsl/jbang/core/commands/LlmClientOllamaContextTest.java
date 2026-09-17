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
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The context window the client asks Ollama for: adopt a loaded model's window, else 64k when it fits, else 32k. A
 * local {@link HttpServer} plays Ollama's {@code /api/ps}, {@code /api/tags} and {@code /api/show}. The environment
 * override cannot be tested here (it wins over everything), so the tests are skipped when it is set.
 */
@DisabledIfEnvironmentVariable(named = "OLLAMA_CONTEXT_LENGTH", matches = ".+")
class LlmClientOllamaContextTest {

    private static final long GIB = 1L << 30;

    /**
     * qwen3.6:35b-a3b as Ollama 0.33 describes it: 41 layers of which every fourth keeps a full attention cache, 2 KV
     * heads with 256-wide keys and values, 22.6 GB of weights.
     */
    private static final String SHOW_MOE = """
            {"model_info":{"general.architecture":"qwen35moe","qwen35moe.block_count":41,
            "qwen35moe.attention.head_count":16,"qwen35moe.attention.head_count_kv":2,
            "qwen35moe.attention.key_length":256,"qwen35moe.attention.value_length":256,
            "qwen35moe.full_attention_interval":4,"qwen35moe.embedding_length":2048}}
            """;
    private static final long MOE_WEIGHTS = 22_621_000_000L;
    /** 10 cache layers x 2 KV heads x (256 + 256) x 2 bytes. */
    private static final long MOE_KV_PER_TOKEN = 10L * 2 * 512 * 2;
    /** A dense 32B: 64 layers, 8 KV heads of 128, 20 GB of weights. */
    private static final String SHOW_DENSE = """
            {"model_info":{"general.architecture":"qwen2","qwen2.block_count":64,
            "qwen2.attention.head_count":40,"qwen2.attention.head_count_kv":8,"qwen2.embedding_length":5120}}
            """;

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void adoptsTheWindowOfAModelThatIsAlreadyLoaded() throws IOException {
        LlmClient client = client("qwen3.6:35b-a3b", 262144, SHOW_MOE, MOE_WEIGHTS);
        assertEquals(262144, client.resolveOllamaContextWindow(64 * GIB));
    }

    @Test
    void raisesASmallLoadedWindowToTheMinimum() throws IOException {
        LlmClient client = client("qwen3.6:35b-a3b", 4096, SHOW_MOE, MOE_WEIGHTS);
        assertEquals(LlmClient.OLLAMA_MIN_CONTEXT, client.resolveOllamaContextWindow(64 * GIB));
    }

    @Test
    void asksForTheMaximumWhenTheModelIsNotLoadedAndItsCacheFits() throws IOException {
        LlmClient client = client("qwen3.6:35b-a3b", 0, SHOW_MOE, MOE_WEIGHTS);
        // 22.6 GB of weights plus 2.5 GiB of cache for 64k: fits 32 GB and 64 GB machines
        assertEquals(LlmClient.OLLAMA_MAX_CONTEXT, client.resolveOllamaContextWindow(64 * GIB));
        assertEquals(LlmClient.OLLAMA_MAX_CONTEXT, client.resolveOllamaContextWindow(32 * GIB));
        // but not a 16 GB machine, where the weights alone exceed the share the GPU can wire
        assertEquals(LlmClient.OLLAMA_MIN_CONTEXT, client.resolveOllamaContextWindow(16 * GIB));
    }

    @Test
    void staysAtTheMinimumForADenseModelWhoseCacheWouldNotFit() throws IOException {
        LlmClient client = client("qwen2.5:32b", 0, SHOW_DENSE, 20L * GIB);
        // 64 layers x 8 KV heads x 128 x 4 bytes = 256 KB per token: 16 GB for 64k on top of 20 GB of weights
        assertEquals(LlmClient.OLLAMA_MIN_CONTEXT, client.resolveOllamaContextWindow(32 * GIB));
        assertEquals(LlmClient.OLLAMA_MAX_CONTEXT, client.resolveOllamaContextWindow(64 * GIB));
    }

    @Test
    void fallsBackToTheMinimumWhenOllamaDoesNotAnswer() {
        LlmClient client = LlmClient.create().withApiType(LlmClient.ApiType.ollama)
                .withUrl("http://127.0.0.1:1").withModel("qwen3.6:35b-a3b");
        assertEquals(LlmClient.OLLAMA_MIN_CONTEXT, client.resolveOllamaContextWindow(64 * GIB));
    }

    @Test
    void resolvedWindowIsCachedPerModelAndForgottenOnReset() throws IOException {
        LlmClient client = client("qwen3.6:35b-a3b", 262144, SHOW_MOE, MOE_WEIGHTS);
        assertEquals(262144, client.ollamaContextWindow());
        server.stop(0);
        server = null;
        // still the cached value although the server is gone
        assertEquals(262144, client.ollamaContextWindow());
        client.resetOllamaContextWindow();
        assertEquals(LlmClient.OLLAMA_MIN_CONTEXT, client.ollamaContextWindow());
    }

    @Test
    void theWindowCanBeResolvedAheadOfTheFirstRequest() throws IOException {
        LlmClient client = client("qwen3.6:35b-a3b", 65536, SHOW_MOE, MOE_WEIGHTS);
        assertNull(client.resolvedOllamaContextWindow(), "nothing resolved until asked");

        client.resolveOllamaContextWindowInBackground();
        await().atMost(10, TimeUnit.SECONDS).until(() -> client.resolvedOllamaContextWindow() != null);

        // the server can go: the first request finds the window without asking
        server.stop(0);
        server = null;
        assertEquals(65536, client.ollamaContextWindow());

        // a model switch invalidates it, and a later warm-up resolves for the new model (no server: the minimum)
        client.withModel("other:latest");
        assertNull(client.resolvedOllamaContextWindow());
        client.resolveOllamaContextWindowInBackground();
        await().atMost(20, TimeUnit.SECONDS).until(() -> client.resolvedOllamaContextWindow() != null);
        assertEquals(LlmClient.OLLAMA_MIN_CONTEXT, client.ollamaContextWindow());
    }

    @Test
    void kvBytesPerTokenFromTheArchitectureFacts() {
        assertEquals(MOE_KV_PER_TOKEN, LlmClient.ollamaKvBytesPerToken(json(SHOW_MOE)));
        assertEquals(64L * 8 * (128 + 128) * 2, LlmClient.ollamaKvBytesPerToken(json(SHOW_DENSE)));
        // key_length wins over embedding / heads when present, value_length defaults to it, and missing KV heads fall
        // back to the head count
        assertEquals(32L * 32 * (96 + 96) * 2, LlmClient.ollamaKvBytesPerToken(json(
                "{\"model_info\":{\"general.architecture\":\"llama\",\"llama.block_count\":32,"
                                                                                    + "\"llama.attention.head_count\":32,\"llama.attention.key_length\":96,\"llama.embedding_length\":4096}}")));
        assertEquals(0, LlmClient.ollamaKvBytesPerToken(json("{\"details\":{}}")));
        assertEquals(0, LlmClient.ollamaKvBytesPerToken(null));
    }

    @Test
    void fitRuleLeavesRoomForComputeBuffersAndTheSystem() {
        long perToken = MOE_KV_PER_TOKEN;
        assertTrue(LlmClient.ollamaContextFits(21 * GIB, perToken, 65_536, 32 * GIB));
        assertFalse(LlmClient.ollamaContextFits(21 * GIB, perToken, 65_536, 16 * GIB));
        assertFalse(LlmClient.ollamaContextFits(20 * GIB, 256 * 1024, 65_536, 32 * GIB));
        // even 32k is 28 GiB for that model; the minimum is requested regardless, this rule only decides 64k
        assertFalse(LlmClient.ollamaContextFits(20 * GIB, 256 * 1024, 32_768, 32 * GIB));
        assertTrue(LlmClient.ollamaContextFits(20 * GIB, 256 * 1024, 32_768, 64 * GIB));
    }

    @Test
    void modelNamesMatchWithOrWithoutTag() {
        assertTrue(LlmClient.ollamaModelMatches("qwen3.6:35b-a3b", "qwen3.6:35b-a3b"));
        assertTrue(LlmClient.ollamaModelMatches("llama3.2", "llama3.2:latest"));
        assertFalse(LlmClient.ollamaModelMatches("llama3.2:1b", "llama3.2:latest"));
        assertFalse(LlmClient.ollamaModelMatches("llama3", "llama3.2:latest"));
        assertFalse(LlmClient.ollamaModelMatches(null, "x"));
    }

    private LlmClient client(String model, long loadedContext, String showBody, long sizeBytes) throws IOException {
        String ps = loadedContext > 0
                ? "{\"models\":[{\"name\":\"" + model + "\",\"model\":\"" + model + "\",\"size\":" + sizeBytes
                  + ",\"size_vram\":" + sizeBytes + ",\"context_length\":" + loadedContext + "}]}"
                : "{\"models\":[]}";
        String tags = "{\"models\":[{\"name\":\"other:latest\",\"size\":1000},{\"name\":\"" + model + "\",\"size\":"
                      + sizeBytes + "}]}";
        Map<String, String> routes = Map.of("/api/ps", ps, "/api/tags", tags, "/api/show", showBody);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String body = routes.getOrDefault(path, "/".equals(path) ? "Ollama is running" : null);
            int status = body != null ? 200 : 404;
            byte[] bytes = (body != null ? body : "not found").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return LlmClient.create().withApiType(LlmClient.ApiType.ollama).withUrl(base).withModel(model);
    }

    private static JsonObject json(String text) {
        try {
            return (JsonObject) Jsoner.deserialize(text);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
