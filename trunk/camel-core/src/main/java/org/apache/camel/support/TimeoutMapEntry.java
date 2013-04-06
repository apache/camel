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

import java.util.Map;

import org.apache.camel.TimeoutMap;

/**
 * Represents an entry in a {@link TimeoutMap}
 *
 * @version 
 */
public class TimeoutMapEntry<K, V> implements Comparable<Object>, Map.Entry<K, V> {
    private K key;
    private V value;
    private long timeout;
    private long expireTime;

    public TimeoutMapEntry(K id, V handler, long timeout) {
        this.key = id;
        this.value = handler;
        this.timeout = timeout;
    }

    public K getKey() {
        return key;
    }

    public long getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(long expireTime) {
        this.expireTime = expireTime;
    }

    public V getValue() {
        return value;
    }

    public V setValue(V value) {
        V oldValue = this.value;
        this.value = value;
        return oldValue;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
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
        return key + " (times out after " + timeout + " millis)";
    }
}