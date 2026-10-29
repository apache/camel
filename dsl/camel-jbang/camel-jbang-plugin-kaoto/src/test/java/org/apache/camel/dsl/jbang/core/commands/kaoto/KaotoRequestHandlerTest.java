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
package org.apache.camel.dsl.jbang.core.commands.kaoto;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.AvailablePortFinder.Port;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;

@Isolated
class KaotoRequestHandlerTest {

    @TempDir
    static Path tempDir;

    private static KaotoWebServer server;
    private static int port;
    private static HttpClient client;

    @BeforeEach
    void cleanTempDir() throws IOException {
        deleteContents(tempDir);
    }

    @BeforeAll
    static void setUp() throws Exception {
        Port reserved = AvailablePortFinder.find();
        port = reserved.getPort();
        reserved.close();

        server = new KaotoWebServer(port, tempDir, new CountDownLatch(1));
        server.start();
        client = HttpClient.newHttpClient();
    }

    @AfterAll
    static void tearDown() {
        if (server != null) {
            server.stop();
        }
        if (client != null) {
            client.close();
        }
    }

    @Test
    void listRoutesReturnsEmptyArrayWhenNoFiles() throws Exception {
        HttpResponse<String> response = get("/api/routes");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonArray files = (JsonArray) Jsoner.deserialize(response.body());
        assertThat(files).isEmpty();
    }

    @Test
    void listRoutesReturnsCamelFiles() throws Exception {
        Files.writeString(tempDir.resolve("my-route.camel.yaml"), "from:\n  uri: timer:tick");
        Files.writeString(tempDir.resolve("another.camel.xml"), "<routes/>");
        Files.writeString(tempDir.resolve("plain.yaml"), "not a camel route");
        Files.writeString(tempDir.resolve("notes.txt"), "not a route");

        HttpResponse<String> response = get("/api/routes");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonArray files = (JsonArray) Jsoner.deserialize(response.body());
        assertThat(files).hasSize(2);
        assertThat(files.stream().map(f -> ((JsonObject) f).getString("name")))
                .containsExactlyInAnyOrder("my-route.camel.yaml", "another.camel.xml");
    }

    @Test
    void getRouteReturnsFileContent() throws Exception {
        String content = "from:\n  uri: timer:tick\n  steps:\n    - log: hello";
        Files.writeString(tempDir.resolve("test.camel.yaml"), content);

        HttpResponse<String> response = get("/api/routes/test.camel.yaml");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonObject json = (JsonObject) Jsoner.deserialize(response.body());
        assertThat(json.getString("name")).isEqualTo("test.camel.yaml");
        assertThat(json.getString("content")).isEqualTo(content);
    }

