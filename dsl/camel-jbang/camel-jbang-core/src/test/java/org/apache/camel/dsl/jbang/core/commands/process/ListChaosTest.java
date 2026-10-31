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
 * camel get chaos: the current rules of each integration from the chaos dev console in its status, or the report with
 * --report, as a table or as JSON; and why chaos is off.
 */
@ExtendWith(MockitoExtension.class)
class ListChaosTest extends ProcessCommandTestSupport {

    @Test
    void rules() throws Exception {
        writeStatus(chaosOn());

        assertThat(run()).isZero();

        String out = printer.getStdout();
        assertThat(out).contains("ID").contains("VER").contains("STATE").contains("FIRED").contains("ENDS IN");
        assertThat(out).contains("r1").contains("active").contains("endpoint=kafka:orders* error rate=0.5");
        // the replaced version is only in the report
        assertThat(out).doesNotContain("replaced").doesNotContain("rate=0.1");
        // fired: 4 at the sends + 1 at the steps
        assertThat(lineOf(out, "r1")).containsPattern("\\b10\\s+5\\s+5\\b");
    }

    @Test
    void rulesAsJson() throws Exception {
        writeStatus(chaosOn());

        assertThat(run("--json")).isZero();

        JsonArray rows = (JsonArray) Jsoner.deserialize(printer.getStdout());
        assertThat(rows).hasSize(1);
        JsonObject row = (JsonObject) rows.get(0);
        assertThat(row.getString("name")).isEqualTo("orders");
        assertThat(row.getLong("pid")).isEqualTo(TEST_PID);
        assertThat(row.getString("id")).isEqualTo("r1");
        assertThat(row.getInteger("version")).isEqualTo(2);
        assertThat(row.getString("state")).isEqualTo("active");
        assertThat(row.getLong("endsIn")).isEqualTo(30000);
        JsonArray hooks = row.getCollection("hooks");
        assertThat(hooks).containsExactly("send", "step");
        assertThat(((JsonObject) row.get("expectation")).getString("outcome")).isEqualTo("unmet");
    }

    @Test
    void report() throws Exception {
        writeStatus(chaosOn());

        assertThat(run("--report")).isZero();

        String out = printer.getStdout();
        assertThat(out).contains("orders (pid " + TEST_PID + "): FAIL");
        assertThat(out).contains("seed: 42 (camel.chaos.seed)").contains("lost: 3");
        assertThat(out).contains("FAIL: route orders completed 97 of 100 exchanges within 1m (rule r1): 3 lost");
        // every version of the rule, and the counts per route
        assertThat(out).contains("replaced").contains("rate=0.1").contains("orders:100 unmet (97 completed)");
        assertThat(out).contains("COMPLETED").contains("REDELIVERIES");
        assertThat(lineOf(out, "orders  ")).containsPattern("97\\s+6\\s+2\\s+1\\s+4");
    }

    @Test
    void reportAsJson() throws Exception {
        writeStatus(chaosOn());

        assertThat(run("--report", "--json")).isZero();

        JsonArray rows = (JsonArray) Jsoner.deserialize(printer.getStdout());
        JsonObject report = (JsonObject) ((JsonObject) rows.get(0)).get("report");
        assertThat(report.getString("verdict")).isEqualTo("FAIL");
        JsonArray rules = report.getCollection("rules");
        assertThat(rules).hasSize(2);
    }

    @Test
    void reportSaysWhyAnExpectationIsNotDecided() throws Exception {
        JsonObject chaos = chaosOn();
        JsonObject report = (JsonObject) chaos.get("report");
        report.put("verdict", "PASS");
        report.put("final", false);
        report.put("lost", 0);
        report.put("reasons", new JsonArray());
        JsonArray rules = report.getCollection("rules");
        // the replaced version keeps its expectation pending for good, the current one has time left
        JsonObject replaced = (JsonObject) rules.get(1);
        replaced.put("expectation", new JsonObject(
                Map.of("route", "orders", "count", 10, "within", 60000, "completed", 4,
                        "outcome", "pending", "notDecided", true)));
        JsonObject current = (JsonObject) rules.get(0);
        current.put("expectation", new JsonObject(
                Map.of("route", "orders", "count", 100, "within", 60000,
                        "completed", 2, "outcome", "pending", "timeLeft", 30000L)));
        writeStatus(chaos);

        assertThat(run("--report")).isZero();

        String out = printer.getStdout();
        assertThat(out).contains("PASS (not final: an expectation is pending)");
        assertThat(out).contains("orders:10 pending (4 completed, not decided)");
        assertThat(out).contains("orders:100 pending (2 completed, 30s left)");
    }

