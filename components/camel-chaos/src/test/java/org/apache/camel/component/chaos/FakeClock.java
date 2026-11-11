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
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.util.TimeSource;

/**
 * A virtual clock for the tests: the time moves only when the test moves it, or when a delay sleeps (the sleep returns
 * right away and moves the clock by its length). It records the sleeps and the threads that slept.
 */
final class FakeClock implements TimeSource.Provider, AutoCloseable {

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final List<Long> sleeps = new ArrayList<>();
    private final List<String> sleepers = new ArrayList<>();
    private final TimeSource.Provider previous;

    private FakeClock() {
        previous = TimeSource.install(this);
    }

    static FakeClock install() {
        return new FakeClock();
    }

    void advance(long millis) {
        now.addAndGet(millis);
    }

    synchronized List<Long> sleeps() {
        return new ArrayList<>(sleeps);
    }

    synchronized List<String> sleepers() {
        return new ArrayList<>(sleepers);
    }

    @Override
    public long currentTimeMillis() {
        return now.get();
    }

    @Override
    public long nanoTime() {
        return now.get() * 1_000_000L;
    }

    @Override
    public void sleep(long millis) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        synchronized (this) {
            sleeps.add(millis);
            sleepers.add(Thread.currentThread().getName());
        }
        now.addAndGet(millis);
    }

    @Override
    public void close() {
        TimeSource.install(previous);
    }
}
