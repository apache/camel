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
package org.apache.camel.component.opa;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.styra.opa.wasm.OpaPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * The pool's own contract, tested without a WebAssembly module: what matters here is the bounding, not the policy.
 * <p/>
 * These are the two properties the SDK's own pool does not give us - a bounded wait, and a permit released exactly once
 * however a lease ends - so they are asserted directly rather than through a route.
 */
class OpaWasmPolicyPoolTest {

    private OpaWasmPolicyPool poolOf(int size, long borrowTimeout) {
        return new OpaWasmPolicyPool(() -> mock(OpaPolicy.class), size, borrowTimeout);
    }

    @Test
    @Timeout(30)
    void timesOutRatherThanParkingWhenExhausted() throws Exception {
        OpaWasmPolicyPool pool = poolOf(1, 150);
        try {
            pool.borrow();

            long start = System.nanoTime();
            assertThatThrownBy(pool::borrow)
                    .isInstanceOf(TimeoutException.class)
                    .hasMessageContaining("Raise poolSize");
            long waited = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertThat(waited).as("waited for the timeout rather than returning at once").isGreaterThanOrEqualTo(100);
        } finally {
            pool.close();
        }
    }

    @Test
    @Timeout(30)
    void freesAWaiterAsSoonAsALeaseEnds() throws Exception {
        OpaWasmPolicyPool pool = poolOf(1, 10_000);
        try {
            OpaWasmPolicyPool.Lease held = pool.borrow();
            CountDownLatch waiting = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();

            Thread waiter = new Thread(() -> {
                waiting.countDown();
                try {
                    pool.borrow().close();
                } catch (Throwable t) {
                    failure.set(t);
                }
            });
            waiter.start();
            assertThat(waiting.await(10, TimeUnit.SECONDS)).isTrue();

            held.close();
            waiter.join(TimeUnit.SECONDS.toMillis(10));

            assertThat(waiter.isAlive()).as("the waiter was handed the returned instance").isFalse();
            assertThat(failure.get()).isNull();
        } finally {
            pool.close();
        }
    }

    @Test
    @Timeout(30)
    void discardAfterCloseDoesNotReleaseThePermitTwice() throws Exception {
        // the bug this pool exists to make unrepresentable: two permits back for one taken means the semaphore
        // stops bounding anything, and the pool quietly allows more live instances than poolSize
        OpaWasmPolicyPool pool = poolOf(1, 150);
        try {
            OpaWasmPolicyPool.Lease lease = pool.borrow();
            lease.close();
            lease.discard();
            lease.close();

            pool.borrow();

            assertThatThrownBy(pool::borrow)
                    .as("only one permit ever came back")
                    .isInstanceOf(TimeoutException.class);
        } finally {
            pool.close();
        }
    }

    @Test
    @Timeout(30)
    void reusesAnInstanceRatherThanBuildingOnePerBorrow() throws Exception {
        AtomicInteger built = new AtomicInteger();
        OpaWasmPolicyPool pool = new OpaWasmPolicyPool(() -> {
            built.incrementAndGet();
            return mock(OpaPolicy.class);
        }, 4, 150);
        try {
            for (int i = 0; i < 10; i++) {
                pool.borrow().close();
            }

            assertThat(built).as("the idle instance was handed back out").hasValue(1);
        } finally {
            pool.close();
        }
    }

    @Test
    @Timeout(30)
    void doesNotHandOutAnInstanceThatWasDiscarded() throws Exception {
        AtomicInteger built = new AtomicInteger();
        OpaWasmPolicyPool pool = new OpaWasmPolicyPool(() -> {
            built.incrementAndGet();
            return mock(OpaPolicy.class);
        }, 1, 150);
        try {
            pool.borrow().discard();
            pool.borrow().close();

            assertThat(built).as("a discarded instance is replaced, not reused").hasValue(2);
        } finally {
            pool.close();
        }
    }

    @Test
    @Timeout(30)
    void refusesToBorrowOnceClosed() {
        OpaWasmPolicyPool pool = poolOf(1, 150);
        pool.close();

        assertThatThrownBy(pool::borrow)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    @Test
    @Timeout(30)
    void leavesTheInterruptFlagSetWhenInterrupted() throws Exception {
        OpaWasmPolicyPool pool = poolOf(1, 60_000);
        try {
            pool.borrow();
            // as a shutdown would have left it: the next wait throws at once and clears the flag
            Thread.currentThread().interrupt();

            assertThatThrownBy(pool::borrow).isInstanceOf(InterruptedException.class);

            assertThat(Thread.currentThread().isInterrupted())
                    .as("borrow() restored the flag the interruptible wait cleared")
                    .isTrue();
        } finally {
            Thread.interrupted();
            pool.close();
        }
    }
}
