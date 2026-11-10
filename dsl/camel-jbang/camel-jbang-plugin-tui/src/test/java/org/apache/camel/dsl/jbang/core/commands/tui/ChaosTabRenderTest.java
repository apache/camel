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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The chaos screen ({@link ChaosTab}) rendered from the chaos section of a status, as the chaos dev console of
 * camel-chaos writes it.
 */
class ChaosTabRenderTest {

    // the chaos section of a status: three rules (an error with an expectation, a waiting burst, a flapping stall), the
    // report with a failed expectation, the counts per route and the recent faults
    private static final String CHAOS = """
            {
              "enabled": true,
              "rules": [
                {
                  "id": "r1", "version": 1, "state": "active",
                  "rule": "endpoint=http://localhost:8765/stock* error status=503 rate=0.25 until=10m expect=orders:handled<=0",
                  "hooks": ["send"], "matches": 170, "fired": {"send": 41}, "skipped": 0, "endsIn": 432000,
                  "rate": 0.25, "observedRate": 0.2412, "lastFiredAgo": 2000, "faultsLastMinute": 12,
                  "faultsPerSecond": [0,0,1,0,0,2,0,0,1,0,0,0,1,0,0,0,0,3,0,0,1,0,0,0,0,1,0,0,0,0,
                                      0,1,0,0,0,2,0,0,0,1,0,0,0,0,1,0,0,0,0,0,1,0,0,0,0,0,0,0,1,0],
                  "effect": {"faulted": 38, "completed": 0, "recovered": 33, "handled": 2, "failed": 0, "inFlight": 3},
                  "expectation": {"route": "orders", "kind": "handled", "op": "<=", "count": 0, "within": 120000,
                                  "value": 2, "outcome": "unmet"}
                },
                {
                  "id": "r2", "version": 2, "state": "waiting",
                  "rule": "endpoint=seda:shipping* drop rate=burst:5s/30s from=+30s",
                  "hooks": ["send"], "matches": 0, "fired": {"send": 0}, "skipped": 0, "startsIn": 25000,
                  "rate": 0.1667, "faultsLastMinute": 0
                },
                {
                  "id": "r3", "version": 1, "state": "active",
                  "rule": "route=poller stall duration=20s repeat=3 every=1m",
                  "hooks": [], "matches": 1, "fired": {"lifecycle": 1}, "skipped": 0, "lastFiredAgo": 12000,
                  "faultsLastMinute": 1,
                  "lifecycle": {"action": "suspend", "route": "poller", "duration": 20000, "state": "down",
                                "repeat": 3, "every": 60000, "round": 1, "downAt": 5, "backIn": 8000}
                }
              ],
              "report": {
                "verdict": "FAIL", "final": true, "seed": 42, "seedGiven": true, "lost": 0,
                "reasons": ["route orders: 2 exchanges handled by the error handler within 2m0s, at most 0 expected (rule r1)"],
                "routes": [
                  {"route": "orders", "completed": 97, "failed": 0, "failedKeptByConsumer": 0, "handled": 2,
                   "redeliveries": 44},
                  {"route": "stock", "completed": 141, "failed": 0, "failedKeptByConsumer": 0, "handled": 0,
                   "redeliveries": 0, "stubbedConsumer": "kafka"}
                ],
                "recentFaults": [
                  {"timestamp": 1000, "ago": 2000, "rule": "r1", "version": 1, "fault": "error status 503",
                   "hook": "send", "route": "orders", "endpoint": "http://localhost:8765/stock",
                   "exchangeId": "3F2A-1E"},
                  {"timestamp": 900, "ago": 12000, "rule": "r3", "version": 1, "fault": "stall 20s",
                   "route": "poller"}
                ]
              }
            }
            """;

    private MonitorContext ctx;
    private IntegrationInfo info;

    @BeforeEach
    void setUp() throws Exception {
        Theme.resetForTesting();
        info = new IntegrationInfo();
        info.pid = "1234";
        info.name = "chaosdemo";
        AtomicReference<List<IntegrationInfo>> data = new AtomicReference<>(List.of(info));
        ctx = new MonitorContext(data, new AtomicReference<>(List.of()));
        ctx.selectedPid = "1234";
    }

    private void chaos(String json) throws Exception {
        info.chaos = ChaosInfo.parse((JsonObject) Jsoner.deserialize(json));
    }

