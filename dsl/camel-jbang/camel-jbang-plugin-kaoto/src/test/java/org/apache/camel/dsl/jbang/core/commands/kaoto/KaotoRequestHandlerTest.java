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
import java.util.concurrent.TimeUnit;

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
import static org.awaitility.Awaitility.await;

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

        KaotoWebServer.CommandLauncher testLauncher = (logFile, args) -> {
            String message = "test log output for: " + String.join(" ", args);
            return new ProcessBuilder("echo", message).start();
        };
        server = new KaotoWebServer(port, tempDir, new CountDownLatch(1), testLauncher);
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
        for (Object f : files) {
            JsonObject file = (JsonObject) f;
            assertThat(file.containsKey("running")).isTrue();
            assertThat(file.getBoolean("running")).isFalse();
        }
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
                .containsExactlyInAnyOrder("root-test.citrus.yaml", "sub-test.citrus.xml",
                        "it-test.citrus.it.yaml");
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

        HttpResponse<String> response = get("/api/tests/sub-test.citrus.yaml");

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
    void putTestInSubdirectoryUpdatesExistingFile() throws Exception {
        Path testDir = tempDir.resolve("test");
        Files.createDirectories(testDir);
        Files.writeString(testDir.resolve("sub-test.citrus.yaml"), "name: original");

        JsonObject body = new JsonObject();
        body.put("content", "name: updated");

        HttpResponse<String> response = put("/api/tests/sub-test.citrus.yaml", Jsoner.serialize(body));

        assertThat(response.statusCode()).isEqualTo(200);
        JsonObject json = (JsonObject) Jsoner.deserialize(response.body());
        assertThat(json.getBoolean("saved")).isTrue();

        String saved = Files.readString(testDir.resolve("sub-test.citrus.yaml"));
        assertThat(saved).isEqualTo("name: updated");
    }

    @Test
    void runRouteReturnsStarted() throws Exception {
        Files.writeString(tempDir.resolve("my-route.camel.yaml"), "from:\n  uri: timer:tick");

        HttpResponse<String> response = post("/api/routes/run/my-route.camel.yaml");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonObject json = (JsonObject) Jsoner.deserialize(response.body());
        assertThat(json.getString("command")).isEqualTo("run");
        assertThat(json.getString("file")).isEqualTo("my-route.camel.yaml");
        assertThat(json.getBoolean("started")).isTrue();
    }

    @Test
    void runRouteReturns404ForMissingFile() throws Exception {
        HttpResponse<String> response = post("/api/routes/run/nonexistent.camel.yaml");

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void runRouteRejectsPathTraversal() throws Exception {
        HttpResponse<String> response = post("/api/routes/run/..%2Fsecret.txt");

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void runTestReturnsStarted() throws Exception {
        Files.writeString(tempDir.resolve("my-test.citrus.yaml"), "name: my-test");

        HttpResponse<String> response = post("/api/tests/run/my-test.citrus.yaml");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonObject json = (JsonObject) Jsoner.deserialize(response.body());
        assertThat(json.getString("command")).isEqualTo("test run");
        assertThat(json.getString("file")).isEqualTo("my-test.citrus.yaml");
        assertThat(json.getBoolean("started")).isTrue();
    }

    @Test
    void runTestFromSubdirectory() throws Exception {
        Path testDir = tempDir.resolve("test");
        Files.createDirectories(testDir);
        Files.writeString(testDir.resolve("sub-test.citrus.yaml"), "name: sub-test");

        HttpResponse<String> response = post("/api/tests/run/sub-test.citrus.yaml");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonObject json = (JsonObject) Jsoner.deserialize(response.body());
        assertThat(json.getString("command")).isEqualTo("test run");
        assertThat(json.getString("file")).isEqualTo("sub-test.citrus.yaml");
        assertThat(json.getBoolean("started")).isTrue();
    }

    @Test
    void runTestReturns404ForMissingFile() throws Exception {
        HttpResponse<String> response = post("/api/tests/run/nonexistent.citrus.yaml");

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void getTestResultsReturnsFlowJson() throws Exception {
        Files.writeString(tempDir.resolve("my-test.citrus.yaml"), "name: my-test\nactions:\n  - print:\n      message: hello");
        Path resultsDir = tempDir.resolve(".citrus-jbang/citrus-reports");
        Files.createDirectories(resultsDir);
        String flowContent = "[{\"name\":\"my-test\",\"result\":{\"result\":\"SUCCESS\"}}]";
        Files.writeString(resultsDir.resolve("my-test-flow.json"), flowContent);

        HttpResponse<String> response = get("/api/tests/results/my-test.citrus.yaml");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(flowContent);
    }

    @Test
    void getTestResultsUsesTestNameFromFile() throws Exception {
        Files.writeString(tempDir.resolve("my-file.citrus.yaml"),
                "name: actual-test-name\nactions:\n  - print:\n      message: hello");
        Path resultsDir = tempDir.resolve(".citrus-jbang/citrus-reports");
        Files.createDirectories(resultsDir);
        String flowContent = "[{\"name\":\"actual-test-name\",\"result\":{\"result\":\"SUCCESS\"}}]";
        Files.writeString(resultsDir.resolve("actual-test-name-flow.json"), flowContent);

        HttpResponse<String> response = get("/api/tests/results/my-file.citrus.yaml");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(flowContent);
    }

    @Test
    void getTestResultsReturnsLatestTimestampedFlowJson() throws Exception {
        Files.writeString(tempDir.resolve("my-test.citrus.yaml"), "name: my-test\nactions:\n  - print:\n      message: hello");
        Path resultsDir = tempDir.resolve(".citrus-jbang/citrus-reports");
        Files.createDirectories(resultsDir);
        Files.writeString(resultsDir.resolve("my-test-flow-1000.json"), "[{\"old\":true}]");
        String latestContent = "[{\"name\":\"my-test\",\"result\":{\"result\":\"FAILURE\"}}]";
        Files.writeString(resultsDir.resolve("my-test-flow-2000.json"), latestContent);

        HttpResponse<String> response = get("/api/tests/results/my-test.citrus.yaml");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(latestContent);
    }

    @Test
    void getTestResultsForIntegrationTests() throws Exception {
        Files.writeString(tempDir.resolve("my-test.citrus.it.yaml"),
                "name: my-test\nactions:\n  - print:\n      message: hello");
        Path resultsDir = tempDir.resolve(".citrus-jbang/citrus-reports");
        Files.createDirectories(resultsDir);
        String flowContent = "[{\"name\":\"my-test\",\"result\":{\"result\":\"SUCCESS\"}}]";
        Files.writeString(resultsDir.resolve("my-test-flow.json"), flowContent);

        HttpResponse<String> response = get("/api/tests/results/my-test.citrus.it.yaml");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(flowContent);
    }

    @Test
    void getTestResultsForXmlTests() throws Exception {
        Files.writeString(tempDir.resolve("my-test.citrus.xml"),
                "<test name=\"my-xml-test\" xmlns=\"http://citrusframework.org/schema/xml/testcase\"><actions/></test>");
        Path resultsDir = tempDir.resolve(".citrus-jbang/citrus-reports");
        Files.createDirectories(resultsDir);
        String flowContent = "[{\"name\":\"my-xml-test\",\"result\":{\"result\":\"SUCCESS\"}}]";
        Files.writeString(resultsDir.resolve("my-xml-test-flow.json"), flowContent);

        HttpResponse<String> response = get("/api/tests/results/my-test.citrus.xml");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(flowContent);
    }

    @Test
    void getTestResultsFromTestSubdirectory() throws Exception {
        Path testDir = tempDir.resolve("test");
        Files.createDirectories(testDir);
        Files.writeString(testDir.resolve("sub-test.citrus.yaml"),
                "name: sub-test\nactions:\n  - print:\n      message: hello");
        Path resultsDir = tempDir.resolve(".citrus-jbang/citrus-reports");
        Files.createDirectories(resultsDir);
        String flowContent = "[{\"name\":\"sub-test\",\"result\":{\"result\":\"SUCCESS\"}}]";
        Files.writeString(resultsDir.resolve("sub-test-flow.json"), flowContent);

        HttpResponse<String> response = get("/api/tests/results/sub-test.citrus.yaml");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(flowContent);
    }

    @Test
    void getTestResultsReturns204WhenNoResults() throws Exception {
        HttpResponse<String> response = get("/api/tests/results/nonexistent.citrus.yaml");

        assertThat(response.statusCode()).isEqualTo(204);
    }

    @Test
    void getTestResultsReturns204WhenResultsDirExistsButNoMatch() throws Exception {
        Path resultsDir = tempDir.resolve(".citrus-jbang/citrus-reports");
        Files.createDirectories(resultsDir);
        Files.writeString(resultsDir.resolve("other-test-flow.json"), "[{}]");

        HttpResponse<String> response = get("/api/tests/results/my-test.citrus.yaml");

        assertThat(response.statusCode()).isEqualTo(204);
    }

    @Test
    void extractTestNameFromYaml() throws Exception {
        Path testFile = tempDir.resolve("sample.citrus.yaml");
        Files.writeString(testFile, "name: my-test\nactions:\n  - print:\n      message: hello");
        assertThat(KaotoRequestHandler.extractTestName(testFile)).isEqualTo("my-test");
    }

    @Test
    void extractTestNameFromXml() throws Exception {
        Path testFile = tempDir.resolve("sample.citrus.xml");
        Files.writeString(testFile,
                "<test name=\"my-xml-test\" xmlns=\"http://citrusframework.org/schema/xml/testcase\"><actions/></test>");
        assertThat(KaotoRequestHandler.extractTestName(testFile)).isEqualTo("my-xml-test");
    }

    @Test
    void extractTestNameReturnsNullWhenNoName() throws Exception {
        Path testFile = tempDir.resolve("sample.citrus.yaml");
        Files.writeString(testFile, "actions:\n  - print:\n      message: hello");
        assertThat(KaotoRequestHandler.extractTestName(testFile)).isNull();
    }

    @Test
    void resolveBaseNameFromRegularTest() {
        assertThat(KaotoRequestHandler.resolveBaseNameFromFilename("my-test.citrus.yaml"))
                .isEqualTo("my-test");
    }

    @Test
    void resolveBaseNameFromIntegrationTest() {
        assertThat(KaotoRequestHandler.resolveBaseNameFromFilename("my-test.citrus.it.yaml"))
                .isEqualTo("my-test");
    }

    @Test
    void resolveBaseNameFromXmlTest() {
        assertThat(KaotoRequestHandler.resolveBaseNameFromFilename("my-test.citrus.xml"))
                .isEqualTo("my-test");
    }

    @Test
    void resolveBaseNameFromXmlIntegrationTest() {
        assertThat(KaotoRequestHandler.resolveBaseNameFromFilename("my-test.citrus.it.xml"))
                .isEqualTo("my-test");
    }

    @Test
    void getRouteLogReturnsContent() throws Exception {
        Path logDir = tempDir.resolve(".camel-jbang");
        Files.createDirectories(logDir);
        String logContent = "2026-10-31 Route started\n2026-10-31 Processing message";
        Files.writeString(logDir.resolve("my-route.camel.log"), logContent);

        HttpResponse<String> response = get("/api/routes/log/my-route.camel.yaml");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(ct -> assertThat(ct).startsWith("text/plain"));
        assertThat(response.body()).isEqualTo(logContent);
    }

    @Test
    void getRouteLogReturns204WhenNoLog() throws Exception {
        HttpResponse<String> response = get("/api/routes/log/nonexistent.camel.yaml");

        assertThat(response.statusCode()).isEqualTo(204);
    }

    @Test
    void getTestLogReturnsContent() throws Exception {
        Path logDir = tempDir.resolve(".citrus-jbang");
        Files.createDirectories(logDir);
        String logContent = "2026-10-31 Test started\n2026-10-31 SUCCESS";
        Files.writeString(logDir.resolve("my-test.citrus.log"), logContent);

        HttpResponse<String> response = get("/api/tests/log/my-test.citrus.yaml");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(logContent);
    }

    @Test
    void getTestLogReturns204WhenNoLog() throws Exception {
        HttpResponse<String> response = get("/api/tests/log/nonexistent.citrus.yaml");

        assertThat(response.statusCode()).isEqualTo(204);
    }

    @Test
    void resolveLogFileForRoute() {
        Path logFile = KaotoRequestHandler.resolveLogFile(tempDir, ".camel-jbang", "my-route.camel.yaml");
        assertThat(logFile).isEqualTo(tempDir.resolve(".camel-jbang/my-route.camel.log"));
    }

    @Test
    void resolveLogFileForTest() {
        Path logFile = KaotoRequestHandler.resolveLogFile(tempDir, ".citrus-jbang", "my-test.citrus.yaml");
        assertThat(logFile).isEqualTo(tempDir.resolve(".citrus-jbang/my-test.citrus.log"));
    }

    @Test
    void runTestCreatesLogFile() throws Exception {
        Files.writeString(tempDir.resolve("log-test.citrus.it.yaml"),
                "name: log-test\nactions:\n  - print:\n      message: hello");

        post("/api/tests/run/log-test.citrus.it.yaml");

        Path logFile = tempDir.resolve(".citrus-jbang/log-test.citrus.it.log");
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(logFile).exists();
            assertThat(Files.readString(logFile)).isNotEmpty();
        });

        HttpResponse<String> response = get("/api/tests/log/log-test.citrus.it.yaml");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isNotEmpty();
    }

    @Test
    void runRouteCreatesLogFile() throws Exception {
        String content = "from:\n  uri: timer:tick\n  steps:\n    - log: hello";
        Files.writeString(tempDir.resolve("log-test.camel.yaml"), content);

        post("/api/routes/run/log-test.camel.yaml");

        Path logFile = tempDir.resolve(".camel-jbang/log-test.camel.log");
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(logFile).exists();
            assertThat(Files.readString(logFile)).isNotEmpty();
        });
    }

    @Test
    void stopRouteReturnsStoppedFalseWhenNotRunning() throws Exception {
        HttpResponse<String> response = post("/api/routes/stop/nonexistent.camel.yaml");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonObject json = (JsonObject) Jsoner.deserialize(response.body());
        assertThat(json.getString("file")).isEqualTo("nonexistent.camel.yaml");
        assertThat(json.getBoolean("stopped")).isFalse();
    }

    @Test
    void stopTestReturnsStoppedFalseWhenNotRunning() throws Exception {
        HttpResponse<String> response = post("/api/tests/stop/nonexistent.citrus.yaml");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonObject json = (JsonObject) Jsoner.deserialize(response.body());
        assertThat(json.getString("file")).isEqualTo("nonexistent.citrus.yaml");
        assertThat(json.getBoolean("stopped")).isFalse();
    }

    @Test
    void stopRouteRejectsPathTraversal() throws Exception {
        HttpResponse<String> response = post("/api/routes/stop/..%2Fsecret.txt");

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void stopRouteKillsRunningProcess() throws Exception {
        try (Port reserved = AvailablePortFinder.find()) {
            int testPort = reserved.getPort();
            reserved.close();

            KaotoWebServer.CommandLauncher blockingLauncher = (logFile, args) -> new ProcessBuilder("cat").start();

            KaotoWebServer testServer = new KaotoWebServer(testPort, tempDir, new CountDownLatch(1), blockingLauncher);
            testServer.start();
            try {
                Files.writeString(tempDir.resolve("running.camel.yaml"), "from:\n  uri: timer:tick");

                HttpResponse<String> runResponse = sendPost(testPort, "/api/routes/run/running.camel.yaml");
                assertThat(runResponse.statusCode()).isEqualTo(200);

                HttpResponse<String> listResponse = sendGet(testPort, "/api/routes");
                JsonArray files = (JsonArray) Jsoner.deserialize(listResponse.body());
                JsonObject file = (JsonObject) files.stream()
                        .map(f -> (JsonObject) f)
                        .filter(f -> "running.camel.yaml".equals(f.getString("name")))
                        .findFirst().orElseThrow();
                assertThat(file.getBoolean("running")).isTrue();

                HttpResponse<String> stopResponse = sendPost(testPort, "/api/routes/stop/running.camel.yaml");
                assertThat(stopResponse.statusCode()).isEqualTo(200);
                JsonObject stopJson = (JsonObject) Jsoner.deserialize(stopResponse.body());
                assertThat(stopJson.getBoolean("stopped")).isTrue();

                await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                    HttpResponse<String> listAfter = sendGet(testPort, "/api/routes");
                    JsonArray filesAfter = (JsonArray) Jsoner.deserialize(listAfter.body());
                    JsonObject fileAfter = (JsonObject) filesAfter.stream()
                            .map(f -> (JsonObject) f)
                            .filter(f -> "running.camel.yaml".equals(f.getString("name")))
                            .findFirst().orElseThrow();
                    assertThat(fileAfter.getBoolean("running")).isFalse();
                });
            } finally {
                testServer.stop();
            }
        }
    }

    @Test
    void stopTestKillsRunningProcess() throws Exception {
        try (Port reserved = AvailablePortFinder.find()) {
            int testPort = reserved.getPort();
            reserved.close();

            KaotoWebServer.CommandLauncher blockingLauncher = (logFile, args) -> new ProcessBuilder("cat").start();

            KaotoWebServer testServer = new KaotoWebServer(testPort, tempDir, new CountDownLatch(1), blockingLauncher);
            testServer.start();
            try {
                Files.writeString(tempDir.resolve("running.citrus.yaml"), "name: running-test");

                HttpResponse<String> runResponse = sendPost(testPort, "/api/tests/run/running.citrus.yaml");
                assertThat(runResponse.statusCode()).isEqualTo(200);

                HttpResponse<String> listResponse = sendGet(testPort, "/api/tests");
                JsonArray files = (JsonArray) Jsoner.deserialize(listResponse.body());
                JsonObject file = (JsonObject) files.stream()
                        .map(f -> (JsonObject) f)
                        .filter(f -> "running.citrus.yaml".equals(f.getString("name")))
                        .findFirst().orElseThrow();
                assertThat(file.getBoolean("running")).isTrue();

                HttpResponse<String> stopResponse = sendPost(testPort, "/api/tests/stop/running.citrus.yaml");
                assertThat(stopResponse.statusCode()).isEqualTo(200);
                JsonObject stopJson = (JsonObject) Jsoner.deserialize(stopResponse.body());
                assertThat(stopJson.getBoolean("stopped")).isTrue();

                await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                    HttpResponse<String> listAfter = sendGet(testPort, "/api/tests");
                    JsonArray filesAfter = (JsonArray) Jsoner.deserialize(listAfter.body());
                    JsonObject fileAfter = (JsonObject) filesAfter.stream()
                            .map(f -> (JsonObject) f)
                            .filter(f -> "running.citrus.yaml".equals(f.getString("name")))
                            .findFirst().orElseThrow();
                    assertThat(fileAfter.getBoolean("running")).isFalse();
                });
            } finally {
                testServer.stop();
            }
        }
    }

    private static HttpResponse<String> get(String path) throws Exception {
        return sendGet(port, path);
    }

    private static HttpResponse<String> sendGet(int targetPort, String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + targetPort + path))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(String path) throws Exception {
        return sendPost(port, path);
    }

    private static HttpResponse<String> sendPost(int targetPort, String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + targetPort + path))
                        .POST(HttpRequest.BodyPublishers.noBody())
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