    @Test
    void getRouteReturns404ForMissingFile() throws Exception {
        HttpResponse<String> response = get("/api/routes/nonexistent.camel.yaml");

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void getRouteRejectsPathTraversal() throws Exception {
        HttpResponse<String> response = get("/api/routes/..%2Fsecret.txt");

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void putRouteSavesFile() throws Exception {
        JsonObject body = new JsonObject();
        body.put("content", "from:\n  uri: timer:tick");

        HttpResponse<String> response = put("/api/routes/new-route.camel.yaml", Jsoner.serialize(body));

        assertThat(response.statusCode()).isEqualTo(200);
        JsonObject json = (JsonObject) Jsoner.deserialize(response.body());
        assertThat(json.getBoolean("saved")).isTrue();

        String saved = Files.readString(tempDir.resolve("new-route.camel.yaml"));
        assertThat(saved).isEqualTo("from:\n  uri: timer:tick");
    }

    @Test
    void putRouteRejectsPathTraversal() throws Exception {
        JsonObject body = new JsonObject();
        body.put("content", "malicious");

        HttpResponse<String> response = put("/api/routes/..%2Fevil.yaml", Jsoner.serialize(body));

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void shutdownEndpoint() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try (Port reserved = AvailablePortFinder.find()) {
            KaotoWebServer shutdownServer = new KaotoWebServer(reserved.getPort(), tempDir, latch);
            shutdownServer.start();
            try {
                HttpResponse<String> response = client.send(
                        HttpRequest.newBuilder()
                                .uri(URI.create("http://127.0.0.1:" + reserved.getPort() + "/api/shutdown"))
                                .POST(HttpRequest.BodyPublishers.noBody())
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(latch.getCount()).isZero();
            } finally {
                shutdownServer.stop();
            }
        }
    }

    @Test
    void staticFilePathTraversalRejected() throws Exception {
        HttpResponse<String> response = get("/..%2F..%2Fetc/passwd");

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void listTestsReturnsEmptyArrayWhenNoFiles() throws Exception {
        HttpResponse<String> response = get("/api/tests");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonArray files = (JsonArray) Jsoner.deserialize(response.body());
        assertThat(files).isEmpty();
    }

    @Test
    void listTestsReturnsCitrusFiles() throws Exception {
        Files.writeString(tempDir.resolve("my-test.citrus.yaml"), "name: my-test");
        Files.writeString(tempDir.resolve("integration.citrus.it.xml"), "<test/>");

        HttpResponse<String> response = get("/api/tests");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonArray files = (JsonArray) Jsoner.deserialize(response.body());
        assertThat(files).hasSize(2);
        assertThat(files.stream().map(f -> ((JsonObject) f).getString("name")))
                .containsExactlyInAnyOrder("my-test.citrus.yaml", "integration.citrus.it.xml");
    }

    @Test
    void listTestsIncludesTestSubdirectory() throws Exception {
        Files.writeString(tempDir.resolve("root-test.citrus.yaml"), "name: root");
        Path testDir = tempDir.resolve("test");
        Files.createDirectories(testDir);
        Files.writeString(testDir.resolve("sub-test.citrus.xml"), "<test/>");
        Files.writeString(testDir.resolve("it-test.citrus.it.yaml"), "name: it");

        HttpResponse<String> response = get("/api/tests");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonArray files = (JsonArray) Jsoner.deserialize(response.body());
        assertThat(files).hasSize(3);
        assertThat(files.stream().map(f -> ((JsonObject) f).getString("name")))
                .containsExactlyInAnyOrder("root-test.citrus.yaml", "test/sub-test.citrus.xml",
                        "test/it-test.citrus.it.yaml");
    }

    @Test
    void getTestReturnsFileContent() throws Exception {
        String content = "name: my-test\nsteps:\n  - send: hello";
        Files.writeString(tempDir.resolve("my-test.citrus.yaml"), content);

        HttpResponse<String> response = get("/api/tests/my-test.citrus.yaml");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonObject json = (JsonObject) Jsoner.deserialize(response.body());
        assertThat(json.getString("name")).isEqualTo("my-test.citrus.yaml");
        assertThat(json.getString("content")).isEqualTo(content);
    }

    @Test
    void getTestFromSubdirectory() throws Exception {
        Path testDir = tempDir.resolve("test");
        Files.createDirectories(testDir);
        String content = "name: sub-test";
        Files.writeString(testDir.resolve("sub-test.citrus.yaml"), content);

        HttpResponse<String> response = get("/api/tests/test/sub-test.citrus.yaml");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonObject json = (JsonObject) Jsoner.deserialize(response.body());
        assertThat(json.getString("name")).isEqualTo("sub-test.citrus.yaml");
        assertThat(json.getString("content")).isEqualTo(content);
    }

    @Test
    void getTestReturns404ForMissingFile() throws Exception {
        HttpResponse<String> response = get("/api/tests/nonexistent.citrus.yaml");

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void putTestSavesFile() throws Exception {
        JsonObject body = new JsonObject();
        body.put("content", "name: new-test");

        HttpResponse<String> response = put("/api/tests/new-test.citrus.yaml", Jsoner.serialize(body));

        assertThat(response.statusCode()).isEqualTo(200);
        JsonObject json = (JsonObject) Jsoner.deserialize(response.body());
        assertThat(json.getBoolean("saved")).isTrue();

        String saved = Files.readString(tempDir.resolve("new-test.citrus.yaml"));
        assertThat(saved).isEqualTo("name: new-test");
    }

    @Test
    void putTestInSubdirectory() throws Exception {
        JsonObject body = new JsonObject();
        body.put("content", "name: sub-test");

        HttpResponse<String> response = put("/api/tests/test/sub-test.citrus.yaml", Jsoner.serialize(body));

        assertThat(response.statusCode()).isEqualTo(200);
        JsonObject json = (JsonObject) Jsoner.deserialize(response.body());
        assertThat(json.getBoolean("saved")).isTrue();

        String saved = Files.readString(tempDir.resolve("test/sub-test.citrus.yaml"));
        assertThat(saved).isEqualTo("name: sub-test");
    }

    private static HttpResponse<String> get(String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + path))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> put(String path, String body) throws Exception {
        return client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + path))
                        .PUT(HttpRequest.BodyPublishers.ofString(body))
                        .header("Content-Type", "application/json")
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static void deleteContents(Path dir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    deleteContents(entry);
                }
                Files.deleteIfExists(entry);
            }
        }
    }
}
