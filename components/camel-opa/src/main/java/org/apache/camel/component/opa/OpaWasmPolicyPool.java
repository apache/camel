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

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import com.styra.opa.wasm.OpaPolicy;

/**
 * A bounded pool of {@link OpaPolicy} instances, which carry mutable input and data and are not thread-safe.
 * <p/>
 * This is deliberately not {@code com.styra.opa.wasm.OpaPolicyPool}, which the SDK also ships. That one waits on
 * {@code Semaphore.acquire()} with no timeout, so a route whose concurrency exceeds {@code poolSize} - or that hits one
 * policy evaluation which never terminates - parks its threads with nothing to say why. In a component that decides
 * authorization, a caller waiting for ever is not meaningfully better than a caller denied, and it is a great deal
 * harder to diagnose. Borrowing here waits at most {@code borrowTimeout} and then reports what to change.
 * <p/>
 * The second difference is in how a lease ends. The SDK's version releases its permit from a {@code finally} but clears
 * its own guard only afterwards, so a lease whose return threw looked un-returned and could release the same permit
 * twice - and a semaphore that gains permits has stopped bounding anything. Here the guard is claimed <em>first</em>,
 * which makes returning and discarding mutually exclusive and exactly-once by construction rather than by care at every
 * call site.
 */
final class OpaWasmPolicyPool implements AutoCloseable {

    private final Deque<OpaPolicy> idle = new ConcurrentLinkedDeque<>();
    private final Semaphore permits;
    private final Supplier<OpaPolicy> factory;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final int size;
    private final long borrowTimeout;

    OpaWasmPolicyPool(Supplier<OpaPolicy> factory, int size, long borrowTimeout) {
        this.factory = factory;
        this.size = size;
        this.borrowTimeout = borrowTimeout;
        this.permits = new Semaphore(size);
    }

    /**
     * Borrows an instance, creating one if the pool has capacity but nothing idle.
     *
     * @throws TimeoutException     if no instance became free within {@code borrowTimeout}
     * @throws InterruptedException if the thread was interrupted while waiting; the interrupt flag is left set
     */
    Lease borrow() throws TimeoutException, InterruptedException {
        if (closed.get()) {
            throw new IllegalStateException("The policy pool is closed");
        }
        boolean acquired;
        try {
            acquired = permits.tryAcquire(borrowTimeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // the interruptible wait clears the flag on its way out, and a caller deciding whether this was a
            // shutdown or a policy failure needs it back
            Thread.currentThread().interrupt();
            throw e;
        }
        if (!acquired) {
            throw new TimeoutException(
                    "Timed out after " + borrowTimeout + "ms waiting for one of the " + size
                                       + " WebAssembly policy instances. Raise poolSize if the route evaluates more"
                                       + " exchanges concurrently than that, or borrowTimeout if the policy itself"
                                       + " legitimately takes this long to evaluate");
        }
        OpaPolicy policy;
        try {
            policy = idle.pollFirst();
            if (policy == null) {
                policy = factory.get();
            }
        } catch (RuntimeException e) {
            permits.release();
            throw e;
        }
        return new Lease(policy);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            idle.clear();
        }
        // threads already waiting are not woken here: they are bounded by borrowTimeout, and a shutdown that
        // interrupts its workers frees them at once, because tryAcquire is interruptible
    }

    /**
     * One borrowed instance. Ends exactly once, either {@link #close() returned} for reuse or {@link #discard()
     * dropped}; whichever happens first wins and the other becomes a no-op.
     */
    final class Lease implements AutoCloseable {

        private final OpaPolicy policy;
        private final AtomicBoolean ended = new AtomicBoolean();

        private Lease(OpaPolicy policy) {
            this.policy = policy;
        }

        OpaPolicy policy() {
            return policy;
        }

        /**
         * Returns the instance to the pool for reuse.
         * <p/>
         * Nothing is reset on the way in. {@code OpaPolicy.reset()} is package-private to the SDK and so cannot be
         * called from here, but it is also not needed: the borrower sets the entrypoint and the data document on every
         * borrow, and {@code OpaPolicy.input()} - which {@code evaluate(String)} calls - rewinds the WebAssembly heap
         * to the data pointer each time, so nothing accumulates across reuses.
         */
        @Override
        public void close() {
            if (!ended.compareAndSet(false, true)) {
                return;
            }
            try {
                if (!closed.get()) {
                    idle.offerFirst(policy);
                }
            } finally {
                permits.release();
            }
        }

        /**
         * Drops the instance instead of returning it. For an instance that may be in an undefined state - a trap part
         * way through an evaluation - so that the next exchange gets a fresh one.
         */
        void discard() {
            if (!ended.compareAndSet(false, true)) {
                return;
            }
            permits.release();
        }
    }
}
