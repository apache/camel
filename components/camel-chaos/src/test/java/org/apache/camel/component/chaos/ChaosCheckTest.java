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

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ChaosManager#checkRules(String)}: a rule is checked as it would be added, and what it would hit now is listed,
 * without adding it ({@code chaos-add --dry-run}, the chaos_check tool).
 */
class ChaosCheckTest extends ChaosTestSupport {

    @BindToRegistry("testpoll")
    private final TestPollComponent testPoll = new TestPollComponent();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:orders").routeId("orders")
                        .to("direct:stock").id("callStock")
                        .to("mock:shipping?retainFirst=10").id("ship")
                        .toD("mock:dyn-${header.n}").id("dyn");
                from("direct:stock").routeId("stock")
                        .log("stock").id("logStock")
                        .to("mock:shipping").id("stockShip");
                from("testpoll:inbox?initialDelay=3600000").routeId("poller").to("mock:in").id("toIn");
            }
        };
    }

    @SuppressWarnings("unchecked")
    private JsonObject check(String rules) {
        return new JsonObject((Map<String, Object>) chaos().checkRules(rules));
    }

    private JsonObject only(String rule) {
        List<Object> rules = check(rule).getCollection("rules");
        assertThat(rules).hasSize(1);
        return (JsonObject) rules.get(0);
    }

    private static List<Object> hits(JsonObject checked, String kind) {
        return ((JsonObject) checked.get("hits")).getCollection(kind);
    }

    @Test
    void theSendsItWouldHitWithTheirRouteAndStep() {
        JsonObject checked = only("endpoint=mock:shipping* error rate=0.5");

        assertThat(checked.getString("rule")).isEqualTo("endpoint=mock:shipping* error rate=0.5");
        assertThat(checked.getString("description")).isEqualTo("fail half of the sends to mock:shipping*");
        assertThat((List<Object>) checked.getCollection("hooks")).containsExactly("send");
        // the query of an endpoint is left out (it can hold secrets)
        assertThat(hits(checked, "sends")).containsExactly(
                Map.of("route", "orders", "endpoint", "mock://shipping", "step", "ship"),
                Map.of("route", "stock", "endpoint", "mock://shipping", "step", "stockShip"));
        // nothing was added
        assertThat((JsonArray) chaos().report().toJson().get("rules")).isEmpty();
    }

    @Test
    void inNarrowsTheHitsToTheRoute() {
        JsonObject checked = only("endpoint=mock:shipping* in=stock error");
        assertThat(hits(checked, "sends")).singleElement()
                .isEqualTo(Map.of("route", "stock", "endpoint", "mock://shipping", "step", "stockShip"));
    }

    @Test
    void aSendKnownOnlyWhenItSendsMayMatch() {
        JsonObject checked = only("endpoint=mock:dyn-* delay duration=1ms");
        assertThat(hits(checked, "dynamicSends")).singleElement()
                .isEqualTo(Map.of("route", "orders", "uri", "mock:dyn-${header.n}"));
    }

    @Test
    void thePollsTheRoutesAndTheSteps() {
        assertThat(hits(only("component=testpoll drop at=poll"), "polls")).singleElement()
                .isEqualTo(Map.of("route", "poller", "endpoint", "testpoll://inbox"));
        assertThat(hits(only("route=sto* error"), "routes")).singleElement()
                .isEqualTo(Map.of("route", "stock", "from", "direct://stock"));
        assertThat(hits(only("step=*Stock error in=stock"), "steps")).singleElement()
                .isEqualTo(Map.of("route", "stock", "step", "logStock"));
        assertThat(hits(only("route=poller stall duration=10s"), "routes")).singleElement()
                .isEqualTo(Map.of("route", "poller", "from", "testpoll://inbox"));
    }

    @Test
    void aRuleThatHitsNothingHasItsWarning() {
        JsonObject checked = only("endpoint=mock:shiping error");
        assertThat((JsonObject) checked.get("hits")).isEmpty();
        assertThat((List<Object>) checked.getCollection("warnings")).singleElement().asString()
                .startsWith("endpoint=mock:shiping matches no endpoint of the context now");
    }

    @Test
    void severalRulesAndACondition() {
        List<Object> rules = check("endpoint=mock:shipping error at=send;route=orders error when=\"${header.a} == 'b'\"")
                .getCollection("rules");
        assertThat(rules).hasSize(2);
        JsonObject second = (JsonObject) rules.get(1);
        assertThat(second.getString("when")).isEqualTo("${header.a} == 'b'");
        assertThat(second.getString("description")).isEqualTo("fail every message entering route orders, when"
                                                              + " ${header.a} == 'b'");
    }

    @Test
    void aRuleThatIsNotValidIsRefusedAsAddWouldRefuseIt() {
        assertThatThrownBy(() -> chaos().checkRules("endpoint=mock:x explode"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unknown fault explode");
        assertThatThrownBy(() -> chaos().checkRules("route=nothere stall duration=1s"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("there is no route nothere");
        assertThatThrownBy(() -> chaos().checkRules(" "))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("The chaos rule is empty");
        assertThat((JsonArray) chaos().report().toJson().get("rules")).isEmpty();
    }
}
