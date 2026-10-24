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
package org.apache.camel.dsl.jbang.core.commands.process;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * camel get route: the routes of the integrations, their state and statistics, filtered, and the last error of the
 * routes that have one.
 */
@ExtendWith(MockitoExtension.class)
class CamelRouteStatusTest extends ProcessCommandTestSupport {

    private static final long OTHER_PID = 23456L;

    @Test
    void testEmptyOutputWhenNoProcesses() throws Exception {
        assertThat(call(command())).isZero();

        assertThat(printer.getStdout()).isEmpty();
        assertThat(printer.getStderr()).isEqualTo("No running Camel integrations");
    }

    @Test
    void routesWithTheirStateAndStatistics() throws Exception {
        writeStatusFile(TEST_PID, integration("myApp",
                route("route1", "timer:tick", "Started", stats(12, 1, 2, 25)),
                route("route2", "timer:tock", "Suspended", stats(0, 0, 0, -1))));

        assertThat(call(command())).isZero();

        String[] lines = printer.getOutput().split(System.lineSeparator());
        assertThat(lines[0]).contains("PID", "NAME", "ID", "FROM", "STATUS", "TOTAL", "FAIL", "INFLIGHT", "MEAN")
                .doesNotContain("GROUP", "REMOTE");
        assertThat(lines[1]).contains("12345", "myApp", "route1", "timer:tick", "Started", "12", "25");
        assertThat(lines[2]).contains("route2", "timer:tock", "Suspended");
    }

    @Test
    void jsonHasTheNumbersOfTheRoutes() throws Exception {
        writeStatusFile(TEST_PID, integration("myApp",
                route("route1", "timer:tick", "Started", stats(12, 1, 2, 25)),
                route("route2", "timer:tock", "Started", stats(0, 0, 0, -1))));

        assertThat(call(command("--json"))).isZero();

        JsonArray routes = Jsoner.deserialize(printer.getOutput(), (JsonArray) null);
        assertThat(routes).hasSize(2);
        JsonObject route1 = routes.getMap(0);
        assertThat(route1.getLong("pid")).isEqualTo(TEST_PID);
        assertThat(route1.getString("routeId")).isEqualTo("route1");
        assertThat(route1.getString("status")).isEqualTo("Started");
        assertThat(route1.getLong("total")).isEqualTo(12);
        assertThat(route1.getLong("failed")).isEqualTo(1);
        assertThat(route1.getLong("inflight")).isEqualTo(2);
        assertThat(route1.getLong("mean")).isEqualTo(25);
        // no exchange yet: no mean
        JsonObject route2 = routes.getMap(1);
        assertThat(route2.get("mean")).isNull();
    }

    @Test
    void checksumShowsTheChecksumAndRevisionOfEachRoute() throws Exception {
        JsonObject changed = route("route1", "timer:tick", "Started", stats(12, 1, 2, 25));
        changed.put("checksum", "0a1b2c3d4e5f");
        changed.put("revision", 2);
        writeStatusFile(TEST_PID, integration("myApp", changed,
                route("route2", "timer:tock", "Started", stats(0, 0, 0, -1))));

        assertThat(call(command())).isZero();
        assertThat(printer.getOutput()).doesNotContain("CHECKSUM", "0a1b2c3d4e5f");

        printer = new StringPrinter();
        assertThat(call(command("--checksum"))).isZero();
        String[] lines = printer.getOutput().split(System.lineSeparator());
        assertThat(lines[0]).contains("CHECKSUM", "REV");
        assertThat(lines[1]).contains("route1", "0a1b2c3d4e5f", " 2 ");

        // the JSON has them whether asked for or not; a route of an older Camel has none
        printer = new StringPrinter();
        assertThat(call(command("--json"))).isZero();
        JsonArray routes = Jsoner.deserialize(printer.getOutput(), (JsonArray) null);
        JsonObject first = routes.getMap(0);
        JsonObject second = routes.getMap(1);
        assertThat(first.getString("checksum")).isEqualTo("0a1b2c3d4e5f");
        assertThat(first.getInteger("revision")).isEqualTo(2);
        assertThat(second.get("revision")).isNull();
    }

