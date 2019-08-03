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
package org.apache.camel.util.concurrent;

import java.util.PriorityQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class AsyncCompletionService<V> {

    private final Executor executor;
    private final boolean ordered;
    private final PriorityQueue<Task> queue = new PriorityQueue<>();
    private final AtomicLong nextId = new AtomicLong();
    private final AtomicLong index = new AtomicLong();
    private final ReentrantLock lock;
    private final Condition available;

    public AsyncCompletionService(Executor executor, boolean ordered) {
        this(executor, ordered, null);
    }

    public AsyncCompletionService(Executor executor, boolean ordered, ReentrantLock lock) {
        this.executor = executor;
        this.ordered = ordered;
        this.lock = lock != null ? lock : new ReentrantLock();
        this.available = this.lock.newCondition();
    }

    public ReentrantLock getLock() {
        return lock;
    }

    public void submit(Consumer<Consumer<V>> runner) {
        Task f = new Task(nextId.getAndIncrement(), runner);
        this.executor.execute(f);
    }

    public void skip() {
        index.incrementAndGet();
    }

    public V pollUnordered() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Task t = queue.poll();
            return t != null ? t.result : null;
        } finally {
            lock.unlock();
        }
    }

    public V poll() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Task t = queue.peek();
            if (t != null && (!ordered || index.compareAndSet(t.id, t.id + 1))) {
                queue.poll();
                return t.result;
            } else {
                return null;
            }
        } finally {
            lock.unlock();
        }
    }

    public V poll(long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            for (;;) {
                Task t = queue.peek();
                if (t != null && (!ordered || index.compareAndSet(t.id, t.id + 1))) {
                    queue.poll();
                    return t.result;
                }
                if (nanos <= 0) {
                    return null;
                } else {
                    nanos = available.awaitNanos(nanos);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public V take() throws InterruptedException {
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            for (;;) {
                Task t = queue.peek();
                if (t != null && (!ordered || index.compareAndSet(t.id, t.id + 1))) {
                    queue.poll();
                    return t.result;
                }
                available.await();
            }
        } finally {
            lock.unlock();
        }
    }

    private void complete(Task task) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            queue.add(task);
            available.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private class Task implements Runnable, Comparable<Task> {
        private final long id;
        private final Consumer<Consumer<V>> runner;
        private V result;

        Task(long id, Consumer<Consumer<V>> runner) {
            this.id = id;
            this.runner = runner;
        }

        @Override
        public void run() {
            runner.accept(this::setResult);
        }

        protected void setResult(V result) {
            this.result = result;
            complete(this);
        }

        @Override
        public int compareTo(Task other) {
            return Long.compare(this.id, other.id);
        }

        @Override
        public String toString() {
            return "SubmitOrderedFutureTask[" + this.id + "]";
        }
    }
}
