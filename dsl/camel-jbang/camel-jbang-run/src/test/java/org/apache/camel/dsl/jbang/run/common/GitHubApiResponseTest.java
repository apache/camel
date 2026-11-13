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
package org.apache.camel.dsl.jbang.run.common;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * How the GitHub and gist API responses are read.
 */
class GitHubApiResponseTest {

    private static final String RAW = "https://gist.githubusercontent.com/me/abc/raw/def/route.java";

    private HttpServer server;
    private String url;
    private volatile String response;
    private volatile int statusCode = 200;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            if (statusCode != 200) {
                exchange.sendResponseHeaders(statusCode, -1);
                exchange.close();
            } else {
                byte[] body = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            }
        });
        server.start();
        url = "http://localhost:" + server.getAddress().getPort() + "/";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void malformedGistResponseFails() {
        response = "{not json";

        assertThatThrownBy(() -> GistHelper.resolveGistAsRawFiles(url, null, null, null, new StringJoiner(",")))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    void malformedGithubResponseFails() {
        response = "[{not json";

        assertThatThrownBy(() -> GitHubHelper.resolveGithubAsRawFiles(url, null, null, null, null, new StringJoiner(",")))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    void gistResponseIsRead() throws Exception {
        response = "{\"files\":{\"route.java\":{\"filename\":\"route.java\",\"raw_url\":\"" + RAW + "\"}}}";
        StringJoiner all = new StringJoiner(",");

        GistHelper.resolveGistAsRawFiles(url, null, null, null, all);

        assertThat(all).hasToString(GistHelper.asGistSingleUrl(RAW));
    }

    @Test
    void trailingContentAndNonTextNamesAreTolerated() throws Exception {
        response = "[{\"name\":{},\"html_url\":\"https://github.com/me/repo/blob/main/route.java\"}] trailing";
        StringJoiner all = new StringJoiner(",");

        GitHubHelper.resolveGithubAsRawFiles(url, null, null, null, null, all);

        assertThat(all).hasToString(GitHubHelper.asGithubSingleUrl("https://github.com/me/repo/blob/main/route.java"));
    }

    @Test
    void nonOkGithubResponseThrows() {
        statusCode = 404;

        assertThatThrownBy(() -> GitHubHelper.resolveGithubAsRawFiles(url, null, null, null, null, new StringJoiner(",")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("404");
    }

    @Test
    void rateLimitedGithubResponseThrowsWithHint() {
        statusCode = 403;

        assertThatThrownBy(() -> GitHubHelper.resolveGithubAsRawFiles(url, null, null, null, null, new StringJoiner(",")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("403")
                .hasMessageContaining("access denied");
    }

    @Test
    void nonOkGistResponseThrows() {
        statusCode = 404;

        assertThatThrownBy(() -> GistHelper.resolveGistAsRawFiles(url, null, null, null, new StringJoiner(",")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("404");
    }
}
