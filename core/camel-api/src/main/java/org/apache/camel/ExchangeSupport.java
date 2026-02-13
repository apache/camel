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
package org.apache.camel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ExchangeSupport is an internal class used to support Exchange default methods.
 */
public class ExchangeSupport {

    protected static <T> List<T> getObjectAsList(Object value, Class<T> elementType) {
        if (value == null || !(value instanceof Iterable<?> iterable)) {
            return null;
        }

        List<T> result = new ArrayList<>();
        for (Object element : iterable) {
            if (!elementType.isInstance(element)) {
                return null;
            }
            result.add(elementType.cast(element));
        }

        return List.copyOf(result);
    }

    protected static <K, V> Map<K, V> getObjectAsMap(Object value, Class<K> keyType, Class<V> valueType) {
        if (value == null || !(value instanceof Map<?, ?> rawMap)) {
            return null;
        }

        Map<K, V> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            Object rawKey = entry.getKey();
            Object rawValue = entry.getValue();

            if (rawKey == null || !keyType.isInstance(rawKey)) {
                return null;
            }

            if (!valueType.isInstance(rawValue)) {
                return null;
            }

            try {
                result.put(
                        keyType.cast(rawKey),
                        valueType.cast(rawValue));
            } catch (ClassCastException cce) {
                return null;
            }
        }

        return Map.copyOf(result);
    }
}
