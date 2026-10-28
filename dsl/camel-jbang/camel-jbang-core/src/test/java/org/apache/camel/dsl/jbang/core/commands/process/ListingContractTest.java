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

import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

/**
 * The listings of camel get, top and ps: a status file they cannot read does not break the listing of the other
 * integrations, a name that matches nothing is not found, and the JSON has numbers as numbers.
 */
class ListingContractTest extends ProcessCommandTestSupport {

    private static final long OTHER_PID = 23456L;

    @Test
    void statusThatCannotBeReadIsSkipped() throws Exception {
        writeStatusFile(TEST_PID, buildContextStatus("good", 5));
        // an older or partial status file: no routes
        JsonObject broken = buildContextStatus("broken", 5);
        broken.remove("routes");
        writeStatusFile(OTHER_PID, broken);

        CamelContextStatus command = new CamelContextStatus(new CamelJBangMain().withPrinter(printer));
        command.jsonOutput = true;

        assertThat(call(command::doCall, TEST_PID, OTHER_PID)).isZero();

        JsonArray rows = Jsoner.deserialize(printer.getStdout(), (JsonArray) null);
        assertThat(rows.stream().map(r -> ((JsonObject) r).getString("name"))).containsExactly("good");
        assertThat(printer.getStderr()).startsWith("WARN: Skipped the integration with pid " + OTHER_PID);
    }

    @Test
    void nameThatMatchesNothingIsNotFound() throws Exception {
        writeStatusFile(TEST_PID, buildContextStatus("orders", 5));

        CamelRouteStatus command = new CamelRouteStatus(new CamelJBangMain().withPrinter(printer));
        command.name = "billing";

        assertThatThrownBy(() -> call(command::doCall, TEST_PID))
                .isInstanceOf(CliException.class)
                .hasMessage("No running Camel integration matches: billing")
                .extracting(e -> ((CliException) e).getExitCode()).isEqualTo(CliExitCode.NOT_FOUND);
    }

    @Test
    void contextJsonHasNumbers() throws Exception {
        JsonObject root = buildContextStatus("orders", 5);
        JsonObject stats = ((JsonObject) root.get("context")).getMap("statistics");
        stats.put("exchangesTotal", "12");
        stats.put("exchangesFailed", "2");
        stats.put("exchangesInflight", "1");
        stats.put("exchangesThroughput", "0.25");
        root.put("routes", new JsonArray(List.of(route("a", "Started"), route("b", "Stopped"))));
        writeStatusFile(TEST_PID, root);

        CamelContextStatus command = new CamelContextStatus(new CamelJBangMain().withPrinter(printer));
        command.jsonOutput = true;

        assertThat(call(command::doCall, TEST_PID)).isZero();

        JsonArray rows = Jsoner.deserialize(printer.getStdout(), (JsonArray) null);
        JsonObject row = rows.getMap(0);
        // numbers, not text
        assertThat(row.get("pid")).isInstanceOf(Number.class);
        assertThat(row.getLong("pid")).isEqualTo(TEST_PID);
        assertThat(row.getLong("total")).isEqualTo(12L);
        assertThat(row.getLong("failed")).isEqualTo(2L);
        assertThat(row.getLong("inflight")).isEqualTo(1L);
        assertThat(row.getDouble("throughput")).isEqualTo(0.25d);
        assertThat(row.getInteger("routesStarted")).isEqualTo(1);
        assertThat(row.getInteger("routesTotal")).isEqualTo(2);
        assertThat(row.getBoolean("ready")).isTrue();
        assertThat(row).containsKey("uptime").doesNotContainKey("route").doesNotContainKey("sinceLast");
    }

    @Test
    void psJsonHasNumbersAndFailed() throws Exception {
        writeStatusFile(TEST_PID, buildContextStatus("orders", 5));

        ListProcess command = new ListProcess(new CamelJBangMain().withPrinter(printer));
        command.jsonOutput = true;

        assertThat(call(command::doCall, TEST_PID)).isZero();

        JsonObject row = Jsoner.deserialize(printer.getStdout(), (JsonArray) null).getMap(0);
        assertThat(row.getLong("pid")).isEqualTo(TEST_PID);
        assertThat(row.getBoolean("ready")).isTrue();
        assertThat(row).containsKey("failed").containsKey("uptime").doesNotContainKey("fail");
    }

    @Test
    void dirtyFileOfAnotherPidIsNotProtected() {
        assertThat(Dirty.pidOf("123-status.json")).isEqualTo(123L);
        assertThat(Dirty.pidOf("12")).isEqualTo(12L);
    }

    private static JsonObject route(String routeId, String state) {
        JsonObject route = new JsonObject();
        route.put("routeId", routeId);
        route.put("state", state);
        return route;
    }

    private int call(Callable<Integer> command, long... pids) throws Exception {
        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            ProcessHandle[] handles = new ProcessHandle[pids.length];
            for (int i = 0; i < pids.length; i++) {
                handles[i] = mockProcessHandle(pids[i]);
            }
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(handles));
            return command.call();
        }
    }
}
