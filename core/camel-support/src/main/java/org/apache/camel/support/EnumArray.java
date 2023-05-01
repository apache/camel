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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * This class provides an array-based structure used to store payloads associated with enums. This is
 * used in the hot path of the core code to allow access to those payloads with constant time and low
 * memory overhead.
 *
 * This data-structure is meant for internal usage of the Camel Core and is not meant for users.
 * @param <T>
 */
public final class EnumArray<T extends Enum<?>> {
    private final Object[] internalArray;
    private final T[] values;

    /**
     * Creates a new EnumArray with a fixed size determined by the length of values
     * @param values the Enum values (as in Enum.values())
     */
    public EnumArray(T[] values) {
        this.internalArray = new Object[values.length];
        this.values = values;
    }

    /**
     * Whether this arrau contains a payload (value) for the given entry
     * @param entry the entry to check for
     * @return true if there is a payload or false otherwise
     */
    public boolean contains(T entry) {
        Object payload = internalArray[entry.ordinal()];

        return payload != null;
    }

    /**
     * Gets the payload for the given entry
     * @param entry the entry to get the payload (must not be null)
     * @return the payload or false otherwise
     */
    public Object get(T entry) {
        Objects.requireNonNull(entry, "The entry for a enum array cannot be null");

        return internalArray[entry.ordinal()];
    }

    /**
     * Gets the payload for the given entry
     * @param entry the entry to get the payload (must not be null)
     * @param clazz the class to cast the payload to
     * @return the payload or false otherwise
     * @param <K> the payload type
     */
    public <K> K get(T entry, Class<K> clazz) {
        Object tmp = get(entry);

        if (tmp != null) {
            return clazz.cast(tmp);
        }

        return null;
    }

    /**
     * Computes a new value for the entry if it's present on this instance
     * @param entry the entry to compute the value for
     * @param mappingFunction the mapping function that will provide the new value. It takes as a parameter the old value.
     * @return the new value that was computed for this entry
     */
    public Object computeIfPresent(T entry, Function<Object, Object> mappingFunction) {
        Object payload = get(entry);

        if (payload != null) {
            payload = mappingFunction.apply(payload);

            set(entry, payload);
        }

        return payload;
    }

    /**
     * Sets the payload for the given entry
     * @param entry the entry to set the payload
     * @param payload the payload
     */
    public void set(T entry, Object payload) {
        internalArray[entry.ordinal()] = payload;
    }

    /**
     * Removes a payload for the given entry and returns its value
     * @param entry the entry to remove the payload
     * @return the payload (may be null)
     */
    public Object remove(T entry) {
        Object tmp = get(entry);
        set(entry, null);

        return tmp;
    }

    /**
     * Completely reset the stored values, setting the internal array to null
     */
    public void reset() {
        Arrays.fill(internalArray, null);
    }

    /**
     * Copy the values of the given EnumArray to this instance
     * @param other the array to copy the values from
     */
    public void copyValues(EnumArray<T> other) {
        this.copyValues(other.internalArray);
    }

    private void copyValues(Object[] other) {
        System.arraycopy(other, 0, internalArray, 0, internalArray.length);
    }

    /**
     * Returns this EnumArray as a Map with the Enums as the keys and the payloads as the values
     * @return a new Map instance
     */
    public Map<T, Object> toMap() {
        Map<T, Object> map = new HashMap<>();
        for (var key : values) {
            Object value = internalArray[key.ordinal()];
            if (value != null) {
                map.put(key, value);
            }
        }
        return map;
    }

    /**
     * Returns this EnumArray as a Map with a String value as the keys and the payloads as the values
     * @param keyMappingFunction a function to convert the Enum values to Strings
     * @return a new Map instance
     */
    public Map<String, Object> toMap(Function<T, String> keyMappingFunction) {
        Map<String, Object> map = new HashMap<>();
        for (var key : values) {
            Object value = internalArray[key.ordinal()];
            if (value != null) {
                map.put(keyMappingFunction.apply(key), value);
            }
        }
        return map;
    }
}
