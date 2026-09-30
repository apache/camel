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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

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
    public boolean containsKey(Object key) {
        lock.lock();
        try {
            return map.containsKey(key);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean containsValue(Object value) {
        lock.lock();
        try {
            return map.containsValue(value);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean contains(Object value) {
        lock.lock();
        try {
            return map.containsValue(value);
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
    public Enumeration<Object> elements() {
        lock.lock();
        try {
            return new Vector<>(map.values()).elements();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void forEach(BiConsumer<? super Object, ? super Object> action) {
        List<Map.Entry<String, Object>> entries;
        lock.lock();
        try {
            // iterate a copy so the action can change the properties
            entries = new ArrayList<>(map.entrySet());
        } finally {
            lock.unlock();
        }
        for (Map.Entry<String, Object> entry : entries) {
            action.accept(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public Object getOrDefault(Object key, Object defaultValue) {
        Object answer = get(key);
        return answer != null ? answer : defaultValue;
    }

    @Override
    public Object putIfAbsent(Object key, Object value) {
        lock.lock();
        try {
            Object answer = get(key);
            if (answer == null) {
                put(key, value);
            }
            return answer;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean remove(Object key, Object value) {
        lock.lock();
        try {
            if (containsKey(key) && Objects.equals(get(key), value)) {
                remove(key);
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Object replace(Object key, Object value) {
        lock.lock();
        try {
            return containsKey(key) ? put(key, value) : null;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean replace(Object key, Object oldValue, Object newValue) {
        lock.lock();
        try {
            if (containsKey(key) && Objects.equals(get(key), oldValue)) {
                put(key, newValue);
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void replaceAll(BiFunction<? super Object, ? super Object, ?> function) {
        lock.lock();
        try {
            for (String key : new ArrayList<>(map.keySet())) {
                put(key, function.apply(key, get(key)));
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Object computeIfAbsent(Object key, Function<? super Object, ?> mappingFunction) {
        lock.lock();
        try {
            Object answer = get(key);
            if (answer == null) {
                answer = mappingFunction.apply(key);
                if (answer != null) {
                    put(key, answer);
                }
            }
            return answer;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Object computeIfPresent(Object key, BiFunction<? super Object, ? super Object, ?> remappingFunction) {
        lock.lock();
        try {
            Object old = get(key);
            if (old == null) {
                return null;
            }
            Object answer = remappingFunction.apply(key, old);
            if (answer == null) {
                remove(key);
            } else {
                put(key, answer);
            }
            return answer;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Object compute(Object key, BiFunction<? super Object, ? super Object, ?> remappingFunction) {
        lock.lock();
        try {
            Object answer = remappingFunction.apply(key, get(key));
            if (answer == null) {
                remove(key);
            } else {
                put(key, answer);
            }
            return answer;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Object merge(Object key, Object value, BiFunction<? super Object, ? super Object, ?> remappingFunction) {
        lock.lock();
        try {
            Object old = get(key);
            Object answer = old == null ? value : remappingFunction.apply(old, value);
            if (answer == null) {
                remove(key);
            } else {
                put(key, answer);
            }
            return answer;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof BaseOrderedProperties other) {
            return map.equals(other.map);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return map.hashCode();
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
