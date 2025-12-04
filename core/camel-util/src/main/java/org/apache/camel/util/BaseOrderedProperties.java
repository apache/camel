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

package org.apache.camel.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Base class for ordered properties implementations.
 */
abstract class BaseOrderedProperties extends Properties {

    protected final Lock lock = new ReentrantLock();
    private final Map<String, Object> map = new LinkedHashMap<>();

    public Map<String, Object> asMap() {
        return map;
    }

    @Override
    public Object put(Object key, Object value) {
        lock.lock();
        try {
            return doPut(key.toString(), value.toString());
        } finally {
            lock.unlock();
        }
    }

    protected Object doPut(String key, String value) {
        return map.put(key, value);
    }

    @Override
    public void putAll(Map<?, ?> t) {
        lock.lock();
        try {
            for (Map.Entry<?, ?> entry : t.entrySet()) {
                put(entry.getKey(), entry.getValue());
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Object get(Object key) {
        lock.lock();
        try {
            return map.get(key);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isEmpty() {
        lock.lock();
        try {
            return map.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Object remove(Object key) {
        lock.lock();
        try {
            return map.remove(key);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void clear() {
        lock.lock();
        try {
            map.clear();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String getProperty(String key) {
        return (String) map.get(key);
    }

    @Override
    public String getProperty(String key, String defaultValue) {
        return (String) map.getOrDefault(key, defaultValue);
    }

    @Override
    public Enumeration<Object> keys() {
        lock.lock();
        try {
            return new Vector<Object>(map.keySet()).elements();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Set<Object> keySet() {
        return new LinkedHashSet<>(map.keySet());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<Map.Entry<Object, Object>> entrySet() {
        return (Set) map.entrySet();
    }

    @Override
    public int size() {
        lock.lock();
        try {
            return map.size();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Set<String> stringPropertyNames() {
        return map.keySet();
    }

    @Override
    public Collection<Object> values() {
        return new ArrayList<>(map.values());
    }

    @Override
    public String toString() {
        lock.lock();
        try {
            return map.toString();
        } finally {
            lock.unlock();
        }
    }
}
