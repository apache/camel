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

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * An {@link OrderedProperties} that also keeps track from which location the properties are sourced from.
 *
 * This can be used to track all the various sources for configuration that a Camel application uses (properties file,
 * ENV variables, hardcoded in java, spring-boot, quarkus, camel-k modeline, camel-yaml-dsl etc.
 *
 * <b>Important:</b> Use the put method that takes location as argument to store location information.
 */
public final class OrderedLocationProperties extends BaseOrderedProperties {

    private final Map<Object, String> locations = new HashMap<>();

    public void put(String location, Object key, Object value) {
        locations.put(key, location);
        put(key, value);
    }

    public void putAll(OrderedLocationProperties other) {
        for (var entry : other.entrySet()) {
            put(other.getLocation(entry.getKey()), entry.getKey(), entry.getValue());
        }
    }

    public void putAll(String location, Map<Object, Object> map) {
        for (var entry : map.entrySet()) {
            put(location, entry.getKey(), entry.getValue());
        }
    }

    public void putAll(String location, Properties properties) {
        for (var entry : properties.entrySet()) {
            put(location, entry.getKey(), entry.getValue());
        }
    }

    public String getLocation(Object key) {
        return locations.get(key);
    }

    @Override
    public synchronized void clear() {
        locations.clear();
        super.clear();
    }

    @Override
    public synchronized Object remove(Object key) {
        locations.remove(key);
        return super.remove(key);
    }
}
