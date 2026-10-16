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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The requests of the TUI to an integration of Camel 4.21 or later go in files of their own (CAMEL-25448), so a route
 * command cannot be overwritten by the next request of a tab before the connector read it; an older integration gets
 * the shared action file.
 */
@Isolated("Uses a temporary Camel home for real file IPC")
class MonitorContextActionFilesTest {

    @TempDir
    Path home;

    private Path previousHome;
    private Path camelDir;
    private final IntegrationInfo info = new IntegrationInfo();
    private MonitorContext context;

    @BeforeEach
    void useTemporaryCamelHome() throws Exception {
        previousHome = CommandLineHelper.getHomeDir();
        CommandLineHelper.useHomeDir(home.toString());
        camelDir = Files.createDirectories(CommandLineHelper.getCamelDir());
        info.pid = "123";
        info.camelVersion = "4.24.0-SNAPSHOT";
        context = new MonitorContext(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));
    }

    @AfterEach
    void restoreCamelHome() throws Exception {
        context.backgroundExecutor.shutdownNow();
        context.backgroundExecutor.awaitTermination(3, TimeUnit.SECONDS);
        CommandLineHelper.useHomeDir(previousHome.toString());
    }

    @Test
    void theRequestFilesCameWithCamel421() {
        assertThat(MonitorContext.isCamelVersionAtLeast("4.24.0-SNAPSHOT", 4, 21)).isTrue();
        assertThat(MonitorContext.isCamelVersionAtLeast("4.21.0", 4, 21)).isTrue();
        assertThat(MonitorContext.isCamelVersionAtLeast("5.0.0", 4, 21)).isTrue();
        assertThat(MonitorContext.isCamelVersionAtLeast("4.18.3", 4, 21)).isFalse();
        assertThat(MonitorContext.isCamelVersionAtLeast(null, 4, 21)).isFalse();
        assertThat(MonitorContext.isCamelVersionAtLeast("unknown", 4, 21)).isFalse();
    }

    @Test
    void eachCommandGetsAFileOfItsOwn() throws Exception {
        context.fireAction("123", routeCommand("stop"));
        context.fireAction("123", routeCommand("start"));

        List<Path> requests = requestFiles();
        assertThat(requests).hasSize(2);
        assertThat(requests).map(MonitorContextActionFilesTest::command).containsExactlyInAnyOrder("stop", "start");
        assertThat(camelDir.resolve("123-action.json")).doesNotExist();
    }

    @Test
    void theAnswerToACommandIsRemovedOnceTheConnectorRanIt() throws Exception {
        context.fireAction("123", routeCommand("stop"));
        Path request = requestFiles().get(0);
        Path answer = request.resolveSibling(request.getFileName().toString().replace("-action-", "-output-"));

        // the connector writes an answer, then deletes the request it ran
        Files.writeString(answer, "{\"status\":\"success\"}");
        Files.delete(request);

        await().atMost(3, TimeUnit.SECONDS).until(() -> !Files.exists(answer));
    }

    @Test
    void aRequestThatWaitsForItsAnswerHasItsOwnFilesToo() throws Exception {
        JsonObject question = new JsonObject();
        question.put("action", "route-topology");
        CompletableFuture<JsonObject> answer = CompletableFuture.supplyAsync(
                () -> context.executeAction("123", question, 5000), context.backgroundExecutor);
        context.fireAction("123", routeCommand("stop"));

        await().atMost(3, TimeUnit.SECONDS).until(() -> requestFiles().size() == 2);
        Path asked = requestFiles().stream().filter(p -> "route-topology".equals(action(p))).findFirst().orElseThrow();
        Files.writeString(asked.resolveSibling(asked.getFileName().toString().replace("-action-", "-output-")),
                "{\"routes\":[]}");

        assertThat(answer.get(3, TimeUnit.SECONDS)).containsKey("routes");
        // the command is still there for the connector: nothing overwrote it
        assertThat(requestFiles()).singleElement().satisfies(p -> assertThat(command(p)).isEqualTo("stop"));
    }

    @Test
    void anIntegrationOlderThanCamel421GetsTheSharedFile() throws Exception {
        info.camelVersion = "4.18.3";

        context.fireAction("123", routeCommand("stop"));

        assertThat(requestFiles()).isEmpty();
        assertThat(command(camelDir.resolve("123-action.json"))).isEqualTo("stop");
    }

    private List<Path> requestFiles() throws Exception {
        try (Stream<Path> files = Files.list(camelDir)) {
            return files.filter(p -> p.getFileName().toString().startsWith("123-action-")).toList();
        }
    }

    private static JsonObject routeCommand(String command) {
        JsonObject root = new JsonObject();
        root.put("action", "route");
        root.put("id", "*");
        root.put("command", command);
        return root;
    }

    private static String command(Path request) {
        return read(request).getString("command");
    }

    private static String action(Path request) {
        return read(request).getString("action");
    }

    private static JsonObject read(Path request) {
        try {
            return Jsoner.deserialize(Files.readString(request), new JsonObject());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
