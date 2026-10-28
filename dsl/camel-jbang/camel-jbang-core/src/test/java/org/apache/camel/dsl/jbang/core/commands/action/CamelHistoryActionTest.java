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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

/**
 * camel get history: the message history of the last completed exchange of the integrations.
 */
@ExtendWith(MockitoExtension.class)
class CamelHistoryActionTest extends ActionCommandTestSupport {

    private static final long FIXED_TIMESTAMP = 1_700_000_000_000L;
    private static final long OTHER_PID = 23456L;

    @Test
    void testRendersMessageHistoryFromHistoryFile() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        // CamelHistoryAction reads a pre-existing <pid>-history.json, not the action/output round trip
        writeMessageHistoryFile(TEST_PID, singleTraceLine());

        assertThat(callWithSingleProcess(command("myApp"))).isZero();

        assertThat(printer.getOutput()).contains("Message History of last completed", "myApp", "ABCDEFGH-0001");
    }

    @Test
    void testJsonOutputIsTheRecordedHistory() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        writeMessageHistoryFile(TEST_PID, singleTraceLine());

        assertThat(callWithSingleProcess(command("myApp", "--json"))).isZero();

        assertThat(printer.getOutput()).contains("\"traces\"", "ABCDEFGH-0001")
                .doesNotContain("Message History of last completed");
    }

    @Test
    void testRendersNothingWhenNameDoesNotMatch() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        writeMessageHistoryFile(TEST_PID, singleTraceLine());

        CamelHistoryAction command = command("doesNotExist");

        assertThatThrownBy(() -> callWithSingleProcess(command))
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.NOT_FOUND))
                .hasMessage("No running Camel integration matches: doesNotExist");
        assertThat(printer.getOutput()).as("nothing should be rendered when no process matches").isEmpty();
    }

    @Test
    void theStepsOfTheExchange() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        writeMessageHistoryFile(TEST_PID, exchange(false));

        assertThat(callWithSingleProcess(command("myApp"))).isZero();

        String out = printer.getOutput();
        assertThat(out).contains("status:success", "from[timer://tick]", "log[Hello]", "to[nested]",
                "split[body]", "Success");
        assertThat(out).doesNotContain("Stacktrace");
    }

    @Test
    void depthHidesTheNestedSteps() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        writeMessageHistoryFile(TEST_PID, exchange(false));

        assertThat(callWithSingleProcess(command("myApp", "--depth=1"))).isZero();

        assertThat(printer.getOutput()).contains("log[Hello]").doesNotContain("to[nested]");
    }

    @Test
    void depthZeroShowsTheStartAndEndOnly() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        writeMessageHistoryFile(TEST_PID, exchange(false));

        assertThat(callWithSingleProcess(command("myApp", "--depth=0"))).isZero();

        assertThat(printer.getOutput()).contains("from[timer://tick]").doesNotContain("log[Hello]");
    }

    @Test
    void limitSplitShowsTheFirstSplitMessages() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        writeMessageHistoryFile(TEST_PID, exchange(false));

        assertThat(callWithSingleProcess(command("myApp", "--limit-split=1"))).isZero();

        assertThat(printer.getOutput()).contains("to[split0]").doesNotContain("to[split1]");
    }

    @Test
    void sourceShowsTheLocationOfTheSteps() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        writeMessageHistoryFile(TEST_PID, exchange(false));

        assertThat(callWithSingleProcess(command("myApp", "--source"))).isZero();

        assertThat(printer.getOutput()).contains("route.yaml:12");
    }

    @Test
    void maskHidesTheSecretsOfTheEndpoints() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        writeMessageHistoryFile(TEST_PID, exchange(false));

        assertThat(callWithSingleProcess(command("myApp", "--mask"))).isZero();

        // the masked password is cut in the column, as it is longer than the password
        assertThat(printer.getOutput()).contains("to[q?password=x").doesNotContain("password=b");
    }

    @Test
    void maskHidesTheSecretsInTheJsonToo() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        writeMessageHistoryFile(TEST_PID, exchange(false));

        assertThat(callWithSingleProcess(command("myApp", "--json", "--mask"))).isZero();

        assertThat(printer.getOutput()).contains("password=xxxxxx").doesNotContain("password=b");
    }

    @Test
    void theStackTraceOfAFailedExchange() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        writeMessageHistoryFile(TEST_PID, exchange(true));

        assertThat(callWithSingleProcess(command("myApp"))).isZero();

        String out = printer.getOutput();
        assertThat(out).contains("status:failed", "Exception: Forced error", "Stacktrace",
                "java.lang.IllegalArgumentException: Forced error");
    }

    @Test
    void aFailedStepWithoutItsException() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        writeMessageHistoryFile(TEST_PID, exchange(true));

        assertThat(callWithSingleProcess(command("myApp", "--show-exception=false"))).isZero();

        assertThat(printer.getOutput()).contains("status:failed", "Failed").doesNotContain("Stacktrace");
    }

    @Test
    void diagramOfSeveralIntegrationsIsAUsageError() throws Exception {
        writeStatusFile(TEST_PID, "orders");
        writeStatusFile(OTHER_PID, "payments");
        writeMessageHistoryFile(TEST_PID, exchange(false));
        Files.writeString(CommandLineHelper.getCamelDir().resolve(OTHER_PID + "-history.json"), exchange(false).toJson());
        CamelHistoryAction command = command("--diagram");

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            List<ProcessHandle> handles = List.of(mockProcessHandle(TEST_PID), mockProcessHandle(OTHER_PID));
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> handles.stream());

            assertThatThrownBy(command::doCall)
                    .isInstanceOfSatisfying(CliException.class,
                            e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.USAGE))
                    .hasMessageContaining("--diagram shows the history of one integration, and 2 integrations have one");
        }
    }

    private CamelHistoryAction command(String... args) {
        CamelHistoryAction command = new CamelHistoryAction(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command,
                Stream.concat(Stream.of("--logging-color=false"), Arrays.stream(args)).toArray(String[]::new));
        return command;
    }

    private static JsonObject singleTraceLine() {
        JsonObject message = new JsonObject();
        message.put("exchangePattern", "InOnly");
        message.put("exchangeId", "ABCDEFGH-0001");
        message.put("body", new JsonObject());

        JsonObject trace = new JsonObject();
        trace.put("uid", 1);
        trace.put("first", true);
        trace.put("last", true);
        trace.put("routeId", "myRoute");
        trace.put("fromRouteId", "myRoute");
        trace.put("nodeId", "to1");
        trace.put("nodeShortName", "to");
        trace.put("nodeLabel", "to[mock://out]");
        trace.put("timestamp", FIXED_TIMESTAMP);
        trace.put("elapsed", 5);
        trace.put("failed", false);
        trace.put("done", true);
        trace.put("threadName", "main");
        trace.put("exchangeId", "ABCDEFGH-0001");
        trace.put("message", message);
        JsonArray traces = new JsonArray();
        traces.add(trace);

        JsonObject line = new JsonObject();
        line.put("name", "myApp");
        line.put("traces", traces);
        return line;
    }

    // the history of an exchange: from, a log, a nested step, two split messages, an endpoint with a secret, the end
    private static JsonObject exchange(boolean failed) {
        List<JsonObject> traces = new ArrayList<>();
        traces.add(flag(step(1, "from1", "from", null, 1, "timer://tick", null), "first"));
        traces.add(step(2, "log1", "log", "log[Hello]", 1, null, null));
        traces.add(step(3, "to2", "to", "to[nested]", 2, null, null));
        traces.add(step(4, "split1", "split", "split[body]", 1, null, null));
        traces.add(step(5, "to3", "to", "to[split0]", 2, null, 0));
        traces.add(step(6, "to3", "to", "to[split1]", 2, null, 1));
        traces.add(step(7, "to4", "to", "to[q?password=b]", 1, null, null));
        if (failed) {
            JsonObject exception = new JsonObject();
            exception.put("type", "java.lang.IllegalArgumentException");
            exception.put("message", "Forced error");
            exception.put("stackTrace", "java.lang.IllegalArgumentException: Forced error\n\tat org.example.Route");
            JsonObject failure = step(8, "throw1", "throwException", "throwException[IllegalArgumentException]", 1,
                    null, null);
            failure.put("failed", true);
            failure.put("exception", exception);
            traces.add(failure);
            JsonObject last = flag(step(9, "from1", "from", null, 1, "timer://tick", null), "last");
            last.put("failed", true);
            last.put("exception", exception);
            traces.add(last);
        } else {
            traces.add(flag(step(8, "from1", "from", null, 1, "timer://tick", null), "last"));
        }
        JsonObject line = new JsonObject();
        line.put("name", "myApp");
        line.put("traces", new JsonArray(traces));
        return line;
    }

    // the first or last step of the route
    private static JsonObject flag(JsonObject step, String flag) {
        step.put(flag, true);
        return step;
    }

    private static JsonObject step(
            long uid, String nodeId, String shortName, String label, int level, String endpointUri, Integer splitIndex) {
        JsonObject message = new JsonObject();
        message.put("exchangePattern", "InOnly");
        message.put("exchangeId", "ABCDEFGH-0001");
        message.put("body", new JsonObject(Map.of("type", "java.lang.String", "value", "Hello")));
        if (splitIndex != null) {
            message.put("exchangeProperties", new JsonArray(
                    List.of(
                            new JsonObject(
                                    Map.of("key", "CamelSplitIndex", "type", "java.lang.Integer", "value", splitIndex)))));
        }
        JsonObject trace = new JsonObject();
        trace.put("uid", uid);
        trace.put("first", false);
        trace.put("last", false);
        trace.put("location", "route.yaml:" + (10 + uid));
        trace.put("routeId", "myRoute");
        trace.put("fromRouteId", "myRoute");
        trace.put("nodeId", nodeId);
        trace.put("nodeShortName", shortName);
        trace.put("nodeLabel", label);
        trace.put("nodeLevel", level);
        if (endpointUri != null) {
            trace.put("endpointUri", endpointUri);
            trace.put("remoteEndpoint", false);
        }
        trace.put("timestamp", FIXED_TIMESTAMP + uid);
        trace.put("elapsed", uid);
        trace.put("failed", false);
        trace.put("done", true);
        trace.put("threadName", "Camel (myApp) thread #1 - timer://tick");
        trace.put("exchangeId", "ABCDEFGH-0001");
        trace.put("message", message);
        return trace;
    }
}
