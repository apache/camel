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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * camel get route-controller: the route controller of an integration and its routes, as the route-controller dev
 * console has them: the default controller, or the supervising controller with the routes it restarts.
 */
@ExtendWith(MockitoExtension.class)
class RouteControllerActionTest extends ActionCommandTestSupport {

    @Test
    void testRequestsRouteControllerAndRendersRoutes() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command(), defaultController())).isZero();

        JsonObject action = readActionFile(TEST_PID);
        assertThat(action.getString("action")).isEqualTo("route-controller");
        // stack traces by default
        assertThat(action.getString("stacktrace")).isEqualTo("true");
        String out = printer.getOutput();
        assertThat(out).contains("Default Route Controller", "Routes Total: 1", "myRoute", "timer://foo", "Started")
                // the default controller does not tell whether its routes are starting
                .doesNotContain("Starting Routes", "ATTEMPT");
    }

    @Test
    void supervisingControllerWithTheRoutesItRestarts() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command(), supervisingController())).isZero();

        String out = printer.getOutput();
        assertThat(out).contains("Supervising Route Controller", "Initial Starting Routes: false",
                "Unhealthy Routes: true", "Routes Total: 4", "Routes Started: 1", "Routes Restarting: 2",
                "Routes Exhausted: 1", "Backoff Delay: 2000", "Thread Pool Size: 1");
        String[] lines = table(out);
        assertThat(lines[0]).contains("ID", "URI", "STATE", "ATTEMPT", "ELAPSED", "LAST-AGO", "ERROR-MESSAGE");
        // the routes by id; an active route is starting at its first attempt, and restarting after
        assertThat(lines[1]).contains("ftp", "Starting", " 1 ", "Cannot login");
        assertThat(lines[2]).contains("kafka", "Restarting", " 3 ", "Broker down");
        assertThat(lines[3]).contains("mail", "Exhausted", " 10 ", "No host");
        assertThat(lines[4]).contains("timer", "Started");
    }

    @Test
    void stackTracesOfTheRoutesThatFail() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command(), supervisingController())).isZero();

        String out = printer.getOutput();
        assertThat(out).contains("STACK-TRACE", "ID: kafka", "URI: kafka://orders", "STATE: Restarting",
                "java.io.IOException: Broker down", "at org.example.Kafka.connect(Kafka.java:10)",
                "at org.example.Kafka.start(Kafka.java:20)");
    }

    @Test
    void depthLimitsTheStackTraces() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command("--depth=2"), supervisingController())).isZero();

        assertThat(printer.getOutput()).contains("at org.example.Kafka.connect(Kafka.java:10)")
                .doesNotContain("at org.example.Kafka.start(Kafka.java:20)");
    }

    @Test
    void withoutTraceTheStackTracesAreNotAskedFor() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command("--trace=false"), supervisingController())).isZero();

        assertThat(readActionFile(TEST_PID).getString("stacktrace")).isEqualTo("false");
        assertThat(printer.getOutput()).contains("Broker down").doesNotContain("STACK-TRACE");
    }

    @Test
    void withoutHeader() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command("--header=false"), supervisingController())).isZero();

        assertThat(printer.getOutput()).doesNotContain("Supervising Route Controller", "Backoff Delay")
                .contains("kafka", "Restarting");
    }

    @Test
    void backoffTimesLongerThanAnInt() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        JsonObject answer = supervisingController();
        // 30 days
        answer.put("backoffMaxElapsedTime", 2_592_000_000L);
        answer.put("backoffMaxAttempts", Long.MAX_VALUE);

        assertThat(callWithResponse(command(), answer)).isZero();

        assertThat(printer.getOutput()).contains("Backoff Max Elapsed Time: 2592000000",
                "Backoff Max Attempts: " + Long.MAX_VALUE);
    }

    @Test
    void sortByState() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command("--sort=state", "--trace=false"), supervisingController())).isZero();

        // by the status of the routes
        String[] lines = table(printer.getOutput());
        assertThat(lines[1]).contains("timer");
    }

    @Test
    void jsonIsTheAnswerOfTheIntegration() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command("--json"), supervisingController())).isZero();

        JsonObject json = Jsoner.deserialize(printer.getOutput(), (JsonObject) null);
        assertThat(json.getString("controller")).isEqualTo("SupervisingRouteController");
        JsonArray routes = (JsonArray) json.get("routes");
        assertThat(routes).hasSize(4);
    }

    @Test
    void anAnswerWithoutRoutesIsAFailure() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        RouteControllerAction command = command();

        assertThatThrownBy(() -> callWithResponse(command, new JsonObject()))
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.FAILURE))
                .hasMessage("Route controller data not available from the running integration");
    }

    @Test
    void testReturnsErrorWhenNameDoesNotMatch() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        RouteControllerAction command = new RouteControllerAction(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command, "doesNotExist");

        assertThatThrownBy(() -> callWithSingleProcess(command))
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.NOT_FOUND))
                .hasMessage("No running Camel integration matches: doesNotExist");
        assertThat(printer.getOutput()).isEmpty();
    }

    private RouteControllerAction command(String... args) {
        RouteControllerAction command = new RouteControllerAction(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command, Stream.concat(Stream.of("myApp"), Arrays.stream(args)).toArray(String[]::new));
        return command;
    }

    // the lines of the table of the routes, from its header
    private static String[] table(String out) {
        String table = out.substring(out.indexOf("ID "));
        int end = table.indexOf("---");
        return (end > 0 ? table.substring(0, end) : table).strip().split(System.lineSeparator());
    }

    // as the route-controller dev console answers
    private static JsonObject defaultController() {
        JsonObject route = new JsonObject();
        route.put("routeId", "myRoute");
        route.put("uri", "timer://foo");
        route.put("status", "Started");
        JsonObject answer = new JsonObject();
        answer.put("controller", "DefaultRouteController");
        answer.put("totalRoutes", 1);
        answer.put("routes", new JsonArray(List.of(route)));
        return answer;
    }

    private static JsonObject supervisingController() {
        long now = System.currentTimeMillis();
        JsonObject answer = new JsonObject();
        answer.put("controller", "SupervisingRouteController");
        answer.put("startingRoutes", false);
        answer.put("unhealthyRoutes", true);
        answer.put("totalRoutes", 4);
        answer.put("startedRoutes", 1L);
        answer.put("restartingRoutes", 2);
        answer.put("exhaustedRoutes", 1);
        answer.put("initialDelay", 0L);
        answer.put("backoffDelay", 2000L);
        answer.put("backoffMaxDelay", 0L);
        answer.put("backoffMaxElapsedTime", 0L);
        answer.put("backoffMaxAttempts", 10L);
        answer.put("threadPoolSize", 1);
        answer.put("unhealthyOnRestarting", true);
        answer.put("unhealthyOnExhausted", true);
        answer.put("routes", new JsonArray(
                List.of(
                        route("timer", "timer://tick", "Started", null, 0, 0, null),
                        route("kafka", "kafka://orders", "Stopped", "Active", 3, now - 5000, "Broker down"),
                        route("ftp", "ftp://host", "Stopped", "Active", 1, now - 1000, "Cannot login"),
                        route("mail", "smtp://host", "Stopped", "Exhausted", 10, 0, "No host"))));
        return answer;
    }

    private static JsonObject route(
            String routeId, String uri, String status, String supervising, long attempts, long lastAttempt,
            String error) {
        JsonObject route = new JsonObject();
        route.put("routeId", routeId);
        route.put("uri", uri);
        route.put("status", status);
        route.put("attempts", attempts);
        route.put("lastAttempt", lastAttempt);
        route.put("nextAttempt", 0L);
        route.put("elapsed", lastAttempt > 0 ? 7000L : 0L);
        if (supervising != null) {
            route.put("supervising", supervising);
        }
        if (error != null) {
            route.put("error", error);
            route.put("stackTrace", new JsonArray(
                    List.of("java.io.IOException: " + error,
                            "\tat org.example.Kafka.connect(Kafka.java:10)", "\tat org.example.Kafka.start(Kafka.java:20)")));
        }
        return route;
    }
}
