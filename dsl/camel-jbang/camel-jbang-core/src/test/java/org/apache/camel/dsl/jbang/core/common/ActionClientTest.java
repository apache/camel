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
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
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
    @DisabledOnOs(OS.WINDOWS)
    void integrationThatStopsIsNotWaitedFor() throws Exception {
        // a process of its own, as the client only watches a process it can see
        Process integration = new ProcessBuilder("sleep", "30").start();
        try {
            long pid = integration.pid();
            executor.submit(() -> {
                // stops while it has the action (such as a send that waits for a reply), without answering it
                await().atMost(5, TimeUnit.SECONDS).until(() -> !requestFiles(CommandLineHelper.getCamelDir(), pid).isEmpty());
                integration.destroy();
                return null;
            });
            JsonObject request = new JsonObject();
            request.put("action", "send");
            StopWatch watch = new StopWatch();

            ActionResult result = ActionClient.execute(pid, request, 30000);

            assertThat(watch.taken()).isLessThan(10000);
            assertThat(result.status()).isEqualTo(Status.ERROR);
            assertThat(result.exitCode()).isEqualTo(CliExitCode.NOT_FOUND);
            assertThat(result.error())
                    .isEqualTo("The integration with pid " + pid + " stopped before it answered the action send");
            assertNoFilesLeft();
        } finally {
            integration.destroyForcibly();
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void textOfAnIntegrationThatStopsIsAnError() throws Exception {
        Process integration = new ProcessBuilder("sleep", "30").start();
        try {
            long pid = integration.pid();
            executor.submit(() -> {
                await().atMost(5, TimeUnit.SECONDS).until(() -> !requestFiles(CommandLineHelper.getCamelDir(), pid).isEmpty());
                integration.destroy();
                return null;
            });
            JsonObject request = new JsonObject();
            request.put("action", "route-dump");

            // the tools of camel ask and the TUI say so, instead of that the integration did not answer in time
            assertThatThrownBy(() -> ActionClient.executeText(pid, request, 30000))
                    .isInstanceOf(ActionClient.StoppedException.class)
                    .hasMessage("The integration with pid " + pid + " stopped before it answered the action route-dump");
            assertNoFilesLeft();
        } finally {
            integration.destroyForcibly();
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void answerOfAnIntegrationThatStopsRightAfterIsRead() throws Exception {
        Process integration = new ProcessBuilder("sleep", "30").start();
        try {
            long pid = integration.pid();
            executor.submit(() -> {
                Path dir = CommandLineHelper.getCamelDir();
                Path request = await().atMost(5, TimeUnit.SECONDS)
                        .until(() -> requestFiles(dir, pid), files -> !files.isEmpty()).get(0);
                String id = request.getFileName().toString().substring((pid + "-action-").length()).replace(".json", "");
                Files.writeString(dir.resolve(pid + "-output-" + id + ".json"), "{\"status\":\"success\",\"value\":42}");
                integration.destroy();
                integration.waitFor(5, TimeUnit.SECONDS);
                Files.deleteIfExists(request);
                return null;
            });
            JsonObject request = new JsonObject();
            request.put("action", "jvm");

            ActionResult result = ActionClient.execute(pid, request, 30000);

            assertThat(result.status()).isEqualTo(Status.OK);
            assertThat(result.reply().getInteger("value")).isEqualTo(42);
        } finally {
            integration.destroyForcibly();
        }
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
    void textOfAnAnswerIsReadWhenItIsAllThere() throws Exception {
        Files.writeString(CommandLineHelper.getCamelDir().resolve(PID + "-status.json"),
                "{\"context\":{\"version\":\"4.23.0\"}}");
        String answer = "{\"status\":\"success\",\"routes\":[{\"id\":\"route1\"}]}";
        // an integration of an earlier Camel version writes the answer in place: half of it is there first
        executor.submit(() -> {
            Path dir = CommandLineHelper.getCamelDir();
            Path request = await().atMost(5, TimeUnit.SECONDS).until(() -> requestFiles(dir, PID), files -> !files.isEmpty())
                    .get(0);
            String id = request.getFileName().toString().substring((PID + "-action-").length()).replace(".json", "");
            Path output = dir.resolve(PID + "-output-" + id + ".json");
            Files.writeString(output, answer.substring(0, 20));
            // several polls of the client find half the answer
            await().pollDelay(500, TimeUnit.MILLISECONDS).atMost(1, TimeUnit.SECONDS).until(() -> true);
            Files.writeString(output, answer);
            Files.delete(request);
            return null;
        });

        JsonObject request = new JsonObject();
        request.put("action", "route-dump");
        assertThat(ActionClient.executeText(PID, request, 5000)).isEqualTo(answer);
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
    void errorOfAnExceptionWithoutMessage() {
        // camel cmd send --reply: the exchange failed with a NullPointerException, and the answer has the reply message
        JsonObject answer = json("{\"status\":\"error\",\"exception\":{\"type\":\"java.lang.NullPointerException\"},"
                                 + "\"message\":{\"body\":{\"type\":\"java.lang.String\",\"value\":\"hello\"}}}");

        assertThat(ActionClient.result(answer).error()).isEqualTo("java.lang.NullPointerException");
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
            Path request = await().atMost(5, TimeUnit.SECONDS).until(() -> requestFiles(dir, PID), files -> !files.isEmpty())
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

    private static List<Path> requestFiles(Path dir, long pid) throws Exception {
        try (var files = Files.list(dir)) {
            return files.filter(f -> f.getFileName().toString().startsWith(pid + "-action-")).toList();
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
