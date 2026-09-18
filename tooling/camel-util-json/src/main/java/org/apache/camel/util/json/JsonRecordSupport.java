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
package org.apache.camel.util.json;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Collection;
import java.util.Map;

/**
 * Reflectively converts a Java {@link Record} into a {@link JsonObject}, so a record can be used as the single source
 * of truth for both a runtime JSON payload and (via reflection over the record's structure) a JSON Schema describing
 * it.
 * <p/>
 * A record component whose value is {@code null} is omitted from the resulting {@link JsonObject}, mirroring the common
 * "only include a field if present" convention. Supported component value types: primitives, {@link String}, boxed
 * numbers/{@link Boolean}, {@link Enum} (converted via {@link Enum#name()}), nested {@link Record}s,
 * {@link Collection}s thereof, and {@link Map}s (converted to a {@link JsonObject}).
 */
public final class JsonRecordSupport {

    private JsonRecordSupport() {
    }

    /**
     * Converts a record instance into a {@link JsonObject}, one entry per record component.
     */
    public static JsonObject toJsonObject(Record record) {
        JsonObject json = new JsonObject();
        for (RecordComponent component : record.getClass().getRecordComponents()) {
            Object value = accessorValue(record, component);
            Object jsonValue = toJsonValue(value);
            if (jsonValue != null) {
                json.put(component.getName(), jsonValue);
            }
        }
        return json;
    }

    private static Object accessorValue(Record record, RecordComponent component) {
        try {
            Method accessor = component.getAccessor();
            accessor.setAccessible(true);
            return accessor.invoke(record);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(
                    "Cannot read record component: " + component.getName() + " on " + record.getClass(), e);
        }
    }

    private static Object toJsonValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Record r) {
            return toJsonObject(r);
        }
        if (value instanceof Map<?, ?> map) {
            JsonObject json = new JsonObject();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object v = toJsonValue(entry.getValue());
                if (v != null) {
                    json.put(String.valueOf(entry.getKey()), v);
                }
            }
            return json;
        }
        if (value instanceof Collection<?> col) {
            JsonArray array = new JsonArray();
            for (Object item : col) {
                array.add(toJsonValue(item));
            }
            return array;
        }
        if (value instanceof Enum<?> e) {
            return e.name();
        }
        // String, boxed numbers, Boolean, and anything already JSON-safe pass through as-is
        return value;
    }
}
