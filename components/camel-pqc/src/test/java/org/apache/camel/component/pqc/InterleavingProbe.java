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
package org.apache.camel.component.pqc;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test helper for stateful JCA objects (KeyGenerator, KeyAgreement) shared by concurrent callers. It holds one caller
 * between the {@code init()} call and the call that returns the result, and records whether another caller ran
 * {@code init()} on the same object in the meantime. Code that runs the whole sequence while holding the object's
 * monitor never lets that happen: the second caller waits until the first one is done.
 */
public final class InterleavingProbe {

    private static final long HOLD_MILLIS = 1000;

    private final AtomicBoolean holdNext = new AtomicBoolean();
    private final CountDownLatch holding = new CountDownLatch(1);
    private final AtomicBoolean interleaved = new AtomicBoolean();
    private volatile CountDownLatch initWhileHolding;

    /**
     * Holds the next caller that asks for the result, for up to a second or until another caller runs {@code init()}.
     */
    public void holdNextResult() {
        holdNext.set(true);
    }

    /**
     * Waits until a caller is being held.
     */
    public boolean awaitHolding(long timeout, TimeUnit unit) throws InterruptedException {
        return holding.await(timeout, unit);
    }

    /**
     * Whether another caller ran {@code init()} while a caller was held between its own {@code init()} and the result.
     */
    public boolean interleaved() {
        return interleaved.get();
    }

    void afterInit() {
        CountDownLatch latch = initWhileHolding;
        if (latch != null) {
            latch.countDown();
        }
    }

    void beforeResult() {
        if (!holdNext.compareAndSet(true, false)) {
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        initWhileHolding = latch;
        holding.countDown();
        try {
            if (latch.await(HOLD_MILLIS, TimeUnit.MILLISECONDS)) {
                interleaved.set(true);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            initWhileHolding = null;
        }
    }
}
