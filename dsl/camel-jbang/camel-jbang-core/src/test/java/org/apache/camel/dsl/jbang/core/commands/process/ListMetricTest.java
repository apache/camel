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
import java.util.Map;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
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
 * camel get metric: the Micrometer metrics of the integrations (counters, timers, gauges and distribution summaries),
 * as the micrometer dev console has them in the status file.
 */
@ExtendWith(MockitoExtension.class)
class ListMetricTest extends ProcessCommandTestSupport {

    @Test
    void testEmptyOutputWhenNoMetrics() throws Exception {
        JsonObject root = new JsonObject();
        root.put("context", new JsonObject(Map.of("name", "myApp")));
        writeStatusFile(TEST_PID, root);

        assertThat(call(command())).isZero();

        assertThat(printer.getOutput()).isEmpty();
    }

    @Test
    void theMetricsOfEachType() throws Exception {
        writeStatusFile(TEST_PID, integration(
                counter("orders.placed", 42, tag("routeId", "orders")),
                timer("orders.time", 10, 25, 80, 250, tag("routeId", "orders"), tag("nodeId", "to1")),
                gauge("orders.queue", 7),
                distribution("orders.size", 4, 1.5, 3, 6)));

        assertThat(call(command())).isZero();

        String[] lines = printer.getOutput().split(System.lineSeparator());
        assertThat(lines[0]).contains("PID", "NAME", "TYPE", "METRIC", "ID", "VALUE", "MEAN", "MAX", "TOTAL")
                .doesNotContain("TAGS");
        assertThat(lines).hasSize(5);
        // the metrics by name
        assertThat(lines[1]).contains("12345", "myApp", "counter", "orders.placed", "orders", "42");
        assertThat(lines[2]).contains("gauge", "orders.queue", "7");
        assertThat(lines[3]).contains("distribution", "orders.size", "4", "1.5", "3", "6");
        assertThat(lines[4]).contains("timer", "orders.time", "orders/to1", "10", "25", "80", "250");
    }

    @Test
    void jsonOfTheMetrics() throws Exception {
        writeStatusFile(TEST_PID, integration(
                timer("orders.time", 10, 25, 80, 250, tag("routeId", "orders"), tag("nodeId", "to1")),
                distribution("orders.size", 4, 1.5, 3, 6)));

        assertThat(call(command("--json"))).isZero();

        JsonArray metrics = Jsoner.deserialize(printer.getOutput(), (JsonArray) null);
        assertThat(metrics).hasSize(2);
        JsonObject size = metrics.getMap(0);
        assertThat(size.getLong("pid")).isEqualTo(TEST_PID);
        assertThat(size.getString("type")).isEqualTo("distribution");
        assertThat(size.getString("metric")).isEqualTo("orders.size");
        assertThat(size.getDouble("value")).isEqualTo(4);
        assertThat(size.getDouble("mean")).isEqualTo(1.5);
        assertThat(size.getDouble("total")).isEqualTo(6);
        JsonObject time = metrics.getMap(1);
        assertThat(time.getString("type")).isEqualTo("timer");
        assertThat(time.getString("id")).isEqualTo("orders/to1");
        assertThat(time.getDouble("max")).isEqualTo(80);
        assertThat(time.getString("tags")).isEqualTo("routeId=orders nodeId=to1");
    }

    @Test
    void unusedMetricsOnlyWithAll() throws Exception {
        writeStatusFile(TEST_PID, integration(counter("orders.placed", 42), counter("orders.lost", 0)));

        assertThat(call(command())).isZero();
        assertThat(printer.getOutput()).contains("orders.placed").doesNotContain("orders.lost");

        printer = new StringPrinter();
        assertThat(call(command("--all"))).isZero();
        assertThat(printer.getOutput()).contains("orders.placed", "orders.lost");
    }

    @Test
    void filterByTypeAsInTheHelp() throws Exception {
        writeStatusFile(TEST_PID, integration(counter("orders.placed", 42), timer("orders.time", 10, 25, 80, 250)));

        assertThat(call(command("--filter=timer*"))).isZero();

        assertThat(printer.getOutput()).contains("orders.time").doesNotContain("orders.placed");
    }

