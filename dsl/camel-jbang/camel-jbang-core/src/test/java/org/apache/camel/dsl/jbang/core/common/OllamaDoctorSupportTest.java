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
package org.apache.camel.dsl.jbang.core.common;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.dsl.jbang.core.commands.LlmClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaDoctorSupportTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void detectReturnsNotRunningWhenEndpointUnreachable() {
        LlmClient client = LlmClient.create().withApiType(LlmClient.ApiType.ollama).withUrl("http://127.0.0.1:1");

        OllamaDoctorSupport.Status status = OllamaDoctorSupport.detect(client);

        assertThat(status.running()).isFalse();
        assertThat(status.baseUrl()).isNull();
        assertThat(status.models()).isEmpty();
    }

    @Test
    void detectUsesLlmClientProbeLogic() throws IOException {
        String baseUrl = startOllamaServer("{\"models\":[{\"name\":\"qwen2.5:32b\"}]}");
        LlmClient client = LlmClient.create().withApiType(LlmClient.ApiType.ollama).withUrl(baseUrl);

        assertThat(client.detectEndpoint()).isTrue();
        assertThat(client.listModels()).containsExactly("qwen2.5:32b");
    }

    @Test
    void detectListsModelsFromRunningOllama() throws IOException {
        String baseUrl = startOllamaServer("{\"models\":[{\"name\":\"qwen2.5:32b\"},{\"name\":\"llama3.2:latest\"}]}");

        OllamaDoctorSupport.Status status = detectAt(baseUrl);

        assertThat(status.running()).isTrue();
        assertThat(status.baseUrl()).isEqualTo(baseUrl);
        assertThat(status.models()).containsExactly("qwen2.5:32b", "llama3.2:latest");
    }

    @Test
    void detectReportsRunningWhenNoModelsPulled() throws IOException {
        String baseUrl = startOllamaServer("{\"models\":[]}");

        OllamaDoctorSupport.Status status = detectAt(baseUrl);

        assertThat(status.running()).isTrue();
        assertThat(status.models()).isEmpty();
        assertThat(OllamaDoctorSupport.formatModels(status.models())).isEqualTo("no models pulled");
    }

    @Test
    void cliRunningLineMatchesDoctorFormat() throws IOException {
        String baseUrl = startOllamaServer("{\"models\":[{\"name\":\"qwen2.5:32b\"},{\"name\":\"llama3.2:latest\"}]}");
        OllamaDoctorSupport.Status status = detectAt(baseUrl);

        assertThat(OllamaDoctorSupport.cliRunningLine(status))
                .isEqualTo("Running at 127.0.0.1:" + displayPort(baseUrl)
                           + " — models: qwen2.5:32b, llama3.2:latest");
    }

    @Test
    void formatDisplayHostStripsSchemeAndTrailingSlash() {
        assertThat(OllamaDoctorSupport.formatDisplayHost("http://localhost:11434/"))
                .isEqualTo("localhost:11434");
        assertThat(OllamaDoctorSupport.formatDisplayHost("https://127.0.0.1:11434"))
                .isEqualTo("127.0.0.1:11434");
    }

    @Test
    void tuiRunningSummaryIncludesModelCount() {
        OllamaDoctorSupport.Status status
                = new OllamaDoctorSupport.Status(true, "http://localhost:11434", List.of("a", "b"));

        assertThat(OllamaDoctorSupport.tuiRunningSummary(status, 30))
                .isEqualTo("localhost:11434 (2 models)");
        assertThat(OllamaDoctorSupport.modelCountLabel(List.of("one"))).isEqualTo("1 model");
    }

    // --- isSmallModel ---

    @Test
    void isSmallModelReturnsTrueForSmallTag() {
        assertThat(OllamaDoctorSupport.isSmallModel("qwen2.5:7b")).isTrue();
        assertThat(OllamaDoctorSupport.isSmallModel("gemma3:1b-it")).isTrue();
    }

    @Test
    void isSmallModelReturnsFalseForLargeTag() {
        assertThat(OllamaDoctorSupport.isSmallModel("qwen2.5:32b")).isFalse();
        assertThat(OllamaDoctorSupport.isSmallModel("llama3.1:70b")).isFalse();
    }

    @Test
    void isSmallModelReturnsFalseForNonNumericTag() {
        assertThat(OllamaDoctorSupport.isSmallModel("llama3.2:latest")).isFalse();
        assertThat(OllamaDoctorSupport.isSmallModel("phi4-mini:latest")).isFalse();
    }

    @Test
    void isSmallModelReturnsFalseForModelWithoutColon() {
        assertThat(OllamaDoctorSupport.isSmallModel("model-without-colon")).isFalse();
    }

    // --- allModelsSmall ---

    @Test
    void allModelsSmallReturnsTrueWhenAllTagsAreSmall() {
        assertThat(OllamaDoctorSupport.allModelsSmall(List.of("qwen2.5:7b", "gemma3:1b-it"))).isTrue();
    }

    @Test
    void allModelsSmallReturnsFalseWhenAnyTagIsLarge() {
        assertThat(OllamaDoctorSupport.allModelsSmall(List.of("qwen2.5:7b", "qwen2.5:32b"))).isFalse();
    }

    @Test
    void allModelsSmallReturnsTrueForUnknownSizeTag() {
        // llama3.2:latest is 3B but has no numeric size tag — treated as "not known to be large"
        assertThat(OllamaDoctorSupport.allModelsSmall(List.of("llama3.2:latest"))).isTrue();
    }

    @Test
    void allModelsSmallReturnsFalseForEmptyList() {
        assertThat(OllamaDoctorSupport.allModelsSmall(List.of())).isFalse();
        assertThat(OllamaDoctorSupport.allModelsSmall(null)).isFalse();
    }

    private OllamaDoctorSupport.Status detectAt(String baseUrl) {
        LlmClient client = LlmClient.create().withApiType(LlmClient.ApiType.ollama).withUrl(baseUrl);
        assertThat(client.detectEndpoint()).isTrue();
        return new OllamaDoctorSupport.Status(true, client.endpointUrl(), client.listModels());
    }

    private String startOllamaServer(String apiTagsBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String body;
            int status;
            if ("/api/tags".equals(path)) {
                body = apiTagsBody;
                status = 200;
            } else if ("/".equals(path)) {
                body = "Ollama is running";
                status = 200;
            } else {
                body = "";
                status = 404;
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static int displayPort(String baseUrl) {
        return Integer.parseInt(baseUrl.substring(baseUrl.lastIndexOf(':') + 1));
    }
}
