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
 * The timeline (CAMEL-26691) worked out from the samples of the route counters, the errors and the chaos rules.
 */
class TimelineTest {

    private static final long T0 = 1_800_000_000_000L;
    private static final long S0 = T0 / 1000;

    private final Timeline timeline = new Timeline();

    private static IntegrationInfo info(long total, long failed, long handled, long redeliveries, long mean) {
        IntegrationInfo info = new IntegrationInfo();
        info.pid = "1234";
        info.name = "shop";
        RouteInfo r = new RouteInfo();
        r.routeId = "orders";
        r.total = total;
        r.failed = failed;
        r.failuresHandled = handled;
        r.redeliveries = redeliveries;
        r.meanTime = mean;
        r.inflight = 2;
        info.routes.add(r);
        return info;
    }

    private Timeline.RouteSeries orders(long endSecond, int seconds) {
        List<Timeline.RouteSeries> routes = timeline.routes("1234", List.of("orders"), endSecond, seconds);
        assertThat(routes).hasSize(1);
        return routes.get(0);
    }

    @Test
    void theOutcomesOfASecondAreTheRiseOfTheCounters() {
        timeline.record(info(10, 0, 0, 0, 4), T0);
        // 3 more exchanges: one handled by the error handler, one recovered by redelivery, one completed
        timeline.record(info(13, 0, 1, 1, 6), T0 + 1000);

        Timeline.RouteSeries r = orders(S0 + 1, 3);
        // the second before the first sample, the first sample (no rise yet), then the rise
        assertThat(r.completed()).containsExactly(-1, -1, 1);
        assertThat(r.recovered()).containsExactly(-1, -1, 1);
        assertThat(r.handled()).containsExactly(-1, -1, 1);
        assertThat(r.failed()).containsExactly(-1, -1, 0);
        assertThat(r.inflight()).containsExactly(-1, 2, 2);
        // the mean of the 3 exchanges of that second: (13 * 6 - 10 * 4) / 3
        assertThat(r.mean()[2]).isEqualTo(12);
        assertThat(r.quiet()).isFalse();
    }

    @Test
    void aSlowRefreshIsSpreadOverItsSeconds() {
        timeline.record(info(0, 0, 0, 0, 0), T0);
        timeline.record(info(7, 1, 0, 0, 0), T0 + 3000);

        Timeline.RouteSeries r = orders(S0 + 3, 3);
        // 6 completed and 1 failed over 3 seconds: the later seconds take the remainder
        assertThat(r.completed()).containsExactly(2, 2, 2);
        assertThat(r.failed()).containsExactly(0, 0, 1);
    }

    @Test
    void aResetOfTheStatisticsIsNoNegativeRate() {
        timeline.record(info(50, 3, 0, 0, 0), T0);
        timeline.record(info(2, 0, 0, 0, 0), T0 + 1000);

        Timeline.RouteSeries r = orders(S0 + 1, 1);
        assertThat(r.completed()).containsExactly(0);
        assertThat(r.failed()).containsExactly(0);
    }

    @Test
    void theErrorsArePerSecondByKindWithoutTheOnesChaosMade() {
        ErrorInfo first = error("orders", "callStock", "HTTP operation failed with statusCode: 503", 1, T0 - 2000);
        ErrorInfo chaos = error("orders", "callStock", "HTTP operation failed with statusCode: 500", 1, T0 - 1000);
        chaos.properties.put(ChaosMarks.PROPERTY, "r1");
        timeline.recordErrors("1234", List.of(first, chaos), T0);
        // the same kind failed 3 more times; the registry keeps the newest entries of a kind
        ErrorInfo second = error("orders", "callStock", "HTTP operation failed with statusCode: 503", 3, T0 + 1500);
        ErrorInfo third = error("orders", "callStock", "HTTP operation failed with statusCode: 503", 4, T0 + 2500);
        timeline.recordErrors("1234", List.of(third, second, first, chaos), T0 + 3000);

        List<Timeline.CauseSeries> causes = timeline.causes("1234", S0 + 2, 5);
        assertThat(causes).hasSize(1);
        assertThat(causes.get(0).label()).isEqualTo("HttpOperationFailedException 503");
        assertThat(causes.get(0).rule()).isNull();
        // the first at its own second, then 3 new ones: the entry at +1.5s, and the rest at the newest (+2.5s)
        assertThat(causes.get(0).values()).containsExactly(1, 0, 0, 1, 2);
        assertThat(timeline.errorsSeen("1234")).isTrue();
    }

    @Test
    void theFaultsOfAChaosRuleAndTheSecondsItHadARouteDown() throws Exception {
        IntegrationInfo info = info(0, 0, 0, 0, 0);
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
        timeline.record(info(1, 0, 0, 0, 0), T0);
        assertThat(timeline.firstSecond("1234")).isEqualTo(S0);
        timeline.retain(Set.of("999"));
        assertThat(timeline.firstSecond("1234")).isEqualTo(-1);
        assertThat(timeline.routes("1234", List.of("orders"), S0, 60)).isEmpty();
    }

    @Test
    void aRouteDownAtTwoSamplesWasDownInTheSecondsBetween() throws Exception {
        IntegrationInfo info = info(0, 0, 0, 0, 0);
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

    @Test
    void theRedeliveryAttemptsOfTheProcessorsShowARecoveryTheRouteDidNotCount() {
        IntegrationInfo info = info(10, 0, 0, 0, 0);
        ProcessorInfo call = new ProcessorInfo();
        call.redeliveries = 4;
        info.routes.get(0).processors.add(call);
        timeline.record(info, T0);
        // a removeHeaders("Camel*") after the call took the redelivered header away: the route counted no recovery
        IntegrationInfo later = info(12, 0, 0, 0, 0);
        ProcessorInfo again = new ProcessorInfo();
        again.redeliveries = 7;
        later.routes.get(0).processors.add(again);
        timeline.record(later, T0 + 1000);

        Timeline.RouteSeries r = orders(S0 + 1, 1);
        assertThat(r.recovered()).containsExactly(0);
        assertThat(r.attempts()).containsExactly(3);
        assertThat(r.completed()).containsExactly(2);
    }

    private static ErrorInfo error(String route, String node, String message, long repeat, long timestamp) {
        ErrorInfo e = new ErrorInfo();
        e.routeId = route;
        e.nodeId = node;
        e.exceptionType = "org.apache.camel.http.base.HttpOperationFailedException";
        e.exceptionMessage = message;
        e.repeatCount = repeat;
        e.timestamp = timestamp;
        return e;
    }
}
