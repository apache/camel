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
import java.nio.file.Path;
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
 * camel get error: the errors the integrations recorded, filtered, and with their details.
 */
@ExtendWith(MockitoExtension.class)
class ListErrorTest extends ProcessCommandTestSupport {

    private static final long OTHER_PID = 23456L;

    @Test
    void testEmptyOutputWhenNoErrors() throws Exception {
        writeStatusFile(TEST_PID, buildContextStatus("myApp", 5));
        // no error file written

        assertThat(call(command())).isZero();

        assertThat(printer.getOutput()).isEmpty();
    }

    @Test
    void testShowsErrorRouteId() throws Exception {
        writeStatusFile(TEST_PID, buildContextStatus("myApp", 5));
        writeErrorFile(TEST_PID, errorEntry("myRoute", "abc-123", false, 0));

        assertThat(call(command())).isZero();

        assertThat(printer.getOutput()).contains("myApp", "myRoute", "abc-123", "RuntimeException", "Test error");
    }

    @Test
    void testJsonOutput() throws Exception {
        writeStatusFile(TEST_PID, buildContextStatus("myApp", 5));
        writeErrorFile(TEST_PID, errorEntry("myRoute", "abc-123", false, 0));

        assertThat(call(command("--json"))).isZero();

        JsonArray errors = Jsoner.deserialize(printer.getOutput(), (JsonArray) null);
        assertThat(errors).hasSize(1);
        JsonObject error = errors.getMap(0);
        assertThat(error.getString("routeId")).isEqualTo("myRoute");
    }

    @Test
    void jsonWithoutErrorsIsAnEmptyArray() throws Exception {
        writeStatusFile(TEST_PID, buildContextStatus("myApp", 5));

        assertThat(call(command("--json"))).isZero();

        assertThat(printer.getOutput()).isEqualTo("[]");
    }

    @Test
    void filterByRoute() throws Exception {
        writeTwoErrors();

        assertThat(call(command("--route=orders"))).isZero();

        assertThat(printer.getOutput()).contains("ex-1").doesNotContain("ex-2");
    }

    @Test
    void filterByHandledInAnyCase() throws Exception {
        writeTwoErrors();

        assertThat(call(command("--handled=FALSE"))).isZero();

        assertThat(printer.getOutput()).contains("ex-2").doesNotContain("ex-1");
    }

    @Test
    void filterByExchangeAndException() throws Exception {
        writeTwoErrors();

        assertThat(call(command("--id=ex-1", "--exception=runtime"))).isZero();

        assertThat(printer.getOutput()).contains("ex-1").doesNotContain("ex-2");
    }

    @Test
    void agoShowsTheRecentErrors() throws Exception {
        writeStatusFile(TEST_PID, buildContextStatus("myApp", 5));
        writeErrorFile(TEST_PID, errorEntry("orders", "ex-1", false, 5), errorEntry("orders", "ex-2", false, 600));

        assertThat(call(command("--ago=1m"))).isZero();

        assertThat(printer.getOutput()).contains("ex-1").doesNotContain("ex-2");
    }

    @Test
    void handledThatIsNotABooleanIsAUsageError() {
        // before the integrations are read
        ListError command = command("--handled=yes");

        assertThatThrownBy(command::doCall)
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.USAGE))
                .hasMessage("--handled must be true or false, was: yes");
    }

    @Test
    void agoThatIsNotADurationIsAUsageError() {
        ListError command = command("--ago=recently");

        assertThatThrownBy(command::doCall)
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.USAGE))
                .hasMessage("--ago must be a duration such as 60s, 5m or 1h, was: recently");
    }

    @Test
    void limitShowsTheFirstErrors() throws Exception {
        writeStatusFile(TEST_PID, buildContextStatus("myApp", 5));
        writeErrorFile(TEST_PID, errorEntry("orders", "ex-1", false, 0), errorEntry("orders", "ex-2", false, 10));

        assertThat(call(command("--limit=1"))).isZero();

        assertThat(printer.getOutput()).contains("ex-1").doesNotContain("ex-2");
    }

    @Test
    void lastShowsTheNewestErrorOfAnyIntegration() throws Exception {
        writeStatusFile(TEST_PID, buildContextStatus("orders", 5));
        writeStatusFile(OTHER_PID, buildContextStatus("payments", 5));
        writeErrorFile(TEST_PID, errorEntry("orders", "ex-old", false, 60));
        writeErrorFile(OTHER_PID, errorEntry("payments", "ex-new", false, 1));

        assertThat(call(command("--last"))).isZero();

        // with the details of the error
        assertThat(printer.getOutput()).contains("ex-new", "Hello", "History", "payments[log1]")
                .doesNotContain("ex-old");
    }

    @Test
    void showTheBodyAndHistoryOfTheErrors() throws Exception {
        writeStatusFile(TEST_PID, buildContextStatus("myApp", 5));
        writeErrorFile(TEST_PID, errorEntry("orders", "ex-1", false, 0));

        assertThat(call(command("--show=body,history"))).isZero();

        assertThat(printer.getOutput()).contains("Hello", "History", "orders[log1]");
    }

    @Test
    void diagramWithoutMessageHistoryIsAnError() throws Exception {
        writeStatusFile(TEST_PID, buildContextStatus("myApp", 5));
        JsonObject error = errorEntry("orders", "ex-1", false, 0);
        error.remove("messageHistory");
        writeErrorFile(TEST_PID, error);

        assertThat(call(command("--diagram"))).isEqualTo(CliExitCode.FAILURE);

        assertThat(printer.getStderr()).contains("No message history available for this error");
    }

    @Test
    void unknownSortIsAUsageError() {
        ListError command = command("--sort=size");

        assertThatThrownBy(command::doCall)
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.USAGE));
    }

    private ListError command(String... args) {
        ListError command = new ListError(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command, Stream.concat(Stream.of("--logging-color=false"), Arrays.stream(args))
                .toArray(String[]::new));
        return command;
    }

    // runs the command with the integrations of the status files of the test
    private static int call(ListError command) throws Exception {
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

    private static void writeTwoErrors() throws Exception {
        writeStatusFile(TEST_PID, buildContextStatus("myApp", 5));
        // newest first, as the error registry lists them
        writeErrorFile(TEST_PID, errorEntry("orders", "ex-1", true, 0), errorEntry("payments", "ex-2", false, 10));
    }

    private static void writeErrorFile(long pid, JsonObject... errors) throws Exception {
        JsonObject root = new JsonObject();
        root.put("errors", new JsonArray(Arrays.asList(errors)));
        Path f = CommandLineHelper.getCamelDir().resolve(pid + "-error.json");
        Files.writeString(f, root.toJson());
    }

    private static JsonObject errorEntry(String routeId, String exchangeId, boolean handled, long secondsAgo) {
        JsonObject body = new JsonObject();
        body.put("type", "java.lang.String");
        body.put("value", "Hello");
        JsonObject e = new JsonObject();
        e.put("routeId", routeId);
        e.put("nodeId", "log1");
        e.put("exchangeId", exchangeId);
        e.put("handled", handled);
        e.put("timestamp", System.currentTimeMillis() - secondsAgo * 1000);
        e.put("exception", new JsonObject(Map.of("type", "java.lang.RuntimeException", "message", "Test error")));
        e.put("message", new JsonObject(Map.of("body", body)));
        e.put("messageHistory", new JsonArray(List.of(routeId + "[from1]", routeId + "[log1]")));
        return e;
    }
}
