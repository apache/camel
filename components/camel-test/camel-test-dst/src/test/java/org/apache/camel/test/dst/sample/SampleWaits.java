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
package org.apache.camel.test.dst.sample;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Code under test that waits, for the blocking wait test.
 */
public final class SampleWaits {

    private SampleWaits() {
    }

    public static String get(CompletableFuture<String> future) throws InterruptedException, ExecutionException {
        return future.get();
    }

    public static String join(CompletableFuture<String> future) {
        return future.join();
    }

    public static String futureGet(Future<String> future) throws InterruptedException, ExecutionException {
        return future.get();
    }

    public static void await(CountDownLatch latch) throws InterruptedException {
        latch.await();
    }

    public static boolean await(CountDownLatch latch, long millis) throws InterruptedException {
        return latch.await(millis, TimeUnit.MILLISECONDS);
    }

    public static void acquire(Semaphore semaphore) throws InterruptedException {
        semaphore.acquire();
    }

    public static <T extends Delayed> T take(DelayQueue<T> queue) throws InterruptedException {
        return queue.take();
    }

    public static String take(BlockingQueue<String> queue) throws InterruptedException {
        return queue.take();
    }

    public static String poll(BlockingQueue<String> queue, long millis) throws InterruptedException {
        return queue.poll(millis, TimeUnit.MILLISECONDS);
    }

    /** An element of a delay queue whose delay reads the (rewritten) clock. */
    public static final class Slot implements Delayed {
        private final long due;

        public Slot(long delayMillis) {
            this.due = System.currentTimeMillis() + delayMillis;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(due - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            return Long.compare(getDelay(TimeUnit.MILLISECONDS), o.getDelay(TimeUnit.MILLISECONDS));
        }
    }
}
