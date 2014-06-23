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

import org.apache.camel.TimeoutMap;

/**
 * Represents an entry in a {@link TimeoutMap}
 */
final class TimeoutMapEntry<K, V> implements Comparable<Object> {
    private final K key;
    private final V value;
    private final long valueTimeout;
    private volatile long expireTime;
    private volatile long keyExpireTime;
    private volatile long valueExpireTime;

    public TimeoutMapEntry(K id, V handler, long keyTimeout, long valueTimeout) {
        this.key = id;
        this.value = handler;
        this.valueTimeout = valueTimeout;

        updateExpireTime(computeExpireTime(keyTimeout), computeExpireTime(valueTimeout));
    }

    public void updateKeyExpireTimeWithPrevious(TimeoutMapEntry<K, V> previous) {
        if (previous != null && previous.keyExpireTime < keyExpireTime) {
            updateExpireTime(keyExpireTime, valueExpireTime);
        }
    }

    public K getKey() {
        return key;
    }

    public V getValue() {
        updateExpireTime(keyExpireTime, computeExpireTime(valueTimeout));
        return value;
    }

    public long getExpireTime() {
        return expireTime;
    }

    @SuppressWarnings("unchecked")
    public int compareTo(Object that) {
        if (this == that) {
            return 0;
        }
        if (that instanceof TimeoutMapEntry) {
            return compareTo((TimeoutMapEntry<K, V>) that);
        }
        return 1;
    }

    public int compareTo(TimeoutMapEntry<K, V> that) {
        long diff = this.expireTime - that.expireTime;
        if (diff > 0) {
            return 1;
        } else if (diff < 0) {
            return -1;
        }
        return this.key.hashCode() - that.key.hashCode();
    }

    public String toString() {
        return key + " (times out after " + valueTimeout + " millis)";
    }

    private void updateExpireTime(long keyExpireTime, long valueExpireTime) {
        this.keyExpireTime = keyExpireTime;
        this.valueExpireTime = valueExpireTime;
        this.expireTime = Math.min(keyExpireTime, valueExpireTime);
    }

    private static long computeExpireTime(long timeout) {
        return timeout > 0 ? System.currentTimeMillis() + timeout : Long.MAX_VALUE;
    }
}
