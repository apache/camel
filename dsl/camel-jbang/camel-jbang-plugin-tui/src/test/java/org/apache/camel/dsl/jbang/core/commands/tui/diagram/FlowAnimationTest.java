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
package org.apache.camel.dsl.jbang.core.commands.tui.diagram;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.diagram.RouteDiagramLayoutEngine.StatInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The messages moving through a route diagram: the rates from the counts the diagram reloads, the heat of the steps,
 * the stuck steps, and the frames drawn only while something moves.
 */
class FlowAnimationTest {

    private final AtomicLong clock = new AtomicLong(10_000);
    private final FlowAnimation flow = new FlowAnimation(clock::get);

    @Test
    void theRateIsTheChangeOfTheCountsBetweenTwoReloads() {
        flow.update(Map.of("a", stat(100, 0, 0)));
        assertThat(flow.get("a").rate()).isZero();

        clock.addAndGet(1000);
        flow.update(Map.of("a", stat(150, 5, 0)));

        assertThat(flow.get("a").rate()).isCloseTo(50, within(0.01));
        assertThat(flow.get("a").failRate()).isCloseTo(5, within(0.01));
    }

    @Test
    void aRateInFitsAndStartsIsSmoothedButStopsAtOnce() {
        flow.update(Map.of("a", stat(0, 0, 0)));
        clock.addAndGet(1000);
        flow.update(Map.of("a", stat(10, 0, 0)));
        clock.addAndGet(1000);
        flow.update(Map.of("a", stat(10 + 2, 0, 0)));
        assertThat(flow.get("a").rate()).as("between the two readings").isBetween(2.0, 10.0);

        clock.addAndGet(1000);
        flow.update(Map.of("a", stat(12, 0, 0)));
        assertThat(flow.get("a").rate()).as("no messages").isZero();
    }

    @Test
    void theFramesBetweenTwoReloadsKeepTheRate() {
        StatInfo first = stat(0, 0, 0);
        flow.update(Map.of("a", first));
        clock.addAndGet(1000);
        StatInfo second = stat(20, 0, 0);
        flow.update(Map.of("a", second));
        double phase = flow.get("a").phase();

        clock.addAndGet(FlowAnimation.FRAME_MS);
        flow.update(Map.of("a", second));

        assertThat(flow.get("a").rate()).isCloseTo(20, within(0.01));
        assertThat(flow.get("a").phase()).as("the dots moved on").isGreaterThan(phase);
    }

    @Test
    void withNoNewCountsNothingMoves() {
        StatInfo stat = stat(0, 0, 0);
        flow.update(Map.of("a", stat));
        clock.addAndGet(1000);
        StatInfo busy = stat(30, 0, 0);
        flow.update(Map.of("a", busy));

        clock.addAndGet(FlowAnimation.STALE_MS + 1);
        flow.update(Map.of("a", busy));

        assertThat(flow.get("a").rate()).isZero();
    }

    @Test
    void nothingMovesWhenTheMessagesStop() {
        flow.update(Map.of("a", stat(0, 0, 0)));
        for (int i = 1; i <= 5; i++) {
            clock.addAndGet(1000);
            flow.update(Map.of("a", stat(50L * i, 0, 0)));
        }
        assertThat(flow.isAnimating()).isTrue();

        // the counts stay the same: no messages
        clock.addAndGet(1000);
        flow.update(Map.of("a", stat(250, 0, 0)));

        assertThat(flow.get("a").rate()).isZero();
        assertThat(flow.isAnimating()).isFalse();
    }

    @Test
    void aStepWhereExchangesWaitWithNoneFinishingIsStuck() {
        flow.update(Map.of("a", stat(10, 0, 0)));
        clock.addAndGet(1000);
        flow.update(Map.of("a", stat(10, 0, 3)));
        FlowAnimation.Track t = flow.get("a");
        assertThat(flow.stuckMillis(t)).as("not yet: waiting a second can be a slow call").isEqualTo(-1);

        clock.addAndGet(1000);
        flow.update(Map.of("a", stat(10, 0, 4)));
        clock.addAndGet(1000);
        flow.update(Map.of("a", stat(10, 0, 4)));

        assertThat(flow.stuckMillis(t)).isGreaterThanOrEqualTo(FlowAnimation.STUCK_MS);
        assertThat(t.inflight()).isEqualTo(4);
        assertThat(flow.isAnimating()).as("the spinner turns").isTrue();

        // one finishes: no longer stuck
        clock.addAndGet(1000);
        flow.update(Map.of("a", stat(11, 0, 3)));
        assertThat(flow.stuckMillis(t)).isEqualTo(-1);
    }

    @Test
    void aFrameIsDueOnlyWhileSomethingMoves() {
        StatInfo quiet = stat(5, 0, 0);
        flow.update(Map.of("a", quiet));
        clock.addAndGet(1000);
        flow.update(Map.of("a", stat(5, 0, 0)));
        clock.addAndGet(FlowAnimation.FRAME_MS);
        assertThat(flow.frameDue()).as("no messages: nothing moves").isFalse();

        clock.addAndGet(1000);
        flow.update(Map.of("a", stat(25, 0, 0)));
        assertThat(flow.frameDue()).as("just drawn").isFalse();
        clock.addAndGet(FlowAnimation.FRAME_MS);
        assertThat(flow.frameDue()).isTrue();

        flow.setEnabled(false);
        assertThat(flow.frameDue()).as("turned off").isFalse();
        assertThat(flow.get("a")).isNull();
    }

    @Test
    void theDotsAreCloserTogetherTheMoreMessagesThereAre() {
        assertThat(FlowAnimation.spacing(1)).isGreaterThan(FlowAnimation.spacing(100));
        assertThat(FlowAnimation.spacing(0.1)).isLessThanOrEqualTo(14);
        assertThat(FlowAnimation.spacing(1_000_000)).isEqualTo(4);
        assertThat(FlowAnimation.speed(100)).isGreaterThan(FlowAnimation.speed(1));
        assertThat(FlowAnimation.speed(0)).isZero();
    }

    private static StatInfo stat(long total, long failed, long inflight) {
        StatInfo s = new StatInfo();
        s.exchangesTotal = total;
        s.exchangesFailed = failed;
        s.exchangesInflight = inflight;
        return s;
    }
}
