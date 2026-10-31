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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.component.chaos.ChaosRule.Expectation.Outcome;
import org.apache.camel.component.chaos.ChaosRule.State;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChaosRuleTest {

    private static final long MAX = TimeUnit.MINUTES.toMillis(10);
    private static final long T0 = 1_000_000L;

    private static ChaosRule rule(String text, String id, long seed) {
        return new ChaosRule(ChaosRuleParser.parse(text, new DefaultClassResolver(), MAX), id, seed, T0, MAX);
    }

    private static List<Boolean> draws(ChaosRule rule, int n) {
        List<Boolean> answer = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            answer.add(rule.fires(T0));
        }
        return answer;
    }

    @Test
    void everyMatchFiresWithoutRate() {
        ChaosRule r = rule("endpoint=x error", "r1", 1);
        assertThat(draws(r, 5)).containsOnly(true);
        assertThat(r.matches()).isEqualTo(5);
    }

    @Test
    void everyNthMatch() {
        ChaosRule r = rule("endpoint=x error rate=every:3", "r1", 1);
        assertThat(draws(r, 7)).containsExactly(false, false, true, false, false, true, false);
    }

    @Test
    void untilCountEndsAfterThatManyMatches() {
        ChaosRule r = rule("endpoint=x error until=3", "r1", 1);
        assertThat(draws(r, 5)).containsExactly(true, true, true, false, false);
        // the matches after the end do not count
        assertThat(r.matches()).isEqualTo(3);
        assertThat(r.state(T0)).isEqualTo(State.ENDED);
    }

    @Test
    void untilDurationEndsTheRule() {
        ChaosRule r = rule("endpoint=x error until=1m", "r1", 1);
        assertThat(r.fires(T0 + 59_999)).isTrue();
        assertThat(r.state(T0 + 59_999)).isEqualTo(State.ACTIVE);
        assertThat(r.fires(T0 + 60_000)).isFalse();
        assertThat(r.state(T0 + 60_000)).isEqualTo(State.ENDED);
        assertThat(r.matches()).isEqualTo(1);
    }

    @Test
    void withoutUntilTheRuleEndsAfterMaxDuration() {
        ChaosRule r = rule("endpoint=x error until=5", "r1", 1);
        assertThat(r.endsAt).isEqualTo(T0 + MAX);
        assertThat(r.fires(T0 + MAX)).isFalse();
    }

    @Test
    void aVeryLongMaxDurationMeansNoEnd() {
        ChaosRule r = new ChaosRule(
                ChaosRuleParser.parse("endpoint=x error expect=o:1", new DefaultClassResolver(),
                        Long.MAX_VALUE),
                "r1", 1, T0, Long.MAX_VALUE);
        assertThat(r.endsAt).isEqualTo(Long.MAX_VALUE);
        assertThat(r.expectation.deadline).isEqualTo(Long.MAX_VALUE);
        assertThat(r.fires(T0)).isTrue();
        assertThat(r.state(T0)).isEqualTo(State.ACTIVE);
        assertThat(r.expectation.outcome(T0)).isEqualTo(Outcome.PENDING);
    }

    @Test
    void theSeedFixesTheSequenceOfEachRule() {
        List<Boolean> a = draws(rule("endpoint=x error rate=0.5", "r1", 42), 200);
        List<Boolean> b = draws(rule("endpoint=x error rate=0.5", "r1", 42), 200);
        List<Boolean> otherSeed = draws(rule("endpoint=x error rate=0.5", "r1", 43), 200);
        List<Boolean> otherRule = draws(rule("endpoint=x error rate=0.5", "r2", 42), 200);
        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(otherSeed);
        assertThat(a).isNotEqualTo(otherRule);
        // about half fire
        long fired = a.stream().filter(x -> x).count();
        assertThat(fired).isBetween(70L, 130L);
    }

    @Test
    void untilCountHoldsUnderConcurrentMatches() throws Exception {
        ChaosRule r = rule("endpoint=x error until=1000", "r1", 1);
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger fired = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int t = 0; t < threads; t++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < 500; i++) {
                        if (r.fires(T0)) {
                            fired.incrementAndGet();
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(fired.get()).isEqualTo(1000);
        assertThat(r.matches()).isEqualTo(1000);
    }

    @Test
    void expectationMetUnmetPending() {
        ChaosRule.Expectation met = new ChaosRule.Expectation("orders", 2, T0 + 100);
        met.completeAt(T0 + 10);
        assertThat(met.outcome(T0 + 10)).isEqualTo(Outcome.PENDING);
        met.completeAt(T0 + 100);
        assertThat(met.outcome(T0 + 1000)).isEqualTo(Outcome.MET);

        ChaosRule.Expectation unmet = new ChaosRule.Expectation("orders", 2, T0 + 100);
        unmet.completeAt(T0 + 10);
        // a completion after the deadline does not count
        unmet.completeAt(T0 + 101);
        assertThat(unmet.completed()).isEqualTo(1);
        assertThat(unmet.outcome(T0 + 100)).isEqualTo(Outcome.PENDING);
        assertThat(unmet.outcome(T0 + 101)).isEqualTo(Outcome.UNMET);

        ChaosRule.Expectation removed = new ChaosRule.Expectation("orders", 2, T0 + 100);
        removed.freeze(T0 + 50);
        removed.completeAt(T0 + 60);
        assertThat(removed.completed()).isZero();
        // removed before it was decided: pending for good, never unmet
        assertThat(removed.outcome(T0 + 1000)).isEqualTo(Outcome.PENDING);

        ChaosRule.Expectation removedLate = new ChaosRule.Expectation("orders", 2, T0 + 100);
        removedLate.freeze(T0 + 200);
        // removed after the deadline: it was unmet already
        assertThat(removedLate.outcome(T0 + 1000)).isEqualTo(Outcome.UNMET);
    }

    @Test
    void withinDefaultsToTheUntilDurationElseMaxDuration() {
        assertThat(rule("endpoint=x error until=1m expect=o:1", "r1", 1).within).isEqualTo(60_000L);
        assertThat(rule("endpoint=x error until=5 expect=o:1", "r1", 1).within).isEqualTo(MAX);
        assertThat(rule("endpoint=x error until=1m expect=o:1 within=2m", "r1", 1).within).isEqualTo(120_000L);
        assertThat(rule("endpoint=x error until=1m expect=o:1 within=2m", "r1", 1).expectation.deadline)
                .isEqualTo(T0 + 120_000L);
    }
}