    @Test
    void theStatusParserReadsTheChaosSection() throws Exception {
        JsonObject root = new JsonObject();
        root.put("context", new JsonObject(Map.of("name", "chaosdemo")));
        IntegrationInfo without = StatusParser.parseIntegration(ProcessHandle.current(), root);
        assertThat(without.chaos).isNull();
        root.put("chaos", Jsoner.deserialize(CHAOS));
        IntegrationInfo parsed = StatusParser.parseIntegration(ProcessHandle.current(), root);
        assertThat(parsed.chaos).isNotNull();
        assertThat(parsed.chaos.rules).hasSize(3);
        assertThat(parsed.chaos.verdict).isEqualTo("FAIL");
    }

    @Test
    void theVerdictTheRulesAndTheRoutes() throws Exception {
        chaos(CHAOS);
        String screen = TuiTestHelper.renderToString(new ChaosTab(ctx), 160, 40);

        assertThat(screen).contains("FAIL").contains("expectations 0 met · 1 unmet · 0 pending")
                .contains("faults 13 in the last minute").contains("rules 2 active · 1 waiting")
                .contains("route orders: 2 exchanges handled by the error handler");
        // the rules: target, fault, observed rate next to the rate, effect, note
        assertThat(screen).contains("http://localhost:8765/stock*").contains("error status=503")
                .contains("24% of 25%").contains("33↩").contains("2⇩").contains("ends in 7m12s");
        assertThat(screen).contains("r2 v2").contains("waiting").contains("starts in 25s");
        assertThat(screen).contains("round 1 of 3").contains("route poller").contains("down, back in 8s");
        // the routes
        assertThat(screen).contains("ROUTE").contains("orders").contains("141");
        // a route whose consumer is a stub (camel run --stub) says so
        assertThat(screen).contains("stock (stub)");
    }

    @Test
    void theSelectedRuleInDetail() throws Exception {
        chaos(CHAOS);
        String screen = TuiTestHelper.renderToString(new ChaosTab(ctx), 160, 40);

        assertThat(screen).contains("33 recovered by redelivery").contains("2 handled by the error handler")
                .contains("3 in flight").contains("38 exchanges");
        assertThat(screen).contains("orders:handled<=0").contains("UNMET");
        assertThat(screen).contains("error status 503 at send http://localhost:8765/stock").contains("3F2A-1E");
    }

    @Test
    void theFaultInAFewWordsAndTheEffectOfAPollRule() throws Exception {
        // CAMEL-26699: as the demo shop had them
        chaos("""
                {"enabled": true, "rules": [
                  {"id": "r3", "version": 1, "state": "active",
                   "rule": "endpoint=http://localhost:8765/pay* error exception=java.net.ConnectException rate=0.5",
                   "hooks": ["send"], "matches": 20, "fired": {"send": 9}, "faultsLastMinute": 9,
                   "effect": {"faulted": 9, "completed": 0, "recovered": 7, "handled": 0, "failed": 0, "inFlight": 2}},
                  {"id": "r4", "version": 1, "state": "active",
                   "rule": "route=shipping lifecycle action=stop duration=5s repeat=3 every=20s",
                   "hooks": [], "matches": 2, "fired": {"lifecycle": 2}, "faultsLastMinute": 2},
                  {"id": "r5", "version": 1, "state": "active", "rule": "endpoint=file://inbox* error at=poll rate=0.5",
                   "hooks": ["poll"], "matches": 80, "fired": {"poll": 39}, "faultsLastMinute": 30}],
                 "report": {"verdict": "PASS", "final": true, "seed": 42}}
                """);
        ChaosInfo.Rule pay = info.chaos.rule("r3");
        assertThat(pay.faultShort()).isEqualTo("error ConnectException");
        assertThat(info.chaos.rule("r4").faultShort()).isEqualTo("stop 5s ×3 every 20s");
        ChaosInfo.Rule poll = info.chaos.rule("r5");
        assertThat(poll.firedAtPoll()).isEqualTo(39);

        String screen = TuiTestHelper.renderToString(new ChaosTab(ctx), 160, 40);
        // in flight is not … , which reads as a cut text
        assertThat(screen).contains("error ConnectException").contains("stop 5s ×3 every 20s").contains("7↩ 2◷")
                .contains("39 polls failed");
    }

