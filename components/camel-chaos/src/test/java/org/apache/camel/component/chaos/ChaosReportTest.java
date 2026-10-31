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
package org.apache.camel.component.chaos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The report: the seed, the rules and what they did, the counts per route, the failures the consumer keeps, the
 * expectations and the verdict. The time of the expectations runs on a virtual clock (it is static, so this class runs
 * alone).
 */
@Isolated
class ChaosReportTest extends ChaosTestSupport {

    @TempDir
    Path dir;

    private FakeClock clock;

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();
        clock = FakeClock.install();
    }

    @Override
    protected void cleanupResources() throws Exception {
        clock.close();
        super.cleanupResources();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:orders").routeId("orders").to("mock:kafka").setHeader("done", constant(true)).id("done");
            }
        };
    }

    private void send(int n) {
        for (int i = 0; i < n; i++) {
            template.send("direct:orders", e -> e.getMessage().setBody("order"));
        }
    }

    @Test
    void withoutRules() {
        ChaosReport report = chaos().report();
        assertThat(report.getSeed()).isEqualTo(42);
        assertThat(report.toText()).contains("\n  seed: 42 (camel.chaos.seed)\n  rules: none");
        assertThat((JsonArray) report.toJson().get("rules")).isEmpty();
    }

    @Test
    void theRulesAndWhatTheyDid() {
        String send = chaos().addRule("endpoint=mock:kafka error rate=every:2");
        String step = chaos().addRule("step=done delay duration=1ms until=1");
        send(4);

        assertThat(chaos().report().toText())
                .contains("seed: 42 (camel.chaos.seed)")
                .contains(send + " active: endpoint=mock:kafka error rate=every:2")
                .contains("matches 4, fired 2 (send 2), skipped 0, ends in ")
                .contains(step + " ended: step=done delay duration=1ms until=1")
                .contains("matches 1, fired 1 (step 1), skipped 0\n");

        JsonObject json = chaos().report().toJson();
        assertThat(json.getLong("seed")).isEqualTo(42);
        assertThat(json.getBoolean("seedGiven")).isTrue();
        JsonObject first = (JsonObject) ((JsonArray) json.get("rules")).get(0);
        assertThat(first.getString("id")).isEqualTo(send);
        assertThat(first.getString("state")).isEqualTo("active");
        assertThat((JsonArray) first.get("hooks")).containsExactly("send", "poll");
        assertThat(first.getLong("matches")).isEqualTo(4);
        assertThat(((JsonObject) first.get("fired")).getLong("send")).isEqualTo(2);
        assertThat(first.getLong("endsIn")).isPositive();
    }

    @Test
    void removedAndReplacedRulesStayInTheReport() {
        String removed = chaos().addRule("endpoint=mock:kafka drop");
        String changed = chaos().addRule("endpoint=mock:kafka error");
        chaos().changeRule(changed, "endpoint=mock:other error");
        chaos().removeRule(removed);

        assertThat(chaos().report().toText())
                .contains(changed + " v2 active: endpoint=mock:other error")
                .contains(changed + " replaced: endpoint=mock:kafka error")
                .contains(removed + " removed: endpoint=mock:kafka drop");
    }

    @Test
    void theEntriesOfAChangedRuleDifferByVersion() {
        String id = chaos().addRule("endpoint=mock:kafka error");
        send(1);
        chaos().changeRule(id, "endpoint=mock:kafka drop");
        send(2);
        chaos().changeRule(id, "endpoint=mock:other error");

        List<JsonObject> entries = new ArrayList<>();
        for (Object o : (JsonArray) chaos().report().toJson().get("rules")) {
            JsonObject rule = (JsonObject) o;
            if (id.equals(rule.getString("id"))) {
                entries.add(rule);
            }
        }
        // the active entry first, then the replaced ones in the order they were replaced
        assertThat(entries).extracting(e -> e.getInteger("version")).containsExactly(3, 1, 2);
        assertThat(entries).extracting(e -> e.getString("state")).containsExactly("active", "replaced", "replaced");
        assertThat(entries).extracting(e -> e.getLong("matches")).containsExactly(0L, 1L, 2L);
        assertThat(chaos().report().toText())
                .contains(id + " v3 active: endpoint=mock:other error")
                .contains(id + " replaced: endpoint=mock:kafka error")
                .contains(id + " v2 replaced: endpoint=mock:kafka drop");
        // a rule that was never changed is version 1
        assertThat(ruleReport(chaos().addRule("endpoint=nothing error")).getInteger("version")).isEqualTo(1);
    }

    @Test
    void anEndedRuleLeavesTheHooksAtTheNextChangeAndStaysInTheReport() {
        String once = chaos().addRule("endpoint=mock:kafka error until=1 expect=orders:3 within=1m");
        String timed = chaos().addRule("step=done delay duration=1ms until=10s");
        send(1);
        clock.advance(10_001);
        // ended, but no change of the rules yet: still in the snapshot, where the hooks skip them
        assertThat(chaos().ruleSet().send).extracting(r -> r.id).containsExactly(once);
        assertThat(chaos().ruleSet().step).extracting(r -> r.id).containsExactly(timed);

        String other = chaos().addRule("endpoint=nothing error");

        assertThat(chaos().ruleSet().send).extracting(r -> r.id).containsExactly(other);
        assertThat(chaos().ruleSet().step).isEmpty();
        assertThat(ruleReport(once).getString("state")).isEqualTo("ended");
        assertThat(ruleReport(timed).getString("state")).isEqualTo("ended");
        // the expectation of the ended rule is not decided yet, so it still counts the completed exchanges
        assertThat(chaos().ruleSet().expectations).hasSize(1);
        send(3);
        JsonObject expectation = (JsonObject) ruleReport(once).get("expectation");
        assertThat(expectation.getString("outcome")).isEqualTo("met");
        assertThat(expectation.getLong("completed")).isEqualTo(3);
        assertThat(ruleReport(once).getLong("matches")).isEqualTo(1);
    }

    @Test
    void aDecidedExpectationLeavesTheSnapshotAtTheNextChange() {
        String met = chaos().addRule("endpoint=nothing error expect=orders:2 within=1m");
        String unmet = chaos().addRule("endpoint=nothing error expect=orders:100 within=10s");
        send(2);
        clock.advance(10_001);
        assertThat(chaos().ruleSet().expectations).hasSize(2);

        chaos().addRule("endpoint=nothing error");

        assertThat(chaos().ruleSet().expectations).isEmpty();
        assertThat(chaos().ruleSet().expectationRules).extracting(r -> r.id).containsExactly(met, unmet);
        send(1);
        assertThat(((JsonObject) ruleReport(met).get("expectation")).getString("outcome")).isEqualTo("met");
        assertThat(((JsonObject) ruleReport(met).get("expectation")).getLong("completed")).isEqualTo(2);
        assertThat(((JsonObject) ruleReport(unmet).get("expectation")).getString("outcome")).isEqualTo("unmet");
        assertThat(chaos().report().getLost()).isEqualTo(98);
    }

    @Test
    void theHistoryKeepsTheLastRules() {
        for (int i = 0; i < ChaosManager.MAX_HISTORY + 5; i++) {
            chaos().removeRule(chaos().addRule("endpoint=nothing" + i + " error"));
        }
        JsonArray rules = (JsonArray) chaos().report().toJson().get("rules");
        assertThat(rules).hasSize(ChaosManager.MAX_HISTORY);
        assertThat(((JsonObject) rules.get(0)).getString("id")).isEqualTo("r6");
    }

    @Test
    void withoutRulesNothingIsCounted() {
        send(3);
        assertThat((JsonArray) chaos().report().toJson().get("routes")).isEmpty();
        assertThat(chaos().report().getVerdict()).isEqualTo(ChaosReport.PASS);
        assertThat(chaos().report().isFinal()).isTrue();
    }

    @Test
    void countsPerRouteWhileThereAreRules() {
        chaos().addRule("endpoint=mock:kafka error rate=every:2");
        send(4);
        JsonObject orders = routeReport("orders");
        assertThat(orders.getLong("completed")).isEqualTo(2);
        assertThat(orders.getLong("failed")).isEqualTo(2);
        // a direct consumer does not keep a failed message
        assertThat(orders.getLong("failedKeptByConsumer")).isZero();
        // failures without an expectation: PASS, nothing lost
        ChaosReport report = chaos().report();
        assertThat(report.getVerdict()).isEqualTo(ChaosReport.PASS);
        assertThat(report.getLost()).isZero();
        assertThat(report.getSeed()).isEqualTo(42);
        assertThat(report.toText())
                .contains("Chaos report: PASS")
                .contains("seed: 42 (camel.chaos.seed)")
                .contains("r1 active: endpoint=mock:kafka error rate=every:2")
                .contains("matches 4, fired 2 (send 2), skipped 0")
                .contains("orders: completed 2, failed 2 (kept by the consumer 0), handled 0, redeliveries 0")
                .contains("lost: 0");
    }

    @Test
    void aFileThatFailsIsKeptByTheConsumerUnlessMoveFailed() throws Exception {
        chaos().addRule("endpoint=nothing error");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("file:" + dir.resolve("kept") + "?initialDelay=0&delay=10").routeId("kept")
                        .throwException(new IllegalStateException("cannot read"));
                from("file:" + dir.resolve("moved") + "?initialDelay=0&delay=10&moveFailed=.error").routeId("moved")
                        .throwException(new IllegalStateException("cannot read"));
            }
        });
        Files.createDirectories(dir.resolve("kept"));
        Files.createDirectories(dir.resolve("moved"));
        Files.writeString(dir.resolve("kept").resolve("a.txt"), "a");
        Files.writeString(dir.resolve("moved").resolve("b.txt"), "b");

        // the kept file is read again at each poll, the moved one only once
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> routeCount("kept", "failed") >= 2 && routeCount("moved", "failed") >= 1);

        JsonObject kept = routeReport("kept");
        assertThat(kept.getLong("failedKeptByConsumer")).isEqualTo(kept.getLong("failed"));
        JsonObject moved = routeReport("moved");
        assertThat(moved.getLong("failed")).isEqualTo(1);
        assertThat(moved.getLong("failedKeptByConsumer")).isZero();
    }

    @Test
    void aFileMarkedRollbackOnlyFailsButIsNotKeptByTheConsumer() throws Exception {
        // the file consumer looks at the exception only: it commits (moves) a file whose exchange is rollback only
        chaos().addRule("endpoint=nothing error");
        Files.createDirectories(dir.resolve("rollback"));
        Files.writeString(dir.resolve("rollback").resolve("c.txt"), "c");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("file:" + dir.resolve("rollback") + "?initialDelay=0&delay=10").routeId("rollback")
                        .markRollbackOnly();
            }
        });

        await().atMost(20, TimeUnit.SECONDS).until(() -> !Files.exists(dir.resolve("rollback").resolve("c.txt"))
                && routeCount("rollback", "failed") >= 1);

        JsonObject rollback = routeReport("rollback");
        assertThat(rollback.getLong("failed")).isEqualTo(1);
        assertThat(rollback.getLong("completed")).isZero();
        assertThat(rollback.getLong("failedKeptByConsumer")).isZero();
    }

    @Test
    void expectationMet() {
        String id = chaos().addRule("endpoint=mock:kafka error rate=every:2 expect=orders:3 within=1m");
        send(5);
        JsonObject expectation = (JsonObject) ruleReport(id).get("expectation");
        assertThat(expectation.getString("outcome")).isEqualTo("met");
        assertThat(expectation.getLong("completed")).isEqualTo(3);
        assertThat(chaos().report().getVerdict()).isEqualTo(ChaosReport.PASS);
        assertThat(chaos().report().isFinal()).isTrue();
    }

    @Test
    void expectationPendingThenUnmet() {
        String id = chaos().addRule("endpoint=mock:kafka error rate=every:2 expect=orders:10 within=1m");
        send(4);
        ChaosReport pending = chaos().report();
        assertThat(pending.getVerdict()).isEqualTo(ChaosReport.PASS);
        assertThat(pending.isFinal()).isFalse();
        JsonObject expectation = (JsonObject) ruleReport(id).get("expectation");
        assertThat(expectation.getString("outcome")).isEqualTo("pending");
        assertThat(expectation.getLong("completed")).isEqualTo(2);
        assertThat(expectation.getLong("timeLeft")).isEqualTo(60_000L);

        clock.advance(60_001);
        // a completion after the time is up does not count
        send(2);
        ChaosReport unmet = chaos().report();
        assertThat(unmet.getVerdict()).isEqualTo(ChaosReport.FAIL);
        assertThat(unmet.isFinal()).isTrue();
        assertThat(unmet.getLost()).isEqualTo(8);
        assertThat(unmet.getReasons()).containsExactly(
                "route orders completed 2 of 10 exchanges within 1m0s (rule " + id + "): 8 lost");
        assertThat(unmet.toText()).contains("FAIL: route orders completed 2 of 10");
    }

    @Test
    void expectationOfARemovedRuleStaysPending() {
        String id = chaos().addRule("endpoint=mock:kafka error rate=every:2 expect=orders:10 within=1m");
        send(2);
        chaos().removeRule(id);
        clock.advance(TimeUnit.MINUTES.toMillis(5));
        send(10);

        ChaosReport report = chaos().report();
        assertThat(report.getVerdict()).isEqualTo(ChaosReport.PASS);
        assertThat(report.isFinal()).isFalse();
        JsonObject expectation = (JsonObject) ruleReport(id).get("expectation");
        assertThat(expectation.getString("outcome")).isEqualTo("pending");
        assertThat(expectation.getBoolean("notDecided")).isTrue();
        assertThat(expectation.getLong("completed")).isEqualTo(1);
        assertThat(report.toText()).contains("(not decided: the rule was removed or changed, or the context stopped, first)");
    }

    @Test
    void expectationOfAnEndedRuleIsStillDecided() {
        // the rule ends after 2 matches, the expectation goes on for its whole time
        String id = chaos().addRule("endpoint=mock:kafka error until=2 expect=orders:3 within=1m");
        send(5);
        assertThat(ruleReport(id).getString("state")).isEqualTo("ended");
        JsonObject expectation = (JsonObject) ruleReport(id).get("expectation");
        assertThat(expectation.getString("outcome")).isEqualTo("met");
    }

    @Test
    void anUnmetExpectationStaysInTheVerdictAfterItsRuleLeftTheReport() {
        String id = chaos().addRule("endpoint=nothing error expect=orders:5 within=1m");
        clock.advance(60_001);
        chaos().removeRule(id);
        for (int i = 0; i < ChaosManager.MAX_HISTORY; i++) {
            chaos().removeRule(chaos().addRule("endpoint=nothing" + i + " error"));
        }
        ChaosReport report = chaos().report();
        assertThat(report.toText()).doesNotContain(id + " removed");
        assertThat(report.getVerdict()).isEqualTo(ChaosReport.FAIL);
        assertThat(report.getLost()).isEqualTo(5);
        assertThat(report.getReasons())
                .containsExactly("1 unmet expectations of rules that are no longer in the report: 5 lost");
    }

    @Test
    void anExpectationStaysPendingWhenTheContextStopsFirst() {
        String id = chaos().addRule("endpoint=nothing error expect=orders:5 within=1m");
        send(2);
        context.stop();
        // the time is up after the stop: exchanges that finished while the context stopped were not counted
        clock.advance(TimeUnit.MINUTES.toMillis(2));

        ChaosReport report = chaos().report();
        JsonObject expectation = (JsonObject) ruleReport(id).get("expectation");
        assertThat(expectation.getString("outcome")).isEqualTo("pending");
        assertThat(expectation.getLong("completed")).isEqualTo(2);
        assertThat(report.getVerdict()).isEqualTo(ChaosReport.PASS);
        assertThat(report.isFinal()).isFalse();
        assertThat(report.getLost()).isZero();
    }

    @Test
    void aPendingExpectationThatLeftTheReportKeepsItNotFinal() {
        String id = chaos().addRule("endpoint=nothing error expect=orders:5 within=1m");
        chaos().removeRule(id);
        for (int i = 0; i < ChaosManager.MAX_HISTORY; i++) {
            chaos().removeRule(chaos().addRule("endpoint=nothing" + i + " error"));
        }
        ChaosReport report = chaos().report();
        assertThat(report.toText()).doesNotContain(id + " removed");
        assertThat(report.getVerdict()).isEqualTo(ChaosReport.PASS);
        assertThat(report.isFinal()).isFalse();
    }
}