    @Test
    void jsonWithoutRoutesIsAnEmptyArray() throws Exception {
        writeStatusFile(TEST_PID, integration("myApp"));

        assertThat(call(command("--json"))).isZero();

        assertThat(printer.getOutput()).isEqualTo("[]");
    }

    @Test
    void errorShowsTheRoutesWithAnErrorAndTheirStackTrace() throws Exception {
        JsonObject failed = route("failed", "kafka:in", "Stopped", stats(0, 0, 0, -1));
        failed.put("lastError", lastError("start", "Cannot connect"));
        writeStatusFile(TEST_PID, integration("myApp", route("ok", "timer:tick", "Started", stats(1, 0, 0, 5)), failed));

        assertThat(call(command("--error"))).isZero();

        assertThat(printer.getOutput())
                .contains("PHASE", "MESSAGE", "failed", "kafka:in", "Error", "start", "Cannot connect", "STACK-TRACE",
                        "java.lang.IllegalStateException: Cannot connect", "at org.example.Kafka.start(Kafka.java:42)")
                .doesNotContain("timer:tick");
    }

    @Test
    void errorAsJson() throws Exception {
        JsonObject failed = route("failed", "kafka:in", "Stopped", stats(0, 0, 0, -1));
        failed.put("lastError", lastError("start", "Cannot connect"));
        writeStatusFile(TEST_PID, integration("myApp", route("ok", "timer:tick", "Started", stats(1, 0, 0, 5)), failed));

        assertThat(call(command("--error", "--json"))).isZero();

        JsonArray routes = Jsoner.deserialize(printer.getOutput(), (JsonArray) null);
        assertThat(routes).hasSize(1);
        JsonObject route = routes.getMap(0);
        assertThat(route.getString("routeId")).isEqualTo("failed");
        assertThat(route.getString("status")).isEqualTo("Error");
        JsonObject error = route.getMap("lastError");
        assertThat(error.getString("phase")).isEqualTo("start");
        assertThat(error.getString("message")).isEqualTo("Cannot connect");
        assertThat(error.getString("ago")).isNotBlank();
        List<String> stackTrace = error.getCollection("stackTrace");
        assertThat(stackTrace).hasSize(2);
    }

    @Test
    void limitCountsTheRoutesWithAnError() throws Exception {
        JsonObject failed = route("failed", "kafka:in", "Stopped", stats(0, 0, 0, -1));
        failed.put("lastError", lastError("start", "Cannot connect"));
        writeStatusFile(TEST_PID, integration("myApp", route("ok", "timer:tick", "Started", stats(1, 0, 0, 5)), failed));

        assertThat(call(command("--error", "--limit=1"))).isZero();

        assertThat(printer.getOutput()).contains("failed", "Cannot connect");
    }

    @Test
    void noteOfARouteWithAnError() throws Exception {
        JsonObject failed = route("failed", "kafka:in", "Stopped", stats(0, 0, 0, -1));
        failed.put("note", "Needs the broker");
        failed.put("lastError", lastError("start", "Cannot connect"));
        writeStatusFile(TEST_PID, integration("myApp", failed));

        assertThat(call(command("--error", "--note"))).isZero();

        assertThat(printer.getOutput()).contains("Needs the broker");
    }

    @Test
    void filterByRouteIdOrEndpoint() throws Exception {
        writeStatusFile(TEST_PID, integration("myApp",
                route("orders", "kafka:orders?groupId=a", "Started", stats(0, 0, 0, -1)),
                route("payments", "jms:payments", "Started", stats(0, 0, 0, -1)),
                route("audit", "timer:audit", "Started", stats(0, 0, 0, -1))));

        // the route id, or the start of the endpoint without its query parameters
        assertThat(call(command("--filter=orders", "--filter=jms:pay"))).isZero();

        assertThat(printer.getOutput()).contains("orders", "payments").doesNotContain("audit");
    }

    @Test
    void filterMeanShowsTheSlowerRoutes() throws Exception {
        writeStatusFile(TEST_PID, integration("myApp",
                route("slow", "timer:slow", "Started", stats(5, 0, 0, 50)),
                route("fast", "timer:fast", "Started", stats(5, 0, 0, 5)),
                route("idle", "timer:idle", "Started", stats(0, 0, 0, -1))));

        assertThat(call(command("--filter-mean=10"))).isZero();

        assertThat(printer.getOutput()).contains("slow").doesNotContain("fast", "idle");
    }