    @Test
    void theRecentFaultsOfAnExchangeOnceAndTheRoutesItWasIn() throws Exception {
        chaos("""
                {"enabled": true, "rules": [
                  {"id": "r1", "version": 1, "state": "active", "rule": "step=reserve error rate=0.6",
                   "hooks": ["step"], "matches": 30, "fired": {"step": 4}, "faultsLastMinute": 4,
                   "effect": {"faulted": 2, "completed": 0, "recovered": 1, "handled": 1, "failed": 0, "inFlight": 0}}],
                 "report": {"verdict": "PASS", "final": true, "seed": 42, "recentFaults": [
                   {"ago": 1000, "rule": "r1", "fault": "error ChaosException", "hook": "step", "route": "inventory",
                    "step": "reserve", "exchangeId": "98AE31B10677BEA-000000000000286B"},
                   {"ago": 1500, "rule": "r1", "fault": "error ChaosException", "hook": "step", "route": "inventory",
                    "step": "reserve", "exchangeId": "98AE31B10677BEA-000000000000286B"},
                   {"ago": 2000, "rule": "r1", "fault": "error ChaosException", "hook": "step", "route": "inventory",
                    "step": "reserve", "exchangeId": "98AE31B10677BEA-000000000000286B"},
                   {"ago": 4000, "rule": "r1", "fault": "error ChaosException", "hook": "step", "route": "inventory",
                    "step": "reserve", "exchangeId": "98AE31B10677BEA-0000000000002861"}]}}
                """);
        RouteInfo inventory = new RouteInfo();
        inventory.routeId = "inventory";
        inventory.total = 900;
        inventory.meanTime = 27;
        inventory.p95Time = 411;
        inventory.p99Time = 907;
        inventory.throughput = "1.69";
        info.routes.add(inventory);

        assertThat(ChaosTab.recentOf(info.chaos, info.chaos.rule("r1")).values()).containsExactly(3, 1);
        String screen = TuiTestHelper.renderToString(new ChaosTab(ctx), 160, 40);
        assertThat(screen).contains("98AE31…286B  ×3").contains("98AE31…2861")
                .contains("inventory  mean 27ms · p95 411ms · p99 907ms · 1.69 msg/s");
        assertThat(screen.lines().filter(l -> l.contains("…286B")).count()).isEqualTo(1);
    }

    // an exploration as camel-chaos reports it (CAMEL-26760): three experiments ended, the fourth running
    private static final String EXPLORATION
            = """
                    {"running": true, "startedAt": 1000000, "budget": 600000, "timeLeft": 300000, "focus": "refunds",
                     "experiments": [
                       {"number": 1, "hypothesis": "When payment returns 503, orders should retry", "verdict": "PASS",
                        "open": false, "startedAt": 1026000, "elapsed": 41000,
                        "ruleTexts": ["endpoint=http://localhost:8080/payment error status=503 rate=0.25 expect=orders:failed<=0 within=40s"],
                        "faults": {"r1": 11},
                        "expectations": [{"rule": "r1", "expect": "orders:failed<=0", "state": "met", "value": "0 failed"}],
                        "routes": [{"route": "orders", "completed": 41, "failed": 0}]},
                       {"number": 2, "hypothesis": "When payment returns 503, refunds should handle errors", "verdict": "FAIL",
                        "open": false, "startedAt": 1117000, "elapsed": 7000,
                        "ruleTexts": ["endpoint=http://localhost:8080/payment error status=503 rate=0.25 expect=refunds:failed<=0 within=40s"],
                        "faults": {"r2": 8},
                        "expectations": [{"rule": "r2", "expect": "refunds:failed<=0", "state": "unmet", "value": "5 failed"}],
                        "routes": [{"route": "refunds", "completed": 15, "failed": 5}],
                        "note": "refunds has no error handling"},
                       {"number": 3, "hypothesis": "HTTP clients see proper error responses", "verdict": "INCONCLUSIVE",
                        "open": false, "startedAt": 1200000, "elapsed": 41000,
                        "ruleTexts": ["route=payment error rate=0.05 expect=payment:failed<=0 within=40s"], "faults": {"r3": 0},
                        "expectations": [{"rule": "r3", "expect": "payment:failed<=0", "state": "met", "value": "0 failed"}],
                        "routes": [], "alsoFailed": {"refunds": 2},
                        "reason": "its faults never fired, so its expectations say nothing about them"},
                       {"number": 4, "hypothesis": "nothing is lost when payment and parking fail", "verdict": "PENDING",
                        "open": true, "startedAt": 1280000, "elapsed": 20000,
                        "ruleTexts": ["endpoint=direct:parked error at=dlq expect=orders:failed<=0 within=90s"],
                        "faults": {"r4": 30}, "lostAtDeadLetterChannel": 12,
                        "expectations": [{"rule": "r4", "expect": "orders:failed<=0", "state": "pending", "value": "0 failed"}],
                        "routes": []}
                     ]}
                    """;

