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

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.TimeSource;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-26701: a circuit breaker runs its steps on a copy of the exchange. The effect of a fault in it is how the
 * exchange was done: completed when the fallback took over, handled by the dead letter channel, or recovered by a
 * redelivery; once per message, also when the circuit breaker runs again at a redelivery. And a fault that a redelivery
 * recovered does not mark a later failure of the exchange.
 */
class ChaosCircuitBreakerEffectTest extends ChaosTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:fallback").routeId("fallback")
                        .circuitBreaker()
                        .to("mock:pay")
                        .onFallback()
                        .setBody(constant("payment later"))
                        .end()
                        .to("mock:shipped");

                // the error handler of a circuit breaker without a fallback is the one of the route that calls it
                from("direct:noFallback").routeId("noFallback")
                        .errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(2).redeliveryDelay(0))
                        .to("direct:pay");
                from("direct:pay").routeId("pay")
                        .errorHandler(noErrorHandler())
                        .circuitBreaker()
                        .to("mock:pay2")
                        .end()
                        .to("mock:shipped2");

                // a step faulted and recovered by a redelivery, then a failure that chaos did not make
                from("direct:later").routeId("later")
                        .errorHandler(deadLetterChannel("mock:dead3").maximumRedeliveries(1).redeliveryDelay(0))
                        .to("mock:stock").id("callStock")
                        .process(e -> {
                            e.getMessage().setHeader("chaosRule", e.getProperty(ChaosManager.CHAOS_RULE));
                            throw new IllegalStateException("a real failure");
                        });
            }
        };
    }

    private JsonObject effect(String id) {
        return (JsonObject) ruleReport(id).get("effect");
    }

    @Test
    void theFallbackTookOverSoTheExchangeCompleted() throws Exception {
        String id = chaos().addRule("endpoint=mock:pay error");
        MockEndpoint shipped = getMockEndpoint("mock:shipped");
        shipped.expectedBodiesReceived("payment later");

        template.sendBody("direct:fallback", "order");

        MockEndpoint.assertIsSatisfied(context);
        JsonObject effect = effect(id);
        assertThat(effect.getLong("faulted")).isEqualTo(1);
        assertThat(effect.getLong("completed")).isEqualTo(1);
        assertThat(effect.getLong("failed")).isZero();
        assertThat(effect.getLong("inFlight")).isZero();
    }

    @Test
    void withoutAFallbackTheDeadLetterChannelHandledOneMessage() throws Exception {
        String id = chaos().addRule("endpoint=mock:pay2 error");
        getMockEndpoint("mock:dead").expectedMessageCount(1);

        template.sendBody("direct:noFallback", "order");

        MockEndpoint.assertIsSatisfied(context);
        JsonObject effect = effect(id);
        // the circuit breaker ran three times (the route that calls it redelivered twice), on three copies: one message
        assertThat(effect.getLong("faulted")).isEqualTo(1);
        assertThat(effect.getLong("handled")).isEqualTo(1);
        assertThat(effect.getLong("failed")).isZero();
        assertThat(effect.getLong("inFlight")).isZero();
    }

    @Test
    void aRedeliveryThatSucceedsAfterTheFaultIsRecovered() throws Exception {
        String id = chaos().addRule("endpoint=mock:pay2 error until=1");
        getMockEndpoint("mock:shipped2").expectedMessageCount(1);

        template.sendBody("direct:noFallback", "order");

        MockEndpoint.assertIsSatisfied(context);
        JsonObject effect = effect(id);
        assertThat(effect.getLong("faulted")).isEqualTo(1);
        assertThat(effect.getLong("recovered")).isEqualTo(1);
        assertThat(effect.getLong("failed")).isZero();
    }

    @Test
    void aRecoveredFaultDoesNotMarkALaterFailure() throws Exception {
        chaos().addRule("endpoint=mock:stock error until=1");
        MockEndpoint dead = getMockEndpoint("mock:dead3");
        dead.expectedMessageCount(1);

        template.sendBody("direct:later", "order");

        MockEndpoint.assertIsSatisfied(context);
        Exchange failed = dead.getReceivedExchanges().get(0);
        // the fault at the stock call was redelivered and recovered: the real failure after it is not chaos
        assertThat(failed.getMessage().getHeader("chaosRule")).isNull();
        assertThat(failed.getProperty(ChaosManager.CHAOS_RULE)).isNull();
    }

    @Test
    void theFaultsOfACopyCountOnceWithItsOutcomeWhenTheExchangeIsNotDoneInAMinute() {
        ChaosRule rule = new ChaosRule(
                ChaosRuleParser.parse("endpoint=mock:pay error", context.getClassResolver(), TimeUnit.MINUTES.toMillis(10)),
                "r9", 1, 0, TimeUnit.MINUTES.toMillis(10));
        ChaosExchangeState.Faults faults = new ChaosExchangeState.Faults();
        faults.add(rule);
        long now = TimeSource.currentTimeMillis();
        assertThat(chaos().waitForOriginal("ex-1", faults, false, true)).isTrue();
        // the circuit breaker ran again for the same exchange: one message
        ChaosExchangeState.Faults again = new ChaosExchangeState.Faults();
        again.add(rule);
        chaos().waitForOriginal("ex-1", again, false, true);

        chaos().countWaitedTooLong(now + 30_000);
        assertThat(chaos().isWaitingForOriginals()).isTrue();
        assertThat(rule.effect.count(ChaosRule.Effect.Outcome.HANDLED)).isZero();

        chaos().countWaitedTooLong(now + 120_000);
        chaos().countWaitedTooLong(now + 240_000);
        assertThat(chaos().isWaitingForOriginals()).isFalse();
        assertThat(rule.effect.faultedCount()).isEqualTo(1);
        assertThat(rule.effect.count(ChaosRule.Effect.Outcome.HANDLED)).isEqualTo(1);
    }
}
