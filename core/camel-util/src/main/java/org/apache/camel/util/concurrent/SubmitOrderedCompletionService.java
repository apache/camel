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

import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link java.util.concurrent.CompletionService} that orders the completed tasks
 * in the same order as they where submitted.
 */
public class SubmitOrderedCompletionService<V> implements CompletionService<V> {
    
    private final Executor executor;

    // the idea to order the completed task in the same order as they where submitted is to leverage
    // the delay queue. With the delay queue we can control the order by the getDelay and compareTo methods
    // where we can order the tasks in the same order as they where submitted.
    private final DelayQueue<SubmitOrderFutureTask> completionQueue = new DelayQueue<>();

    // id is the unique id that determines the order in which tasks was submitted (incrementing)
    private final AtomicInteger id = new AtomicInteger();
    // index is the index of the next id that should expire and thus be ready to take from the delayed queue
    private final AtomicInteger index = new AtomicInteger();

    private class SubmitOrderFutureTask extends FutureTask<V> implements Delayed {

        // the id this task was assigned
        private final long id;

        SubmitOrderFutureTask(long id, Callable<V> voidCallable) {
            super(voidCallable);
            this.id = id;
        }

        SubmitOrderFutureTask(long id, Runnable runnable, V result) {
            super(runnable, result);
            this.id = id;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            // if the answer is 0 then this task is ready to be taken
            long answer = id - index.get();
            if (answer <= 0) {
                return answer;
            }
            // okay this task is not ready yet, and we don't really know when it would be
            // so we have to return a delay value of one time unit
            if (TimeUnit.NANOSECONDS == unit) {
                // okay this is too fast so use a little more delay to avoid CPU burning cycles
                // To avoid align with java 11 impl of
                // "java.util.concurrent.locks.AbstractQueuedSynchronizer.SPIN_FOR_TIMEOUT_THRESHOLD", otherwise
                // no sleep with very high CPU usage
                answer = 1001L;
            } else {
                answer = unit.convert(1, unit);
            }
            return answer;
        }

        @Override
        @SuppressWarnings("unchecked")
        public int compareTo(Delayed o) {
            SubmitOrderFutureTask other = (SubmitOrderFutureTask) o;
            return (int) (this.id - other.id);
        }

        @Override
        protected void done() {
            // when we are done add to the completion queue
            completionQueue.add(this);
        }

        @Override
        public String toString() {
            // output using zero-based index
            return "SubmitOrderedFutureTask[" + (id - 1) + "]";
        }
    }

    public SubmitOrderedCompletionService(Executor executor) {
        this.executor = executor;
    }

    @Override
    public Future<V> submit(Callable<V> task) {
        if (task == null) {
            throw new IllegalArgumentException("Task must be provided");
        }
        SubmitOrderFutureTask f = new SubmitOrderFutureTask(id.incrementAndGet(), task);
        executor.execute(f);
        return f;
    }

    @Override
    public Future<V> submit(Runnable task, Object result) {
        if (task == null) {
            throw new IllegalArgumentException("Task must be provided");
        }
        SubmitOrderFutureTask f = new SubmitOrderFutureTask(id.incrementAndGet(), task, null);
        executor.execute(f);
        return f;
    }

    @Override
    public Future<V> take() throws InterruptedException {
        index.incrementAndGet();
        return completionQueue.take();
    }

    @Override
    public Future<V> poll() {
        index.incrementAndGet();
        Future<V> answer = completionQueue.poll();
        if (answer == null) {
            // decrease counter if we didnt get any data
            index.decrementAndGet();
        }
        return answer;
    }

    @Override
    public Future<V> poll(long timeout, TimeUnit unit) throws InterruptedException {
        index.incrementAndGet();
        Future<V> answer = completionQueue.poll(timeout, unit);
        if (answer == null) {
            // decrease counter if we didnt get any data
            index.decrementAndGet();
        }
        return answer;
    }

    /**
     * Marks the current task as timeout, which allows you to poll the next
     * tasks which may already have been completed.
     */
    public void timeoutTask() {
        index.incrementAndGet();
    }

}