    @Test
    void theExplorationWithItsExperiments() throws Exception {
        JsonObject chaos = (JsonObject) Jsoner.deserialize(CHAOS);
        ((JsonObject) chaos.get("report")).put("exploration", Jsoner.deserialize(EXPLORATION));
        info.chaos = ChaosInfo.parse(chaos);
        ChaosTab tab = new ChaosTab(ctx);
        // the rules first; l shows the exploration (let there be chaos)
        assertThat(TuiTestHelper.renderToString(tab, 160, 44)).contains("Rules [3]");
        tab.toggleExplore();
        String screen = TuiTestHelper.renderToString(tab, 160, 44);

        assertThat(screen).contains("Let there be chaos").contains("exploring 5m of 10m").contains("focus refunds")
                .contains("1 pass").contains("1 fail").contains("1 inconclusive").contains("1 running");
        assertThat(screen).contains("Experiments [4]").contains("0:26").contains("PASS").contains("FAIL")
                .contains("INCONCLUSIVE").contains("running").contains("refunds should handle errors")
                .contains("5 failed in refunds").contains("the faults never fired")
                .contains("12 lost at the dead letter channel");
        assertThat(screen).containsPattern("│ 0 {100,}10m");
        // the latest experiment is selected, in detail
        assertThat(screen).contains("Experiment 4").contains("endpoint=direct:parked error at=dlq")
                .contains("12 messages at the dead letter channel, handled there without being delivered");
        // the rules again
        tab.toggleExplore();
        assertThat(TuiTestHelper.renderToString(tab, 160, 44)).contains("Rules [3]").doesNotContain("Experiments [");
    }

    @Test
    void theExperimentThatCouldNotTell() throws Exception {
        ChaosInfo.Exploration ex = ChaosInfo.parse((JsonObject) Jsoner.deserialize(
                "{\"enabled\": true, \"report\": {\"exploration\": " + EXPLORATION + "}}")).exploration;

        ChaosInfo.Experiment third = ex.experiments().get(2);
        assertThat(third.alsoFailed()).containsEntry("refunds", 2L);
        assertThat(third.at()).isEqualTo(200_000);
        assertThat(ChaosExploreView.evidence(third)).isEqualTo(
                "the faults never fired; 0 failed in payment; also 2 failed in refunds");
        assertThat(ChaosExploreView.clock(third.at())).isEqualTo("3:20");
        // the strip: the experiments as bars where they ran, the time gone by, the time left
        String strip = ChaosExploreView.strip(ex, 60).rawContent();
        assertThat(strip).hasSize(61).contains("█").contains("─").contains("·");
        assertThat(strip.indexOf('█')).isEqualTo(1 + 26_000 * 60 / 600_000);
        assertThat(ex.elapsed()).isEqualTo(300_000);
    }

