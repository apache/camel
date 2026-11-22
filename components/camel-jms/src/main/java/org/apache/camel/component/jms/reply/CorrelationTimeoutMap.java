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
package org.apache.camel.component.jms.reply;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

import org.apache.camel.support.DefaultTimeoutMap;

import static org.apache.camel.TimeoutMap.Listener.Type.*;

/**
 * A {@link org.apache.camel.TimeoutMap} which is used to track reply messages which has been timed out, and thus should
 * trigger the waiting {@link org.apache.camel.Exchange} to timeout as well. Zero (or negative) timeout means infinite
 * but is actually encoded as {@link Integer#MAX_VALUE} which is 24 days.
 * <p/>
 * Every handler that is added to the map is completed once: by its reply, by its timeout, or when the map stops. Once
 * the map has stopped, no handler is added any more: registering a new reply is refused, and a handler that is moved to
 * another correlation id (the JMSMessageID) is completed at once.
 */
class CorrelationTimeoutMap extends DefaultTimeoutMap<String, ReplyHandler> {

    // the lock of the map, which also guards closed
    private final Lock lock;
    private final BiConsumer<ReplyHandler, String> evictionTask;
    // the onTimeout tasks handed to the executor that have not started yet
    private final Set<EvictionTask> pendingEvictions = ConcurrentHashMap.newKeySet();
    // set when the map stops, before the drain, so a handler can no longer be added after the drain
    private boolean closed;

    CorrelationTimeoutMap(ScheduledExecutorService executor, long requestMapPollTimeMillis, ExecutorService executorService) {
        this(executor, requestMapPollTimeMillis, executorService, new ReentrantLock());
    }

    private CorrelationTimeoutMap(ScheduledExecutorService executor, long requestMapPollTimeMillis,
                                  ExecutorService executorService, Lock lock) {
        super(executor, requestMapPollTimeMillis, lock);
        this.lock = lock;
        // Support synchronous or asynchronous handling of evictions
        evictionTask = executorService == null
                ? ReplyHandler::onTimeout
                : (handler, key) -> submitEviction(executorService, handler, key);
        addListener(this::listener);
    }

    private static long encode(long timeoutMillis) {
        return timeoutMillis > 0 ? timeoutMillis : Integer.MAX_VALUE; // TODO why not Long.MAX_VALUE!
    }

    private void listener(Listener.Type type, String key, ReplyHandler handler) {
        if (type == Put) {
            log.trace("Added correlationID: {}", key);
        } else if (type == Remove) {
            log.trace("Removed correlationID: {}", key);
        } else if (type == Evict) {
            evictionTask.accept(handler, key);
            log.trace("Evicted correlationID: {}", key);
        }
    }

    private void submitEviction(ExecutorService executorService, ReplyHandler handler, String key) {
        EvictionTask task = new EvictionTask(handler, key);
        pendingEvictions.add(task);
        try {
            executorService.submit(task);
        } catch (RejectedExecutionException e) {
            // such as with rejectedPolicy=Abort and a full queue: the entry is no longer in the map, so nothing else
            // would complete the exchange; run the task here as the CallerRuns policy does
            log.warn("The timeout task of correlationID: {} was rejected by the executor ({}). Running it in the calling"
                     + " thread instead.",
                    key, e.getMessage());
            task.run();
        }
    }

    /**
     * Completes the exchanges whose timeout task was handed to the executor but not run by it, such as the tasks that
     * were still queued when the executor was shut down (a forced shutdown drops them). The reply manager calls this
     * after it has shut down the executor. Each task runs at most once.
     */
    void completePendingEvictions() {
        int size = pendingEvictions.size();
        if (size > 0) {
            log.warn("Completing {} timed out requests whose timeout task was not run before the executor was shut down",
                    size);
            for (EvictionTask task : pendingEvictions) {
                task.run();
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        lock.lock();
        try {
            closed = false;
        } finally {
            lock.unlock();
        }
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        // close the map before the drain: a handler added after the drain would be cleared by super.doStop() (or stay
        // in the stopped map), and its exchange would never complete
        lock.lock();
        try {
            closed = true;
        } finally {
            lock.unlock();
        }
        drainAndEvictAll();
        super.doStop();
    }

    @Override
    public ReplyHandler put(String key, ReplyHandler value, long timeoutMillis) {
        lock.lock();
        try {
            if (!closed) {
                return super.put(key, value, encode(timeoutMillis));
            }
        } finally {
            lock.unlock();
        }
        // the map has stopped and was drained: this handler (moved to another correlation id after the drain) is in
        // no map any more, so it is completed here as the drain would have done
        log.debug("Cannot add correlationID: {} as the reply manager is stopped. Completing its exchange.", key);
        value.onTimeout(key);
        return null;
    }

    @Override
    public ReplyHandler putIfAbsent(String key, ReplyHandler value, long timeoutMillis) {
        lock.lock();
        try {
            if (closed) {
                throw new RejectedExecutionException(
                        "Cannot wait for the reply with correlationID: " + key + " as the reply manager is stopped");
            }
            return super.putIfAbsent(key, value, encode(timeoutMillis));
        } finally {
            lock.unlock();
        }
    }

    /**
     * The timeout task of a handler, which runs once: on the executor, in the calling thread when the executor rejects
     * it, or from {@link #completePendingEvictions()} when the executor never ran it.
     */
    private final class EvictionTask implements Runnable {

        private final ReplyHandler handler;
        private final String key;
        private final AtomicBoolean claimed = new AtomicBoolean();

        EvictionTask(ReplyHandler handler, String key) {
            this.handler = handler;
            this.key = key;
        }

        @Override
        public void run() {
            if (claimed.compareAndSet(false, true)) {
                pendingEvictions.remove(this);
                handler.onTimeout(key);
            }
        }
    }

}
