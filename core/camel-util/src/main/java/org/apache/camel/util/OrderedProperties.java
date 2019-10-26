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

/**
 * This class is an ordered {@link Properties} where the key/values are stored in the order they are added or loaded.
 * <p/>
 * Note: This implementation is only intended as implementation detail for the Camel properties component, and has only
 * been designed to provide the needed functionality.
 * The complex logic for loading properties has been kept from the JDK {@link Properties} class.
 */
public final class OrderedProperties extends Properties {

    private final Map<String, String> map = new LinkedHashMap<>();

    public OrderedProperties() {
    }

    @Override
    public synchronized Object put(Object key, Object value) {
        return map.put(key.toString(), value.toString());
    }

    @Override
    public synchronized void putAll(Map<?, ?> t) {
        for (Map.Entry<?, ?> entry : t.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public synchronized Object get(Object key) {
        return map.get(key);
    }

    @Override
    public synchronized boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public synchronized Object remove(Object key) {
        return map.remove(key);
    }

    @Override
    public synchronized void clear() {
        map.clear();
    }

    @Override
    public String getProperty(String key) {
        return map.get(key);
    }

    @Override
    public String getProperty(String key, String defaultValue) {
        return map.getOrDefault(key, defaultValue);
    }

    @Override
    public synchronized Enumeration<Object> keys() {
        return new Vector<Object>(map.keySet()).elements();
    }

    @Override
    public Set<Object> keySet() {
        return new LinkedHashSet<>(map.keySet());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<Map.Entry<Object, Object>> entrySet() {
        Set entrySet = map.entrySet();
        return entrySet;
    }

    @Override
    public synchronized int size() {
        return map.size();
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
    public synchronized String toString() {
        return map.toString();
    }
}
