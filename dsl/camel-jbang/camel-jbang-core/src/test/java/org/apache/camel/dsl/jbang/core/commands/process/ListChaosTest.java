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
        // below the rule, what it does in plain words
        assertThat(out).contains("fail half of the sends to kafka:orders*");
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
        assertThat(row.getString("description")).isEqualTo("fail half of the sends to kafka:orders*");
        assertThat(row.getLong("endsIn")).isEqualTo(30000);
        JsonArray hooks = row.getCollection("hooks");
        assertThat(hooks).containsExactly("send", "step");
        assertThat(((JsonObject) row.get("expectation")).getString("outcome")).isEqualTo("unmet");
        assertThat(row).doesNotContainKey("lifecycle");
    }

    @Test
    void lifecycleRuleAsJson() throws Exception {
        JsonObject chaos = chaosOn();
        JsonObject stall = rule(1, "ended", "route=poller stall duration=20s");
        stall.put("id", "r2");
        JsonObject lifecycle = new JsonObject();
        lifecycle.put("action", "suspend");
        lifecycle.put("route", "poller");
        lifecycle.put("duration", 20000L);
        lifecycle.put("state", "comingBack");
        lifecycle.put("downAt", 4L);
        stall.put("lifecycle", lifecycle);
        ((JsonArray) chaos.get("rules")).add(stall);
        writeStatus(chaos);

        assertThat(run("--json")).isZero();

        JsonArray rows = (JsonArray) Jsoner.deserialize(printer.getStdout());
        JsonObject row = (JsonObject) rows.get(1);
        assertThat(row.getString("id")).isEqualTo("r2");
        JsonObject copied = (JsonObject) row.get("lifecycle");
        assertThat(copied.getString("action")).isEqualTo("suspend");
        assertThat(copied.getString("route")).isEqualTo("poller");
        assertThat(copied.getString("state")).isEqualTo("comingBack");
        assertThat(copied.getLong("downAt")).isEqualTo(4);
    }

    @Test
    void theLiveStateOfTheRules() throws Exception {
        writeStatus(withLiveState(chaosOn()));

        assertThat(run()).isZero();

        String out = printer.getStdout();
        assertThat(out).contains("RATE").contains("LAST").contains("EFFECT");
        assertThat(lineOf(out, "r1")).contains("40% of 50%").contains("3s ago")
                .contains("2 recovered, 1 handled, 1 in flight");
    }

    @Test
    void theLiveStateAsJson() throws Exception {
        writeStatus(withLiveState(chaosOn()));

        assertThat(run("--json")).isZero();

        JsonObject row = (JsonObject) ((JsonArray) Jsoner.deserialize(printer.getStdout())).get(0);
        assertThat(row.getDouble("rate")).isEqualTo(0.5d);
        assertThat(row.getDouble("observedRate")).isEqualTo(0.4d);
        assertThat(row.getLong("lastFiredAgo")).isEqualTo(3000);
        assertThat(row.getLong("faultsLastMinute")).isEqualTo(5);
        assertThat(((JsonObject) row.get("effect")).getLong("inFlight")).isEqualTo(1);
    }

    @Test
    void theRecentFaultsInTheReport() throws Exception {
        writeStatus(withLiveState(chaosOn()));

        assertThat(run("--report")).isZero();

        String out = printer.getStdout();
        assertThat(out).contains("recent faults:").contains("FAULT").contains("EXCHANGE");
        assertThat(lineOf(out, "ABC-1")).contains("2s").contains("r1").contains("error ChaosException").contains("send")
                .contains("orders").contains("kafka://orders");
        assertThat(lineOf(out, "r1  ")).contains("2 recovered, 1 handled, 1 in flight");
    }

    @Test
    void withoutLiveStateTheColumnsAreLeftOut() throws Exception {
        writeStatus(chaosOn());

        assertThat(run()).isZero();

        // a rule of an older camel-chaos has no live state
        assertThat(printer.getStdout()).doesNotContain("RATE").doesNotContain("EFFECT");
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
        assertThat(out).doesNotContain("stub");
    }

    @Test
    void aRouteWhoseConsumerIsAStub() throws Exception {
        JsonObject chaos = chaosOn();
        JsonObject route = (JsonObject) ((JsonArray) ((JsonObject) chaos.get("report")).get("routes")).get(0);
        route.put("stubbedConsumer", "kafka");
        writeStatus(chaos);

        assertThat(run("--report")).isZero();

        String out = printer.getStdout();
        assertThat(lineOf(out, "orders (stub of kafka)")).containsPattern("97\\s+6\\s+2\\s+1\\s+4");
        assertThat(out).contains("a stub does not read a kept message again, as the component it stands in for would");
    }

    @Test
    void theExplorationInTheReport() throws Exception {
        JsonObject chaos = chaosOn();
        JsonObject exploration = new JsonObject();
        exploration.put("running", true);
        exploration.put("budget", 600_000L);
        exploration.put("timeLeft", 372_000L);
        exploration.put("focus", "orders");
        exploration.put("rules", new JsonArray(List.of("r1", "r2")));
        exploration.put("baselineAt", 1_700_000_030_000L);
        JsonObject orders = new JsonObject();
        orders.put("route", "orders");
        orders.put("completed", 30L);
        orders.put("failed", 0L);
        exploration.put("baseline", new JsonArray(List.of(orders)));
        JsonObject experiment = new JsonObject();
        experiment.put("number", 1);
        experiment.put("hypothesis", "an order never fails");
        experiment.put("open", false);
        experiment.put("endedBy", "ended");
        experiment.put("elapsed", 64_000L);
        experiment.put("rules", new JsonArray(List.of("r1", "r2")));
        experiment.put("verdict", "FAIL");
        JsonObject expectation = new JsonObject();
        expectation.put("expect", "orders:failed<=0");
        expectation.put("state", "unmet");
        expectation.put("value", "9 failed");
        experiment.put("expectations", new JsonArray(List.of(expectation)));
        JsonObject during = new JsonObject();
        during.put("route", "orders");
        during.put("completed", 41L);
        during.put("failed", 9L);
        experiment.put("routes", new JsonArray(List.of(during)));
        experiment.put("note", "orders has no error handler");
        experiment.put("lostAtDeadLetterChannel", 58L);
        JsonObject alsoFailed = new JsonObject();
        alsoFailed.put("refunds", 2L);
        experiment.put("alsoFailed", alsoFailed);
        // its fault never fired
        JsonObject second = new JsonObject();
        second.put("number", 2);
        second.put("open", false);
        second.put("endedBy", "ended");
        second.put("elapsed", 41_000L);
        second.put("rules", new JsonArray(List.of("r3")));
        second.put("verdict", "INCONCLUSIVE");
        second.put("reason", "its faults never fired, so its expectations say nothing about them");
        second.put("expectations", new JsonArray());
        exploration.put("experiments", new JsonArray(List.of(experiment, second)));
        ((JsonObject) chaos.get("report")).put("exploration", exploration);
        writeStatus(chaos);

        assertThat(run("--report")).isZero();

        String out = printer.getStdout();
        assertThat(out).contains("exploration: running, 6m12s left of 10m0s, focus: orders",
                "rules tried: r1, r2", "baseline (before the first rule): orders 30 completed, 0 failed",
                "experiments:", "1 FAIL  an order never fails (r1, r2, ended after 1m4s)",
                "expect orders:failed<=0: unmet, 9 failed",
                "lost at the dead letter channel: 58 messages, handled there without being delivered",
                "also failed, in no expectation: refunds 2",
                "routes: orders 41 completed, 9 failed",
                "note: orders has no error handler", "2 INCONCLUSIVE",
                "inconclusive: its faults never fired, so its expectations say nothing about them");
    }

    @Test
    void whatHasBeenTriedInTheReport() throws Exception {
        JsonObject chaos = chaosOn();
        JsonObject kind = new JsonObject();
        kind.put("kind", "errors");
        kind.put("rules", new JsonArray(List.of("r1 v2")));
        kind.put("fired", 24L);
        kind.put("recovered", 21L);
        kind.put("handled", 1L);
        kind.put("failed", 0L);
        kind.put("expectations", new JsonArray(List.of("orders:100 unmet")));
        JsonObject route = new JsonObject();
        route.put("route", "orders");
        route.put("kinds", new JsonArray(List.of(kind)));
        route.put("notTried", new JsonArray(List.of("slow", "duplicate")));
        ((JsonObject) chaos.get("report")).put("tried", new JsonArray(List.of(route)));
        writeStatus(chaos);

        assertThat(run("--report")).isZero();

        String out = printer.getStdout();
        assertThat(out).contains("tried, per route:").contains("OUTCOME");
        assertThat(lineOf(out, "r1 v2")).contains("orders").contains("errors").contains("24")
                .contains("21 recovered, 1 handled, 0 failed; expect orders:100 unmet");
        assertThat(lineOf(out, "not tried yet")).contains("slow, duplicate");
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
    void reportShowsACancelledExpectation() throws Exception {
        JsonObject chaos = chaosOn();
        JsonObject report = (JsonObject) chaos.get("report");
        report.put("verdict", "PASS");
        report.put("final", false);
        report.put("lost", 0);
        report.put("reasons", new JsonArray());
        JsonArray rules = report.getCollection("rules");
        // the replaced version has its expectation cancelled, the current one has time left
        JsonObject replaced = (JsonObject) rules.get(1);
        replaced.put("expectation", new JsonObject(
                Map.of("route", "orders", "count", 10, "within", 60000, "completed", 4, "outcome", "cancelled")));
        JsonObject current = (JsonObject) rules.get(0);
        current.put("expectation", new JsonObject(
                Map.of("route", "orders", "count", 100, "within", 60000,
                        "completed", 2, "outcome", "pending", "timeLeft", 30000L)));
        writeStatus(chaos);

        assertThat(run("--report")).isZero();

        String out = printer.getStdout();
        assertThat(out).contains("PASS (not final: an expectation is pending)");
        assertThat(out).contains("orders:10 cancelled (4 completed)");
        assertThat(out).contains("orders:100 pending (2 completed, 30s left)");
    }

    @Test
    void theNoteSaysWhatALifecycleRuleDidAndWhyARuleWasSkipped() throws Exception {
        JsonObject chaos = chaosOn();
        JsonObject stall = rule(1, "active", "route=poller stall duration=20s");
        stall.put("id", "r2");
        JsonObject lifecycle = new JsonObject();
        lifecycle.put("action", "suspend");
        lifecycle.put("route", "poller");
        lifecycle.put("state", "down");
        lifecycle.put("backIn", 12000L);
        stall.put("lifecycle", lifecycle);
        JsonObject corrupt = rule(1, "active", "route=shipping corrupt mode=charset");
        corrupt.put("id", "r3");
        corrupt.put("skippedReasons", new JsonObject(Map.of("a stream body", 79L)));
        JsonArray rules = chaos.getCollection("rules");
        rules.add(stall);
        rules.add(corrupt);
        writeStatus(chaos);

        assertThat(run()).isZero();

        String out = printer.getStdout();
        assertThat(out).contains("NOTE");
        assertThat(lineOf(out, "r2")).contains("route poller down, back in 12s");
        assertThat(lineOf(out, "r3")).contains("skipped 79: a stream body");
    }

    @Test
    void theNoteSaysWhenARuleStartsItsBurstsAndTheRoundOfAFlappingRoute() throws Exception {
        JsonObject chaos = chaosOn();
        JsonObject waiting = rule(1, "waiting", "endpoint=mock:x drop from=+30s");
        waiting.put("id", "r2");
        waiting.put("startsIn", 25000L);
        JsonObject burst = rule(1, "active", "endpoint=mock:x error rate=burst:5s/30s");
        burst.put("id", "r3");
        burst.put("nextBurstIn", 12000L);
        JsonObject flap = rule(1, "active", "route=poller stall duration=10s repeat=3 every=1m");
        flap.put("id", "r4");
        JsonObject lifecycle = new JsonObject();
        lifecycle.put("action", "suspend");
        lifecycle.put("route", "poller");
        lifecycle.put("state", "pending");
        lifecycle.put("repeat", 3);
        lifecycle.put("every", 60000L);
        lifecycle.put("round", 1);
        lifecycle.put("actsIn", 40000L);
        flap.put("lifecycle", lifecycle);
        JsonArray rules = chaos.getCollection("rules");
        rules.add(waiting);
        rules.add(burst);
        rules.add(flap);
        writeStatus(chaos);

        assertThat(run()).isZero();

        String out = printer.getStdout();
        assertThat(lineOf(out, "r2")).contains("starts in 25s");
        assertThat(lineOf(out, "r3")).contains("next burst in 12s");
        // the note wraps in its column
        assertThat(lineOf(out, "r4")).contains("route poller pending (round 1 of 3),");
        assertThat(out).contains("acts in 40s");
    }

    @Test
    void withoutANoteThereIsNoNoteColumn() throws Exception {
        writeStatus(chaosOn());

        assertThat(run()).isZero();

        assertThat(printer.getStdout()).doesNotContain("NOTE");
    }

    @Test
    void noRulesSaysSo() throws Exception {
        JsonObject chaos = chaosOn();
        chaos.put("rules", new JsonArray());
        writeStatus(chaos);

        assertThat(run()).isZero();

        assertThat(printer.getStdout()).isEmpty();
        assertThat(printer.getStderr()).contains("orders (pid " + TEST_PID + "): no chaos rules");
    }

    @Test
    void reportShowsTheKindAndOpOfAnExpectation() throws Exception {
        JsonObject chaos = chaosOn();
        JsonObject report = (JsonObject) chaos.get("report");
        JsonObject rule = (JsonObject) ((JsonArray) report.getCollection("rules")).get(0);
        rule.put("expectation", new JsonObject(
                Map.of("route", "orders", "kind", "handled", "op", "<=", "count", 0, "within", 60000, "value", 3,
                        "outcome", "unmet")));
        writeStatus(chaos);

        assertThat(run("--report")).isZero();

        assertThat(printer.getStdout()).contains("orders:handled<=0 unmet (3 handled)");
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

        // nothing on stdout, and why on stderr
        assertThat(printer.getStdout()).isEmpty();
        assertThat(printer.getStderr()).contains("No running integration has camel-chaos: run it with camel run --chaos");
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
        current.put("description", "fail half of the sends to kafka:orders*");
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

    /** The live state of rule r1, as camel-chaos adds it to its rules and its report. */
    private static JsonObject withLiveState(JsonObject chaos) {
        JsonObject rule = (JsonObject) ((JsonArray) chaos.get("rules")).get(0);
        rule.put("rate", 0.5d);
        rule.put("observedRate", 0.4d);
        rule.put("lastFiredAgo", 3000L);
        rule.put("faultsLastMinute", 5L);
        JsonObject effect = new JsonObject();
        effect.put("faulted", 4L);
        effect.put("completed", 0L);
        effect.put("recovered", 2L);
        effect.put("handled", 1L);
        effect.put("failed", 0L);
        effect.put("inFlight", 1L);
        rule.put("effect", effect);
        JsonObject fault = new JsonObject();
        fault.put("timestamp", 1_000_000L);
        fault.put("ago", 2000L);
        fault.put("rule", "r1");
        fault.put("version", 2);
        fault.put("fault", "error ChaosException");
        fault.put("hook", "send");
        fault.put("route", "orders");
        fault.put("endpoint", "kafka://orders");
        fault.put("exchangeId", "ABC-1");
        ((JsonObject) chaos.get("report")).put("recentFaults", new JsonArray(List.of(fault)));
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
