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

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A client of {@code camel tui --mcp} learns that a tool failed from {@code isError}, not from reading its text: the
 * error of a shared tool is an error of the call, and only a tool that does not exist is an unknown tool.
 */
class TuiMcpServerToolErrorTest {

    private final HttpClient http = HttpClient.newHttpClient();
    private TuiMcpServer server;
    private int id;

    @BeforeEach
    void start() throws Exception {
        server = new TuiMcpServer(0, null);
        server.start();
        rpc("initialize", new JsonObject(Map.of("clientInfo", new JsonObject(Map.of("name", "test")))));
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    void errorOfASharedToolIsAnError() throws Exception {
        JsonObject result = call("camel_eval_expression", Map.of("language", "simple"));

        assertThat(result.getBoolean("isError")).isTrue();
        assertThat(text(result)).isEqualTo("Error: expression is required");
    }

    @Test
    void toolThatDoesNotExistIsUnknown() throws Exception {
        JsonObject result = call("no_such_tool", Map.of());

        assertThat(result.getBoolean("isError")).isTrue();
        assertThat(text(result)).isEqualTo("Unknown tool: no_such_tool");
    }

    @Test
    void answerIsNoError() throws Exception {
        JsonObject result = call("camel_eval_expression", Map.of("expression", "${body} == 'x'", "body", "x"));

        assertThat(result.get("isError")).isNull();
        assertThat(text(result)).contains("\"status\":\"ok\"");
    }

    private JsonObject call(String tool, Map<String, Object> arguments) throws Exception {
        JsonObject params = new JsonObject();
        params.put("name", tool);
        params.put("arguments", new JsonObject(arguments));
        return rpc("tools/call", params);
    }

    private static String text(JsonObject result) {
        JsonArray content = (JsonArray) result.get("content");
        return ((JsonObject) content.get(0)).getString("text");
    }

    private JsonObject rpc(String method, JsonObject params) throws Exception {
        JsonObject request = new JsonObject();
        request.put("jsonrpc", "2.0");
        request.put("id", ++id);
        request.put("method", method);
        request.put("params", params);
        HttpRequest post = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getPort() + "/mcp"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(request.toJson()))
                .build();
        String body = http.send(post, HttpResponse.BodyHandlers.ofString()).body();
        return (JsonObject) ((JsonObject) Jsoner.deserialize(body)).get("result");
    }
}
