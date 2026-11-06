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

import java.net.SocketTimeoutException;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The faults that depend on time: an error after a wait (a timeout), a jittered delay, a start offset and bursts. A
 * virtual clock moves the time (it is static, so this class runs alone).
 */
@Isolated
class ChaosTimingFaultsTest extends ChaosTestSupport {

    @BindToRegistry("testpoll")
    private final TestPollComponent component = new TestPollComponent();

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
                from("direct:start").routeId("start").to("mock:target").id("send1").log("done").id("log1");
                from("testpoll:p?delay=5&initialDelay=0").routeId("poller").autoStartup(false).to("mock:polled");
            }
        };
    }

    private boolean sendFails() {
        return template.send("direct:start", e -> e.getMessage().setBody("x")).getException() != null;
    }

    @Test
    void anErrorAfterAWaitTimesOutWithoutCallingTheProducer() throws Exception {
        String id = chaos().addRule(
                "endpoint=mock:target error after=5s exception=" + SocketTimeoutException.class.getName());
        getMockEndpoint("mock:target").expectedMessageCount(0);

        Exchange out = template.send("direct:start", e -> e.getMessage().setBody("x"));

        assertThat(out.getException()).isInstanceOf(SocketTimeoutException.class);
        MockEndpoint.assertIsSatisfied(context);
        assertThat(clock.sleeps()).containsExactly(5000L);
        assertThat(clock.sleepers()).containsExactly(Thread.currentThread().getName());
        assertThat(fired(id, "send")).isEqualTo(1);
        assertThat(chaos().report().toText()).contains("error SocketTimeoutException after 5s at send mock://target");
    }

    @Test
    void anErrorAfterAWaitAtAStep() {
        chaos().addRule("step=log1 error after=2s");

        assertThat(template.send("direct:start", e -> e.getMessage().setBody("x")).getException())
                .isInstanceOf(ChaosException.class);
        assertThat(clock.sleeps()).containsExactly(2000L);
    }

    @Test
    void anErrorAfterAWaitAtAPoll() throws Exception {
        String id = chaos().addRule("endpoint=testpoll:p* error after=750ms at=poll until=1");
        context.getRouteController().startRoute("poller");

        await().atMost(10, TimeUnit.SECONDS).until(() -> fired(id, "poll") == 1);
        assertThat(clock.sleeps()).startsWith(750L);
    }

    @Test
    void aJitteredDelayDrawsBetweenItsTimes() {
        chaos().addRule("endpoint=mock:target delay duration=100ms..2s");

        for (int i = 0; i < 20; i++) {
            template.sendBody("direct:start", "x");
        }

        List<Long> sleeps = clock.sleeps();
        assertThat(sleeps).hasSize(20).allSatisfy(d -> assertThat(d).isBetween(100L, 2000L));
        assertThat(new HashSet<>(sleeps)).hasSizeGreaterThan(10);
        assertThat(chaos().report().toText()).contains("delay 100ms..2s");
    }

    @Test
    void aRuleWithFromStartsLater() {
        String id = chaos().addRule("endpoint=mock:target error from=+30s until=1m");
        assertThat(sendFails()).isFalse();
        JsonObject waiting = ruleReport(id);
        assertThat(waiting.getString("state")).isEqualTo("waiting");
        assertThat(waiting.getLong("startsIn")).isEqualTo(30_000L);
        // a match before the start does not count
        assertThat(waiting.getLong("matches")).isZero();
        assertThat(chaos().report().toText()).contains(id + " waiting: ").contains("starts in 30s");

        clock.advance(30_000);
        assertThat(sendFails()).isTrue();
        JsonObject active = ruleReport(id);
        assertThat(active.getString("state")).isEqualTo("active");
        // until counts from the start
        assertThat(active.getLong("endsIn")).isEqualTo(60_000L);

        clock.advance(60_000);
        assertThat(sendFails()).isFalse();
        assertThat(ruleReport(id).getString("state")).isEqualTo("ended");
    }

    @Test
    void burstsFireAtEveryMatchDuringTheFirstPartOfEachPeriod() {
        String id = chaos().addRule("endpoint=mock:target error rate=burst:5s/30s");
        assertThat(sendFails()).isTrue();
        clock.advance(4_000);
        assertThat(sendFails()).isTrue();
        assertThat(ruleReport(id).getLong("burstEndsIn")).isEqualTo(1_000L);
        assertThat(chaos().report().toText()).contains("in a burst for 1s more");

        clock.advance(1_000);
        assertThat(sendFails()).isFalse();
        assertThat(ruleReport(id).getLong("nextBurstIn")).isEqualTo(25_000L);
        clock.advance(20_000);
        assertThat(sendFails()).isFalse();
        assertThat(chaos().report().toText()).contains("rate 50% of 5s in 30s").contains("next burst in 5s");

        clock.advance(5_000);
        assertThat(sendFails()).isTrue();
        JsonObject rule = ruleReport(id);
        assertThat(rule.getLong("matches")).isEqualTo(5);
        assertThat(rule.getDouble("rate")).isEqualTo(5.0d / 30);
    }
}
