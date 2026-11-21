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
package org.apache.camel.maven.packaging;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CAMEL-27347: a sync with camel-website fails instead of writing a shorter list when GitHub does not answer, so a
 * scheduled run never removes advisories or releases from the catalog.
 */
class CamelWebsiteContentTest {

    private HttpServer server;
    private String base;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        reply("/security", 200, """
                [{"name": "CVE-2026-1.md", "download_url": "%1$s/raw/CVE-2026-1.md"},
                 {"name": "_index.md", "download_url": "%1$s/raw/_index.md"}]""");
        reply("/empty", 200, "[]");
        reply("/limited", 403, "{\"message\": \"API rate limit exceeded\"}");
        reply("/raw/CVE-2026-1.md", 200, "---\ncve: CVE-2026-1\n---\n");
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void reply(String path, int status, String body) {
        server.createContext(path, exchange -> {
            byte[] bytes = body.formatted(base).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
    }

    @Test
    void listsTheMatchingFiles() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            assertThat(CamelWebsiteContent.downloadUrls(client, base + "/security", n -> n.startsWith("CVE-")))
                    .containsExactly(base + "/raw/CVE-2026-1.md");
            assertThat(CamelWebsiteContent.download(client, base + "/raw/CVE-2026-1.md")).contains("cve: CVE-2026-1");
        }
    }

    @Test
    void failsWhenRateLimited() {
        try (HttpClient client = HttpClient.newHttpClient()) {
            assertThatThrownBy(() -> CamelWebsiteContent.downloadUrls(client, base + "/limited", n -> true))
                    .isInstanceOf(IOException.class).hasMessageContaining("HTTP status 403")
                    .hasMessageContaining("GITHUB_TOKEN");
        }
    }

    @Test
    void failsWhenNothingIsListed() {
        try (HttpClient client = HttpClient.newHttpClient()) {
            assertThatThrownBy(() -> CamelWebsiteContent.downloadUrls(client, base + "/empty", n -> true))
                    .isInstanceOf(IOException.class).hasMessageContaining("No files found");
            assertThatThrownBy(() -> CamelWebsiteContent.downloadUrls(client, base + "/security", n -> n.endsWith(".adoc")))
                    .isInstanceOf(IOException.class).hasMessageContaining("No files found");
        }
    }

    @Test
    void failsWhenAFileCannotBeDownloaded() {
        try (HttpClient client = HttpClient.newHttpClient()) {
            assertThatThrownBy(() -> CamelWebsiteContent.download(client, base + "/raw/missing.md"))
                    .isInstanceOf(IOException.class).hasMessageContaining("HTTP status 404");
        }
    }

    @Test
    void theTokenIsOnlyForTheGitHubApi() {
        assertThat(CamelWebsiteContent.request("https://raw.githubusercontent.com/apache/camel-website/main/x.md")
                .headers().firstValue("Authorization")).isEmpty();
    }
}
