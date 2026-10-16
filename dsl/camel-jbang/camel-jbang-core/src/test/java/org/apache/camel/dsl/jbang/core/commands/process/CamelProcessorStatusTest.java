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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
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
 * camel get processor: the routes of the integrations with their processors as a tree, their statistics, and filtered.
 */
@ExtendWith(MockitoExtension.class)
class CamelProcessorStatusTest extends ProcessCommandTestSupport {

    @Test
    void testEmptyOutputWhenNoProcesses() throws Exception {
        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.empty());

            assertThat(command().doCall()).isZero();
        }

        assertThat(printer.getStdout()).isEmpty();
        assertThat(printer.getStderr()).isEqualTo("No running Camel integrations");
    }

    @Test
    void processorsOfTheRoutesAsATree() throws Exception {
        writeStatusFile(TEST_PID, integration(route("orders", "timer:tick", "Started", 25,
                processor("orders-log1", "log", 1, 5),
                processor("orders-to1", "to[mock:out]", 2, 20))));

        assertThat(call(command())).isZero();

        String[] lines = printer.getOutput().split(System.lineSeparator());
        assertThat(lines[0]).contains("PID", "NAME", "ID", "PROCESSOR", "STATUS", "TOTAL", "MEAN");
        assertThat(lines[1]).contains("12345", "myApp", "orders", "timer:tick", "Started", "25");
        // the processors under their route, the id without the node prefix and indented by level
        assertThat(lines[2]).doesNotContain("12345", "myApp").contains(" log1 ", "   log ", "5");
        assertThat(lines[3]).contains(" to1 ", "     to[mock:out]", "20");
    }

    @Test
    void jsonHasTheRoutesAndTheirProcessors() throws Exception {
        writeStatusFile(TEST_PID, integration(route("orders", "timer:tick", "Started", 25,
                processor("orders-log1", "log", 1, 5))));

        assertThat(call(command("--json"))).isZero();

        JsonArray rows = Jsoner.deserialize(printer.getOutput(), (JsonArray) null);
        assertThat(rows).hasSize(2);
        JsonObject route = rows.getMap(0);
        assertThat(route.getLong("pid")).isEqualTo(TEST_PID);
        assertThat(route.getString("id")).isEqualTo("orders");
        assertThat(route.getString("processor")).isEqualTo("timer:tick");
        assertThat(route.getLong("mean")).isEqualTo(25);
        JsonObject log = rows.getMap(1);
        assertThat(log.getString("routeId")).isEqualTo("orders");
        assertThat(log.getString("id")).isEqualTo("log1");
        assertThat(log.getString("processor")).isEqualTo("log");
        assertThat(log.getString("status")).isEqualTo("Started");
        assertThat(log.getLong("total")).isEqualTo(10);
        assertThat(log.getLong("mean")).isEqualTo(5);
    }

    @Test
    void disabledProcessor() throws Exception {
        JsonObject log = processor("orders-log1", "log", 1, 5);
        log.put("disabled", true);
        writeStatusFile(TEST_PID, integration(route("orders", "timer:tick", "Started", 25, log)));

        assertThat(call(command())).isZero();

        String[] lines = printer.getOutput().split(System.lineSeparator());
        assertThat(lines[2]).contains("log1", "Disabled");
    }

    @Test
    void limitIsANumberOfRoutes() throws Exception {
        writeStatusFile(TEST_PID, integration(
                route("orders", "timer:a", "Started", 25, processor("orders-log1", "log", 1, 5)),
                route("payments", "timer:b", "Started", 25, processor("payments-log2", "log", 1, 5))));

        assertThat(call(command("--limit=1"))).isZero();

        // with the processors of the route
        assertThat(printer.getOutput()).contains("orders", "log1").doesNotContain("payments", "log2");
    }

    @Test
    void filterShowsTheMatchingProcessorsWithTheirRoute() throws Exception {
        writeStatusFile(TEST_PID, integration(
                route("orders", "timer:a", "Started", 25,
                        processor("orders-log1", "log", 1, 5),
                        processor("orders-to1", "to[mock:out]", 1, 20)),
                route("payments", "timer:b", "Started", 25, processor("payments-to2", "to[mock:pay]", 1, 5))));

        assertThat(call(command("--filter=log*"))).isZero();

        String[] lines = printer.getOutput().split(System.lineSeparator());
        assertThat(lines).hasSize(3);
        assertThat(lines[1]).contains("12345", "myApp", "orders");
        assertThat(lines[2]).contains("log1");
    }

    @Test
    void filterMeanShowsTheSlowerProcessors() throws Exception {
        writeStatusFile(TEST_PID, integration(
                route("orders", "timer:a", "Started", 25,
                        processor("orders-log1", "log", 1, 2),
                        processor("orders-to1", "to[mock:out]", 1, 20)),
                route("payments", "timer:b", "Started", 5, processor("payments-log2", "log", 1, 5))));

        assertThat(call(command("--filter-mean=10"))).isZero();

        assertThat(printer.getOutput()).contains("orders", "to1").doesNotContain("log1", "payments", "log2");
    }

    @Test
    void groupShowsTheRoutesOfTheGroupWithTheirProcessors() throws Exception {
        JsonObject orders = route("orders", "timer:a", "Started", 25, processor("orders-log1", "log", 1, 5));
        orders.put("group", "sales");
        JsonObject audit = route("audit", "timer:b", "Started", 25, processor("audit-log2", "log", 1, 5));
        audit.put("group", "ops");
        writeStatusFile(TEST_PID, integration(orders, audit));

        assertThat(call(command("--group=sales", "--show-group"))).isZero();

        String[] lines = printer.getOutput().split(System.lineSeparator());
        assertThat(lines[0]).contains("GROUP");
        assertThat(printer.getOutput()).contains("sales", "orders", "log1").doesNotContain("audit", "log2");
    }

    @Test
    void runningHidesTheRoutesThatAreNotStarted() throws Exception {
        JsonObject stoppedLog = processor("stopped-log2", "log", 1, 5);
        stoppedLog.put("state", "Stopped");
        writeStatusFile(TEST_PID, integration(
                route("started", "timer:a", "Started", 25, processor("started-log1", "log", 1, 5)),
                route("stopped", "timer:b", "Stopped", 25, stoppedLog)));

        assertThat(call(command("--running"))).isZero();

        assertThat(printer.getOutput()).contains("started", "log1").doesNotContain("stopped", "log2");
    }

    @Test
    void sourceShowsTheSourceLocationAndTheCodeOfTheProcessors() throws Exception {
        JsonObject log = processor("orders-log1", "log", 1, 5);
        log.put("source", "file:src/main/resources/camel/app.yaml:14");
        log.put("code", new JsonArray(
                List.of(
                        new JsonObject(Map.of("line", 13, "code", "- from:")),
                        new JsonObject(Map.of("line", 14, "code", "    - log: \\\"hi\\\"", "match", true)))));
        writeStatusFile(TEST_PID, integration(route("orders", "timer:tick", "Started", 25, log)));

        assertThat(call(command("--source"))).isZero();

        String[] lines = printer.getOutput().split(System.lineSeparator());
        assertThat(lines[2]).contains("app.yaml:14", "- log: \"hi\"").doesNotContain("log1");
    }

    @Test
    void descriptionInTheIdColumn() throws Exception {
        JsonObject log = processor("orders-log1", "log", 1, 5);
        log.put("description", "Logs the order");
        writeStatusFile(TEST_PID, integration(route("orders", "timer:tick", "Started", 25, log)));

        assertThat(call(command("--description"))).isZero();

        assertThat(printer.getOutput()).contains("log1", "Logs the order");
    }

    @Test
    void testFilterMeanExcludesSlowProcessors() throws Exception {
        // the route with mean=5 is excluded when --filter-mean=10
        writeStatusFile(TEST_PID, integration(route("myRoute", "log:output", "Started", 5)));

        assertThat(call(command("--filter-mean=10"))).isZero();

        assertThat(printer.getOutput()).isEmpty();
    }

    private CamelProcessorStatus command(String... args) {
        CamelProcessorStatus command = new CamelProcessorStatus(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command, args);
        return command;
    }

    // runs the command with the integration of the status file of the test
    private static int call(CamelProcessorStatus command) throws Exception {
        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));
            return command.doCall();
        }
    }

    private static JsonObject integration(JsonObject... routes) {
        JsonObject context = new JsonObject();
        context.put("name", "myApp");
        context.put("phase", 5);
        JsonObject root = new JsonObject();
        root.put("context", context);
        root.put("routes", new JsonArray(Arrays.asList(routes)));
        return root;
    }

    // as the route dev console has them, with the processors
    private static JsonObject route(String routeId, String from, String state, long mean, JsonObject... processors) {
        JsonObject route = new JsonObject();
        route.put("routeId", routeId);
        route.put("nodePrefixId", routeId + "-");
        route.put("from", from);
        route.put("state", state);
        route.put("statistics", stats(mean));
        route.put("processors", new JsonArray(Arrays.asList(processors)));
        return route;
    }

    private static JsonObject processor(String id, String name, int level, long mean) {
        JsonObject processor = new JsonObject();
        processor.put("id", id);
        processor.put("nodePrefixId", id.substring(0, id.indexOf('-') + 1));
        processor.put("processor", name);
        processor.put("level", level);
        processor.put("state", "Started");
        processor.put("statistics", stats(mean));
        return processor;
    }

    private static JsonObject stats(long mean) {
        JsonObject stats = new JsonObject();
        stats.put("exchangesTotal", 10);
        stats.put("exchangesFailed", 0);
        stats.put("exchangesInflight", 0);
        stats.put("meanProcessingTime", mean);
        stats.put("maxProcessingTime", mean);
        stats.put("minProcessingTime", mean);
        return stats;
    }
}
