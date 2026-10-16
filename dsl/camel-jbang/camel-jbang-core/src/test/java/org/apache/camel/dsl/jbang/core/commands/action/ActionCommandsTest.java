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
package org.apache.camel.dsl.jbang.core.commands.action;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The camel cmd actions against a fake integration: this JVM, with a status file, and a thread that answers the actions
 * like the camel-cli-connector of Camel 4.24 does. Runs the CLI the way {@code camel} does, through
 * {@link CamelJBangMain#execute(String...)}.
 */
class ActionCommandsTest {

    private static final long PID = ProcessHandle.current().pid();

    @TempDir
    Path home;

    private Path previousHome;
    private StringPrinter printer;
    private TestMain main;
    private ExecutorService executor;

    @BeforeEach
    void setup() throws Exception {
        previousHome = CommandLineHelper.getHomeDir();
        CommandLineHelper.useHomeDir(home.toString());
        Files.createDirectories(CommandLineHelper.getCamelDir());
        printer = new StringPrinter();
        main = new TestMain();
        main.withPrinter(printer);
        main.setDiscoverPlugins(false);
        executor = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void restore() {
        executor.shutdownNow();
        CommandLineHelper.useHomeDir(previousHome.toString());
    }

    @Test
    void actionWithoutResultWaitsForTheConfirmation() throws Exception {
        integration();
        Future<JsonObject> request = answer(r -> json("{\"status\":\"success\"}"));

        assertThat(run("cmd", "stop-route", Long.toString(PID), "--id=route1")).isEqualTo(CliExitCode.OK);

        assertThat(request.get(5, TimeUnit.SECONDS)).containsEntry("action", "route").containsEntry("id", "route1")
                .containsEntry("command", "stop");
        assertThat(printer.getStderr()).isEmpty();
    }

    @Test
    void actionThatFailsIsAnError() throws Exception {
        integration();
        answer(r -> json("{\"status\":\"failed\",\"error\":\"Error stop route: route1 due to: Boom\"}"));

        assertThat(run("cmd", "stop-route", Long.toString(PID), "--id=route1")).isEqualTo(CliExitCode.FAILURE);

        assertThat(printer.getStderr()).isEqualTo("ERROR: Error stop route: route1 due to: Boom");
    }

    @Test
    void routeThatDoesNotExistIsNotFound() throws Exception {
        integration();
        answer(r -> json("{\"status\":\"failed\",\"error\":\"No route matching: route9\",\"reason\":\"not-found\"}"));

        assertThat(run("cmd", "start-route", Long.toString(PID), "--id=route9")).isEqualTo(CliExitCode.NOT_FOUND);

        assertThat(printer.getStderr()).isEqualTo("ERROR: No route matching: route9");
    }

    @Test
    void noAnswerIsATimeout() throws Exception {
        integration();

        assertThat(run("cmd", "gc", Long.toString(PID), "--timeout=300")).isEqualTo(CliExitCode.TIMEOUT);

        assertThat(printer.getStderr()).startsWith("ERROR: No answer from the integration with pid " + PID
                                                   + " to the action gc within 300 millis");
        // the action is withdrawn
        try (var files = Files.list(CommandLineHelper.getCamelDir())) {
            assertThat(files.map(f -> f.getFileName().toString())).containsExactly(PID + "-status.json");
        }
    }

    @Test
    void nameThatMatchesNothingIsNotFound() {
        assertThat(run("cmd", "reload", "no-such-integration")).isEqualTo(CliExitCode.NOT_FOUND);

        assertThat(printer.getStderr()).isEqualTo("ERROR: No running Camel integration matches: no-such-integration");
    }

    @Test
    void nothingRunningIsNothingToDo() {
        assertThat(run("cmd", "reset-stats")).isEqualTo(CliExitCode.OK);

        assertThat(printer.getStderr()).isEqualTo("No running Camel integrations");
    }

    @Test
    void unknownTraceActionIsAUsageError() throws Exception {
        integration();

        assertThat(run("trace", Long.toString(PID), "--action=bogus")).isEqualTo(CliExitCode.USAGE);

        assertThat(printer.getStderr()).startsWith("ERROR: Unknown action: bogus");
    }

    @Test
    void threadDumpAsJson() throws Exception {
        integration();
        answer(r -> json("""
                {"threadCount":2,"peakThreadCount":3,"threads":[
                  {"id":1,"name":"Camel (camel-1) thread #1","state":"RUNNABLE","waitedCount":0,"waitedTime":0,
                   "blockedCount":0,"blockedTime":0,"stackTrace":["a.b.C.run(C.java:1)"]},
                  {"id":2,"name":"main","state":"WAITING","waitedCount":1,"waitedTime":0,
                   "blockedCount":0,"blockedTime":0}]}
                """));

        assertThat(run("cmd", "thread-dump", Long.toString(PID), "--json")).isEqualTo(CliExitCode.OK);

        JsonObject dump = Jsoner.deserialize(printer.getStdout(), (JsonObject) null);
        assertThat(dump).as(printer.getStdout()).isNotNull();
        assertThat(dump.getInteger("threadCount")).isEqualTo(2);
        // the default filter keeps the threads of Camel
        JsonArray threads = dump.getCollection("threads");
        assertThat(threads).hasSize(1);
        JsonObject thread = threads.getMap(0);
        assertThat(thread).containsEntry("name", "Camel (camel-1) thread #1");
    }

    @Test
    void errorOfADataActionIsAJsonDocument() throws Exception {
        integration();
        answer(r -> json("{\"status\":\"error\",\"exception\":{\"message\":\"Forced\"}}"));

        assertThat(run("get", "bean", Long.toString(PID), "--json")).isEqualTo(CliExitCode.FAILURE);

        JsonObject error = Jsoner.deserialize(printer.getStdout(), (JsonObject) null);
        assertThat(error).containsEntry("status", "error").containsEntry("message", "Forced");
    }

    @Test
    void routeDumpFilterThatMatchesNoRouteIsNotFound() throws Exception {
        integration();
        answer(r -> json("{\"routes\":[{\"routeId\":\"route1\",\"source\":\"file:hello.yaml:1\",\"code\":[]}]}"));

        assertThat(run("cmd", "route-dump", Long.toString(PID), "--filter=other")).isEqualTo(CliExitCode.NOT_FOUND);

        assertThat(printer.getStderr()).isEqualTo("ERROR: No route matching: other");
    }

    @Test
    void routeDumpFilterTakesSeveralNames() throws Exception {
        integration();
        answer(r -> json("""
                {"routes":[
                  {"routeId":"orders","source":"file:orders.yaml:1","code":[{"line":1,"code":"- route:"}]},
                  {"routeId":"billing","source":"file:billing.yaml:1","code":[{"line":1,"code":"- route:"}]},
                  {"routeId":"audit","source":"file:audit.yaml:1","code":[{"line":1,"code":"- route:"}]}]}
                """));

        assertThat(run("cmd", "route-dump", Long.toString(PID), "--filter=orders,billing", "--json"))
                .isEqualTo(CliExitCode.OK);

        JsonArray routes = Jsoner.deserialize(printer.getStdout(), (JsonArray) null);
        assertThat(routes.stream().map(o -> ((JsonObject) o).getString("routeId"))).containsExactly("billing", "orders");
    }

    @Test
    void followWithoutValueFollows() {
        // not the opposite of the default, as picocli has it for a boolean option
        CamelLogAction log = new CamelLogAction(main);
        new CommandLine(log).parseArgs("--follow", "orders");
        assertThat(log.follow).isTrue();
        assertThat(log.name).isEqualTo("orders");

        CamelTraceAction trace = new CamelTraceAction(main);
        new CommandLine(trace).parseArgs("--follow=false");
        assertThat(trace.follow).isFalse();

        CamelReceiveAction receive = new CamelReceiveAction(main);
        new CommandLine(receive).parseArgs("--follow");
        assertThat(receive.follow).isTrue();
    }

    @Test
    void filterMatching() {
        assertThat(ActionBaseCommand.matchesFilter("ord", "orders")).isTrue();
        assertThat(ActionBaseCommand.matchesFilter("orders,billing", "billing")).isTrue();
        assertThat(ActionBaseCommand.matchesFilter("orders,billing", "audit")).isFalse();
        // a name with - excludes what it matches
        assertThat(ActionBaseCommand.matchesFilter("-audit", "orders")).isTrue();
        assertThat(ActionBaseCommand.matchesFilter("-audit", "audit")).isFalse();
        assertThat(ActionBaseCommand.matchesFilter("ord*,-orders-eu", "orders-eu")).isFalse();
        // the route id or the source file
        assertThat(ActionBaseCommand.matchesFilter("hello", "route1", "hello.yaml")).isTrue();
        assertThat(ActionBaseCommand.matchesFilter("hello", null, null)).isFalse();
    }

    /**
     * This JVM as a running integration on Camel 4.24.
     */
    private static void integration() throws Exception {
        Files.writeString(CommandLineHelper.getCamelDir().resolve(PID + "-status.json"),
                "{\"context\":{\"name\":\"test\",\"version\":\"4.24.0\",\"phase\":5}}");
    }

    /**
     * Answers the next request like the connector: writes the answer, then deletes the request.
     */
    private Future<JsonObject> answer(Function<JsonObject, JsonObject> answer) {
        return executor.submit(() -> {
            Path dir = CommandLineHelper.getCamelDir();
            Path request = await().atMost(10, TimeUnit.SECONDS).until(() -> requests(dir), files -> !files.isEmpty())
                    .get(0);
            JsonObject action = json(Files.readString(request));
            String id = request.getFileName().toString().substring((PID + "-action-").length()).replace(".json", "");
            Files.writeString(dir.resolve(PID + "-output-" + id + ".json"), answer.apply(action).toJson());
            Files.delete(request);
            return action;
        });
    }

    private static List<Path> requests(Path dir) throws Exception {
        try (var files = Files.list(dir)) {
            return files.filter(f -> f.getFileName().toString().startsWith(PID + "-action-")).toList();
        }
    }

    private static JsonObject json(String text) {
        return Jsoner.deserialize(text, (JsonObject) null);
    }

    private int run(String... args) {
        main.execute(args);
        return main.exitCode;
    }

    private static class TestMain extends CamelJBangMain {
        int exitCode = -1;

        @Override
        public void postAddCommands(CommandLine commandLine, String[] args) {
            // the commands of the CLI only
        }

        @Override
        public void quit(int exitCode) {
            this.exitCode = exitCode;
        }
    }
}
