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
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CAMEL-26726: expectations on the time of a route (meanTime, maxTime), measured by chaos from when an exchange enters
 * the route until the route is done with it, and on the messages lost between two routes (orders->ship:lost<=0). The
 * clock is virtual: a delay moves it by its length.
 */
@Isolated
class ChaosExpectTimeAndLossTest extends ChaosTestSupport {

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
                from("direct:orders").routeId("orders")
                        .to("mock:stock").id("callStock")
                        .to("direct:ship");
                from("direct:ship").routeId("ship").to("mock:ship");
                // the orders it cannot charge are parked by its dead letter channel, and never go to ship
                from("direct:charged").routeId("charged")
                        .errorHandler(deadLetterChannel("mock:parked"))
                        .to("mock:pay").id("callPay")
                        .to("direct:ship");
            }
        };
    }

    private void orders(int n) {
        for (int i = 0; i < n; i++) {
            template.sendBody("direct:orders", "order " + i);
        }
    }

    private JsonObject expectation(String id) {
        return (JsonObject) ruleReport(id).get("expectation");
    }

    @Test
    void theMeanTimeOfARouteIsDecidedWhenTheTimeIsUp() {
        // every second call to stock is 3s slow: a mean of 1.5s over 4 orders
        String id = chaos().addRule("step=callStock delay duration=3s rate=every:2 expect=orders:meanTime<=2s within=1m");
        orders(4);

        assertThat(expectation(id).getString("outcome")).isEqualTo("pending");
        clock.advance(60_001);
        JsonObject e = expectation(id);
        assertThat(e.getString("outcome")).isEqualTo("met");
        assertThat(e.getLong("value")).isEqualTo(1500);
        assertThat(e.getLong("samples")).isEqualTo(4);
        assertThat(e.getString("spec")).isEqualTo("orders:meanTime<=2s");
        assertThat(chaos().report().getVerdict()).isEqualTo(ChaosReport.PASS);
        assertThat(chaos().report().toText())
                .contains("expect orders:meanTime<=2s within 1m0s: met, mean 1s500ms of 4 exchanges");
        assertThat(chaos().describeRule(id))
                .endsWith("; expects a mean time of at most 2 seconds in route orders within 1 minute");
    }

    @Test
    void oneSlowExchangeFailsTheMaxTimeAtOnce() {
        String id = chaos().addRule("step=callStock delay duration=3s rate=every:3 expect=orders:maxTime<=1s within=1m");
        orders(2);
        assertThat(expectation(id).getString("outcome")).isEqualTo("pending");
        orders(1);

        // decided before the time is up
        JsonObject e = expectation(id);
        assertThat(e.getString("outcome")).isEqualTo("unmet");
        assertThat(e.getLong("value")).isEqualTo(3000);
        ChaosReport report = chaos().report();
        assertThat(report.getVerdict()).isEqualTo(ChaosReport.FAIL);
        assertThat(report.getReasons()).containsExactly(
                "route orders: max 3s of 3 exchanges within 1m0s, at most 1s expected (rule " + id + ")");
        // a time is not a message lost
        assertThat(report.getLost()).isZero();
        assertThat(chaos().describeRule(id)).contains("expects every exchange in route orders to take at most 1 second");
    }

    @Test
    void aTimeOfNoExchangeIsNotMet() {
        String id = chaos().addRule("step=callStock delay duration=1ms expect=ship:meanTime<=1s within=10s");
        clock.advance(10_001);

        JsonObject e = expectation(id);
        assertThat(e.getString("outcome")).isEqualTo("unmet");
        assertThat(chaos().report().getReasons()).singleElement().asString()
                .startsWith("route ship: no exchange done yet within 10s");
    }

    @Test
    void theMessagesLostBetweenTwoRoutes() {
        // every second order never reaches the ship route
        String id = chaos().addRule("endpoint=direct:ship drop rate=every:2 expect=orders->ship:lost<=0 within=1m");
        orders(4);
        assertThat(expectation(id).getString("outcome")).isEqualTo("pending");
        clock.advance(60_001);

        JsonObject e = expectation(id);
        assertThat(e.getString("outcome")).isEqualTo("unmet");
        assertThat(e.getString("toRoute")).isEqualTo("ship");
        assertThat(e.getLong("value")).isEqualTo(2);
        ChaosReport report = chaos().report();
        assertThat(report.getLost()).isEqualTo(2);
        assertThat(report.getReasons()).containsExactly(
                "routes orders->ship: 2 lost (4 completed in orders, 2 in ship) within 1m0s, at most 0 expected (rule " + id
                                                        + "): 2 lost");
        assertThat(chaos().describeRule(id))
                .endsWith("; expects no message lost between route orders and route ship within 1 minute");
    }

    @Test
    void theMessagesParkedByTheDeadLetterChannelAreNotLost() {
        // every second payment fails, and the dead letter channel of route charged parks the order
        String id = chaos().addRule("step=callPay error rate=every:2 expect=charged->ship:lost<=0 within=1m");
        for (int i = 0; i < 4; i++) {
            template.sendBody("direct:charged", "order " + i);
        }
        clock.advance(60_001);

        assertThat(expectation(id).getString("outcome")).isEqualTo("met");
        ChaosReport report = chaos().report();
        assertThat(report.getVerdict()).isEqualTo(ChaosReport.PASS);
        assertThat(report.getLost()).isZero();
        assertThat(report.toText()).contains("met, 0 lost (2 completed in charged, 2 in ship)");
    }

    @Test
    void nothingLostWhenTheNextRouteCompletesThemAll() {
        String id = chaos().addRule("step=callStock delay duration=10ms expect=orders->ship:lost=0 within=1m");
        orders(3);
        clock.advance(60_001);

        assertThat(expectation(id).getString("outcome")).isEqualTo("met");
        assertThat(chaos().report().toText()).contains("met, 0 lost (3 completed in orders, 3 in ship)");
    }

    @Test
    void theExampleOfTheDocumentation() {
        assertThat(chaos().addRules(
                "endpoint=http:stock* delay duration=1s..3s rate=0.3 until=1m expect=orders:meanTime<=2s within=2m;"
                                    + "endpoint=seda:shipping drop rate=0.01 until=1m expect=orders->shipping:lost<=0 within=2m"))
                .hasSize(2);
        assertThat(chaos().addRule("step=callStock delay duration=1s expect=orders:meanTime>=1s")).isNotNull();
    }

    @Test
    void theFormsThatAreRefused() {
        assertThatThrownBy(() -> chaos().addRule("step=callStock delay duration=1ms expect=orders:meanTime=2s"))
                .hasMessageContaining("needs <= or >= for a time");
        assertThatThrownBy(() -> chaos().addRule("step=callStock delay duration=1ms expect=orders:maxTime<=soon"))
                .hasMessageContaining("expect=");
        assertThatThrownBy(() -> chaos().addRule("step=callStock delay duration=1ms expect=orders:lost<=0"))
                .hasMessageContaining("counts the messages lost between two routes, such as expect=orders->shipping:lost<=0");
        assertThatThrownBy(() -> chaos().addRule("step=callStock delay duration=1ms expect=orders->ship:completed>=1"))
                .hasMessageContaining("has two routes, which only lost counts");
        assertThatThrownBy(() -> chaos().addRule("step=callStock delay duration=1ms expect=orders->ship:lost>=1"))
                .hasMessageContaining("needs <= or = for the messages lost");
        assertThatThrownBy(() -> chaos().addRule("step=callStock delay duration=1ms expect=orders->orders:lost<=0"))
                .hasMessageContaining("needs two routes");
        assertThatThrownBy(() -> chaos().addRule("step=callStock delay duration=1ms expect=orders:speed<=1"))
                .hasMessageContaining(
                        "the kinds are completed, handled, failed, redeliveries, meanTime, maxTime, lost and duplicates");
    }
}
