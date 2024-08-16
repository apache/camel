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
package org.apache.camel.converter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.camel.Converter;

/**
 * Some core java.util Collection based <a href="http://camel.apache.org/type-converter.html">Type Converters</a>
 */
@Converter(generateBulkLoader = true)
public final class CollectionConverter {

    /**
     * Utility classes should not have a public constructor.
     */
    private CollectionConverter() {
    }

    /**
     * Converts a collection to an array
     */
    @Converter(order = 1)
    public static Object[] toArray(Collection<?> value) {
        return value.toArray();
    }

    /**
     * Converts an array to a collection
     */
    @Converter(order = 2)
    public static List<Object> toList(Object[] array) {
        return Arrays.asList(array);
    }

    /**
     * Converts a collection to a List if it is not already
     */
    @Converter(order = 3)
    public static <T> List<T> toList(Collection<T> collection) {
        return new ArrayList<>(collection);
    }

    /**
     * Converts an {@link Iterator} to a {@link ArrayList}
     */
    @Converter(order = 4)
    public static <T> ArrayList<T> toArrayList(Iterator<T> it) {
        if (it instanceof ArrayList list) {
            return list;
        }
        ArrayList<T> list = new ArrayList<>();
        while (it.hasNext()) {
            list.add(it.next());
        }
        return list;
    }

    /**
     * Converts an {@link Iterable} to a {@link ArrayList}
     */
    @Converter(order = 5)
    public static <T> ArrayList<T> toArrayList(Iterable<T> it) {
        if (it instanceof ArrayList list) {
            return list;
        }
        ArrayList<T> list = new ArrayList<>();
        for (T value : it) {
            list.add(value);
        }
        return list;
    }

    @Converter(order = 6)
    public static Set<Object> toSet(Object[] array) {
        return new HashSet<>(Arrays.asList(array));
    }

    @Converter(order = 7)
    public static <T> Set<T> toSet(Collection<T> collection) {
        return new HashSet<>(collection);
    }

    @Converter(order = 8)
    public static <K, V> Set<Map.Entry<K, V>> toSet(Map<K, V> map) {
        return map.entrySet();
    }

    @Converter(order = 9)
    public static <K, V> Collection<Map.Entry<K, V>> toCollection(Map<K, V> map) {
        return map.entrySet();
    }

    @Converter(order = 10)
    public static Properties toProperties(Map<Object, Object> map) {
        Properties answer = new Properties();
        answer.putAll(map);
        return answer;
    }

    @Converter(order = 11)
    public static <K, V> Hashtable<K, V> toHashtable(Map<? extends K, ? extends V> map) {
        return new Hashtable<>(map);
    }

    @Converter(order = 12)
    public static <K, V> HashMap<K, V> toHashMap(Map<? extends K, ? extends V> map) {
        return new HashMap<>(map);
    }

    /**
     * Converts an {@link Iterable} into a {@link List}
     */
    @Converter(order = 13)
    public static <T> List<T> toList(Iterable<T> iterable) {
        if (iterable instanceof List list) {
            return list;
        }
        List<T> result = new LinkedList<>();
        for (T value : iterable) {
            result.add(value);
        }
        return result;
    }

    /**
     * Converts an {@link Iterator} into a {@link List}
     */
    @Converter(order = 14)
    public static <T> List<T> toList(Iterator<T> it) {
        if (it instanceof List value) {
            return value;
        }
        List<T> result = new LinkedList<>();
        while (it.hasNext()) {
            result.add(it.next());
        }
        return result;
    }
}
