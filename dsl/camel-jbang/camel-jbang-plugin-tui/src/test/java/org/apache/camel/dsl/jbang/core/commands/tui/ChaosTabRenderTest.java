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
                   "redeliveries": 0}
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
    }
}