    @Test
    void groupFilterAndColumn() throws Exception {
        JsonObject orders = route("orders", "timer:orders", "Started", stats(0, 0, 0, -1));
        orders.put("group", "sales");
        JsonObject audit = route("audit", "timer:audit", "Started", stats(0, 0, 0, -1));
        audit.put("group", "ops");
        writeStatusFile(TEST_PID, integration("myApp", orders, audit));

        assertThat(call(command("--group=sales", "--show-group"))).isZero();

        String[] lines = printer.getOutput().split(System.lineSeparator());
        assertThat(lines[0]).contains("GROUP");
        assertThat(lines).hasSize(2);
        assertThat(lines[1]).contains("sales", "orders");
    }

    @Test
    void runningHidesTheRoutesThatAreNotStarted() throws Exception {
        writeStatusFile(TEST_PID, integration("myApp",
                route("started", "timer:a", "Started", stats(0, 0, 0, -1)),
                route("stopped", "timer:b", "Stopped", stats(0, 0, 0, -1)),
                route("suspended", "timer:c", "Suspended", stats(0, 0, 0, -1))));

        assertThat(call(command("--running"))).isZero();

        assertThat(printer.getOutput()).contains("started").doesNotContain("stopped", "suspended");
    }

    @Test
    void shortUriLeavesOutTheQueryParameters() throws Exception {
        writeStatusFile(TEST_PID, integration("myApp", route("route1", "timer:t?period=5", "Started", stats(0, 0, 0, -1))));

        assertThat(call(command("--short-uri"))).isZero();

        assertThat(printer.getOutput()).contains("timer:t").doesNotContain("period");
    }

    @Test
    void wideUriShowsTheWholeEndpoint() throws Exception {
        writeStatusFile(TEST_PID, integration("myApp",
                route("route1", "timer:tick?period=5000&delay=1000&repeatCount=10", "Started", stats(0, 0, 0, -1))));

        assertThat(call(command("--wide-uri"))).isZero();

        // across lines when wider than the column
        assertThat(printer.getOutput().replaceAll("\\s", "")).contains("repeatCount=10");
    }

    @Test
    void sourceShowsTheSourceLocationInsteadOfTheId() throws Exception {
        JsonObject route = route("route1", "timer:tick", "Started", stats(0, 0, 0, -1));
        route.put("source", "file:src/main/resources/camel/orders.yaml:12");
        writeStatusFile(TEST_PID, integration("myApp", route));

        assertThat(call(command("--source"))).isZero();

        assertThat(printer.getOutput()).contains("orders.yaml:12").doesNotContain("route1");
    }

    @Test
    void descriptionAndNoteInTheIdColumn() throws Exception {
        JsonObject route = route("route1", "timer:tick", "Started", stats(0, 0, 0, -1));
        route.put("description", "Polls the orders");
        route.put("note", "Owned by sales");
        writeStatusFile(TEST_PID, integration("myApp", route));

        assertThat(call(command("--description", "--note"))).isZero();

        assertThat(printer.getOutput()).contains("route1", "Polls the orders", "Owned by sales");
    }

    @Test
    void remoteColumnWhenTheIntegrationTellsWhichEndpointsAreRemote() throws Exception {
        JsonObject kafka = route("kafka", "kafka:in", "Started", stats(0, 0, 0, -1));
        kafka.put("remote", true);
        JsonObject timer = route("timer", "timer:tick", "Started", stats(0, 0, 0, -1));
        timer.put("remote", false);
        writeStatusFile(TEST_PID, integration("myApp", kafka, timer));

        assertThat(call(command())).isZero();

        String[] lines = printer.getOutput().split(System.lineSeparator());
        assertThat(lines[0]).contains("REMOTE");
        assertThat(lines[1]).contains("kafka", " x ");
        assertThat(lines[2]).contains("timer").doesNotContain(" x ");
    }

