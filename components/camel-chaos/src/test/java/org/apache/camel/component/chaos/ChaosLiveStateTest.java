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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The live state of the rules, for the chaos screen: the observed rate next to the rate of the rule, when it last fired
 * and its faults per second, the recent faults, the exchange marked with the rule, and the effect of the faults on the
 * exchanges. The time runs on a virtual clock (it is static, so this class runs alone).
 */
@Isolated
class ChaosLiveStateTest extends ChaosTestSupport {

    private final CountDownLatch release = new CountDownLatch(1);
    private FakeClock clock;

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();
        clock = FakeClock.install();
    }

    @Override
    protected void cleanupResources() throws Exception {
        release.countDown();
        clock.close();
        super.cleanupResources();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:orders").routeId("orders")
                        .errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(2).redeliveryDelay(0))
                        .to("mock:stock").id("callStock");
                from("direct:plain").routeId("plain").errorHandler(noErrorHandler()).to("mock:stock");
                // a dead letter channel to a seda queue, which hands the completions over to its copy
                from("direct:queued").routeId("queued")
                        .errorHandler(deadLetterChannel("seda:dlq").maximumRedeliveries(1).redeliveryDelay(0))
                        .to("mock:stock");
                from("seda:dlq?pollTimeout=10").routeId("dlq").to("mock:dlq");
                from("direct:fast").routeId("fast").to("mock:fast?retainFirst=5");
                from("direct:slow").routeId("slow").to("mock:slow").process(e -> release.await(10, TimeUnit.SECONDS));
            }
        };
    }

    private void send(String uri, int n) {
        for (int i = 0; i < n; i++) {
            template.send(uri, e -> e.getMessage().setBody("order"));
        }
    }

    private JsonObject effect(String id) {
        return (JsonObject) ruleReport(id).get("effect");
    }

    private JsonArray recentFaults() {
        return (JsonArray) chaos().report().toJson().get("recentFaults");
    }

    @Test
    void theObservedRateIsNextToTheRateOfTheRule() {
        String id = chaos().addRule("endpoint=mock:fast* drop rate=every:4");
        send("direct:fast", 10);

        JsonObject rule = ruleReport(id);
        assertThat(rule.getDouble("rate")).isEqualTo(0.25d);
        assertThat(rule.getDouble("observedRate")).isEqualTo(0.2d);
        assertThat(rule.getLong("nextFireIn")).isEqualTo(2);
        assertThat(rule.getLong("lastFiredAgo")).isZero();
        assertThat(rule.getLong("faultsLastMinute")).isEqualTo(2);
        List<Object> perSecond = rule.getCollection("faultsPerSecond");
        assertThat(perSecond).hasSize(60).last().isEqualTo(2L);

        clock.advance(3_000);
        rule = ruleReport(id);
        assertThat(rule.getLong("lastFiredAgo")).isEqualTo(3_000);
        // the second of the faults moved 3 seconds back in the minute
        List<Object> moved = rule.getCollection("faultsPerSecond");
        assertThat(moved).element(56).isEqualTo(2L);
        assertThat(chaos().report().toText())
                .contains("rate 20% of 1 in 4, fires again in 2 matches, last fired 3s ago, 2 faults in the last minute");

        // a minute later the faults are out of the last minute
        clock.advance(60_000);
        rule = ruleReport(id);
        assertThat(rule.getLong("faultsLastMinute")).isZero();
        assertThat(rule.containsKey("faultsPerSecond")).isFalse();
        assertThat(rule.getLong("lastFiredAgo")).isEqualTo(63_000);
    }

    @Test
    void aRuleWithoutRateFiresAtAllItsMatches() {
        String id = chaos().addRule("endpoint=mock:fast* drop until=3");
        assertThat(ruleReport(id).getDouble("rate")).isEqualTo(1.0d);
        // no match yet: no observed rate, and no fault
        assertThat(ruleReport(id).containsKey("observedRate")).isFalse();
        assertThat(ruleReport(id).containsKey("lastFiredAgo")).isFalse();
        assertThat(ruleReport(id).containsKey("nextFireIn")).isFalse();

        send("direct:fast", 5);
        assertThat(ruleReport(id).getDouble("observedRate")).isEqualTo(1.0d);
        // the rate is not news for such a rule
        assertThat(chaos().report().toText()).doesNotContain("rate 100%")
                .contains("last fired 0ms ago, 3 faults in the last minute");
    }

    @Test
    void aRedeliveryThatSucceedsRecoversTheExchange() {
        String id = chaos().addRule("endpoint=mock:stock error until=1");
        send("direct:orders", 1);

        JsonObject effect = effect(id);
        assertThat(effect.getLong("faulted")).isEqualTo(1);
        assertThat(effect.getLong("recovered")).isEqualTo(1);
        assertThat(effect.getLong("handled")).isZero();
        assertThat(effect.getLong("inFlight")).isZero();
        assertThat(chaos().report().toText()).contains("effect on 1 exchange: 1 recovered by redelivery");
    }

    @Test
    void theDeadLetterChannelHandlesTheFaultedExchangesAndSeesTheRule() throws Exception {
        MockEndpoint dead = getMockEndpoint("mock:dead");
        dead.expectedMessageCount(2);
        String id = chaos().addRule("endpoint=mock:stock error");
        send("direct:orders", 2);
        // an exchange of a route without an error handler fails
        Exchange failed = template.send("direct:plain", e -> e.getMessage().setBody("order"));

        dead.assertIsSatisfied();
        // the exchange tells which rule faulted it, so a dead letter route (or the tracer) can tell
        assertThat(dead.getExchanges()).allSatisfy(e -> assertThat(e.getProperty(ChaosManager.CHAOS_RULE)).isEqualTo(id));
        assertThat(failed.getProperty(ChaosManager.CHAOS_RULE)).isEqualTo(id);

        JsonObject effect = effect(id);
        // faulted three times each (and two redeliveries), but counted once per exchange
        assertThat(effect.getLong("faulted")).isEqualTo(3);
        assertThat(effect.getLong("handled")).isEqualTo(2);
        assertThat(effect.getLong("failed")).isEqualTo(1);
        assertThat(effect.getLong("recovered")).isZero();
        assertThat(chaos().report().toText())
                .contains("effect on 3 exchanges: 2 handled by the error handler, 1 failed");
    }

    @Test
    void theEffectIsTheOneOfTheExchangeNotOfTheCopyOfASedaDeadLetterChannel() throws Exception {
        MockEndpoint dlq = getMockEndpoint("mock:dlq");
        dlq.expectedMessageCount(2);
        String id = chaos().addRule("endpoint=mock:stock error");
        send("direct:queued", 2);

        dlq.assertIsSatisfied();
        // the copy in the queue keeps the property
        assertThat(dlq.getExchanges()).allSatisfy(e -> assertThat(e.getProperty(ChaosManager.CHAOS_RULE)).isEqualTo(id));
        JsonObject effect = effect(id);
        assertThat(effect.getLong("handled")).isEqualTo(2);
        assertThat(effect.getLong("recovered")).isZero();
        assertThat(effect.getLong("completed")).isZero();
    }

    @Test
    void aDelayedExchangeCompletes() {
        String id = chaos().addRule("endpoint=mock:fast* delay duration=1ms");
        send("direct:fast", 2);

        assertThat(effect(id).getLong("completed")).isEqualTo(2);
        assertThat(getMockEndpoint("mock:fast").getReceivedExchanges())
                .allSatisfy(e -> assertThat(e.getProperty(ChaosManager.CHAOS_RULE)).isEqualTo(id));
    }

    @Test
    void anExchangeThatIsNotDoneIsInFlight() {
        String id = chaos().addRule("endpoint=mock:slow delay duration=1ms");
        CompletableFuture<Exchange> future = template.asyncSend("direct:slow", e -> e.getMessage().setBody("order"));

        await().atMost(10, TimeUnit.SECONDS).until(() -> effect(id) != null && effect(id).getLong("inFlight") == 1);
        assertThat(effect(id).getLong("completed")).isZero();
        assertThat(chaos().report().toText()).contains("effect on 1 exchange: 1 in flight");

        release.countDown();
        future.join();
        await().atMost(10, TimeUnit.SECONDS).until(() -> effect(id).getLong("completed") == 1);
        assertThat(effect(id).getLong("inFlight")).isZero();
    }

    @Test
    void theRecentFaultsAreTheLatestFirst() {
        String id = chaos().addRule("endpoint=mock:fast* drop");
        Exchange last = null;
        for (int i = 0; i < 25; i++) {
            last = template.send("direct:fast", e -> e.getMessage().setBody("order"));
        }

        JsonArray recent = recentFaults();
        assertThat(recent).hasSize(ChaosRecentFaults.SIZE);
        JsonObject latest = (JsonObject) recent.get(0);
        assertThat(latest.getString("rule")).isEqualTo(id);
        assertThat(latest.getInteger("version")).isEqualTo(1);
        assertThat(latest.getString("fault")).isEqualTo("drop");
        assertThat(latest.getString("hook")).isEqualTo("send");
        assertThat(latest.getString("route")).isEqualTo("fast");
        // without the query of the endpoint, which can hold secrets
        assertThat(latest.getString("endpoint")).isEqualTo("mock://fast");
        assertThat(latest.getString("exchangeId")).isEqualTo(last.getExchangeId());
        assertThat(latest.getLong("timestamp")).isEqualTo(clock.currentTimeMillis());
        assertThat(latest.getLong("ago")).isZero();
        assertThat(chaos().report().toText()).contains("recent faults:\n    0ms ago " + id
                                                       + " drop at send mock://fast, route fast, exchange "
                                                       + last.getExchangeId());

        // a reset starts a new report, without the faults
        chaos().reset();
        assertThat(recentFaults()).isEmpty();
        assertThat(chaos().report().toText()).doesNotContain("recent faults");
    }

    @Test
    void aFaultAtAStepOrAtTheEntryOfARoute() {
        String step = chaos().addRule("step=callStock error until=1");
        Exchange exchange = template.send("direct:orders", e -> e.getMessage().setBody("order"));

        JsonObject fault = (JsonObject) recentFaults().get(0);
        assertThat(fault.getString("rule")).isEqualTo(step);
        assertThat(fault.getString("hook")).isEqualTo("step");
        assertThat(fault.getString("step")).isEqualTo("callStock");
        assertThat(fault.getString("route")).isEqualTo("orders");
        assertThat(fault.getString("fault")).isEqualTo("error ChaosException");
        assertThat(fault.containsKey("endpoint")).isFalse();
        assertThat(fault.getString("exchangeId")).isEqualTo(exchange.getExchangeId());

        String entry = chaos().addRule("route=fast corrupt mode=headers");
        template.send("direct:fast", e -> e.getMessage().setHeader("country", "DK"));
        fault = (JsonObject) recentFaults().get(0);
        assertThat(fault.getString("rule")).isEqualTo(entry);
        assertThat(fault.getString("hook")).isEqualTo("entry");
        assertThat(fault.getString("route")).isEqualTo("fast");
        assertThat(chaos().report().toText()).contains(entry + " corrupt headers at the entry of route fast, exchange ");
    }
}
