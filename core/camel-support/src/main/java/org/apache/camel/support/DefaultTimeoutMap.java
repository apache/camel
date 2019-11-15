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
package org.apache.camel.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.TimeoutMap;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Comparator.comparing;
import static org.apache.camel.TimeoutMap.Listener.Type.Evict;
import static org.apache.camel.TimeoutMap.Listener.Type.Put;
import static org.apache.camel.TimeoutMap.Listener.Type.Remove;

/**
 * Default implementation of the {@link TimeoutMap}.
 * <p/>
 * This implementation supports thread safe and non thread safe, in the manner you can enable locking or not.
 * By default locking is enabled and thus we are thread safe.
 * <p/>
 * You must provide a {@link java.util.concurrent.ScheduledExecutorService} in the constructor which is used
 * to schedule a background task which check for old entries to purge. This implementation will shutdown the scheduler
 * if its being stopped.
 * You must also invoke {@link #start()} to startup the timeout map, before its ready to be used.
 * And you must invoke {@link #stop()} to stop the map when no longer in use.
 */
public class DefaultTimeoutMap<K, V> extends ServiceSupport implements TimeoutMap<K, V> {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final ConcurrentMap<K, TimeoutMapEntry<K, V>> map = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor;
    private volatile ScheduledFuture<?> future;
    private final long purgePollTime;
    private final Lock lock;

    private final List<Listener<K, V>> listeners = new ArrayList<>(2);

    public DefaultTimeoutMap(ScheduledExecutorService executor) {
        this(executor, 1000);
    }

    public DefaultTimeoutMap(ScheduledExecutorService executor, long requestMapPollTimeMillis) {
        this(executor, requestMapPollTimeMillis, true);
    }

    public DefaultTimeoutMap(ScheduledExecutorService executor, long requestMapPollTimeMillis, boolean useLock) {
        this(executor, requestMapPollTimeMillis, useLock ? new ReentrantLock() : NoLock.INSTANCE);
    }

    public DefaultTimeoutMap(ScheduledExecutorService executor, long requestMapPollTimeMillis, Lock lock) {
        ObjectHelper.notNull(executor, "ScheduledExecutorService");
        this.executor = executor;
        this.purgePollTime = requestMapPollTimeMillis;
        this.lock = lock;
    }

    @Override
    public V get(K key) {
        TimeoutMapEntry<K, V> entry;
        lock.lock();
        try {
            entry = map.get(key);
            if (entry == null) {
                return null;
            }
            updateExpireTime(entry);
        } finally {
            lock.unlock();
        }
        return entry.getValue();
    }

    @Override
    public V put(K key, V value, long timeoutMillis) {
        TimeoutMapEntry<K, V> entry = new TimeoutMapEntry<>(key, value, timeoutMillis);
        lock.lock();
        try {
            updateExpireTime(entry);
            TimeoutMapEntry<K, V> result = map.put(key, entry);
            return unwrap(result);
        } finally {
            lock.unlock();
            emitEvent(Put, key, value);
        }
    }

    @Override
    public V putIfAbsent(K key, V value, long timeoutMillis) {
        TimeoutMapEntry<K, V> entry = new TimeoutMapEntry<>(key, value, timeoutMillis);
        TimeoutMapEntry<K, V> result = null;
        lock.lock();
        try {
            updateExpireTime(entry);
            //Just make sure we don't override the old entry
            result = map.putIfAbsent(key, entry);
            return unwrap(result);
        } finally {
            lock.unlock();
            if (result != entry) {
                emitEvent(Put, key, value); // conditional on map being changed
            }
        }
    }

    @Override
    public V remove(K key) {
        V value = null;
        lock.lock();
        try {
            value = unwrap(map.remove(key));
            return value;
        } finally {
            lock.unlock();
            if (value != null) {
                emitEvent(Remove, key, value); // conditional on map being changed
            }
        }
    }

    @Override
    public int size() {
        return map.size();
    }

    /**
     * The timer task which purges old requests and schedules another poll
     */
    private void purgeTask() {
        // only purge if allowed
        if (!isRunAllowed()) {
            log.trace("Purge task not allowed to run");
            return;
        }

        log.trace("Running purge task to see if any entries have been timed out");
        try {
            purge();
        } catch (Throwable t) {
            // must catch and log exception otherwise the executor will now schedule next purgeTask
            log.warn("Exception occurred during purge task. This exception will be ignored.", t);
        }
    }

    protected void purge() {
        log.trace("There are {} in the timeout map", map.size());
        if (map.isEmpty()) {
            return;
        }

        long now = currentTime();

        List<TimeoutMapEntry<K, V>> expired = new ArrayList<>(map.size());
        lock.lock();
        try {
            // need to find the expired entries and add to the expired list
            for (Map.Entry<K, TimeoutMapEntry<K, V>> entry : map.entrySet()) {
                if (entry.getValue().getExpireTime() < now) {
                    if (isValidForEviction(entry.getValue())) {
                        log.debug("Evicting inactive entry ID: {}", entry.getValue());
                        expired.add(entry.getValue());
                    }
                }
            }

            // if we found any expired then we need to sort, onEviction and remove
            if (!expired.isEmpty()) {
                // sort according to the expired time so we got the first expired first
                expired.sort(comparing(TimeoutMapEntry::getExpireTime));

                // and must remove from list after we have fired the notifications
                for (TimeoutMapEntry<K, V> entry : expired) {
                    map.remove(entry.getKey());
                }
            }
        } finally {
            lock.unlock();
            for (TimeoutMapEntry<K, V> entry : expired) {
                emitEvent(Evict, entry.getKey(), entry.getValue());
            }
        }
    }

    // Properties
    // -------------------------------------------------------------------------
    
    public long getPurgePollTime() {
        return purgePollTime;
    }

    public ScheduledExecutorService getExecutor() {
        return executor;
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    private static <K, V> V unwrap(TimeoutMapEntry<K, V> entry) {
        return entry == null ? null : entry.getValue();
    }

    @Override
    public void addListener(Listener<K, V> listener) {
        this.listeners.add(listener);
    }

    private void emitEvent(Listener.Type type, K key, V value) {
        for (Listener<K, V> listener : listeners) {
            try {
                listener.timeoutMapEvent(type, key, value);
            } catch (Throwable t) {
                // Ignore
            }
        }
    }

    /**
     * lets schedule each time to allow folks to change the time at runtime
     */
    protected void schedulePoll() {
        future = executor.scheduleWithFixedDelay(this::purgeTask, 0, purgePollTime, TimeUnit.MILLISECONDS);
    }

    /**
     * A hook to allow derivations to avoid evicting the current entry
     */
    protected boolean isValidForEviction(TimeoutMapEntry<K, V> entry) {
        return true;
    }

    protected void updateExpireTime(TimeoutMapEntry<K, V> entry) {
        long now = currentTime();
        entry.setExpireTime(entry.getTimeout() + now);
    }

    protected long currentTime() {
        return System.currentTimeMillis();
    }

    @Override
    protected void doStart() throws Exception {
        if (executor.isShutdown()) {
            throw new IllegalStateException("The ScheduledExecutorService is shutdown");
        }
        schedulePoll();
    }

    @Override
    protected void doStop() throws Exception {
        if (future != null) {
            future.cancel(false);
            future = null;
        }
        // clear map if we stop
        map.clear();
    }

}