    @Test
    void remoteBreaksDownTheCountersIntoRemoteAndTotal() throws Exception {
        JsonObject stats = stats(10, 2, 1, 5);
        stats.put("remoteExchangesTotal", 6);
        stats.put("remoteExchangesFailed", 1);
        stats.put("remoteExchangesInflight", 0);
        writeStatusFile(TEST_PID, integration("myApp", route("audit", "direct:audit", "Started", stats)));

        assertThat(call(command())).isZero();
        String[] lines = printer.getOutput().split(System.lineSeparator());
        assertThat(lines[1]).contains(" 10 ").doesNotContain("6/10");

        printer = new StringPrinter();
        assertThat(call(command("--remote"))).isZero();
        lines = printer.getOutput().split(System.lineSeparator());
        assertThat(lines[1]).contains("6/10", "1/2", "0/1");

        printer = new StringPrinter();
        assertThat(call(command("--json"))).isZero();
        JsonObject route = Jsoner.deserialize(printer.getOutput(), (JsonArray) null).getMap(0);
        assertThat(route.getLong("remoteTotal")).isEqualTo(6);
        assertThat(route.getLong("remoteFailed")).isEqualTo(1);
        assertThat(route.getLong("remoteInflight")).isZero();
    }

    @Test
    void sortByNameOfTheIntegrations() throws Exception {
        writeStatusFile(TEST_PID, integration("zeta", route("z1", "timer:z", "Started", stats(0, 0, 0, -1))));
        writeStatusFile(OTHER_PID, integration("alpha", route("a1", "timer:a", "Started", stats(0, 0, 0, -1))));

        assertThat(call(command("--sort=name"))).isZero();
        String output = printer.getOutput();
        assertThat(output.indexOf("alpha")).isLessThan(output.indexOf("zeta"));

        printer = new StringPrinter();
        assertThat(call(command("--sort=-name"))).isZero();
        output = printer.getOutput();
        assertThat(output.indexOf("zeta")).isLessThan(output.indexOf("alpha"));
    }

    @Test
    void testLimitReducesRows() throws Exception {
        JsonObject[] routes = new JsonObject[5];
        for (int i = 0; i < routes.length; i++) {
            routes[i] = route("route" + (i + 1), "timer:tick" + (i + 1), "Started", stats(0, 0, 0, -1));
        }
        writeStatusFile(TEST_PID, integration("bigApp", routes));

        assertThat(call(command("--limit=2"))).isZero();

        assertThat(printer.getOutput()).contains("route1", "route2").doesNotContain("route3", "route5");
    }

    private CamelRouteStatus command(String... args) {
        CamelRouteStatus command = new CamelRouteStatus(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command, args);
        return command;
    }

    // runs the command with the integrations of the status files of the test
    private static int call(CamelRouteStatus command) throws Exception {
        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            List<ProcessHandle> handles = Stream.of(TEST_PID, OTHER_PID)
                    .filter(pid -> Files.exists(CommandLineHelper.getCamelDir().resolve(pid + "-status.json")))
                    .map(ProcessCommandTestSupport::mockProcessHandle).toList();
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> handles.stream());
            return command.doCall();
        }
    }

    private static JsonObject integration(String name, JsonObject... routes) {
        JsonObject context = new JsonObject();
        context.put("name", name);
        context.put("phase", 5);
        JsonObject root = new JsonObject();
        root.put("context", context);
        root.put("routes", new JsonArray(Arrays.asList(routes)));
        return root;
    }

    // as the route dev console has them
    private static JsonObject route(String routeId, String from, String state, JsonObject stats) {
        JsonObject route = new JsonObject();
        route.put("routeId", routeId);
        route.put("from", from);
        route.put("state", state);
        route.put("uptime", "1m");
        route.put("statistics", stats);
        return route;
    }

    private static JsonObject stats(long total, long failed, long inflight, long mean) {
        JsonObject stats = new JsonObject();
        stats.put("exchangesTotal", total);
        stats.put("exchangesFailed", failed);
        stats.put("exchangesInflight", inflight);
        stats.put("meanProcessingTime", mean);
        stats.put("maxProcessingTime", Math.max(mean, 0));
        stats.put("minProcessingTime", Math.max(mean, 0));
        return stats;
    }

    private static JsonObject lastError(String phase, String message) {
        JsonObject error = new JsonObject();
        error.put("phase", phase);
        error.put("timestamp", System.currentTimeMillis() - 5000);
        error.put("message", message);
        error.put("stackTrace", new JsonArray(
                List.of("java.lang.IllegalStateException: " + message,
                        "\tat org.example.Kafka.start(Kafka.java:42)")));
        return error;
    }
}
