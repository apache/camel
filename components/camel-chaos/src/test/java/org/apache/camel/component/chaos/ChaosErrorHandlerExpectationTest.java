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

/**
 * Expectations on the error handler of a route: how many exchanges it handled (the dead letter channel), failed, or
 * redelivered, at least, at most or exactly. The time of the expectations runs on a virtual clock (it is static, so
 * this class runs alone).
 */
@Isolated
class ChaosErrorHandlerExpectationTest extends ChaosTestSupport {

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
                        .errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(2).redeliveryDelay(0))
                        .to("mock:stock");
                from("direct:plain").routeId("plain").errorHandler(noErrorHandler()).to("mock:stock");
            }
        };
    }

    private void send(int n) {
        for (int i = 0; i < n; i++) {
            template.send("direct:orders", e -> e.getMessage().setBody("order"));
        }
    }

    private JsonObject expectation(String id) {
        return (JsonObject) ruleReport(id).get("expectation");
    }

    @Test
    void redeliveryRecoversEverythingSoNothingReachesTheDeadLetterChannel() {
        String id = chaos().addRule("endpoint=mock:stock error rate=every:3 expect=orders:handled<=0 within=1m");
        send(5);

        // nothing handled yet, but at most is decided when the time is up
        assertThat(expectation(id).getString("outcome")).isEqualTo("pending");
        clock.advance(60_001);

        JsonObject e = expectation(id);
        assertThat(e.getString("outcome")).isEqualTo("met");
        assertThat(e.getString("kind")).isEqualTo("handled");
        assertThat(e.getString("op")).isEqualTo("<=");
        assertThat(e.getLong("value")).isZero();
        assertThat(e.containsKey("completed")).isFalse();
        assertThat(chaos().report().getVerdict()).isEqualTo(ChaosReport.PASS);
    }

    @Test
    void anAtMostIsUnmetAsSoonAsTheCountGoesOver() {
        String id = chaos().addRule("endpoint=mock:stock error expect=orders:handled<=0 within=1m");
        send(1);

        // decided before the time is up
        JsonObject e = expectation(id);
        assertThat(e.getString("outcome")).isEqualTo("unmet");
        assertThat(e.getLong("value")).isEqualTo(1);
        ChaosReport report = chaos().report();
        assertThat(report.getVerdict()).isEqualTo(ChaosReport.FAIL);
        assertThat(report.isFinal()).isTrue();
        assertThat(report.getReasons()).containsExactly(
                "route orders: 1 exchange handled by the error handler within 1m0s, at most 0 expected (rule " + id
                                                        + ")");
        // the message reached the dead letter channel: not lost
        assertThat(report.getLost()).isZero();
        assertThat(report.toText()).contains("expect orders:handled<=0 within 1m0s: unmet, 1 handled");
    }

    @Test
    void anAtLeastOfHandledExchangesIsMetWhenReached() {
        String id = chaos().addRule("endpoint=mock:stock error expect=orders:handled>=2 within=1m");
        send(1);
        assertThat(expectation(id).getString("outcome")).isEqualTo("pending");
        send(1);
        assertThat(expectation(id).getString("outcome")).isEqualTo("met");

        String unmet = chaos().addRule("endpoint=nothing error expect=orders:handled>=5 within=1m");
        clock.advance(60_001);
        assertThat(expectation(unmet).getString("outcome")).isEqualTo("unmet");
        assertThat(chaos().report().getReasons()).containsExactly(
                "route orders: 0 exchanges handled by the error handler within 1m0s, at least 5 expected (rule " + unmet
                                                                  + ")");
    }

    @Test
    void redeliveriesAreCounted() {
        String id = chaos().addRule("endpoint=mock:stock error expect=orders:redeliveries<=3 within=1m");
        send(1);
        assertThat(expectation(id).getLong("value")).isEqualTo(2);
        assertThat(expectation(id).getString("outcome")).isEqualTo("pending");
        send(1);
        assertThat(expectation(id).getString("outcome")).isEqualTo("unmet");
        assertThat(chaos().report().getReasons()).singleElement().asString()
                .isEqualTo("route orders: 4 redeliveries within 1m0s, at most 3 expected (rule " + id + ")");
    }

    @Test
    void failedExchangesAreCounted() {
        String handled = chaos().addRule("endpoint=nothing error expect=orders:failed=0 within=1m");
        String failed = chaos().addRule("endpoint=mock:stock error expect=plain:failed>=1 within=1m");
        send(1);
        template.send("direct:plain", e -> e.getMessage().setBody("x"));

        assertThat(expectation(failed).getString("outcome")).isEqualTo("met");
        clock.advance(60_001);
        // the dead letter channel handled the failure of orders: not failed
        assertThat(expectation(handled).getString("outcome")).isEqualTo("met");
    }

    @Test
    void anExactCountIsDecidedWhenTheTimeIsUp() {
        String id = chaos().addRule("endpoint=nothing error expect=orders:completed=2 within=1m");
        send(2);
        assertThat(expectation(id).getString("outcome")).isEqualTo("pending");
        clock.advance(60_001);
        assertThat(expectation(id).getString("outcome")).isEqualTo("met");

        String over = chaos().addRule("endpoint=nothing error expect=orders:completed=1 within=1m");
        send(2);
        assertThat(expectation(over).getString("outcome")).isEqualTo("unmet");
        // more completed than expected: nothing lost
        assertThat(chaos().report().getLost()).isZero();

        String under = chaos().addRule("endpoint=nothing error expect=orders:completed=5 within=1m");
        send(3);
        clock.advance(60_001);
        // fewer completed than expected: the missing ones are lost, as for at least
        assertThat(expectation(under).getString("outcome")).isEqualTo("unmet");
        assertThat(chaos().report().getLost()).isEqualTo(2);
    }

    @Test
    void aCountAloneIsAtLeastThatManyCompletedExchanges() {
        String id = chaos().addRule("endpoint=nothing error expect=orders:2 within=1m");
        send(2);

        JsonObject e = expectation(id);
        assertThat(e.getString("outcome")).isEqualTo("met");
        assertThat(e.getString("kind")).isEqualTo("completed");
        assertThat(e.getString("op")).isEqualTo(">=");
        assertThat(e.getLong("value")).isEqualTo(2);
        assertThat(e.getLong("completed")).isEqualTo(2);
        assertThat(chaos().report().toText()).contains("expect orders:2 within 1m0s: met, 2 completed");
    }
}
