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

/**
 * The clock of the TUI's timers, deadlines and animations: milliseconds that only move forward, read from
 * {@link System#nanoTime()}. The wall clock ({@link System#currentTimeMillis()}) can be set back or forward (an NTP
 * step, a manual change, a VM resume), and a timer measured with it then freezes until the clock catches up, or fires
 * at once.
 * <p/>
 * A time of this clock is no time of day: compare it only with another time of this clock, never with an epoch time
 * from the wall clock, a file or another process.
 */
final class MonotonicClock {

    // the clock starts a day in, so a time still at 0 (never set) reads as long ago, as it does on the wall clock
    private static final long ORIGIN = System.nanoTime() - TimeUnit.DAYS.toNanos(1);

    private MonotonicClock() {
    }

    /** The time in milliseconds: at least a day, and never less than a time read before. */
    static long millis() {
        // nanoTime is compared only by its difference, which is right even when its value overflows
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - ORIGIN);
    }
}
