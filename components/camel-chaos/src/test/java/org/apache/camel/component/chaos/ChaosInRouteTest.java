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
import java.util.concurrent.TimeUnit;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.chaos.TestPollComponent.TestPollConsumer;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * {@code in=} narrows the target of a rule to a route: the sends, steps and polls of the target in the routes of
 * {@code in=} only, which is the route of the exchange (not the routes it calls), and for a poll the route that
 * consumes.
 */
class ChaosInRouteTest extends ChaosTestSupport {

    @BindToRegistry("testpoll")
    private final TestPollComponent testPoll = new TestPollComponent();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // both routes send to mock:shared; orders calls stock, which sends to it too
                from("direct:orders").routeId("orders")
                        .setHeader("seen", constant("orders")).id("mark")
                        .to("direct:stock").id("callStock")
                        .to("mock:shared").id("ordersShared");
                from("direct:stock").routeId("stock")
                        .to("mock:shared").id("stockShared");
                from("direct:billing").routeId("orderBilling")
                        .to("mock:shared").id("billingShared");
                from("direct:other").routeId("other")
                        .to("mock:shared").id("otherShared");
                from("testpoll:a?delay=5&initialDelay=0").routeId("pollA").to("mock:a");
                from("testpoll:b?delay=5&initialDelay=0").routeId("pollB").to("mock:b");
            }
        };
    }

    @Test
    void onlyTheSendsOfTheRoute() throws Exception {
        String id = chaos().addRule("endpoint=mock:shared in=other error");
        getMockEndpoint("mock:shared").expectedMessageCount(3);

        Exchange other = template.send("direct:other", e -> e.getMessage().setBody("x"));
        // orders sends to mock:shared, and so does stock, which it calls
        Exchange orders = template.send("direct:orders", e -> e.getMessage().setBody("x"));
        template.sendBody("direct:billing", "x");

        assertThat(other.getException()).isInstanceOf(ChaosException.class);
        assertThat(orders.getException()).isNull();
        MockEndpoint.assertIsSatisfied(context);
        assertThat(fired(id, "send")).isEqualTo(1);
    }

    @Test
    void theRouteOfTheExchangeNotTheRoutesItCalls() throws Exception {
        String id = chaos().addRule("endpoint=mock:shared in=orders error");
        getMockEndpoint("mock:shared").expectedMessageCount(1);

        Exchange orders = template.send("direct:orders", e -> e.getMessage().setBody("x"));

        // the send of stock went through, the send of orders after it failed
        assertThat(orders.getException()).isInstanceOf(ChaosException.class);
        MockEndpoint.assertIsSatisfied(context);
        assertThat(fired(id, "send")).isEqualTo(1);
        JsonArray recent = (JsonArray) chaos().report().toJson().get("recentFaults");
        assertThat(((JsonObject) recent.get(0)).getString("route")).isEqualTo("orders");
    }

    @Test
    void aPatternTakesMoreRoutes() throws Exception {
        String id = chaos().addRule("endpoint=mock:shared in=order* error");
        getMockEndpoint("mock:shared").expectedMessageCount(2);

        assertThat(template.send("direct:orders", e -> e.getMessage().setBody("x")).getException())
                .isInstanceOf(ChaosException.class);
        assertThat(template.send("direct:billing", e -> e.getMessage().setBody("x")).getException())
                .isInstanceOf(ChaosException.class);
        template.sendBody("direct:other", "x");

        MockEndpoint.assertIsSatisfied(context);
        assertThat(fired(id, "send")).isEqualTo(2);
    }

    @Test
    void theRateCountsOnlyTheMatchesInTheRoute() {
        String id = chaos().addRule("endpoint=mock:shared in=other error rate=every:2");

        for (int i = 0; i < 3; i++) {
            template.sendBody("direct:billing", "x");
        }
        assertThat(template.send("direct:other", e -> e.getMessage().setBody("x")).getException()).isNull();
        assertThat(template.send("direct:other", e -> e.getMessage().setBody("x")).getException())
                .isInstanceOf(ChaosException.class);

        assertThat(ruleReport(id).getLong("matches")).isEqualTo(2);
    }

    @Test
    void theStepsOfTheRoute() throws Exception {
        String id = chaos().addRule("step=* in=stock error");
        getMockEndpoint("mock:shared").expectedMessageCount(0);

        Exchange orders = template.send("direct:orders", e -> e.getMessage().setBody("x"));

        // the steps of orders ran up to the call of stock, whose first step failed
        assertThat(orders.getException()).isInstanceOf(ChaosException.class);
        assertThat(orders.getMessage().getHeader("seen")).isEqualTo("orders");
        MockEndpoint.assertIsSatisfied(context);
        assertThat(fired(id, "step")).isEqualTo(1);
        JsonArray recent = (JsonArray) chaos().report().toJson().get("recentFaults");
        assertThat(((JsonObject) recent.get(0)).getString("step")).isEqualTo("stockShared");
    }

    @Test
    void thePollsOfTheRouteThatConsumes() {
        TestPollConsumer b = (TestPollConsumer) context.getRoute("pollB").getConsumer();
        String id = chaos().addRule("component=testpoll drop at=poll in=pollA");

        await().atMost(10, TimeUnit.SECONDS).until(() -> fired(id, "poll") >= 3);
        int polls = b.polls.get();
        await().atMost(10, TimeUnit.SECONDS).until(() -> b.polls.get() >= polls + 3);
        JsonArray recent = (JsonArray) chaos().report().toJson().get("recentFaults");
        // b polls on, and every fault is a poll of a
        assertThat(recent).allSatisfy(f -> assertThat(((JsonObject) f).getString("route")).isEqualTo("pollA"));
    }

    @Test
    void aWarningWhenNoRouteMatches() {
        String id = chaos().addRule("endpoint=mock:shared in=ordres error");
        assertThat(chaos().checkRule(id)).singleElement().asString()
                .startsWith("in=ordres matches no route of the context now, so the rule fires 0 times; the routes are:"
                            + " orderBilling, orders, other, pollA, pollB");
    }

    @Test
    void aWarningWhenTheTargetIsNotInTheRoute() {
        // the endpoint is there, but orders does not send to it
        String id = chaos().addRule("endpoint=mock:a in=orders error");
        assertThat(chaos().checkRule(id)).singleElement().asString()
                .isEqualTo("endpoint=mock:a matches no send or poll of route orders (in=orders), so the rule fires 0 times"
                           + " (in= is the route of the exchange, not the routes it calls); the sends there are:"
                           + " direct://stock, mock://shared");
        String step = chaos().addRule("step=billing* in=orders delay duration=1ms");
        assertThat(chaos().checkRule(step)).singleElement().asString()
                .isEqualTo("step=billing* matches no step of route orders (in=orders), so the rule fires 0 times; the"
                           + " steps there are: callStock, mark, ordersShared");
        // the steps of stock are not in orders
        assertThat(chaos().checkRule(chaos().addRule("step=stockShared in=orders error"))).hasSize(1);
        assertThat(chaos().checkRule(chaos().addRule("endpoint=mock:shared in=order* error"))).isEmpty();
        assertThat(chaos().checkRule(chaos().addRule("step=mark in=orders error"))).isEmpty();
    }

    @Test
    void theHooksThatCanFireAreThoseOfTheRoute() {
        assertThat(hooks(chaos().addRule("component=testpoll error in=pollA"))).containsExactly("poll");
        // orders consumes direct, which does not poll, and sends to no testpoll endpoint
        assertThat(hooks(chaos().addRule("component=testpoll error in=orders"))).isEmpty();
        assertThat(hooks(chaos().addRule("endpoint=mock:shared delay duration=1ms in=stock"))).containsExactly("send");
        assertThat(ruleReport("r3").getString("description"))
                .isEqualTo("delay every send to mock:shared in route stock by 1 millisecond");
    }

    private List<Object> hooks(String id) {
        return (JsonArray) ruleReport(id).get("hooks");
    }
}
