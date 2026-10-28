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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
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
 * camel trace: the status of tracing in the integrations, starting, stopping and clearing it, and the traced messages.
 */
@ExtendWith(MockitoExtension.class)
class CamelTraceActionTest extends ActionCommandTestSupport {

    @Test
    void statusAsJson() throws Exception {
        writeStatusFile(TEST_PID, status(true, false));

        assertThat(callWithSingleProcess(command("--json"))).isZero();

        JsonArray rows = Jsoner.deserialize(printer.getStdout(), (JsonArray) null);
        JsonObject row = rows.getMap(0);
        assertThat(row.getString("name")).isEqualTo("orders");
        assertThat(row.getString("status")).isEqualTo("Started");
        assertThat(row.getLong("total")).isEqualTo(7L);
        assertThat(row.getLong("queue")).isEqualTo(2L);
        assertThat(row.getString("pattern")).isEqualTo("route*");
    }

    @Test
    void statusOfStandbyTracing() throws Exception {
        writeStatusFile(TEST_PID, status(false, true));

        assertThat(callWithSingleProcess(command())).isZero();

        assertThat(printer.getStdout()).contains("Standby");
    }

    @Test
    void statusOfStoppedTracing() throws Exception {
        writeStatusFile(TEST_PID, status(false, false));

        assertThat(callWithSingleProcess(command())).isZero();

        assertThat(printer.getStdout()).contains("Stopped");
    }

    @Test
    void startAndStopSendTheAction() throws Exception {
        writeStatusFile(TEST_PID, status(false, false));

        assertThat(callWithSingleProcess(command("--action=start"))).isZero();
        assertThat(readActionFile(TEST_PID).getString("action")).isEqualTo("trace");
        assertThat(readActionFile(TEST_PID).getString("enabled")).isEqualTo("true");

        assertThat(callWithSingleProcess(command("--action=stop"))).isZero();
        assertThat(readActionFile(TEST_PID).getString("enabled")).isEqualTo("false");
    }

    @Test
    void clearEmptiesTheTraceFile() throws Exception {
        writeStatusFile(TEST_PID, status(true, false));
        Path traceFile = CommandLineHelper.getCamelDir().resolve(TEST_PID + "-trace.json");
        Files.writeString(traceFile, "{\"traces\":[{\"uid\":1}]}");

        assertThat(callWithSingleProcess(command("--action=clear"))).isZero();

        assertThat(Files.readString(traceFile)).isEqualTo("{}");
    }

