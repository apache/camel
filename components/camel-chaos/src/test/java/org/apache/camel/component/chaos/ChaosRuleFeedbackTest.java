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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What a developer (or a tool) learns when adding rules: a warning for a rule that matches nothing in the context now
 * (often a typo), rules added together (all or none), and why a rule did nothing when it was skipped.
 */
class ChaosRuleFeedbackTest extends ChaosTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:orders").routeId("orders")
                        .setHeader("country", constant("DK")).id("enrich")
                        .to("mock:stock?retainFirst=1").id("callStock");
                // a consumer that polls on a schedule, which never polls in the test
                from("file:target/chaos-feedback?initialDelay=3600000").routeId("poller").to("mock:polled");
            }
        };
    }

    @Test
    void aRuleThatFindsSomethingHasNoWarning() {
        for (String rule : List.of("endpoint=mock:stock* error", "endpoint=direct:orders delay duration=1ms",
                "component=mock error", "step=enrich error", "step=call* delay duration=1ms",
                "route=orders corrupt mode=headers", "endpoint=file:target* drop at=poll",
                "endpoint=mock:stock* error expect=orders:1", "route=orders stall duration=1s")) {
            assertThat(chaos().checkRule(chaos().addRule(rule))).as(rule).isEmpty();
            chaos().reset();
        }
    }

    @Test
    void anEndpointTypoNamesTheEndpoints() {
        String id = chaos().addRule("endpoint=mock:stok* error");

        assertThat(chaos().checkRule(id)).singleElement().asString()
                .startsWith("endpoint=mock:stok* matches no endpoint of the context now, so the rule fires 0 times")
                .contains("the endpoints are: ")
                .contains("mock://stock")
                // without the query of the endpoint
                .doesNotContain("retainFirst");
        assertThat(chaos().noTargetWarnings.get()).isEqualTo(1);
    }

    @Test
    void aStepRouteAndExpectationTypoAreNamed() {
        assertThat(chaos().checkRule(chaos().addRule("step=callStok error"))).singleElement().asString()
                .startsWith("step=callStok matches no step of the context now")
                .contains("callStock", "enrich");
        assertThat(chaos().checkRule(chaos().addRule("route=order corrupt mode=headers"))).singleElement().asString()
                .startsWith("route=order matches no route of the context now")
                .endsWith("the routes are: orders, poller");
        assertThat(chaos().checkRule(chaos().addRule("endpoint=mock:stock* error expect=shiping:1")))
                .containsExactly("expects route shiping, but there is no route with that id (yet)");
    }

    @Test
    void aPollRuleForAnEndpointWithoutAScheduledPollIsNamed() {
        assertThat(chaos().checkRule(chaos().addRule("endpoint=direct:orders drop at=poll"))).singleElement()
                .asString().startsWith("at=poll: no route consumes an endpoint that matches endpoint=direct:orders");
        // without at=poll the sends to it are reached
        assertThat(chaos().checkRule(chaos().addRule("endpoint=direct:orders drop"))).isEmpty();
    }

    @Test
    void aChangedRuleIsCheckedToo() {
        String id = chaos().addRule("step=enrich error");
        chaos().changeRule(id, "step=enrihc error");

        assertThat(chaos().checkRule(id)).singleElement().asString().startsWith("step=enrihc matches no step");
        assertThatThrownBy(() -> chaos().checkRule("r99")).hasMessage("There is no chaos rule r99");
    }

    @Test
    void rulesAreAddedTogether() {
        List<String> ids = chaos().addRules(
                "endpoint=mock:stock* error at=dlq; step=enrich delay duration=1ms ;endpoint=mock:stock* error message='a; b'");

        assertThat(ids).containsExactly("r1", "r2", "r3");
        assertThat(ruleReport("r3").getString("rule")).isEqualTo("endpoint=mock:stock* error message='a; b'");
    }

    @Test
    void rulesAreAddedAllOrNone() {
        assertThatThrownBy(() -> chaos().addRules("step=enrich error; step=enrich drop"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid chaos rule [step=enrich drop]");
        assertThat(chaos().hasRule("r1")).isFalse();
        assertThatThrownBy(() -> chaos().addRules(" ; ")).hasMessage("The chaos rule is empty");
        // the ids go on from the rules that were added
        assertThat(chaos().addRule("step=enrich error")).isEqualTo("r1");
    }

    @Test
    void theReasonOfASkipIsCounted() {
        String charset = chaos().addRule("route=orders corrupt mode=charset until=2");
        template.sendBody("direct:orders", new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)));
        template.sendBody("direct:orders", null);
        String truncate = chaos().addRule("endpoint=mock:stock* corrupt mode=truncate until=1");
        template.sendBody("direct:orders", "text");

        JsonObject reasons = ruleReport(charset).getJsonObject("skippedReasons");
        assertThat(reasons).containsEntry("a stream body (charset changes a text body; mode=truncate changes a stream)",
                1L).containsEntry("no body", 1L);
        assertThat(ruleReport(truncate).getJsonObject("skippedReasons"))
                .containsEntry("not a stream body (truncate changes a stream or a stream cache)", 1L);
        assertThat(chaos().report().toText())
                .contains("skipped 2 (a stream body (charset changes a text body; mode=truncate changes a stream): 1;"
                          + " no body: 1)");
    }
}
