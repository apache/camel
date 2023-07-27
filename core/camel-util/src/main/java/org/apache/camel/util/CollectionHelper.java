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

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Supplier;

import org.w3c.dom.NodeList;

/**
 * A number of helper methods for working with collections
 */
public final class CollectionHelper {

    /**
     * Utility classes should not have a public constructor.
     */
    private CollectionHelper() {
    }

    /**
     * Returns the size of the collection if it can be determined to be a collection
     *
     * @param  value the collection
     * @return       the size, or <tt>null</tt> if not a collection
     */
    public static Integer size(Object value) {
        if (value != null) {
            if (value instanceof Collection<?> collection) {
                return collection.size();
            } else if (value instanceof Map<?, ?> map) {
                return map.size();
            } else if (value instanceof Object[] array) {
                return array.length;
            } else if (value.getClass().isArray()) {
                return Array.getLength(value);
            } else if (value instanceof NodeList nodeList) {
                return nodeList.getLength();
            }
        }
        return null;
    }

    /**
     * Sets the value of the entry in the map for the given key, though if the map already contains a value for the
     * given key then the value is appended to a list of values.
     *
     * @param map   the map to add the entry to
     * @param key   the key in the map
     * @param value the value to put in the map
     */
    @SuppressWarnings("unchecked")
    public static void appendValue(Map<String, Object> map, String key, Object value) {
        Object oldValue = map.get(key);
        if (oldValue != null) {
            List<Object> list;
            if (oldValue instanceof List) {
                list = (List<Object>) oldValue;
            } else {
                list = new ArrayList<>();
                list.add(oldValue);
                // replace old entry with list
                map.remove(key);
                map.put(key, list);
            }
            list.add(value);
        } else {
            map.put(key, value);
        }
    }

    @SafeVarargs
    public static <T> Set<T> createSetContaining(T... contents) {
        return new HashSet<>(Arrays.asList(contents));
    }

    public static String collectionAsCommaDelimitedString(Collection<?> col) {
        if (col == null || col.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        Iterator<?> it = col.iterator();
        while (it.hasNext()) {
            sb.append(it.next().toString());
            if (it.hasNext()) {
                sb.append(",");
            }
        }

        return sb.toString();
    }

    /**
     * Traverses the given map recursively and flattern the keys by combining them with the optional separator.
     *
     * @param  map       the map
     * @param  separator optional separator to use in key name, for example a hyphen or dot.
     * @return           the map with flattern keys
     */
    public static Map<String, Object> flattenKeysInMap(Map<String, Object> map, String separator) {
        Map<String, Object> answer = new LinkedHashMap<>();
        doFlattenKeysInMap(map, "", ObjectHelper.isNotEmpty(separator) ? separator : "", answer);
        return answer;
    }

    private static void doFlattenKeysInMap(
            Map<String, Object> source, String prefix, String separator, Map<String, Object> target) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String newKey = prefix.isEmpty() ? key : prefix + separator + key;

            if (value instanceof Map map) {
                doFlattenKeysInMap(map, newKey, separator, target);
            } else {
                target.put(newKey, value);
            }
        }
    }

    /**
     * Build an unmodifiable map on top of a given map. Note tha thew given map is copied if not null.
     *
     * @param  map a map
     * @return     an unmodifiable map.
     */
    public static <K, V> Map<K, V> unmodifiableMap(Map<K, V> map) {
        return map == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(map));
    }

    /**
     * Build a map from varargs.
     */
    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> mapOf(Supplier<Map<K, V>> creator, K key, V value, Object... keyVals) {
        Map<K, V> map = creator.get();
        map.put(key, value);

        for (int i = 0; i < keyVals.length; i += 2) {
            map.put(
                    (K) keyVals[i],
                    (V) keyVals[i + 1]);
        }

        return map;
    }

    /**
     * Build an immutable map from varargs.
     */
    public static <K, V> Map<K, V> immutableMapOf(Supplier<Map<K, V>> creator, K key, V value, Object... keyVals) {
        return Collections.unmodifiableMap(
                mapOf(creator, key, value, keyVals));
    }

    /**
     * Build a map from varargs.
     */
    public static <K, V> Map<K, V> mapOf(K key, V value, Object... keyVals) {
        return mapOf(HashMap::new, key, value, keyVals);
    }

    /**
     * Build an immutable map from varargs.
     */
    public static <K, V> Map<K, V> immutableMapOf(K key, V value, Object... keyVals) {
        return Collections.unmodifiableMap(
                mapOf(HashMap::new, key, value, keyVals));
    }

    /**
     * Build a {@link java.util.Properties} from varargs.
     */
    public static Properties propertiesOf(String key, String value, String... keyVals) {
        Properties properties = new Properties();
        properties.setProperty(key, value);

        for (int i = 0; i < keyVals.length; i += 2) {
            properties.setProperty(
                    keyVals[i],
                    keyVals[i + 1]);
        }

        return properties;
    }

    /**
     * Build a new map that is the result of merging the given list of maps.
     */
    @SafeVarargs
    public static <K, V> Map<K, V> mergeMaps(Map<K, V> map, Map<K, V>... maps) {
        Map<K, V> answer = new HashMap<>();

        if (map != null) {
            answer.putAll(map);
        }

        for (Map<K, V> m : maps) {
            answer.putAll(m);
        }

        return answer;
    }
}