    @Test
    void chaosOff() throws Exception {
        writeStatus(new JsonObject(Map.of("enabled", false, "reason", "Chaos is off: it stays off with profile prod")));

        assertThat(run()).isZero();

        assertThat(printer.getStdout()).isEmpty();
        assertThat(printer.getStderr()).contains("orders (pid " + TEST_PID + "): Chaos is off: it stays off with profile prod");
    }

    @Test
    void chaosOffReport() throws Exception {
        writeStatus(new JsonObject(Map.of("enabled", false, "reason", "Chaos is off")));

        assertThat(run("--report", "--json")).isZero();

        assertThat((JsonArray) Jsoner.deserialize(printer.getStdout())).isEmpty();
    }

    @Test
    void withoutCamelChaos() throws Exception {
        JsonObject root = new JsonObject();
        root.put("context", new JsonObject(Map.of("name", "orders")));
        writeStatusFile(TEST_PID, root);

        assertThat(run()).isZero();

        assertThat(printer.getOutput()).isEmpty();
    }

    private int run(String... args) throws Exception {
        ListChaos command = new ListChaos(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command, args);
        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));
            return command.doCall();
        }
    }

    private static String lineOf(String out, String text) {
        return out.lines().filter(l -> l.contains(text)).findFirst().orElseThrow();
    }

    private static void writeStatus(JsonObject chaos) throws Exception {
        JsonObject root = new JsonObject();
        root.put("context", new JsonObject(Map.of("name", "orders")));
        root.put("chaos", chaos);
        writeStatusFile(TEST_PID, root);
    }

    /** The chaos dev console of an integration with rule r1 in its second version, as camel-chaos makes it. */
    private static JsonObject chaosOn() {
        JsonObject current = rule(2, "active", "endpoint=kafka:orders* error rate=0.5 expect=orders:100 within=1m");
        current.put("endsIn", 30000L);
        JsonObject replaced = rule(1, "replaced", "endpoint=kafka:orders* error rate=0.1");
        JsonObject expectation = new JsonObject();
        expectation.put("route", "orders");
        expectation.put("count", 100);
        expectation.put("within", 60000);
        expectation.put("completed", 97);
        expectation.put("outcome", "unmet");
        current.put("expectation", expectation);
        JsonObject route = new JsonObject();
        route.put("route", "orders");
        route.put("completed", 97);
        route.put("failed", 6);
        route.put("failedKeptByConsumer", 2);
        route.put("handled", 1);
        route.put("redeliveries", 4);
        JsonObject report = new JsonObject();
        report.put("verdict", "FAIL");
        report.put("final", true);
        report.put("seed", 42L);
        report.put("seedGiven", true);
        report.put("lost", 3);
        report.put("reasons", new JsonArray(
                List.of("route orders completed 97 of 100 exchanges within 1m (rule r1): 3 lost")));
        report.put("rules", new JsonArray(List.of(current, replaced)));
        report.put("routes", new JsonArray(List.of(route)));
        JsonObject chaos = new JsonObject();
        chaos.put("enabled", true);
        chaos.put("rules", new JsonArray(List.of(current)));
        chaos.put("report", report);
        return chaos;
    }

    private static JsonObject rule(int version, String state, String text) {
        JsonObject rule = new JsonObject();
        rule.put("id", "r1");
        rule.put("version", version);
        rule.put("rule", text);
        rule.put("state", state);
        rule.put("hooks", new JsonArray(List.of("send", "step")));
        rule.put("matches", 10L);
        rule.put("fired", new JsonObject(Map.of("send", 4L, "step", 1L)));
        rule.put("skipped", 5L);
        return rule;
    }
}
