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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A scenario: the rules of an experiment in a file, one per line, with comments and a seed, loaded all at once.
 */
class ChaosScenarioTest extends ChaosTestSupport {

    private static final String OUTAGE = """
            # the stock service fails half of the time
            seed=7

            endpoint=mock:stock error rate=0.5
              # and the shipping is slow
            endpoint=mock:ship delay duration=1ms
            """;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:orders").routeId("orders").to("mock:stock").to("mock:ship");
            }
        };
    }

    // which of 20 orders failed
    private List<Boolean> failures() {
        List<Boolean> answer = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            answer.add(template.send("direct:orders", e -> e.getMessage().setBody("order")).getException() != null);
        }
        return answer;
    }

    @Test
    void oneRulePerLineWithCommentsAndASeed() {
        ChaosScenario scenario = ChaosScenario.parse("outage.chaos", OUTAGE.replace("\n", "\r\n"));

        assertThat(scenario.getName()).isEqualTo("outage.chaos");
        assertThat(scenario.getSeed()).isEqualTo(7L);
        assertThat(scenario.getRules())
                .containsExactly("endpoint=mock:stock error rate=0.5", "endpoint=mock:ship delay duration=1ms");
        assertThat(scenario.lineOf(1)).isEqualTo(6);
    }

    @Test
    void aSeedOrNoRulesThatAreNotValidAreRefused() {
        assertThatThrownBy(() -> ChaosScenario.parse("a.chaos", "seed=abc\nendpoint=mock:stock error"))
                .hasMessage("Chaos scenario a.chaos, line 1: seed=abc is not a whole number");
        assertThatThrownBy(() -> ChaosScenario.parse("a.chaos", "seed=1\nseed=2\nendpoint=mock:stock error"))
                .hasMessage("Chaos scenario a.chaos, line 2: the seed is given twice");
        assertThatThrownBy(() -> ChaosScenario.parse("a.chaos", "# nothing yet\nseed=1\n"))
                .hasMessage("The chaos scenario a.chaos has no rules");
    }

    @Test
    void loadingAScenarioStartsAnExperimentOfItsOwn() {
        String before = chaos().addRule("endpoint=mock:ship error");
        assertThat(template.send("direct:orders", e -> e.getMessage().setBody("order")).getException()).isNotNull();

        List<String> ids = chaos().loadScenario("outage.chaos", OUTAGE);

        assertThat(ids).hasSize(2);
        JsonObject report = chaos().report().toJson();
        // the rules there were are gone, and so is their report
        assertThat(((JsonArray) report.get("rules")).stream().map(r -> ((JsonObject) r).getString("id")))
                .containsExactlyElementsOf(ids).doesNotContain(before);
        assertThat(ruleReport(ids.get(0)).getString("scenario")).isEqualTo("outage.chaos");
        assertThat(chaos().report().toText()).contains("endpoint=mock:stock error rate=0.5  (scenario outage.chaos)");
    }

    @Test
    void aRuleThatIsNotValidKeepsTheRulesThereAre() {
        String before = chaos().addRule("endpoint=mock:ship error");

        assertThatThrownBy(
                () -> chaos().loadScenario("bad.chaos", "# one bad\nendpoint=mock:stock error\nendpoint=mock:stock bang"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Chaos scenario bad.chaos, line 3: Invalid chaos rule [endpoint=mock:stock bang]");

        assertThat(chaos().hasRule(before)).isTrue();
        assertThat((JsonArray) chaos().report().toJson().get("rules")).hasSize(1);
    }

    @Test
    void theSeedOfTheScenarioFailsTheSameMessagesWhereverItIsLoaded() {
        chaos().loadScenario("outage.chaos", OUTAGE);
        List<Boolean> first = failures();
        assertThat(first).contains(true, false);

        // other rules come and go, so the ids of the rules of the scenario differ the next time
        chaos().addRules("endpoint=mock:nowhere error; endpoint=mock:nowhere drop");
        List<String> ids = chaos().loadScenario("outage.chaos", OUTAGE);
        assertThat(ids).doesNotContain("r1", "r2");

        assertThat(failures()).isEqualTo(first);
    }

    @Test
    void drawReplaysARuleThatWasAddedWhileRunning() {
        // rule r2 was added while the application ran (seed 42, camel.chaos.seed)
        assertThat(chaos().addRule("endpoint=mock:nowhere error")).isEqualTo("r1");
        assertThat(chaos().addRule("endpoint=mock:stock error rate=0.5")).isEqualTo("r2");
        List<Boolean> live = failures();
        assertThat(live).contains(true, false);

        // a scenario with the rule alone: it is r1 there, so it draws other choices, unless draw= says it is r2
        chaos().loadScenario("other.chaos", "seed=42\nendpoint=mock:stock error rate=0.5\n");
        assertThat(failures()).isNotEqualTo(live);
        chaos().loadScenario("r2-test.chaos", "# rule r2 again\nseed=42\nendpoint=mock:stock error rate=0.5 draw=r2\n");
        assertThat(failures()).isEqualTo(live);

        assertThatThrownBy(() -> chaos().addRule("endpoint=mock:stock error rate=0.5 draw=orders"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("draw=orders is not the id of a rule");
    }

    @Test
    void withoutASeedTheRulesDrawFromTheSeedOfTheApplication() {
        chaos().loadScenario("outage.chaos", OUTAGE);
        List<Boolean> withSeed = failures();

        chaos().loadScenario("unseeded.chaos", OUTAGE.replace("seed=7", ""));
        List<Boolean> appSeed = failures();

        assertThat(appSeed).isNotEqualTo(withSeed);
        assertThat(chaos().report().toJson().getString("seedFrom")).isEqualTo("camel.chaos.seed");
    }
}
