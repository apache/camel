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
 * The experiments of an exploration (CAMEL-26774): a hypothesis and the rules that test it, and what came of it in the
 * journal: the counts per route while it ran, the faults of each rule, the expectations and the verdict.
 */
class ChaosExperimentTest extends ChaosTestSupport {

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
                // a dead letter channel that parks what could not be charged
                from("direct:billing").routeId("billing")
                        .errorHandler(deadLetterChannel("direct:parked"))
                        .to("mock:charged");
                from("direct:parked").routeId("parked").to("mock:parked");
            }
        };
    }

    private void send(int count) {
        for (int i = 0; i < count; i++) {
            // a failed exchange is counted by chaos, and not thrown here
            template.send("direct:orders", e -> e.getMessage().setBody("order"));
        }
    }

    private JsonObject experiment(int number) {
        JsonObject exploration = (JsonObject) chaos().report().toJson().get("exploration");
        return (JsonObject) list(exploration, "experiments").get(number - 1);
    }

    private static List<Object> list(JsonObject jo, String key) {
        return jo.getCollection(key);
    }

    private static JsonObject first(JsonObject jo, String key) {
        return (JsonObject) list(jo, key).get(0);
    }

    @Test
    void aHypothesisThatFails() {
        chaos().startExploration("10m", null);
        // before the experiment: not counted in it
        send(1);

        Map<String, Object> begun = chaos().beginExperiment("an order never fails",
                "endpoint=mock:shipped error rate=every:2 expect=orders:failed<=0 within=1m");
        assertThat(begun).containsEntry("number", 1).containsEntry("open", true).containsEntry("verdict", "PENDING");
        send(4);
        clock.advance(5_000);

        // the expectation is unmet as soon as an order fails, before the experiment ends
        assertThat(experiment(1).getString("verdict")).isEqualTo("FAIL");
        Map<String, Object> ended = chaos().endExperiment("orders has no error handler");

        assertThat(ended).containsEntry("open", false).containsEntry("verdict", "FAIL").containsEntry("endedBy", "ended")
                .containsEntry("elapsed", 5_000L).containsEntry("note", "orders has no error handler");
        JsonObject result = experiment(1);
        assertThat(list(result, "ruleTexts")).containsExactly(
                "endpoint=mock:shipped error rate=every:2 expect=orders:failed<=0 within=1m");
        assertThat(((JsonObject) result.get("faults")).getLong("r1")).isEqualTo(2);
        JsonObject expectation = first(result, "expectations");
        assertThat(expectation.getString("expect")).isEqualTo("orders:failed<=0");
        assertThat(expectation.getString("state")).isEqualTo("unmet");
        assertThat(expectation.getLong("actual")).isEqualTo(2);
        JsonObject orders = first(result, "routes");
        assertThat(orders.getString("route")).isEqualTo("orders");
        assertThat(orders.getLong("completed")).isEqualTo(2);
        assertThat(orders.getLong("failed")).isEqualTo(2);
        // its rules are removed, and it stays in the journal
        assertThat(chaos().rules()).isEmpty();
        assertThat(chaos().report().toText()).contains(
                "experiments:", "1 FAIL  an order never fails (r1, ended after 5s)",
                "expect orders:failed<=0: unmet, 2 failed", "routes: orders 2 completed, 2 failed",
                "note: orders has no error handler");
    }

    @Test
    void aHypothesisThatHoldsAndOneNotDecided() {
        chaos().startExploration("10m", null);
        chaos().beginExperiment("orders keep up with a slow shipping",
                "endpoint=mock:shipped delay duration=10ms expect=orders:2 within=1m");
        send(1);
        assertThat(experiment(1).getString("verdict")).isEqualTo("PENDING");
        send(1);
        assertThat(experiment(1).getString("verdict")).isEqualTo("PASS");
        assertThat(chaos().endExperiment(null)).containsEntry("verdict", "PASS").doesNotContainKey("note");

        // ended before its expectation was decided: the messages may still have come
        chaos().beginExperiment(null, "endpoint=mock:shipped delay duration=10ms expect=orders:5 within=1m");
        send(1);
        Map<String, Object> ended = chaos().endExperiment(null);
        assertThat(ended).containsEntry("number", 2).containsEntry("verdict", "INCONCLUSIVE");
        assertThat(first(experiment(2), "expectations").getString("state")).isEqualTo("pending");
        assertThat(list((JsonObject) chaos().report().toJson().get("exploration"), "rules"))
                .containsExactly("r1", "r2");
    }

    @Test
    void messagesLostAtTheDeadLetterChannelFailTheExperiment() {
        chaos().startExploration("10m", null);
        // the service is down and the dead letter channel fails too: the dead letter channel handles each message it
        // could not deliver, so billing does not fail, and only the count of the rule sees the loss (CAMEL-26789)
        chaos().beginExperiment("nothing is lost when billing and parking fail",
                "endpoint=mock:charged error expect=billing:failed<=0 within=1m; endpoint=direct:parked error at=dlq");
        for (int i = 0; i < 3; i++) {
            template.send("direct:billing", e -> e.getMessage().setBody("bill"));
        }

        Map<String, Object> ended = chaos().endExperiment(null);

        assertThat(ended).containsEntry("verdict", "FAIL").containsEntry("lostAtDeadLetterChannel", 3L);
        assertThat(((JsonObject) ended.get("lostAtDeadLetterChannelPerRule")).getLong("r2")).isEqualTo(3);
        // the expectation alone sees nothing wrong: nothing failed in billing
        assertThat(first(new JsonObject(ended), "expectations").getLong("actual")).isZero();
        assertThat(chaos().report().toText()).contains("1 FAIL  nothing is lost when billing and parking fail",
                "lost at the dead letter channel: 3 messages, handled there without being delivered");
    }

    @Test
    void oneAtATimeWithAnExpectationAndInAnExploration() {
        String rule = "endpoint=mock:shipped error expect=orders:failed<=0 within=1m";
        assertThatThrownBy(() -> chaos().beginExperiment("h", rule)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No exploration is running");

        chaos().startExploration("10m", null);
        assertThatThrownBy(() -> chaos().beginExperiment("h", "endpoint=mock:shipped error"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("needs an expectation (expect=)");
        assertThatThrownBy(() -> chaos().beginExperiment("h", rule + "; endpoint=mock:shipped nonsense"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(chaos().rules()).isEmpty();
        assertThatThrownBy(() -> chaos().endExperiment(null)).isInstanceOf(IllegalStateException.class)
                .hasMessage("No experiment is open");

        // a pair of faults: two rules, one of them with the expectation
        Map<String, Object> begun = chaos().beginExperiment("h", rule + "; endpoint=mock:shipped delay duration=10ms");
        assertThat(list(new JsonObject(begun), "rules")).containsExactly("r1", "r2");
        assertThatThrownBy(() -> chaos().beginExperiment("h", rule)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Experiment 1 is open: end it first");
        // a rule added by hand is not part of the experiment, and stays when it ends
        String other = chaos().addRule("endpoint=mock:shipped delay duration=1ms");
        chaos().endExperiment(null);
        assertThat(chaos().rules()).extracting(r -> r.id).containsExactly(other);
    }

    @Test
    void anExperimentOpenAtTheDeadlineEndsWithTheExploration() {
        chaos().startExploration("1m", null);
        chaos().beginExperiment("h", "endpoint=mock:shipped error expect=orders:failed<=0 within=5m");
        send(1);
        clock.advance(60_000);

        JsonObject experiment = experiment(1);
        assertThat(experiment.getString("endedBy")).isEqualTo("deadline");
        assertThat(experiment.getString("verdict")).isEqualTo("FAIL");
        JsonObject exploration = (JsonObject) chaos().report().toJson().get("exploration");
        assertThat(exploration.getLong("removed")).isEqualTo(1);
        assertThat(chaos().report().toText()).contains("1 FAIL  h (r1, ended at the deadline after 1m0s)");

        // finished by hand, with an experiment open
        chaos().startExploration("1m", null);
        chaos().beginExperiment("h", "endpoint=mock:shipped delay duration=1ms expect=orders:1 within=5m");
        chaos().finishExploration();
        assertThat(experiment(1).getString("endedBy")).isEqualTo("finished");
        assertThat(experiment(1).getString("verdict")).isEqualTo("INCONCLUSIVE");
        assertThat(chaos().rules()).isEmpty();
    }
}
