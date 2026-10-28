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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import dev.tamboui.style.Style;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The timers run on the {@link MonotonicClock}, so a wall clock set back or forward (an NTP step, a manual change, a VM
 * resume) neither freezes them nor fires them at once. The times here are far from any wall-clock time: a timer that
 * read the wall clock would fire at once, or never.
 */
class MonotonicTimersTest {

    /** A clock the tests set by hand. */
    private static final class FakeClock {
        long now;
    }

    @Test
    void aPendingMcpKeyFiresOnTime() {
        Queue<McpFacade.PendingKey> keys = new ConcurrentLinkedQueue<>();
        McpFacade facade = new McpFacade(null, null, null, null, null, null, null, null, null, null, null, keys, null);

        long before = MonotonicClock.millis();
        assertThat(facade.injectKeys(List.of("a", "Enter"), 500)).isEqualTo(2);
        long after = MonotonicClock.millis();

        // the tick compares the keys with the monotonic time: the first fires at once, the next half a second later
        McpFacade.PendingKey first = keys.poll();
        McpFacade.PendingKey second = keys.poll();
        assertThat(first.fireAt()).isBetween(before, after);
        assertThat(first.isDue(after)).isTrue();
        assertThat(second.isDue(first.fireAt() + 499)).isFalse();
        assertThat(second.isDue(first.fireAt() + 500)).isTrue();
    }

    @Test
    void aCaptionExpiresOnItsClock() {
        FakeClock clock = new FakeClock();
        clock.now = 5_000;
        CaptionOverlay caption = new CaptionOverlay(() -> clock.now);

        caption.showCaption("Hello", 2);
        clock.now += 2_000;
        caption.tick(clock.now);
        assertThat(caption.isCaptionVisible()).as("shown for two seconds").isTrue();
        clock.now += 1;
        caption.tick(clock.now);
        assertThat(caption.isCaptionVisible()).isFalse();

        // without a duration: typed a character every 50 ms, then held for three seconds and faded out in one
        caption.showCaption("Hi");
        clock.now += 100;
        caption.tick(clock.now);
        clock.now += 4_000;
        caption.tick(clock.now);
        assertThat(caption.isCaptionVisible()).as("held and fading").isTrue();
        clock.now += 1;
        caption.tick(clock.now);
        assertThat(caption.isCaptionVisible()).isFalse();
    }

    @Test
    void aDrawingExpiresOnTime() {
        DrawOverlay overlay = new DrawOverlay();
        long before = MonotonicClock.millis();
        overlay.setDrawing(List.of(new DrawOverlay.DrawCell(0, 0, "x", Style.EMPTY)), 1);
        long after = MonotonicClock.millis();

        // the tick passes the monotonic time: the drawing shows for its second, then goes
        overlay.tick(before + 1_000);
        assertThat(overlay.isVisible()).isTrue();
        overlay.tick(after + 1_001);
        assertThat(overlay.isVisible()).isFalse();
    }
}
