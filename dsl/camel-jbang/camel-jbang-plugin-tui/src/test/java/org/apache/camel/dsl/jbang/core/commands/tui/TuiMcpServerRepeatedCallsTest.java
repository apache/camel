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

import org.apache.camel.dsl.jbang.core.commands.ai.RepeatedToolCalls;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A client of {@code camel tui --mcp} that asks a catalog tool the same question over and over gets a short note from
 * the third time (CAMEL-25075).
 */
class TuiMcpServerRepeatedCallsTest {

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
    void theListingMarksTheCatalogLookupsDeterministic() throws Exception {
        JsonArray tools = (JsonArray) rpc("tools/list", new JsonObject()).get("tools");
        JsonObject catalogDoc = null;
        JsonObject getFiles = null;
        for (Object o : tools) {
            JsonObject tool = (JsonObject) o;
            if ("camel_catalog_doc".equals(tool.getString("name"))) {
                catalogDoc = tool;
            } else if ("camel_get_files".equals(tool.getString("name"))) {
                getFiles = tool;
            }
        }
        assertThat(catalogDoc).isNotNull();
        assertThat(((JsonObject) catalogDoc.get("_meta")).getBoolean(RepeatedToolCalls.DETERMINISTIC_META_KEY)).isTrue();
        assertThat(getFiles).isNotNull();
        assertThat(getFiles.get("_meta")).isNull();
    }

    @Test
    void aThirdIdenticalCallGetsAShortNoteUntilTheClientConnectsAgain() throws Exception {
        String first = callCatalogDoc("timer");
        String second = callCatalogDoc("timer");
        String third = callCatalogDoc("timer");

        assertThat(second).isEqualTo(first);
        JsonObject note = (JsonObject) Jsoner.deserialize(third);
        assertThat(note.getBoolean("repeated")).isTrue();
        assertThat(note.getInteger("timesAsked")).isEqualTo(3);
        assertThat(third.length()).isLessThan(first.length() / 4);

        // a new initialize is a new session: the agent has not seen the answer there
        rpc("initialize", new JsonObject(Map.of("clientInfo", new JsonObject(Map.of("name", "test")))));
        assertThat(callCatalogDoc("timer")).isEqualTo(first);
    }

    private String callCatalogDoc(String name) throws Exception {
        JsonObject params = new JsonObject();
        params.put("name", "camel_catalog_doc");
        params.put("arguments", new JsonObject(Map.of("name", name, "kind", "component")));
        JsonArray content = (JsonArray) rpc("tools/call", params).get("content");
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