    @Test
    void statusWithAnUnknownSortIsAUsageError() throws Exception {
        writeStatusFile(TEST_PID, status(true, false));

        CamelTraceAction command = command("--sort=size");

        assertThatThrownBy(() -> callWithSingleProcess(command))
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.USAGE))
                .hasMessageContaining("--sort must be one of pid, name, age");
    }

    @Test
    void dumpPrintsTheTracedMessages() throws Exception {
        writeStatusFile(TEST_PID, status(true, false));
        writeTraces(exchange());

        assertThat(callWithSingleProcess(dump())).isZero();

        String out = printer.getOutput();
        assertThat(out).contains("Created", "route1/*-->", "route1/log1", "Processed", "Completed (success)",
                "Hello Camel", "orderId");
        assertThat(out).as("the elapsed time of a step").contains(" (5ms)");
    }

    @Test
    void dumpWithColorsKeepsAllTheOutputWithThePrinter() throws Exception {
        writeStatusFile(TEST_PID, status(true, false));
        writeTraces(exchange());

        assertThat(callWithSingleProcess(dump("--logging-color=true"))).isZero();

        // the timestamp, pid, thread and node of each trace are colored, and printed with the printer of the command
        String out = printer.getOutput().replaceAll("\u001B\\[[;\\d]*m", "");
        assertThat(out).contains(Long.toString(TEST_PID), "[", "route1/log1", "Completed (success)", "(5ms)");
    }

    @Test
    void dumpTheSourceLocationInsteadOfTheIds() throws Exception {
        writeStatusFile(TEST_PID, status(true, false));
        writeTraces(exchange());

        assertThat(callWithSingleProcess(dump("--source"))).isZero();

        assertThat(printer.getOutput()).contains("route.yaml:3").doesNotContain("route1/log1");
    }

    @Test
    void dumpOnlyCreatedAndCompletedAtDepthZero() throws Exception {
        writeStatusFile(TEST_PID, status(true, false));
        writeTraces(exchange());

        assertThat(callWithSingleProcess(dump("--depth=0"))).isZero();

        assertThat(printer.getOutput()).contains("Created", "Completed (success)").doesNotContain("route1/log1");
    }

    @Test
    void dumpTheMatchingTraces() throws Exception {
        writeStatusFile(TEST_PID, status(true, false));
        writeTraces(exchange());

        assertThat(callWithSingleProcess(dump("--grep=world"))).isZero();

        // the message of the step after the log matches
        assertThat(printer.getOutput()).contains("route1/log1").doesNotContain("Created", "Completed");
    }

    @Test
    void dumpTheLastTraces() throws Exception {
        writeStatusFile(TEST_PID, status(true, false));
        writeTraces(exchange());

        assertThat(callWithSingleProcess(dump("--tail=1"))).isZero();

        assertThat(printer.getOutput()).contains("Completed (success)").doesNotContain("Created", "route1/log1");
    }

    @Test
    void dumpOfANameThatMatchesNoIntegrationIsNotFound() throws Exception {
        writeStatusFile(TEST_PID, status(true, false));

        CamelTraceAction command = command("payments", "--action=dump", "--follow=false");

        assertThatThrownBy(() -> callWithSingleProcess(command))
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.NOT_FOUND))
                .hasMessage("No running Camel integration matches: payments");
    }

    private CamelTraceAction dump(String... args) {
        List<String> all = new ArrayList<>(List.of("--action=dump", "--follow=false"));
        if (Arrays.stream(args).noneMatch(a -> a.startsWith("--logging-color"))) {
            all.add("--logging-color=false");
        }
        all.addAll(Arrays.asList(args));
        return command(all.toArray(String[]::new));
    }

    // the traces of an exchange, a line per step as the camel-cli-connector writes them
    private static void writeTraces(JsonObject... traces) throws Exception {
        Path f = CommandLineHelper.getCamelDir().resolve(TEST_PID + "-trace.json");
        Files.write(f, Arrays.stream(traces).map(t -> {
            JsonObject line = new JsonObject();
            line.put("traces", new JsonArray(List.of(t)));
            return line.toJson();
        }).toList());
    }

    private static JsonObject[] exchange() {
        return new JsonObject[] {
                trace(1, true, false, null, false, 0, "Hello Camel"),
                trace(2, false, false, "log1", true, 5, "Hello World"),
                trace(3, false, true, null, true, 7, "Hello Camel") };
    }

    private static JsonObject trace(
            long uid, boolean first, boolean last, String nodeId, boolean done, long elapsed, String text) {
        JsonObject header = new JsonObject();
        header.put("key", "orderId");
        header.put("type", "java.lang.String");
        header.put("value", "42");
        JsonObject body = new JsonObject();
        body.put("type", "java.lang.String");
        body.put("value", text);
        JsonObject message = new JsonObject();
        message.put("exchangeId", "A-1");
        message.put("exchangePattern", "InOnly");
        message.put("headers", new JsonArray(List.of(header)));
        message.put("body", body);
        JsonObject trace = new JsonObject();
        trace.put("uid", uid);
        trace.put("first", first);
        trace.put("last", last);
        trace.put("location", "route.yaml:" + (uid + 1));
        trace.put("routeId", "route1");
        trace.put("nodeId", nodeId);
        trace.put("timestamp", System.currentTimeMillis());
        trace.put("elapsed", elapsed);
        trace.put("failed", false);
        trace.put("done", done && !last);
        trace.put("threadName", "Camel (orders) thread #1 - timer://tick");
        trace.put("message", message);
        return trace;
    }

    private CamelTraceAction command(String... args) {
        CamelTraceAction command = new CamelTraceAction(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command, args);
        return command;
    }

    private static JsonObject status(boolean enabled, boolean standby) {
        JsonObject trace = new JsonObject();
        trace.put("enabled", enabled);
        trace.put("standby", standby);
        trace.put("counter", 7);
        trace.put("queueSize", 2);
        trace.put("traceFilter", "*");
        trace.put("tracePattern", "route*");
        JsonObject context = new JsonObject();
        context.put("name", "orders");
        JsonObject root = new JsonObject();
        root.put("context", context);
        root.put("trace", trace);
        return root;
    }
}
