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
package org.apache.camel.test.dst;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

/**
 * The target of the rewritten blocking waits of the code under test (see {@link DstInstrumentation}). On the loop
 * thread a wait runs the loop until what it waits for is there, as if another thread had provided it meanwhile; a timed
 * wait gives up in virtual time. A wait that no task of the run can end fails the seed at once with a
 * {@link DstViolation} that names the call site, instead of blocking the loop thread for ever. Off the loop thread, or
 * outside a run, the wait is the real one.
 * <p>
 * Rewritten: {@code CompletableFuture.get()/get(timeout)/join()}, {@code Future.get()/get(timeout)},
 * {@code CountDownLatch.await()/await(timeout)}, {@code DelayQueue.take()/poll(timeout)},
 * {@code BlockingQueue.take()/poll(timeout)}, {@code Semaphore.acquire()/tryAcquire(timeout)}.
 */
public final class DstWaits {

    private DstWaits() {
    }

    private static Dst loop() {
        Dst dst = Dst.active();
        return dst != null && dst.isLoopThread() ? dst : null;
    }

    private static void await(Dst dst, BooleanSupplier ready, String what) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException(what);
        }
        dst.awaitCondition(ready, what);
    }

    // ------------------------------------------------------------------------------------------------------------
    // futures

    public static Object get(CompletableFuture<?> future, String where) throws InterruptedException, ExecutionException {
        Dst dst = loop();
        if (dst != null) {
            await(dst, future::isDone, "CompletableFuture.get at " + where);
        }
        return future.get();
    }

    public static Object get(CompletableFuture<?> future, long timeout, TimeUnit unit, String where)
            throws InterruptedException, ExecutionException, TimeoutException {
        Dst dst = loop();
        if (dst != null && !dst.awaitUntil(future::isDone, unit.toMillis(timeout), "CompletableFuture.get at " + where)) {
            throw new TimeoutException("DST: CompletableFuture.get at " + where);
        }
        return future.get(timeout, unit);
    }

    public static Object join(CompletableFuture<?> future, String where) {
        Dst dst = loop();
        if (dst != null) {
            dst.awaitCondition(future::isDone, "CompletableFuture.join at " + where);
        }
        return future.join();
    }

    public static Object futureGet(Future<?> future, String where) throws InterruptedException, ExecutionException {
        Dst dst = loop();
        if (dst != null && !(future instanceof DstTask)) {
            await(dst, future::isDone, "Future.get at " + where);
        }
        return future.get();
    }

    public static Object futureGet(Future<?> future, long timeout, TimeUnit unit, String where)
            throws InterruptedException, ExecutionException, TimeoutException {
        Dst dst = loop();
        if (dst != null && !(future instanceof DstTask)
                && !dst.awaitUntil(future::isDone, unit.toMillis(timeout), "Future.get at " + where)) {
            throw new TimeoutException("DST: Future.get at " + where);
        }
        return future.get(timeout, unit);
    }

    // ------------------------------------------------------------------------------------------------------------
    // latches and semaphores

    public static void await(CountDownLatch latch, String where) throws InterruptedException {
        Dst dst = loop();
        if (dst != null) {
            await(dst, () -> latch.getCount() == 0, "CountDownLatch.await at " + where);
        }
        latch.await();
    }

    public static boolean await(CountDownLatch latch, long timeout, TimeUnit unit, String where)
            throws InterruptedException {
        Dst dst = loop();
        if (dst != null) {
            return dst.awaitUntil(() -> latch.getCount() == 0, unit.toMillis(timeout), "CountDownLatch.await at " + where);
        }
        return latch.await(timeout, unit);
    }

    public static void acquire(Semaphore semaphore, String where) throws InterruptedException {
        Dst dst = loop();
        if (dst != null) {
            await(dst, () -> semaphore.availablePermits() > 0, "Semaphore.acquire at " + where);
        }
        semaphore.acquire();
    }

    public static boolean tryAcquire(Semaphore semaphore, long timeout, TimeUnit unit, String where)
            throws InterruptedException {
        Dst dst = loop();
        if (dst != null) {
            return dst.awaitUntil(() -> semaphore.availablePermits() > 0, unit.toMillis(timeout),
                    "Semaphore.tryAcquire at " + where)
                    && semaphore.tryAcquire();
        }
        return semaphore.tryAcquire(timeout, unit);
    }

    // ------------------------------------------------------------------------------------------------------------
    // queues

    public static Delayed take(DelayQueue<?> queue, String where) throws InterruptedException {
        Dst dst = loop();
        if (dst == null) {
            return queue.take();
        }
        String what = "DelayQueue.take at " + where;
        while (true) {
            Delayed head = queue.peek();
            if (head == null) {
                await(dst, () -> queue.peek() != null, what);
                continue;
            }
            long delay = head.getDelay(TimeUnit.MILLISECONDS);
            if (delay <= 0) {
                return queue.take();
            }
            // a real take waits until the head expires: let that much virtual time pass, running the other tasks
            dst.awaitUntil(() -> queue.peek() != head, delay, what);
            if (queue.peek() == head && head.getDelay(TimeUnit.MILLISECONDS) > delay / 2) {
                throw new DstViolation(
                        "blocking wait",
                        what + ": the delay of the head element does not follow the virtual clock (" + delay
                                         + " ms before and " + head.getDelay(TimeUnit.MILLISECONDS) + " ms after "
                                         + delay + " virtual ms): the class that computes it reads the real clock;"
                                         + " add it to @DstInstrument(alsoRewrite)");
            }
        }
    }

    public static Delayed poll(DelayQueue<?> queue, long timeout, TimeUnit unit, String where)
            throws InterruptedException {
        Dst dst = loop();
        if (dst == null) {
            return queue.poll(timeout, unit);
        }
        dst.awaitUntil(() -> expired(queue), unit.toMillis(timeout), "DelayQueue.poll at " + where);
        return queue.poll();
    }

    public static Object take(BlockingQueue<?> queue, String where) throws InterruptedException {
        if (queue instanceof DelayQueue<?> delayQueue) {
            return take(delayQueue, where);
        }
        Dst dst = loop();
        if (dst != null) {
            await(dst, () -> !queue.isEmpty(), "BlockingQueue.take at " + where);
        }
        return queue.take();
    }

    public static Object poll(BlockingQueue<?> queue, long timeout, TimeUnit unit, String where)
            throws InterruptedException {
        if (queue instanceof DelayQueue<?> delayQueue) {
            return poll(delayQueue, timeout, unit, where);
        }
        Dst dst = loop();
        if (dst == null) {
            return queue.poll(timeout, unit);
        }
        dst.awaitUntil(() -> !queue.isEmpty(), unit.toMillis(timeout), "BlockingQueue.poll at " + where);
        return queue.poll();
    }

    private static boolean expired(DelayQueue<?> queue) {
        Delayed head = queue.peek();
        return head != null && head.getDelay(TimeUnit.MILLISECONDS) <= 0;
    }
}
