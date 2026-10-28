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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The client of the MCP server stays connected while it is idle: an MCP client over HTTP only sends a request when it
 * uses a tool, so a minute without one is not a disconnect. It is gone when it closes its session.
 */
class TuiMcpServerClientTest {

    private final HttpClient http = HttpClient.newHttpClient();
    private TuiMcpServer server;

    @BeforeEach
    void start() throws Exception {
        server = new TuiMcpServer(0, null);
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    void aClientStaysConnectedWhileIdleAndGoesWhenItClosesItsSession() throws Exception {
        assertThat(server.getConnectedClient()).isNull();

        JsonObject params = new JsonObject(Map.of("clientInfo", new JsonObject(Map.of("name", "claude-code"))));
        post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":" + params.toJson() + "}");
        assertThat(server.getConnectedClient()).isEqualTo("claude-code");

        // ten minutes without a tool call
        server.idleFor(10 * 60_000);
        assertThat(server.getConnectedClient()).isEqualTo("claude-code");

        HttpRequest delete = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getPort() + "/mcp"))
                .DELETE().build();
        assertThat(http.send(delete, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(200);
        assertThat(server.getConnectedClient()).isNull();
    }

    @Test
    void aClientThatNeverSaysInitializeIsConnectedForAMinute() throws Exception {
        post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}");
        assertThat(server.getConnectedClient()).isEqualTo("connected");

        server.idleFor(2 * 60_000);
        assertThat(server.getConnectedClient()).isNull();
    }

    private void post(String body) throws Exception {
        HttpRequest post = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getPort() + "/mcp"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        http.send(post, HttpResponse.BodyHandlers.ofString());
    }
}
