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

package org.apache.camel.component.rocketmq.reply;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.support.DefaultTimeoutMap;

/**
 * The handlers of the requests that wait for a reply, by message key. Every handler that is added to the map is
 * completed once: by its reply, by its timeout, or when the map stops. Once the map has stopped, no handler is added
 * any more: a handler that is added then (the send of its request succeeded after the stop) is completed at once.
 */
public class ReplyTimeoutMap extends DefaultTimeoutMap<String, ReplyHandler> {

    // the lock of the map, which also guards closed
    private final Lock lock;
    // set when the map stops, before the drain, so a handler can no longer be added after the drain
    private boolean closed;

    public ReplyTimeoutMap(ScheduledExecutorService executor, long requestMapPollTimeMillis) {
        this(executor, requestMapPollTimeMillis, new ReentrantLock());
    }

    private ReplyTimeoutMap(ScheduledExecutorService executor, long requestMapPollTimeMillis, Lock lock) {
        super(executor, requestMapPollTimeMillis, lock);
        this.lock = lock;
        addListener(this::listener);
    }

    private static long encode(long timeoutMillis) {
        return timeoutMillis > 0 ? timeoutMillis : Integer.MAX_VALUE;
    }

    private void listener(Listener.Type type, String key, ReplyHandler handler) {
        switch (type) {
            case Put:
                log.trace("Added messageKey: {}", key);
                break;
            case Remove:
                log.trace("Removed messageKey: {}", key);
                break;
            case Evict:
                onTimeout(key, handler);
                log.trace("Evicted messageKey: {}", key);
                break;
            default:
        }
    }

    private void onTimeout(String key, ReplyHandler handler) {
        try {
            handler.onTimeout(key);
        } catch (Exception e) {
            log.warn("Error processing onTimeout for messageKey: {} due: {}. This exception is ignored.",
                    key, e.getLocalizedMessage(), e);
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
        completeClosed(key, value);
        return null;
    }

    @Override
    public ReplyHandler putIfAbsent(String key, ReplyHandler value, long timeoutMillis) {
        lock.lock();
        try {
            if (!closed) {
                return super.putIfAbsent(key, value, encode(timeoutMillis));
            }
        } finally {
            lock.unlock();
        }
        completeClosed(key, value);
        return null;
    }

    private void completeClosed(String key, ReplyHandler handler) {
        // the map has stopped and was drained: the request has been sent, but this handler is in no map, so it is
        // completed here as the drain would have done
        log.debug("Cannot add messageKey: {} as the reply manager is stopped. Completing its exchange.", key);
        onTimeout(key, handler);
    }
}
