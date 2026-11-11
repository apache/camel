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
import static org.awaitility.Awaitility.await;
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
    void refusesAQueuedBorrowAfterThePoolCloses() throws Exception {
        AtomicInteger built = new AtomicInteger();
        OpaWasmPolicyPool pool = new OpaWasmPolicyPool(() -> {
            built.incrementAndGet();
            return mock(OpaPolicy.class);
        }, 1, 10_000);
        OpaWasmPolicyPool.Lease held = pool.borrow();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread waiter = new Thread(() -> {
            try (OpaWasmPolicyPool.Lease ignored = pool.borrow()) {
                failure.set(new AssertionError("The closed pool handed out a lease"));
            } catch (Exception e) {
                failure.set(e);
            }
        });
        try {
            waiter.start();
            await().atMost(10, TimeUnit.SECONDS).until(() -> waiter.getState() == Thread.State.TIMED_WAITING);

            pool.close();
            held.close();
            waiter.join(TimeUnit.SECONDS.toMillis(10));

            assertThat(waiter.isAlive()).isFalse();
            assertThat(failure.get()).isInstanceOf(IllegalStateException.class).hasMessageContaining("closed");
            assertThat(built).as("the queued borrower did not create another policy after close").hasValue(1);
        } finally {
            pool.close();
            held.close();
            waiter.interrupt();
            waiter.join(TimeUnit.SECONDS.toMillis(10));
        }
    }

    @Test
    @Timeout(30)
    void refusesALeaseWhenThePoolClosesDuringConstruction() throws Exception {
        CountDownLatch constructing = new CountDownLatch(1);
        CountDownLatch finishConstruction = new CountDownLatch(1);
        OpaWasmPolicyPool pool = new OpaWasmPolicyPool(() -> {
            constructing.countDown();
            try {
                if (!finishConstruction.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to finish policy construction");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return mock(OpaPolicy.class);
        }, 1, 10_000);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread borrower = new Thread(() -> {
            try (OpaWasmPolicyPool.Lease ignored = pool.borrow()) {
                failure.set(new AssertionError("The closed pool handed out a lease"));
            } catch (Exception e) {
                failure.set(e);
            }
        });
        try {
            borrower.start();
            assertThat(constructing.await(10, TimeUnit.SECONDS)).isTrue();

            pool.close();
            finishConstruction.countDown();
            borrower.join(TimeUnit.SECONDS.toMillis(10));

            assertThat(borrower.isAlive()).isFalse();
            assertThat(failure.get()).isInstanceOf(IllegalStateException.class).hasMessageContaining("closed");
        } finally {
            pool.close();
            finishConstruction.countDown();
            borrower.interrupt();
            borrower.join(TimeUnit.SECONDS.toMillis(10));
        }
    }

    @Test
    @Timeout(30)
    void releasesThePermitWhenConstructionFails() throws Exception {
        AtomicInteger built = new AtomicInteger();
        OpaWasmPolicyPool pool = new OpaWasmPolicyPool(() -> {
            if (built.incrementAndGet() == 1) {
                throw new IllegalArgumentException("Invalid policy");
            }
            return mock(OpaPolicy.class);
        }, 1, 150);
        try {
            assertThatThrownBy(pool::borrow).isInstanceOf(IllegalArgumentException.class);
            try (OpaWasmPolicyPool.Lease held = pool.borrow()) {
                assertThatThrownBy(pool::borrow).isInstanceOf(TimeoutException.class);
            }
            try (OpaWasmPolicyPool.Lease held = pool.borrow()) {
                assertThat(built).as("the permit was released exactly once and the instance was reused").hasValue(2);
            }
        } finally {
            pool.close();
        }
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
