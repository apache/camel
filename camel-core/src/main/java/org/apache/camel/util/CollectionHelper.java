/**
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.w3c.dom.NodeList;

/**
 * A number of helper methods for working with collections
 *
 * @version
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
     * @param value the collection
     * @return the size, or <tt>null</tt> if not a collection
     */
    public static Integer size(Object value) {
        if (value != null) {
            if (value instanceof Collection) {
                Collection<?> collection = (Collection<?>) value;
                return collection.size();
            } else if (value instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) value;
                return map.size();
            } else if (value instanceof Object[]) {
                Object[] array = (Object[]) value;
                return array.length;
            } else if (value.getClass().isArray()) {
                return Array.getLength(value);
            } else if (value instanceof NodeList) {
                NodeList nodeList = (NodeList) value;
                return nodeList.getLength();
            }
        }
        return null;
    }

    /**
     * Sets the value of the entry in the map for the given key, though if the
     * map already contains a value for the given key then the value is appended
     * to a list of values.
     *
     * @param map the map to add the entry to
     * @param key the key in the map
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
                list = new ArrayList<Object>();
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

    public static <T> Set<T> createSetContaining(T... contents) {
        Set<T> contentsAsSet = new HashSet<T>();
        contentsAsSet.addAll(Arrays.asList(contents));
        return contentsAsSet;
    }

    public static String collectionAsCommaDelimitedString(String[] col) {
        if (col == null || col.length == 0) {
            return "";
        }
        return collectionAsCommaDelimitedString(Arrays.asList(col));
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
     * @param map  the map
     * @param separator optional separator to use in key name, for example a hyphen or dot.
     * @return the map with flattern keys
     */
    public static Map<String, Object> flatternKeysInMap(Map<String, Object> map, String separator) {
        Map<String, Object> answer = new LinkedHashMap<>();
        doFlatternKeysInMap(map, "", ObjectHelper.isNotEmpty(separator) ? separator : "", answer);
        return answer;
    }

    private static void doFlatternKeysInMap(Map<String, Object> source, String prefix, String separator, Map<String, Object> target) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String newKey = prefix.isEmpty() ? key : prefix + separator + key;

            if (value instanceof Map) {
                Map map = (Map) value;
                doFlatternKeysInMap(map, newKey, separator, target);
            } else {
                target.put(newKey, value);
            }
        }
    }
}
