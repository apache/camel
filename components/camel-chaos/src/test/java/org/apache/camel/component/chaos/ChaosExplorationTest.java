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

import java.util.List;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The journal of an exploration (CAMEL-26773): the baseline and the rules tried, and a deadline that camel-chaos
 * enforces, on the clock of TimeSource.
 */
class ChaosExplorationTest extends ChaosTestSupport {

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
                from("direct:orders").routeId("orders").to("mock:shipped");
            }
        };
    }

    private JsonObject exploration() {
        return (JsonObject) chaos().report().toJson().get("exploration");
    }

    private static List<Object> list(JsonObject jo, String key) {
        return jo.getCollection(key);
    }

    @Test
    void theBaselineAndTheRulesTriedUntilTheDeadline() {
        Map<String, Object> started = chaos().startExploration("5m", "orders");
        assertThat(started).containsEntry("running", true).containsEntry("budget", 300_000L)
                .containsEntry("focus", "orders").containsEntry("timeLeft", 300_000L);
        assertThat(chaos().report().toText()).contains("baseline: taken when the first rule is added");

        // the run without faults before the first rule is the baseline, so what fails there is not blamed on chaos
        template.sendBody("direct:orders", "before");
        clock.advance(30_000);
        String r1 = chaos().addRule("endpoint=mock:shipped error until=8m");
        String r2 = chaos().addRule("endpoint=mock:shipped delay duration=10ms");
        // a rule ends at the deadline at the latest
        long deadline = (long) started.get("deadline");
        assertThat(chaos().rules()).allMatch(r -> r.endsAt <= deadline);

        clock.advance(120_000);
        JsonObject running = exploration();
        assertThat(running.getLong("timeLeft")).isEqualTo(150_000);
        assertThat(list(running, "rules")).containsExactly(r1, r2);
        JsonObject orders = (JsonObject) running.getCollection("baseline").iterator().next();
        assertThat(orders.getString("route")).isEqualTo("orders");
        assertThat(orders.getLong("completed")).isEqualTo(1);
        assertThat(chaos().report().toText()).contains("exploration: running, 2m30s left of 5m0s (focus: orders)",
                "rules tried: r1, r2", "baseline: orders 1 completed, 0 failed in the 30s before the first rule");

        // the time is up: the next look at the report finishes it, and the rules are removed
        clock.advance(150_000);
        JsonObject finished = exploration();
        assertThat(finished.getBoolean("running")).isFalse();
        assertThat(finished.getString("finishedBy")).isEqualTo("deadline");
        assertThat(finished.getLong("removed")).isEqualTo(2);
        assertThat(finished.containsKey("timeLeft")).isFalse();
        assertThat(chaos().rules()).isEmpty();
        assertThat(chaos().report().toText()).contains("exploration: finished at the deadline after 5m0s of 5m0s,"
                                                       + " 2 rules removed");
    }

    @Test
    void finishedBeforeTheDeadline() {
        chaos().startExploration(null, null);
        chaos().addRule("endpoint=mock:shipped error");
        clock.advance(60_000);

        Map<String, Object> finished = chaos().finishExploration();

        assertThat(finished).containsEntry("running", false).containsEntry("finishedBy", "finished")
                .containsEntry("budget", ChaosExploration.DEFAULT_BUDGET).containsEntry("removed", 1);
        assertThat(chaos().rules()).isEmpty();
        // the journal stays in the report, and rules added now are not part of it
        chaos().addRule("endpoint=mock:shipped drop");
        assertThat(list(exploration(), "rules")).containsExactly("r1");
        assertThatThrownBy(() -> chaos().finishExploration()).isInstanceOf(IllegalStateException.class)
                .hasMessage("No exploration is running");
    }

    @Test
    void oneAtATimeAndWithinItsTime() {
        assertThatThrownBy(() -> chaos().startExploration("3h", null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 10s and 2h");
        assertThatThrownBy(() -> chaos().startExploration("1s", null)).hasMessageContaining("between 10s and 2h");
        assertThatThrownBy(() -> chaos().startExploration("soon", null)).hasMessageContaining("not a time: soon");

        chaos().startExploration("10m", null);
        assertThatThrownBy(() -> chaos().startExploration("10m", null)).isInstanceOf(IllegalStateException.class)
                .hasMessage("An exploration is running, for another 10m0s: finish it first");
        // a rule that would only start after the deadline is refused, and none of the rules is added
        chaos().finishExploration();
        chaos().startExploration("1m", null);
        assertThatThrownBy(() -> chaos().addRules("endpoint=mock:shipped error; endpoint=mock:shipped drop from=2m"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("start after the end of the exploration");
        assertThat(chaos().rules()).isEmpty();
        // a new exploration once the time of the last one is up
        clock.advance(60_000);
        assertThat(chaos().startExploration("1m", null)).containsEntry("running", true);
    }

    @Test
    void aChangedRuleEndsAtTheDeadlineToo() {
        chaos().startExploration("2m", null);
        String id = chaos().addRule("endpoint=mock:shipped error until=1m");
        clock.advance(90_000);

        chaos().changeRule(id, "endpoint=mock:shipped error until=5m");

        ChaosRule changed = chaos().rules().get(0);
        assertThat(changed.endsAt).isEqualTo(exploration().getLong("deadline"));
        assertThat(list(exploration(), "rules")).containsExactly(id);
    }

    @Test
    void aRuleChangedAfterTheDeadlineIsGone() {
        chaos().startExploration("1m", null);
        String id = chaos().addRule("endpoint=mock:shipped error");
        clock.advance(60_000);

        // the change is the first to see the time is up: the exploration removes its rules before the change looks
        assertThatThrownBy(() -> chaos().changeRule(id, "endpoint=mock:shipped drop"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("There is no chaos rule " + id);
        assertThat(exploration().getString("finishedBy")).isEqualTo("deadline");
    }

    @Test
    void theCountingStopsWithTheExploration() {
        chaos().startExploration("1m", null);
        assertThat(chaos().isCounting()).isTrue();
        chaos().addRule("endpoint=mock:shipped error");

        chaos().finishExploration();
        assertThat(chaos().isCounting()).isFalse();

        // and at the deadline
        chaos().startExploration("1m", null);
        chaos().addRule("endpoint=mock:shipped error");
        clock.advance(60_000);
        chaos().report();
        assertThat(chaos().isCounting()).isFalse();
    }

    @Test
    void aScenarioLoadedWhileItRunsStartsTheCountsAgain() {
        template.sendBody("direct:orders", "before");
        chaos().startExploration("10m", null);

        List<String> ids = chaos().loadScenario("outage", "endpoint=mock:shipped error until=1m");

        JsonObject running = exploration();
        assertThat(running.getBoolean("running")).isTrue();
        assertThat(running.getBoolean("baselineReset")).isTrue();
        assertThat(list(running, "baseline")).isEmpty();
        assertThat(list(running, "rules")).containsExactlyElementsOf(ids);

        // a finished exploration goes with a reset of the report
        chaos().finishExploration();
        chaos().reset();
        assertThat(exploration()).isNull();
    }
}