    @Test
    void theMessagesLostAtAHandOff() throws Exception {
        JsonObject chaos = (JsonObject) Jsoner.deserialize(CHAOS);
        ((JsonObject) chaos.get("report")).put("exploration", Jsoner.deserialize("""
                {"running": false, "finishedBy": "deadline", "startedAt": 1000000, "budget": 120000, "ran": 120000,
                 "experiments": [
                   {"number": 1, "hypothesis": "inbox: The route after a hand-off fails", "verdict": "FAIL",
                    "open": false, "startedAt": 1001000, "elapsed": 118000,
                    "ruleTexts": ["route=ship error rate=0.2 until=116s expect=inbox->ship:lost<=0 within=116s"],
                    "faults": {"r1": 45},
                    "expectations": [{"rule": "r1", "expect": "inbox->ship:lost<=0", "state": "unmet",
                                      "value": "45 lost (231 completed in inbox, 186 in ship)"}],
                    "routes": [{"route": "inbox", "completed": 234, "failed": 0},
                               {"route": "ship", "completed": 189, "failed": 45}],
                    "alsoFailed": {"ship": 45, "audit": 2},
                    "note": "Tried for its 1m56s share of the 2m budget: until= and within= are set to it"}
                 ]}
                """));
        info.chaos = ChaosInfo.parse(chaos);
        ChaosInfo.Experiment e = info.chaos.exploration.experiments().get(0);
        // short: the counts are in the detail, and the failures in ship are where the lost ones failed
        assertThat(ChaosExploreView.evidence(e)).isEqualTo("45 lost from inbox to ship; also 2 failed in audit");
        assertThat(ChaosExploreView.handedOffTo(e, "ship")).isEqualTo("inbox");
        assertThat(ChaosExploreView.handedOffTo(e, "audit")).isNull();
        assertThat(ChaosExploreView.cut("45 lost from inbox to ship", 10)).isEqualTo("45 lost f…");

        ChaosTab tab = new ChaosTab(ctx);
        tab.toggleExplore();
        String screen = TuiTestHelper.renderToString(tab, 160, 44);
        assertThat(screen).contains("finished at its deadline").doesNotContain("focus")
                .contains("45 messages failed in ship, after inbox had handed them over and completed: they are the lost"
                          + " ones")
                .contains("failed, in no expectation: audit 2").doesNotContain("in no expectation: ship")
                .contains("over the whole experiment (an expectation counts until it is decided)")
                .contains("Tried for its 1m56s share of the 2m budget");
    }

    @Test
    void withoutCamelChaosOrWhenItIsOff() throws Exception {
        String none = TuiTestHelper.renderToString(new ChaosTab(ctx), 120, 15);
        assertThat(none).contains("camel-chaos is not in this integration: run it with camel run --chaos");

        chaos("{\"enabled\": false, \"reason\": \"Chaos is off: it stays off with profile prod\"}");
        assertThat(TuiTestHelper.renderToString(new ChaosTab(ctx), 120, 15))
                .contains("Chaos is off: it stays off with profile prod");
    }

    @Test
    void withoutRulesItSaysHowToAddOne() throws Exception {
        chaos("{\"enabled\": true, \"rules\": [], \"report\": {\"verdict\": \"PASS\", \"final\": true, \"seed\": 7}}");
        String screen = TuiTestHelper.renderToString(new ChaosTab(ctx), 160, 20);
        assertThat(screen).contains("PASS").contains("No chaos rules: add one with camel cmd chaos-add chaosdemo");
    }

    @Test
    void theBadgeOfTheTitleBarAndTheMoreMenu() throws Exception {
        assertThat(ChaosTab.badge(info)).isNull();
        chaos(CHAOS);
        assertThat(ChaosTab.badge(info)).isEqualTo("3 rules · FAIL");
        chaos("{\"enabled\": true, \"rules\": [{\"id\": \"r1\", \"state\": \"active\", \"rule\": \"step=a error\"}],"
              + " \"report\": {\"verdict\": \"PASS\"}}");
        assertThat(ChaosTab.badge(info)).isEqualTo("1 rule");
        chaos("{\"enabled\": true, \"rules\": [{\"id\": \"r1\", \"state\": \"ended\", \"rule\": \"step=a error\"}],"
              + " \"report\": {\"verdict\": \"PASS\"}}");
        assertThat(ChaosTab.badge(info)).isNull();
        chaos("{\"enabled\": false}");
        assertThat(ChaosTab.badge(info)).isNull();
        chaos(CHAOS);
        assertThat(ChaosTab.headerBadge(info).get(0).content()).isEqualTo(" 🎲 chaos 3 rules · FAIL ");
    }

    @Test
    void theTableAsJsonForTheTools() throws Exception {
        chaos(CHAOS);
        JsonObject json = new ChaosTab(ctx).getTableDataAsJson();
        assertThat(json.getString("verdict")).isEqualTo("FAIL");
        JsonArray rows = json.getCollection("rows");
        assertThat(rows).hasSize(3);
        JsonObject r1 = (JsonObject) rows.get(0);
        assertThat(r1.getString("rate")).isEqualTo("24% of 25%");
        assertThat(((Map<?, ?>) r1.get("effect")).get("recovered")).isEqualTo(33L);
        assertThat(r1.getString("expectation")).isEqualTo("unmet");
    }

