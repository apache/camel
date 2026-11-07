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
import java.util.Set;

import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The timeline (CAMEL-26691) as the integration reports it (the timeline dev console), with the chaos rules of the
 * status.
 */
class TimelineTest {

    private static final long T0 = 1_800_000_000_000L;
    private static final long S0 = T0 / 1000;

    private final Timeline timeline = new Timeline();

    private static IntegrationInfo info() {
        IntegrationInfo info = new IntegrationInfo();
        info.pid = "1234";
        info.name = "shop";
        RouteInfo r = new RouteInfo();
        r.routeId = "orders";
        info.routes.add(r);
        return info;
    }

    private Timeline.RouteSeries orders(long endSecond, int seconds) {
        List<Timeline.RouteSeries> routes = timeline.routes("1234", List.of("orders"), endSecond, seconds);
        assertThat(routes).hasSize(1);
        return routes.get(0);
    }

    @Test
    void theSecondsOfTheIntegrationAreTakenAsTheyAre() {
        timeline.merge("1234", new TimelineJson(S0 + 2, 3)
                .route("orders", "completed", 0, 4).route("orders", "recovered", 1, 1).route("orders", "handled", 1, 2)
                .route("orders", "failed", 2, 1).route("orders", "meanMillis", 1, 30).route("orders", "p95Millis", 1, 80)
                .route("orders", "maxMillis", 1, 90).route("orders", "redeliveryAttempts", 1, 3)
                .route("orders", "inflight", 2, 5).json(), true);

        Timeline.RouteSeries r = orders(S0 + 2, 3);
        assertThat(r.completed()).containsExactly(4, 0, 0);
        assertThat(r.recovered()).containsExactly(0, 1, 0);
        assertThat(r.handled()).containsExactly(0, 2, 0);
        assertThat(r.failed()).containsExactly(0, 0, 1);
        assertThat(r.mean()).containsExactly(0, 30, 0);
        assertThat(r.p95()).containsExactly(0, 80, 0);
        assertThat(r.max()).containsExactly(0, 90, 0);
        assertThat(r.attempts()).containsExactly(0, 3, 0);
        assertThat(r.inflight()).containsExactly(0, 0, 5);
        assertThat(timeline.endSecond("1234")).isEqualTo(S0 + 2);
        assertThat(timeline.isFull("1234")).isTrue();
        assertThat(timeline.done("1234", S0, S0 + 2)).isEqualTo(4 + 3 + 1);
    }

    @Test
    void aSecondNotReportedIsUnknownAndTheLastSecondsReplaceTheOnesKept() {
        timeline.merge("1234", new TimelineJson(S0 + 1, 2).route("orders", "completed", 1, 4).json(), true);
        // the next refresh: the last seconds again, one of them done since
        timeline.merge("1234", new TimelineJson(S0 + 2, 2).route("orders", "completed", 0, 5)
                .route("orders", "completed", 1, 6).json(), false);

        assertThat(orders(S0 + 2, 4).completed()).containsExactly(-1, 0, 5, 6);
        assertThat(timeline.endSecond("1234")).isEqualTo(S0 + 2);
    }

    @Test
    void theErrorsArePerSecondByKindWithoutTheOnesChaosMade() {
        timeline.merge("1234", new TimelineJson(S0 + 1, 2)
                .error("orders", "org.apache.camel.http.base.HttpOperationFailedException", 503, null, new long[] { 2, 1 })
                .error("orders", "java.net.ConnectException", null, null, new long[] { 0, 1 })
                .error("orders", "org.apache.camel.component.chaos.ChaosException", null, "r1", new long[] { 5, 5 })
                .json(), false);

        List<Timeline.CauseSeries> causes = timeline.causes("1234", S0 + 1, 2);
        assertThat(causes).extracting(Timeline.CauseSeries::label)
                .containsExactly("HttpOperationFailedException 503", "ConnectException");
        assertThat(causes.get(0).values()).containsExactly(2, 1);
        assertThat(timeline.errorsSeen("1234")).isTrue();
        assertThat(timeline.errorsSeen("999")).isFalse();
    }

    @Test
    void theExchangesKeptAreFoundByTheSecondTheyWereDone() {
        timeline.merge("1234", new TimelineJson(S0 + 2, 3)
                .exchange("A", "orders", 0, 20, "completed", null, null)
                .exchange("B", "orders", 2, 900, "handled", "HTTP 503", "r1", "http://stock@850", "kafka:orders@5")
                .json(), false);

        assertThat(timeline.exchanges("1234", S0, S0 + 1)).extracting(Timeline.Kept::exchangeId).containsExactly("A");
        Timeline.Kept b = timeline.exchanges("1234", S0 + 2, S0 + 2).get(0);
        assertThat(b.outcome()).isEqualTo("handled");
        assertThat(b.chaosRule()).isEqualTo("r1");
        assertThat(b.calls()).containsExactly(new Timeline.Call("http://stock", 850), new Timeline.Call("kafka:orders", 5));
    }

