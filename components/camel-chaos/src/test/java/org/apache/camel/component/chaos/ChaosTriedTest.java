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

import java.util.concurrent.TimeUnit;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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
                // one rule in two routes, done in another way in each
                from("direct:handled").routeId("handled")
                        .onException(Exception.class).handled(true).end()
                        .to("mock:audit");
                from("direct:unhandled").routeId("unhandled")
                        .errorHandler(noErrorHandler())
                        .to("mock:audit");
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
    void howItWentIsPerRoute() {
        String id = chaos().addRule("endpoint=mock:audit error until=10m");
        template.send("direct:handled", e -> e.getMessage().setBody("a"));
        template.send("direct:unhandled", e -> e.getMessage().setBody("b"));

        JsonObject handled = (JsonObject) ((JsonArray) route("handled").get("kinds")).get(0);
        assertThat((JsonArray) handled.get("rules")).containsExactly(id);
        assertThat(handled.getLong("fired")).isEqualTo(1);
        assertThat(handled.getLong("handled")).isEqualTo(1);
        assertThat(handled.getLong("failed")).isZero();
        // the outcome of the other route does not count here
        JsonObject unhandled = (JsonObject) ((JsonArray) route("unhandled").get("kinds")).get(0);
        assertThat(unhandled.getLong("fired")).isEqualTo(1);
        assertThat(unhandled.getLong("handled")).isZero();
        assertThat(unhandled.getLong("failed")).isEqualTo(1);
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
    void theHandOffSuggestionIsTriedByARuleThatExpectsNothingLost() {
        // the suggestion for route orders fails the next route, and expects nothing lost between them
        chaos().addRule("route=shipping error until=1 expect=orders->shipping:lost<=0 within=10s");
        template.sendBody("direct:orders", "order");
        // shipping takes the order from seda on its own thread
        await().atMost(10, TimeUnit.SECONDS).until(this::firedInShipping);

        JsonObject orders = route("orders");
        assertThat(((JsonArray) orders.get("kinds")).stream().map(k -> ((JsonObject) k).getString("kind")))
                .contains("lost");
        assertThat((JsonArray) orders.get("notTried")).doesNotContain("lost");
        boolean lostTried = false;
        for (Object o : (JsonArray) chaos().suggest().get("suggestions")) {
            JsonObject s = (JsonObject) o;
            if ("orders".equals(s.getString("route")) && "lost".equals(s.getString("kind"))) {
                lostTried = s.getBooleanOrDefault("tried", false);
            }
        }
        assertThat(lostTried).isTrue();
    }

    private boolean firedInShipping() {
        for (Object o : (JsonArray) chaos().report().toJson().get("tried")) {
            if ("shipping".equals(((JsonObject) o).getString("route"))
                    && !((JsonArray) ((JsonObject) o).get("kinds")).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Test
    void theKindOfARule() {
        assertThat(kind("endpoint=x error")).isEqualTo(ChaosAdvisor.Kind.ERRORS);
        assertThat(kind("endpoint=x delay duration=1s")).isEqualTo(ChaosAdvisor.Kind.SLOW);
        // Times out: the suggestion of an HTTP call is slow, so trying it marks it tried
        assertThat(kind("endpoint=x error after=5s exception=java.net.SocketTimeoutException"))
                .isEqualTo(ChaosAdvisor.Kind.SLOW);
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
