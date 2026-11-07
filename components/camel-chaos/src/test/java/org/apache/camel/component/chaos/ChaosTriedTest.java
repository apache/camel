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

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-26728: what has been tried per route, how it went, and what the suggestions offer that no rule tried yet.
 */
class ChaosTriedTest extends ChaosTestSupport {

    @BindToRegistry("http")
    private final TestHttpComponent http = new TestHttpComponent();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:orders").routeId("orders")
                        .errorHandler(defaultErrorHandler().maximumRedeliveries(2).redeliveryDelay(0))
                        .to("http://stock/check")
                        .to("seda:shipping");
                from("seda:shipping?pollTimeout=10").routeId("shipping")
                        .errorHandler(deadLetterChannel("mock:dlq"))
                        .to("mock:ship");
            }
        };
    }

    private JsonObject route(String routeId) {
        for (Object o : (JsonArray) chaos().report().toJson().get("tried")) {
            JsonObject jo = (JsonObject) o;
            if (routeId.equals(jo.getString("route"))) {
                return jo;
            }
        }
        throw new AssertionError("No route " + routeId + " in what was tried");
    }

    @Test
    void whatWasTriedPerRouteAndWhatIsLeft() {
        // nothing tried yet: the suggestions say what is left
        assertThat((JsonArray) route("orders").get("kinds")).isEmpty();
        assertThat((JsonArray) route("orders").get("notTried")).contains("errors", "slow", "lost");
        assertThat((JsonArray) route("shipping").get("notTried")).contains("dead letter channel");

        String id = chaos().addRule("endpoint=http://stock/check error status=503 rate=every:2 until=10m");
        for (int i = 0; i < 2; i++) {
            template.sendBody("direct:orders", "order " + i);
        }

        JsonObject orders = route("orders");
        JsonObject errors = (JsonObject) ((JsonArray) orders.get("kinds")).get(0);
        assertThat(errors.getString("kind")).isEqualTo("errors");
        assertThat((JsonArray) errors.get("rules")).containsExactly(id);
        assertThat(errors.getLong("fired")).isPositive();
        // the redelivery recovered them
        assertThat(errors.getLong("recovered")).isPositive();
        assertThat(errors.getLong("failed")).isZero();
        assertThat((JsonArray) orders.get("notTried")).doesNotContain("errors").contains("slow", "lost");

        assertThat(chaos().report().toText()).contains("tried, per route:").contains("orders:")
                .contains("errors (" + id + "): fired").contains("not tried yet: slow");
    }

    @Test
    void aSuggestionOfAKindTriedAlreadyIsMarked() {
        chaos().addRule("endpoint=http://stock/check delay duration=1ms until=10m");
        template.sendBody("direct:orders", "order");

        boolean slowTried = false;
        boolean errorsTried = false;
        for (Object o : (JsonArray) chaos().suggest().get("suggestions")) {
            JsonObject s = (JsonObject) o;
            if ("orders".equals(s.getString("route")) && "slow".equals(s.getString("kind"))) {
                slowTried = s.getBooleanOrDefault("tried", false);
            }
            if ("orders".equals(s.getString("route")) && "errors".equals(s.getString("kind"))) {
                errorsTried = s.getBooleanOrDefault("tried", false);
            }
        }
        assertThat(slowTried).isTrue();
        assertThat(errorsTried).isFalse();
    }

    @Test
    void theKindOfARule() {
        assertThat(kind("endpoint=x error")).isEqualTo(ChaosAdvisor.Kind.ERRORS);
        assertThat(kind("endpoint=x delay duration=1s")).isEqualTo(ChaosAdvisor.Kind.SLOW);
        assertThat(kind("endpoint=x drop")).isEqualTo(ChaosAdvisor.Kind.LOST);
        assertThat(kind("endpoint=x drop at=poll")).isEqualTo(ChaosAdvisor.Kind.CONSUMER);
        assertThat(kind("endpoint=x error at=poll")).isEqualTo(ChaosAdvisor.Kind.CONSUMER);
        assertThat(kind("route=orders error")).isEqualTo(ChaosAdvisor.Kind.CONSUMER);
        assertThat(kind("route=orders corrupt mode=headers")).isEqualTo(ChaosAdvisor.Kind.CORRUPT);
        assertThat(kind("endpoint=x duplicate")).isEqualTo(ChaosAdvisor.Kind.DUPLICATE);
        assertThat(kind("endpoint=seda:dlq error at=dlq")).isEqualTo(ChaosAdvisor.Kind.DEAD_LETTER);
        assertThat(kind("route=orders stall duration=1s")).isEqualTo(ChaosAdvisor.Kind.LIFECYCLE);
    }

    private ChaosAdvisor.Kind kind(String text) {
        ChaosRule.Builder b = ChaosRuleParser.parse(text, context.getClassResolver(), 600_000);
        return ChaosAdvisor.kindOf(new ChaosRule(b, "r1", 1, 0, 600_000));
    }
}