    @Test
    void theIntegrationRecordsTheFaultsOfTheChaosRulesAndTheRoutesTheyHadDown() throws Exception {
        JsonObject json = new TimelineJson(S0 + 2, 3).route("orders", "completed", 0, 1)
                .route("orders", "down", 1, 1).route("orders", "down", 2, 1).json();
        JsonObject r2 = new JsonObject();
        r2.put("id", "r2");
        r2.put("rule", "route=orders stall duration=20s");
        r2.put("faults", List.of(0L, 1L, 0L));
        json.put("chaos", List.of(r2));
        timeline.merge("1234", json, true);

        assertThat(orders(S0 + 2, 3).down()).containsExactly(0, 1, 1);
        List<Timeline.CauseSeries> causes = timeline.causes("1234", S0 + 2, 3);
        assertThat(causes).singleElement().satisfies(c -> {
            assertThat(c.rule()).isEqualTo("r2");
            assertThat(c.label()).isEqualTo("r2 stall (orders)");
            assertThat(c.values()).containsExactly(0, 1, 0);
        });

        // the status then only gives the labels: the TUI no longer samples the state of the rules
        IntegrationInfo info = info();
        info.chaos = ChaosInfo.parse((JsonObject) Jsoner.deserialize("""
                {"enabled": true,
                 "rules": [{"id": "r2", "version": 1, "state": "active", "rule": "route=orders stall duration=20s",
                            "matches": 1, "fired": {"lifecycle": 1}, "faultsPerSecond": [9, 9, 9],
                            "lifecycle": {"action": "suspend", "route": "orders", "state": "down"}}],
                 "report": {"verdict": "PASS", "routes": [], "recentFaults": []}}
                """));
        timeline.record(info, (S0 + 2) * 1000);
        assertThat(timeline.causes("1234", S0 + 2, 3).get(0).values()).containsExactly(0, 1, 0);
        assertThat(timeline.causes("1234", S0 + 2, 3).get(0).label()).isEqualTo("r2 stall 20s (route orders)");
    }

    @Test
    void theLabelOfARuleFromItsText() {
        assertThat(Timeline.ruleLabel("r1", "endpoint=http://localhost:8765/stock* error status=503 rate=1"))
                .isEqualTo("r1 error (stock)");
        assertThat(Timeline.ruleLabel("r2", "route=shipping stall duration=15s")).isEqualTo("r2 stall (shipping)");
    }

    @Test
    void notATimelineIsIgnored() {
        timeline.merge("1234", new JsonObject(), true);
        timeline.merge("1234", null, true);
        assertThat(timeline.endSecond("1234")).isEqualTo(-1);
        assertThat(timeline.isFull("1234")).isFalse();
    }

    @Test
    void theFaultsOfAChaosRuleAndTheSecondsItHadARouteDown() throws Exception {
        IntegrationInfo info = info();
        info.chaos = ChaosInfo.parse((JsonObject) Jsoner.deserialize("""
                {"enabled": true,
                 "rules": [{"id": "r1", "version": 1, "state": "active",
                            "rule": "endpoint=http://localhost:8765/stock* error status=503 rate=0.2",
                            "matches": 10, "fired": {"send": 3}, "faultsPerSecond": [0, 2, 1]},
                           {"id": "r3", "version": 1, "state": "active",
                            "rule": "route=orders stall duration=20s",
                            "matches": 1, "fired": {"lifecycle": 1},
                            "lifecycle": {"action": "suspend", "route": "orders", "state": "down"}}],
                 "report": {"verdict": "PASS", "routes": [], "recentFaults": []}}
                """));
        timeline.record(info, T0);

        List<Timeline.CauseSeries> causes = timeline.causes("1234", S0, 3);
        assertThat(causes).hasSize(1);
        assertThat(causes.get(0).rule()).isEqualTo("r1");
        assertThat(causes.get(0).label()).isEqualTo("r1 error status 503 (stock)");
        assertThat(causes.get(0).values()).containsExactly(0, 2, 1);
        assertThat(orders(S0, 3).down()).containsExactly(0, 0, 1);
    }

    @Test
    void aStoppedIntegrationIsForgotten() {
        timeline.merge("1234", new TimelineJson(S0, 1).route("orders", "completed", 0, 1).json(), false);
        assertThat(timeline.firstSecond("1234")).isEqualTo(S0);
        timeline.retain(Set.of("999"));
        assertThat(timeline.firstSecond("1234")).isEqualTo(-1);
        assertThat(timeline.routes("1234", List.of("orders"), S0, 60)).isEmpty();
    }

    @Test
    void aRouteDownAtTwoSamplesWasDownInTheSecondsBetween() throws Exception {
        IntegrationInfo info = info();
        info.chaos = ChaosInfo.parse((JsonObject) Jsoner.deserialize("""
                {"enabled": true,
                 "rules": [{"id": "r3", "version": 1, "state": "active", "rule": "route=orders stall duration=20s",
                            "matches": 1, "fired": {"lifecycle": 1},
                            "lifecycle": {"action": "suspend", "route": "orders", "state": "down"}}],
                 "report": {"verdict": "PASS", "routes": [], "recentFaults": []}}
                """));
        timeline.record(info, T0);
        // the status was not written for two seconds
        timeline.record(info, T0 + 3000);

        assertThat(orders(S0 + 3, 4).down()).containsExactly(1, 1, 1, 1);
    }

}