    @Test
    void filterByNameOrTag() throws Exception {
        writeStatusFile(TEST_PID, integration(
                counter("orders.placed", 42, tag("routeId", "orders")),
                counter("payments.made", 5, tag("routeId", "payments")),
                counter("audit.logged", 3, tag("routeId", "audit"))));

        assertThat(call(command("--filter=placed"))).isZero();
        assertThat(printer.getOutput()).contains("orders.placed").doesNotContain("payments.made", "audit.logged");

        printer = new StringPrinter();
        assertThat(call(command("--filter=routeId=payments"))).isZero();
        assertThat(printer.getOutput()).contains("payments.made").doesNotContain("orders.placed", "audit.logged");
    }

    @Test
    void customLeavesOutTheMetricsOfCamel() throws Exception {
        writeStatusFile(TEST_PID, integration(
                // the micrometer naming (the default) and the classic naming of the metrics of camel
                counter("camel.exchanges.total", 42),
                counter("CamelExchangesTotal", 42),
                counter("orders.placed", 5)));

        assertThat(call(command("--custom"))).isZero();

        // not even the start of the names, as the column is narrow
        assertThat(printer.getOutput()).contains("orders.placed").doesNotContain("camel.", "CamelExch");
    }

    @Test
    void tagsColumn() throws Exception {
        writeStatusFile(TEST_PID, integration(counter("orders.placed", 42, tag("routeId", "orders"), tag("nodeId", "to1"))));

        assertThat(call(command("--tags"))).isZero();

        assertThat(printer.getOutput().split(System.lineSeparator())[0]).contains("TAGS");
        // across lines when wider than the column
        assertThat(printer.getOutput().replaceAll("\\s", "")).contains("routeId=ordersnodeId=to1");
    }

    @Test
    void theInfoGaugeOfTheApplicationIsNotAMetric() throws Exception {
        writeStatusFile(TEST_PID, integration(gauge("app.info", 1), gauge("orders.queue", 7)));

        assertThat(call(command())).isZero();

        assertThat(printer.getOutput()).contains("orders.queue").doesNotContain("app.info");
    }

    private ListMetric command(String... args) {
        ListMetric command = new ListMetric(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command, args);
        return command;
    }

    // runs the command with the integration of the status file of the test
    private static int call(ListMetric command) throws Exception {
        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));
            return command.doCall();
        }
    }

    private static JsonObject integration(JsonObject... metrics) {
        JsonArray counters = new JsonArray();
        JsonArray timers = new JsonArray();
        JsonArray gauges = new JsonArray();
        JsonArray distribution = new JsonArray();
        for (JsonObject metric : metrics) {
            String type = (String) metric.remove("type");
            switch (type) {
                case "counter" -> counters.add(metric);
                case "timer" -> timers.add(metric);
                case "gauge" -> gauges.add(metric);
                default -> distribution.add(metric);
            }
        }
        JsonObject micrometer = new JsonObject();
        micrometer.put("counters", counters);
        micrometer.put("timers", timers);
        micrometer.put("gauges", gauges);
        micrometer.put("distribution", distribution);
        JsonObject root = new JsonObject();
        root.put("context", new JsonObject(Map.of("name", "myApp")));
        root.put("micrometer", micrometer);
        return root;
    }

    // as the micrometer dev console has them
    private static JsonObject counter(String name, long count, JsonObject... tags) {
        JsonObject metric = metric("counter", name, tags);
        metric.put("count", count);
        return metric;
    }

    private static JsonObject timer(String name, long count, long mean, long max, long total, JsonObject... tags) {
        JsonObject metric = metric("timer", name, tags);
        metric.put("count", count);
        metric.put("mean", mean);
        metric.put("max", max);
        metric.put("total", total);
        return metric;
    }

    private static JsonObject gauge(String name, double value) {
        JsonObject metric = metric("gauge", name);
        metric.put("value", value);
        return metric;
    }

    private static JsonObject distribution(String name, long count, double mean, double max, double totalAmount) {
        JsonObject metric = metric("distribution", name);
        metric.put("count", count);
        metric.put("mean", mean);
        metric.put("max", max);
        metric.put("totalAmount", totalAmount);
        return metric;
    }

    private static JsonObject metric(String type, String name, JsonObject... tags) {
        JsonObject metric = new JsonObject();
        metric.put("type", type);
        metric.put("name", name);
        if (tags.length > 0) {
            metric.put("tags", new JsonArray(Arrays.asList(tags)));
        }
        return metric;
    }

    private static JsonObject tag(String key, String value) {
        return new JsonObject(Map.of("key", key, "value", value));
    }
}
