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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

    private final Map<K, TimeoutMapEntry<K, V>> map = new HashMap<K, TimeoutMapEntry<K, V>>();
    private final SortedSet<TimeoutMapEntry<K, V>> index = new TreeSet<TimeoutMapEntry<K, V>>();
    private final ScheduledExecutorService executor;
    private final long purgePollTime;
    private final long initialDelay = 1000L;

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
        synchronized (map) {
            entry = map.get(key);
            if (entry == null) {
                return null;
            }
            index.remove(entry);
            updateExpireTime(entry);
            index.add(entry);
        }
        return entry.getValue();
    }

    public void put(K key, V value, long timeoutMillis) {
        TimeoutMapEntry<K, V> entry = new TimeoutMapEntry<K, V>(key, value, timeoutMillis);
        synchronized (map) {
            TimeoutMapEntry<K, V> oldValue = map.put(key, entry);
            if (oldValue != null) {
                index.remove(oldValue);
            }
            updateExpireTime(entry);
            index.add(entry);
        }
    }

    public void remove(K id) {
        synchronized (map) {
            TimeoutMapEntry entry = map.remove(id);
            if (entry != null) {
                index.remove(entry);
            }
        }
    }

    public Object[] getKeys() {
        Object[] keys = null;
        synchronized (map) {
            Set<K> keySet = map.keySet();
            keys = new Object[keySet.size()];
            keySet.toArray(keys);
        }
        return keys;
    }
    
    public int size() {
        synchronized (map) {
            return map.size();
        }
    }

    /**
     * The timer task which purges old requests and schedules another poll
     */
    public void run() {
        purge();
    }

    public void purge() {
        long now = currentTime();
        synchronized (map) {
            for (Iterator<TimeoutMapEntry<K, V>> iter = index.iterator(); iter.hasNext();) {
                TimeoutMapEntry<K, V> entry = iter.next();
                if (entry == null) {
                    break;
                }
                if (entry.getExpireTime() < now) {
                    if (isValidForEviction(entry)) {
                        if (log.isDebugEnabled()) {
                            log.debug("Evicting inactive request for correlationID: " + entry);
                        }
                        map.remove(entry.getKey());
                        iter.remove();
                    }
                } else {
                    break;
                }
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
        index.clear();
    }
}
