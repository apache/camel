/**
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.TimeoutMap;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *
 * @version 
 */
public class DefaultTimeoutMap<K, V> extends ServiceSupport implements TimeoutMap<K, V>, Runnable {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final ConcurrentMap<K, TimeoutMapEntry<K, V>> map = new ConcurrentHashMap<K, TimeoutMapEntry<K, V>>();
    private final ScheduledExecutorService executor;
    private volatile ScheduledFuture<?> future;
    private final long purgePollTime;
    private final Lock lock = new ReentrantLock();
    private boolean useLock = true;

    public DefaultTimeoutMap(ScheduledExecutorService executor) {
        this(executor, 1000);
    }

    public DefaultTimeoutMap(ScheduledExecutorService executor, long requestMapPollTimeMillis) {
        this(executor, requestMapPollTimeMillis, true);
    }

    public DefaultTimeoutMap(ScheduledExecutorService executor, long requestMapPollTimeMillis, boolean useLock) {
        ObjectHelper.notNull(executor, "ScheduledExecutorService");
        this.executor = executor;
        this.purgePollTime = requestMapPollTimeMillis;
        this.useLock = useLock;
    }

    public V get(K key) {
        TimeoutMapEntry<K, V> entry;
        if (useLock) {
            lock.lock();
        }
        try {
            entry = map.get(key);
            if (entry == null) {
                return null;
            }
            updateExpireTime(entry);
        } finally {
            if (useLock) {
                lock.unlock();
            }
        }
        return entry.getValue();
    }
    
    public V put(K key, V value, long timeoutMillis) {
        TimeoutMapEntry<K, V> entry = new TimeoutMapEntry<K, V>(key, value, timeoutMillis);
        if (useLock) {
            lock.lock();
        }
        try {
            updateExpireTime(entry);
            TimeoutMapEntry<K, V> result = map.put(key, entry);
            return result != null ? result.getValue() : null;
        } finally {
            if (useLock) {
                lock.unlock();
            }
        }
    }
    
    public V putIfAbsent(K key, V value, long timeoutMillis) {
        TimeoutMapEntry<K, V> entry = new TimeoutMapEntry<K, V>(key, value, timeoutMillis);
        if (useLock) {
            lock.lock();
        }
        try {
            updateExpireTime(entry);
            //Just make sure we don't override the old entry
            TimeoutMapEntry<K, V> result = map.putIfAbsent(key, entry);
            return result != null ? result.getValue() : null;
        } finally {
            if (useLock) {
                lock.unlock();
            }
        }
    }

    public V remove(K key) {
        TimeoutMapEntry<K, V> entry;

        if (useLock) {
            lock.lock();
        }
        try {
            entry = map.remove(key);
        } finally {
            if (useLock) {
                lock.unlock();
            }
        }

        return entry != null ? entry.getValue() : null;
    }

    public Object[] getKeys() {
        Object[] keys;
        if (useLock) {
            lock.lock();
        }
        try {
            Set<K> keySet = map.keySet();
            keys = new Object[keySet.size()];
            keySet.toArray(keys);
        } finally {
            if (useLock) {
                lock.unlock();
            }
        }
        return keys;
    }
    
    public int size() {
        return map.size();
    }

    /**
     * The timer task which purges old requests and schedules another poll
     */
    public void run() {
        // only run if allowed
        if (!isRunAllowed()) {
            log.trace("Purge task not allowed to run");
            return;
        }

        log.trace("Running purge task to see if any entries has been timed out");
        try {
            purge();
        } catch (Throwable t) {
            // must catch and log exception otherwise the executor will now schedule next run
            log.warn("Exception occurred during purge task. This exception will be ignored.", t);
        }
    }

    public void purge() {
        log.trace("There are {} in the timeout map", map.size());
        if (map.isEmpty()) {
            return;
        }
        
        long now = currentTime();

        List<TimeoutMapEntry<K, V>> expired = new ArrayList<TimeoutMapEntry<K, V>>();

        if (useLock) {
            lock.lock();
        }
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
                expired.sort(new Comparator<TimeoutMapEntry<K, V>>() {
                    public int compare(TimeoutMapEntry<K, V> a, TimeoutMapEntry<K, V> b) {
                        long diff = a.getExpireTime() - b.getExpireTime();
                        if (diff == 0) {
                            return 0;
                        }
                        return diff > 0 ? 1 : -1;
                    }
                });

                List<K> evicts = new ArrayList<K>(expired.size());
                try {
                    // now fire eviction notification
                    for (TimeoutMapEntry<K, V> entry : expired) {
                        boolean evict = false;
                        try {
                            evict = onEviction(entry.getKey(), entry.getValue());
                        } catch (Throwable t) {
                            log.warn("Exception happened during eviction of entry ID {}, won't evict and will continue trying: {}", 
                                    entry.getValue(), t);
                        }
                        if (evict) {
                            // okay this entry should be evicted
                            evicts.add(entry.getKey());
                        }
                    }
                } finally {
                    // and must remove from list after we have fired the notifications
                    for (K key : evicts) {
                        map.remove(key);
                    }
                }
            }
        } finally {
            if (useLock) {
                lock.unlock();
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

    /**
     * lets schedule each time to allow folks to change the time at runtime
     */
    protected void schedulePoll() {
        future = executor.scheduleWithFixedDelay(this, 0, purgePollTime, TimeUnit.MILLISECONDS);
    }

    /**
     * A hook to allow derivations to avoid evicting the current entry
     */
    protected boolean isValidForEviction(TimeoutMapEntry<K, V> entry) {
        return true;
    }

    public boolean onEviction(K key, V value) {
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
