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
package org.apache.camel.util;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.Service;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Default implementation of the {@link TimeoutMap}.
 *
 * @version $Revision$
 */
public class DefaultTimeoutMap<K, V> implements TimeoutMap<K, V>, Runnable, Service {

    protected final transient Log log = LogFactory.getLog(getClass());

    private final ConcurrentMap<K, TimeoutMapEntry<K, V>> map = new ConcurrentHashMap<K, TimeoutMapEntry<K, V>>();
    private final ScheduledExecutorService executor;
    private final long purgePollTime;
    private final long initialDelay = 1000L;
    private final Lock lock = new ReentrantLock();

    public DefaultTimeoutMap() {
        this(null, 1000L);
    }

    public DefaultTimeoutMap(ScheduledExecutorService executor, long requestMapPollTimeMillis) {
        this.executor = executor;
        this.purgePollTime = requestMapPollTimeMillis;
        schedulePoll();
    }

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

    public void put(K key, V value, long timeoutMillis) {
        TimeoutMapEntry<K, V> entry = new TimeoutMapEntry<K, V>(key, value, timeoutMillis);
        lock.lock();
        try {
            map.put(key, entry);
            updateExpireTime(entry);
        } finally {
            lock.unlock();
        }
    }

    public void remove(K id) {
        lock.lock();
        try {
            map.remove(id);
        } finally {
            lock.unlock();
        }
    }

    public Object[] getKeys() {
        Object[] keys;
        lock.lock();
        try {
            Set<K> keySet = map.keySet();
            keys = new Object[keySet.size()];
            keySet.toArray(keys);
        } finally {
            lock.unlock();
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
        if (log.isTraceEnabled()) {
            log.trace("Running purge task to see if any entries has been timed out");
        }
        try {
            purge();
        } catch (Throwable t) {
            // must catch and log exception otherwise the executor will now schedule next run
            log.error("Exception occurred during purge task", t);
        }
    }

    public void purge() {
        if (log.isTraceEnabled()) {
            log.debug("There are " + map.size() + " in the timeout map");
        }
        long now = currentTime();

        lock.lock();
        try {
            for (Map.Entry<K, TimeoutMapEntry<K, V>> entry : map.entrySet()) {
                if (entry.getValue().getExpireTime() < now) {
                    if (isValidForEviction(entry.getValue())) {
                        if (log.isDebugEnabled()) {
                            log.debug("Evicting inactive request for correlationID: " + entry);
                        }
                        map.remove(entry.getKey(), entry.getValue());
                    }
                }
            }
        } finally {
            lock.unlock();
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
        if (executor != null) {
            executor.scheduleWithFixedDelay(this, initialDelay, purgePollTime, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * A hook to allow derivations to avoid evicting the current entry
     */
    protected boolean isValidForEviction(TimeoutMapEntry<K, V> entry) {
        return true;
    }

    protected void updateExpireTime(TimeoutMapEntry entry) {
        long now = currentTime();
        entry.setExpireTime(entry.getTimeout() + now);
    }

    protected long currentTime() {
        return System.currentTimeMillis();
    }

    public void start() throws Exception {
    }

    public void stop() throws Exception {
        if (executor != null) {
            executor.shutdown();
        }
        map.clear();
    }
}
