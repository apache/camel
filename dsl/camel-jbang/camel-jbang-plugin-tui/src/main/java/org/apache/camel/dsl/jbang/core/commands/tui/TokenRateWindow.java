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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * Turns a monotonically increasing token counter, sampled at irregular intervals, into a tokens-per-second rate over a
 * sliding window. The window smooths the bursts a speculative decoder produces (several tokens land at once, then
 * nothing for a step) while still dropping to zero within one window of the counter standing still.
 * <p/>
 * The counter resets when a new request starts (llama-server counts decoded tokens per request), so a sample below the
 * previous one starts a fresh baseline instead of producing a negative rate.
 */
final class TokenRateWindow {

    private final long windowMillis;
    private final Deque<long[]> samples = new ArrayDeque<>();

    TokenRateWindow(long windowMillis) {
        this.windowMillis = Math.max(1, windowMillis);
    }

    /** Records the counter value observed at {@code nowMillis}. */
    synchronized void sample(long nowMillis, long counter) {
        long[] last = samples.peekLast();
        if (last != null && counter < last[1]) {
            samples.clear();
        }
        samples.addLast(new long[] { nowMillis, counter });
        // keep exactly one sample at or before the window start as the baseline
        long cutoff = nowMillis - windowMillis;
        while (samples.size() > 2) {
            // the second sample decides whether the first is still needed as the baseline
            Iterator<long[]> it = samples.iterator();
            it.next();
            long[] second = it.next();
            if (second[0] <= cutoff) {
                samples.pollFirst();
            } else {
                break;
            }
        }
    }

    /** Tokens per second across the window, or zero when there is no movement or the last sample is stale. */
    synchronized double ratePerSecond(long nowMillis) {
        if (samples.size() < 2) {
            return 0;
        }
        long[] first = samples.peekFirst();
        long[] last = samples.peekLast();
        if (nowMillis - last[0] > windowMillis) {
            return 0;
        }
        long dt = last[0] - first[0];
        long tokens = last[1] - first[1];
        if (dt <= 0 || tokens <= 0) {
            return 0;
        }
        return tokens * 1000.0 / dt;
    }

    synchronized void clear() {
        samples.clear();
    }
}
