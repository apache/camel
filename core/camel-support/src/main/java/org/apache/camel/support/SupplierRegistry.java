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
import java.util.function.Supplier;

/**
 * Used for storing beans that are lazy supplied via a {@link Supplier}.
 *
 * To bind a bean as a supplier, then use the {@link org.apache.camel.spi.Registry#bind(String, Class, Supplier)} method.
 */
public class SupplierRegistry extends SimpleRegistry {

    @Override
    public <T> Set<T> findByType(Class<T> type) {
        Set<T> result = new LinkedHashSet<>();
        for (Map.Entry<String, Map<Class<?>, Object>> entry : entrySet()) {
            for (Map.Entry<Class<?>, Object> subEntry : entry.getValue().entrySet()) {
                if (type.isAssignableFrom(subEntry.getKey())) {
                    Object value = unwrap(subEntry.getValue());
                    result.add(type.cast(value));
                }
            }
        }
        return result;
    }

    @Override
    public <T> Map<String, T> findByTypeWithName(Class<T> type) {
        Map<String, T> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<Class<?>, Object>> entry : entrySet()) {
            for (Map.Entry<Class<?>, Object> subEntry : entry.getValue().entrySet()) {
                if (type.isAssignableFrom(subEntry.getKey())) {
                    Object value = unwrap(subEntry.getValue());
                    result.put(entry.getKey(), type.cast(value));
                }
            }
        }
        return result;
    }

    @Override
    public Object unwrap(Object value) {
        if (value instanceof Supplier) {
            value = ((Supplier<?>) value).get();
        }
        return value;
    }

}
