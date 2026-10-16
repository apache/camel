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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.apache.camel.dsl.jbang.core.common.ActionClient.ActionResult;
import org.apache.camel.dsl.jbang.core.common.ActionClient.Status;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder.CamelProcess;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Talks to a fake integration that answers like the camel-cli-connector of a given Camel version.
 */
class ActionClientTest {

    private static final long PID = 123;

    @TempDir
    Path home;

    private Path previousHome;
    private ExecutorService executor;

    @BeforeEach
    void setup() throws Exception {
        previousHome = CommandLineHelper.getHomeDir();
        CommandLineHelper.useHomeDir(home.toString());
        Files.createDirectories(CommandLineHelper.getCamelDir());
        executor = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void restore() {
        executor.shutdownNow();
        CommandLineHelper.useHomeDir(previousHome.toString());
    }

    @Test
    void resultOfAnAction() throws Exception {
        Future<JsonObject> request = answerRequest(r -> json("{\"status\":\"success\",\"routes\":[]}"));

        ActionResult result = ActionClient.execute(integration("4.24.0"), "route-dump", o -> o.put("filter", "*"), 5000);

        assertThat(result.status()).isEqualTo(Status.OK);
        assertThat(result.reply()).containsKey("routes");
        assertThat(result.exitCode()).isEqualTo(CliExitCode.OK);
        assertThat(request.get(5, TimeUnit.SECONDS)).containsEntry("action", "route-dump").containsEntry("filter", "*");
        assertNoFilesLeft();
    }

    @Test
    void failedActionIsAnError() throws Exception {
        answerRequest(r -> json("{\"status\":\"failed\",\"error\":\"Boom\"}"));

        ActionResult result = ActionClient.execute(integration("4.24.0"), "eval", null, 5000);

        assertThat(result.status()).isEqualTo(Status.ERROR);
        assertThat(result.error()).isEqualTo("Boom");
        assertThat(result.exitCode()).isEqualTo(CliExitCode.FAILURE);
        assertThatThrownBy(result::orThrow).isInstanceOf(CliException.class).hasMessage("Boom");
    }

    @Test
    void notFoundIsExitCodeThree() throws Exception {
        answerRequest(r -> json("{\"status\":\"failed\",\"error\":\"No route matching: x\",\"reason\":\"not-found\"}"));

        ActionResult result = ActionClient.send(integration("4.24.0"), "route", o -> o.put("id", "x"), 5000);

        assertThat(result.isNotFound()).isTrue();
        assertThat(result.exitCode()).isEqualTo(CliExitCode.NOT_FOUND);
        assertThatThrownBy(result::orThrow).isInstanceOf(CliException.class)
                .extracting(e -> ((CliException) e).getExitCode()).isEqualTo(CliExitCode.NOT_FOUND);
    }

    @Test
    void actionWithoutResultIsConfirmed() throws Exception {
        answerRequest(r -> json("{\"status\":\"success\"}"));

        ActionResult result = ActionClient.send(integration("4.24.0"), "gc", null, 5000);

        assertThat(result.status()).isEqualTo(Status.OK);
        assertNoFilesLeft();
    }

    @Test
    void noAnswerIsATimeout() throws Exception {
        ActionResult result = ActionClient.send(integration("4.24.0"), "gc", null, 300);

        assertThat(result.status()).isEqualTo(Status.TIMEOUT);
        assertThat(result.exitCode()).isEqualTo(CliExitCode.TIMEOUT);
        assertThat(result.error()).contains("No answer from the integration with pid 123 to the action gc");
        // the action is withdrawn, so the integration does not run it later
        assertNoFilesLeft();
    }

    @Test
    void olderIntegrationTakesAnActionWithoutConfirmingIt() throws Exception {
        // before 4.24 the connector deletes the request without answering an action without a result
        answerRequest(r -> null);

        ActionResult result = ActionClient.send(integration("4.22.1"), "gc", null, 5000);

        assertThat(result.status()).isEqualTo(Status.UNCONFIRMED);
        assertThat(result.isOk()).isTrue();
        assertThat(result.exitCode()).isEqualTo(CliExitCode.OK);
    }

    @Test
    void integrationBefore421UsesTheSharedActionFile() throws Exception {
        Path output = CommandLineHelper.getCamelDir().resolve(PID + "-output.json");
        // a stale answer left by an earlier command must not be taken as the answer
        Files.writeString(output, "{\"status\":\"success\",\"stale\":true}");
        Path action = CommandLineHelper.getCamelDir().resolve(PID + "-action.json");
        executor.submit(() -> {
            await().atMost(5, TimeUnit.SECONDS).until(() -> Files.exists(action));
            Files.writeString(output, "{\"status\":\"success\",\"fresh\":true}");
            Files.delete(action);
            return null;
        });

        ActionResult result = ActionClient.execute(integration("4.18.3"), "route-dump", null, 5000);

        assertThat(result.reply()).containsEntry("fresh", true).doesNotContainKey("stale");
        assertThat(output).doesNotExist();
    }

    @Test
    void executeByPidReadsTheVersionFromTheStatusFile() throws Exception {
        Files.writeString(CommandLineHelper.getCamelDir().resolve(PID + "-status.json"),
                "{\"context\":{\"version\":\"4.24.0\"}}");
        answerRequest(r -> json("{\"status\":\"success\",\"value\":42}"));

        JsonObject request = new JsonObject();
        request.put("action", "jvm");
        ActionResult result = ActionClient.execute(PID, request, 5000);

        assertThat(result.status()).isEqualTo(Status.OK);
        assertThat(result.reply().getInteger("value")).isEqualTo(42);
    }

    @Test
    void errorMessageComesFromTheAnswer() {
        assertThat(ActionClient.result(json("{\"status\":\"error\",\"exception\":{\"message\":\"Forced\"}}")).error())
                .isEqualTo("Forced");
        assertThat(ActionClient.result(json("{\"status\":\"error\",\"message\":\"No such datasource\"}")).error())
                .isEqualTo("No such datasource");
        assertThat(ActionClient.result(json("{\"status\":\"failed\"}")).error()).isEqualTo("Action failed");
        assertThat(ActionClient.result(json("{\"value\":1}")).status()).isEqualTo(Status.OK);
    }

    @Test
    void versions() {
        assertThat(ActionClient.isAtLeast("4.24.0-SNAPSHOT", 4, 24)).isTrue();
        assertThat(ActionClient.isAtLeast("4.24.1", 4, 21)).isTrue();
        assertThat(ActionClient.isAtLeast("4.22.0", 4, 24)).isFalse();
        assertThat(ActionClient.isAtLeast("3.22.0", 4, 21)).isFalse();
        assertThat(ActionClient.isAtLeast("5.0.0", 4, 24)).isTrue();
    }

    private static CamelProcess integration(String version) {
        JsonObject context = new JsonObject();
        context.put("name", "app");
        context.put("version", version);
        JsonObject status = new JsonObject();
        status.put("context", context);
        return new CamelProcess(PID, "app", status);
    }

    /**
     * Answers the next request with a file of its own like the connector does: writes the answer (unless null), then
     * deletes the request.
     */
    private Future<JsonObject> answerRequest(Function<JsonObject, JsonObject> answer) {
        return executor.submit(() -> {
            Path dir = CommandLineHelper.getCamelDir();
            Path request = await().atMost(5, TimeUnit.SECONDS).until(() -> requestFiles(dir), files -> !files.isEmpty())
                    .get(0);
            JsonObject action = json(Files.readString(request));
            JsonObject reply = answer.apply(action);
            String id = request.getFileName().toString().substring((PID + "-action-").length()).replace(".json", "");
            if (reply != null) {
                Files.writeString(dir.resolve(PID + "-output-" + id + ".json"), reply.toJson());
            }
            Files.delete(request);
            return action;
        });
    }

    private static List<Path> requestFiles(Path dir) throws Exception {
        try (var files = Files.list(dir)) {
            return files.filter(f -> f.getFileName().toString().startsWith(PID + "-action-")).toList();
        }
    }

    private static void assertNoFilesLeft() throws Exception {
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            try (var files = Files.list(CommandLineHelper.getCamelDir())) {
                assertThat(files.map(f -> f.getFileName().toString()).filter(n -> !n.endsWith("-status.json")))
                        .isEmpty();
            }
        });
    }

    private static JsonObject json(String text) {
        return (JsonObject) Jsoner.deserialize(text, (JsonObject) null);
    }
}
