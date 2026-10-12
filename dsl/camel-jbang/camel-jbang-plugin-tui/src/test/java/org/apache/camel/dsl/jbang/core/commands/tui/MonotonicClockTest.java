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

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class MonotonicClockTest {

    @Test
    void neverGoesBack() {
        long previous = MonotonicClock.millis();
        for (int i = 0; i < 100_000; i++) {
            long now = MonotonicClock.millis();
            assertThat(now).isGreaterThanOrEqualTo(previous);
            previous = now;
        }
    }

    @Test
    void movesOnWithTheTimeThatPasses() {
        long start = MonotonicClock.millis();
        long startNanos = System.nanoTime();
        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> System.nanoTime() - startNanos >= TimeUnit.MILLISECONDS.toNanos(50));

        assertThat(MonotonicClock.millis() - start).isGreaterThanOrEqualTo(50);
    }

    @Test
    void aTimeNeverSetReadsAsLongAgo() {
        // a time field starts at 0: an interval since then has passed, as it has on the wall clock
        long neverSet = 0;
        assertThat(MonotonicClock.millis() - neverSet).isGreaterThanOrEqualTo(TimeUnit.DAYS.toMillis(1));
    }
}
