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
package org.apache.camel.util;

import java.time.Instant;

/**
 * The clock and the sleeps of the Camel core code that waits, times tasks or throttles (for example the redelivery
 * delay, the wait loop of the graceful shutdown, {@link StopWatch} and the throttlers).
 * <p>
 * <b>Internal:</b> this is not an API for Camel users or components. The default provider delegates to
 * {@link System#currentTimeMillis()}, {@link System#nanoTime()}, {@link Instant#now()} and {@link Thread#sleep(long)}.
 * Only a test harness, such as the deterministic simulation tests of camel-test-dst, installs another provider with
 * {@link #install(Provider)}, for example a virtual clock, and puts the previous one back when the test is done.
 * <p>
 * The provider is static rather than per {@code CamelContext}, because {@link StopWatch} and the other timers have no
 * context. Event timestamps and the direct clock reads of the debugger, the tracer, management and the developer
 * consoles stay on {@link System}; the durations they measure with {@link StopWatch} or {@code MonotonicClock} follow
 * this class (with the default provider this is the same {@link System#nanoTime()}).
 */
public final class TimeSource {

    /**
     * The clock and the sleep behind {@link TimeSource}. Implemented only by test harnesses.
     */
    public interface Provider {

        /**
         * The current time in milliseconds since the epoch, as {@link System#currentTimeMillis()}.
         */
        long currentTimeMillis();

        /**
         * A monotonic time in nanoseconds with an arbitrary origin, as {@link System#nanoTime()}: only the difference
         * of two values means something.
         */
        long nanoTime();

        /**
         * The current instant, as {@link Instant#now()}. By default {@link #currentTimeMillis()} as an instant.
         */
        default Instant instant() {
            return Instant.ofEpochMilli(currentTimeMillis());
        }

        /**
         * Sleeps for the given milliseconds, as {@link Thread#sleep(long)}.
         *
         * @throws IllegalArgumentException if the value of millis is negative
         * @throws InterruptedException     if the thread is interrupted before or while it sleeps; the interrupted
         *                                  status of the thread is cleared when this exception is thrown
         */
        void sleep(long millis) throws InterruptedException;
    }

    private static final Provider SYSTEM = new SystemProvider();

    private static volatile Provider provider = SYSTEM;

    private TimeSource() {
    }

    /**
     * The current time in milliseconds since the epoch: {@link System#currentTimeMillis()} unless a test harness
     * installed another provider.
     */
    public static long currentTimeMillis() {
        return provider.currentTimeMillis();
    }

    /**
     * A monotonic time in nanoseconds with an arbitrary origin: {@link System#nanoTime()} unless a test harness
     * installed another provider. Only the difference of two values means something.
     */
    public static long nanoTime() {
        return provider.nanoTime();
    }

    /**
     * The current instant: {@link Instant#now()} unless a test harness installed another provider.
     */
    public static Instant instant() {
        return provider.instant();
    }

    /**
     * Sleeps for the given milliseconds: {@link Thread#sleep(long)} unless a test harness installed another provider.
     *
     * @throws IllegalArgumentException if the value of millis is negative
     * @throws InterruptedException     if the thread is interrupted before or while it sleeps; the interrupted status
     *                                  of the thread is cleared when this exception is thrown
     */
    public static void sleep(long millis) throws InterruptedException {
        provider.sleep(millis);
    }

    /**
     * Installs a provider, for test harnesses only.
     *
     * @param  provider the provider to use from now on
     * @return          the provider that was installed before, to be installed again when the test is done
     */
    public static Provider install(Provider provider) {
        ObjectHelper.notNull(provider, "provider");
        synchronized (TimeSource.class) {
            Provider previous = TimeSource.provider;
            TimeSource.provider = provider;
            return previous;
        }
    }

    /**
     * Installs the default provider again ({@link System} and {@link Thread#sleep(long)}), for test harnesses only.
     */
    public static void reset() {
        install(SYSTEM);
    }

    /**
     * Whether the default provider ({@link System} and {@link Thread#sleep(long)}) is installed.
     */
    public static boolean isDefault() {
        return provider == SYSTEM;
    }

    /**
     * The default provider: {@link System} and {@link Thread#sleep(long)}.
     */
    private static final class SystemProvider implements Provider {

        @Override
        public long currentTimeMillis() {
            return System.currentTimeMillis();
        }

        @Override
        public long nanoTime() {
            return System.nanoTime();
        }

        @Override
        public Instant instant() {
            return Instant.now();
        }

        @Override
        public void sleep(long millis) throws InterruptedException {
            Thread.sleep(millis);
        }

        @Override
        public String toString() {
            return "SystemTimeSource";
        }
    }
}
