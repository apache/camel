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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The report: the seed, and each rule with its state, matches and the faults that fired.
 */
class ChaosReportTest extends ChaosTestSupport {

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
        assertThat(report.toText()).isEqualTo("Chaos report\n  seed: 42 (camel.chaos.seed)\n  rules: none");
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
                .contains("matches 4, fired 2 (send 2), ends in ")
                .contains(step + " ended: step=done delay duration=1ms until=1")
                .endsWith("matches 1, fired 1 (step 1)");

        JsonObject json = chaos().report().toJson();
        assertThat(json.getLong("seed")).isEqualTo(42);
        assertThat(json.getBoolean("seedGiven")).isTrue();
        JsonObject first = (JsonObject) ((JsonArray) json.get("rules")).get(0);
        assertThat(first.getString("id")).isEqualTo(send);
        assertThat(first.getString("state")).isEqualTo("active");
        assertThat((JsonArray) first.get("hooks")).containsExactly("send");
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
                .contains(changed + " active: endpoint=mock:other error")
                .contains(changed + " replaced: endpoint=mock:kafka error")
                .contains(removed + " removed: endpoint=mock:kafka drop");
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
}
