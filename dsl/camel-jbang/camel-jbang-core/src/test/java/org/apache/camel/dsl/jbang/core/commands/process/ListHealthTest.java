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

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

/**
 * camel get health: the health checks of the integrations, as the health dev console has them in the status file.
 */
@ExtendWith(MockitoExtension.class)
class ListHealthTest extends ProcessCommandTestSupport {

    @Test
    void testEmptyOutputWhenNoProcesses() throws Exception {
        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.empty());

            assertThat(command().doCall()).isZero();
        }

        assertThat(printer.getStdout()).isEmpty();
        assertThat(printer.getStderr()).isEqualTo("No running Camel integrations");
    }

    @Test
    void checksWithTheirStateRateAndSince() throws Exception {
        JsonObject context = check("context", "UP", true, true);
        context.put("details", details(10, 9, 1, "check.kind", "ALL"));
        writeStatusFile(TEST_PID, integration(context));

        assertThat(call(command())).isZero();

        String[] lines = printer.getOutput().split(System.lineSeparator());
        assertThat(lines[0]).contains("PID", "NAME", "AGE", "ID", "RL", "STATUS", "RATE", "SINCE", "MESSAGE");
        assertThat(lines[1]).contains("12345", "myApp", "camel/context", " RL ", "UP", "10/9/1", "5s/");
    }

    @Test
    void jsonOfTheChecks() throws Exception {
        JsonObject context = check("context", "UP", true, true);
        context.put("details", details(10, 9, 1, "check.kind", "ALL"));
        writeStatusFile(TEST_PID, integration(context));

        assertThat(call(command("--json"))).isZero();

        JsonArray checks = Jsoner.deserialize(printer.getOutput(), (JsonArray) null);
        assertThat(checks).hasSize(1);
        JsonObject check = checks.getMap(0);
        assertThat(check.getLong("pid")).isEqualTo(TEST_PID);
        assertThat(check.getString("id")).isEqualTo("camel/context");
        assertThat(check.getString("status")).isEqualTo("UP");
        assertThat(check.getBoolean("readiness")).isTrue();
        assertThat(check.getBoolean("liveness")).isTrue();
        assertThat(check.getLong("total")).isEqualTo(10);
        assertThat(check.getLong("success")).isEqualTo(9);
        assertThat(check.getLong("failure")).isEqualTo(1);
        assertThat(check.getString("sinceLast")).isNotBlank();
    }

    @Test
    void theRoutesAndConsumersThatAreUpOnlyAtFullLevel() throws Exception {
        writeStatusFile(TEST_PID, integration(
                check("context", "UP", true, true),
                check("route:orders", "UP", true, false),
                check("consumer:orders", "UP", true, false),
                check("route:payments", "DOWN", true, false)));

        assertThat(call(command("--json"))).isZero();
        assertThat(ids()).containsExactly("camel/context", "camel/route:payments");

        printer = new StringPrinter();
        assertThat(call(command("--level=full", "--json"))).isZero();
        assertThat(ids()).containsExactly("camel/context", "camel/route:orders", "camel/consumer:orders",
                "camel/route:payments");
    }

    @Test
    void unknownLevelIsAUsageError() {
        ListHealth command = command("--level=all");

        assertThatThrownBy(command::doCall)
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.USAGE))
                .hasMessage("--level must be full or default, was: all");
    }

    @Test
    void downShowsTheChecksThatAreDown() throws Exception {
        writeStatusFile(TEST_PID, integration(
                check("context", "UP", true, true),
                check("route:payments", "DOWN", true, false)));

        assertThat(call(command("--down", "--json"))).isZero();

        assertThat(ids()).containsExactly("camel/route:payments");
    }

    @Test
    void liveAndReadyByTheKindOfTheCheck() throws Exception {
        // readiness and liveness, but the kind of the check says which one it is
        JsonObject ready = check("ready-only", "UP", true, true);
        ready.put("details", details(1, 1, 0, "check.kind", "READINESS"));
        JsonObject live = check("live-only", "UP", true, true);
        live.put("details", details(1, 1, 0, "check.kind", "LIVENESS"));
        writeStatusFile(TEST_PID, integration(ready, live));

        assertThat(call(command("--live", "--json"))).isZero();
        assertThat(ids()).containsExactly("camel/live-only");

        printer = new StringPrinter();
        assertThat(call(command("--ready", "--json"))).isZero();
        assertThat(ids()).containsExactly("camel/ready-only");
    }

    @Test
    void traceShowsTheStackTraceAndTheDetailsOfTheCheck() throws Exception {
        writeStatusFile(TEST_PID, integration(failedKafkaCheck()));

        assertThat(call(command("--trace"))).isZero();

        assertThat(printer.getOutput()).contains("STACK-TRACE", "CHECK-ID: camel/consumer:kafka", "STATE: DOWN",
                "METADATA:", "bootstrap.servers = localhost:9092", "MESSAGE: Broker not available",
                "org.apache.kafka.common.errors.TimeoutException: Broker not available",
                "at org.apache.kafka.Client.poll(Client.java:10)", "at org.apache.kafka.Client.run(Client.java:20)");
    }

    @Test
    void depthLimitsTheStackTrace() throws Exception {
        writeStatusFile(TEST_PID, integration(failedKafkaCheck()));

        assertThat(call(command("--trace", "--depth=2"))).isZero();

        assertThat(printer.getOutput()).contains("at org.apache.kafka.Client.poll(Client.java:10)")
                .doesNotContain("at org.apache.kafka.Client.run(Client.java:20)");
    }

    @Test
    void traceAsJson() throws Exception {
        writeStatusFile(TEST_PID, integration(failedKafkaCheck()));

        assertThat(call(command("--trace", "--json"))).isZero();

        JsonArray checks = Jsoner.deserialize(printer.getOutput(), (JsonArray) null);
        JsonObject check = checks.getMap(0);
        assertThat(check.getString("message")).isEqualTo("Broker not available");
        List<String> stackTrace = check.getCollection("stackTrace");
        assertThat(stackTrace).hasSize(3);
        Map<String, Object> metadata = check.getMap("metadata");
        assertThat(metadata).containsEntry("bootstrap.servers", "localhost:9092");
    }

    private List<String> ids() {
        JsonArray checks = Jsoner.deserialize(printer.getOutput(), (JsonArray) null);
        return checks.stream().map(c -> ((JsonObject) c).getString("id")).toList();
    }

    private ListHealth command(String... args) {
        ListHealth command = new ListHealth(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command, args);
        return command;
    }

    // runs the command with the integration of the status file of the test
    private static int call(ListHealth command) throws Exception {
        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));
            return command.doCall();
        }
    }

    private static JsonObject integration(JsonObject... checks) {
        JsonObject health = new JsonObject();
        health.put("up", true);
        health.put("checks", new JsonArray(Arrays.asList(checks)));
        JsonObject root = new JsonObject();
        root.put("context", new JsonObject(Map.of("name", "myApp")));
        root.put("healthChecks", health);
        return root;
    }

    // as the health dev console has them
    private static JsonObject check(String id, String state, boolean readiness, boolean liveness) {
        JsonObject check = new JsonObject();
        check.put("id", id);
        check.put("group", "camel");
        check.put("up", "UP".equals(state));
        check.put("state", state);
        check.put("enabled", true);
        check.put("readiness", readiness);
        check.put("liveness", liveness);
        return check;
    }

    // the details are text, the times as the health checks print them
    private static JsonObject details(int total, int success, int failure, String... more) {
        String fiveSecondsAgo = ZonedDateTime.now().minusSeconds(5).format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("invocation.count", String.valueOf(total));
        details.put("invocation.time", fiveSecondsAgo);
        details.put("success.count", String.valueOf(success));
        details.put("failure.count", String.valueOf(failure));
        for (int i = 0; i < more.length; i += 2) {
            details.put(more[i], more[i + 1]);
        }
        return new JsonObject(details);
    }

    private static JsonObject failedKafkaCheck() {
        JsonObject check = check("consumer:kafka", "DOWN", true, false);
        check.put("message", "Broker not available");
        check.put("stackTrace", new JsonArray(
                List.of(
                        "org.apache.kafka.common.errors.TimeoutException: Broker not available",
                        "\tat org.apache.kafka.Client.poll(Client.java:10)",
                        "\tat org.apache.kafka.Client.run(Client.java:20)")));
        check.put("details", details(3, 0, 3, "bootstrap.servers", "localhost:9092"));
        return check;
    }
}