    @Test
    void theNoteOfAnEveryNRuleCountsMatches() {
        ChaosInfo.Rule every = new ChaosInfo.Rule();
        every.text = "endpoint=x error rate=every:5";
        every.nextFireIn = 3;
        assertThat(ChaosTab.note(every)).isEqualTo("fires again in 3 matches");
        every.nextFireIn = 1;
        assertThat(ChaosTab.note(every)).isEqualTo("fires again in 1 match");
    }

    @Test
    void aRuleWithoutAnIdIsShownWithoutFailing() throws Exception {
        chaos("{\"enabled\": true, \"rules\": [{\"state\": \"active\", \"rule\": \"step=a error\"}],"
              + " \"report\": {\"verdict\": \"PASS\", \"recentFaults\": [{\"rule\": \"r1\", \"fault\": \"error\"}]}}");
        assertThat(TuiTestHelper.renderToString(new ChaosTab(ctx), 160, 30)).contains("step a");
    }

    @Test
    void sparklineAndRates() {
        long[] perSecond = new long[60];
        perSecond[0] = 1;
        perSecond[59] = 4;
        String spark = ChaosTab.sparkline(perSecond, 20);
        assertThat(spark).hasSize(20).startsWith("▂").endsWith("█");
        assertThat(spark.substring(1, 19).isBlank()).isTrue();
        assertThat(ChaosTab.sparkline(new long[0], 20)).isEmpty();

        assertThat(ChaosTab.shortTime(800)).isEqualTo("800ms");
        assertThat(ChaosTab.shortTime(64_000)).isEqualTo("1m4s");
        assertThat(ChaosTab.shortTime(120_000)).isEqualTo("2m");
        // a countdown in the note is in whole seconds: "903ms" cut to "903m" reads as minutes
        assertThat(ChaosTab.countdown(903)).isEqualTo("1s");
        assertThat(ChaosTab.countdown(64_001)).isEqualTo("1m5s");
        assertThat(ChaosTab.countdown(0)).isEqualTo("0s");
        ChaosInfo.Rule stalled = new ChaosInfo.Rule();
        stalled.lifecycleState = "suspended";
        stalled.lifecycleBackIn = 903;
        assertThat(ChaosTab.note(stalled)).isEqualTo("suspended, back in 1s");

        ChaosInfo.Rule every = new ChaosInfo.Rule();
        every.text = "endpoint=x error rate=every:5";
        every.rate = 0.2;
        every.observedRate = 0.2;
        assertThat(ChaosTab.rate(every)).isEqualTo("20% of 1 in 5");
        ChaosInfo.Rule all = new ChaosInfo.Rule();
        all.text = "step=a delay duration='1s'";
        all.rate = 1;
        assertThat(ChaosTab.rate(all)).isEqualTo("all");
        assertThat(all.target()).isEqualTo("step=a");
        assertThat(ChaosTab.target(all)).isEqualTo("step a");
        assertThat(all.fault()).isEqualTo("delay duration='1s'");

        // narrowed to the sends and steps of a route (in=): with the target, not with the fault
        ChaosInfo.Rule in = new ChaosInfo.Rule();
        in.text = "endpoint=http:* in=orders error status=503 rate=0.1";
        assertThat(in.target()).isEqualTo("endpoint=http:* in=orders");
        assertThat(ChaosTab.target(in)).isEqualTo("http:* in orders");
        assertThat(in.fault()).isEqualTo("error status=503");
        ChaosInfo.Rule steps = new ChaosInfo.Rule();
        steps.text = "step=* in=order* delay duration=1s";
        assertThat(ChaosTab.target(steps)).isEqualTo("step * in order*");
        assertThat(steps.fault()).isEqualTo("delay duration=1s");
        // a condition (when=) is an option, not a parameter of the fault
        ChaosInfo.Rule when = new ChaosInfo.Rule();
        when.text = "endpoint=http:stock* error status=503 when=\"${header.country} == 'ES'\" rate=0.5";
        assertThat(when.fault()).isEqualTo("error status=503");
    }
}
