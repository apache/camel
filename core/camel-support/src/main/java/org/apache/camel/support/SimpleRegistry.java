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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.camel.NoSuchBeanException;
import org.apache.camel.spi.Registry;

/**
 * A {@link Map}-based registry.
 * <p/>
 * Favour using {@link DefaultRegistry} instead of this.
 *
 * @see DefaultRegistry
 */
public class SimpleRegistry extends LinkedHashMap<String, Map<Class<?>, Object>> implements Registry {

    @Override
    public Object lookupByName(String name) {
        return lookupByNameAndType(name, Object.class);
    }

    @Override
    public <T> T lookupByNameAndType(String name, Class<T> type) {
        Map<Class<?>, Object> map = this.get(name);
        if (map == null) {
            return null;
        }

        Object answer = map.get(type);
        if (answer == null) {
            // look for first entry that is the type
            for (Object value : map.values()) {
                if (type.isInstance(value)) {
                    answer = value;
                    break;
                }
            }
        }
        if (answer == null) {
            return null;
        }
        try {
            answer = unwrap(answer);
            return type.cast(answer);
        } catch (Throwable e) {
            String msg = "Found bean: " + name + " in SimpleRegistry: " + this
                    + " of type: " + answer.getClass().getName() + " expected type was: " + type;
            throw new NoSuchBeanException(name, msg, e);
        }
    }

    @Override
    public <T> Map<String, T> findByTypeWithName(Class<T> type) {
        Map<String, T> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<Class<?>, Object>> entry : entrySet()) {
            for (Object value : entry.getValue().values()) {
                if (type.isInstance(value)) {
                    value = unwrap(value);
                    result.put(entry.getKey(), type.cast(value));
                }
            }
        }
        return result;
    }

    @Override
    public <T> Set<T> findByType(Class<T> type) {
        Set<T> result = new LinkedHashSet<>();
        for (Map.Entry<String, Map<Class<?>, Object>> entry : entrySet()) {
            for (Object value : entry.getValue().values()) {
                if (type.isInstance(value)) {
                    value = unwrap(value);
                    result.add(type.cast(value));
                }
            }
        }
        return result;
    }

    @Override
    public void bind(String id, Class type, Object bean) {
        computeIfAbsent(id, k -> new LinkedHashMap<>()).put(type, wrap(bean));
    }

}
