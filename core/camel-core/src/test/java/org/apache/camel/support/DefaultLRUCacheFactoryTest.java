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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The test class for {@link DefaultLRUCacheFactory}.
 */
class DefaultLRUCacheFactoryTest {

    private final List<String> consumed = new ArrayList<>();
    private final DefaultLRUCacheFactory.SimpleLRUCache<String, String> map
            = (DefaultLRUCacheFactory.SimpleLRUCache<String, String>) new DefaultLRUCacheFactory().<String,
                    String> createLRUCache(3, consumed::add);

    @Test
    void forbiddenOperations() {
        assertThrows(UnsupportedOperationException.class, () -> map.values().add("foo"));
        assertThrows(UnsupportedOperationException.class, () -> map.keySet().add("foo"));
        assertThrows(UnsupportedOperationException.class, () -> map.entrySet().add(Map.entry("x", "y")));
    }

    @Test
    void setValue() {
        assertNull(map.put("1", "One"));
        assertEquals(1, map.size());
        assertEquals(1, map.getQueueSize());
        map.entrySet().iterator().next().setValue("bar");
        assertEquals(1, map.size());
        assertEquals(2, map.getQueueSize());
    }

    @Test
    void queueSize() {
        assertEquals(0, map.getQueueSize());
        map.put("1", "1");
        assertEquals(1, map.size());
        assertEquals(1, map.getQueueSize());
        map.put("1", "2");
        assertEquals(1, map.size());
        assertEquals(2, map.getQueueSize());
        map.put("1", "3");
        assertEquals(1, map.size());
        assertEquals(3, map.getQueueSize());
        map.put("1", "4");
        assertEquals(1, map.size());
        assertEquals(4, map.getQueueSize());
        map.put("1", "5");
        assertEquals(1, map.size());
        assertEquals(5, map.getQueueSize());
        map.put("1", "6");
        assertEquals(1, map.size());
        assertEquals(6, map.getQueueSize());
        map.put("1", "7");
        assertEquals(1, map.size());
        assertEquals(6, map.getQueueSize());
        map.put("1", "8");
        assertEquals(1, map.size());
        assertEquals(6, map.getQueueSize());
    }

    @Test
    void put() {
        assertEquals(0, map.size());
        assertNull(map.put("1", "One"));
        assertEquals(1, map.size());
        assertNull(map.put("2", "Two"));
        assertEquals(2, map.size());
        assertNull(map.put("3", "Three"));
        assertEquals(3, map.size());
        assertEquals(0, consumed.size());
        assertNull(map.put("4", "Four"));
        assertEquals(3, map.size());
        assertEquals(1, consumed.size());
        assertFalse(map.containsKey("1"));
        assertTrue(consumed.contains("One"));
        assertEquals("Two", map.put("2", "Two v2"));
        assertEquals(3, map.size());
        assertEquals(1, consumed.size());
        assertTrue(map.containsKey("2"));
        assertEquals("Two v2", map.get("2"));
    }

    @Test
    void putIfAbsent() {
        assertEquals(0, map.size());
        assertNull(map.putIfAbsent("1", "One"));
        assertEquals(1, map.size());
        assertNull(map.putIfAbsent("2", "Two"));
        assertEquals(2, map.size());
        assertNull(map.putIfAbsent("3", "Three"));
        assertEquals(3, map.size());
        assertEquals(0, consumed.size());
        assertNull(map.putIfAbsent("4", "Four"));
        assertEquals(3, map.size());
        assertEquals(1, consumed.size());
        assertFalse(map.containsKey("1"));
        assertTrue(consumed.contains("One"));
        assertEquals("Two", map.putIfAbsent("2", "Two v2"));
        assertEquals(3, map.size());
        assertEquals(1, consumed.size());
        assertTrue(map.containsKey("2"));
        assertEquals("Two", map.get("2"));
        assertNull(map.putIfAbsent("5", "Five"));
        assertEquals(3, map.size());
        assertEquals(2, consumed.size());
        assertFalse(map.containsKey("2"));
        assertTrue(consumed.contains("Two"));
    }

    @Test
    void computeIfAbsent() {
        assertEquals(0, map.size());
        assertEquals("One", map.computeIfAbsent("1", k -> "One"));
        assertEquals(1, map.size());
        assertEquals("Two", map.computeIfAbsent("2", k -> "Two"));
        assertEquals(2, map.size());
        assertEquals("Three", map.computeIfAbsent("3", k -> "Three"));
        assertEquals(3, map.size());
        assertEquals(0, consumed.size());
        assertEquals("Four", map.computeIfAbsent("4", k -> "Four"));
        assertEquals(3, map.size());
        assertEquals(1, consumed.size());
        assertFalse(map.containsKey("1"));
        assertTrue(consumed.contains("One"));
        assertNull(map.computeIfAbsent("1", k -> null));
        assertEquals(3, map.size());
        assertEquals(1, consumed.size());
        assertNull(map.computeIfAbsent("5", k -> null));
        assertEquals(3, map.size());
        assertEquals(1, consumed.size());
        assertEquals("Two", map.computeIfAbsent("2", k -> "Two v2"));
        assertEquals(3, map.size());
        assertEquals(1, consumed.size());
        assertTrue(map.containsKey("2"));
        assertEquals("Two", map.get("2"));
        assertEquals("Five", map.computeIfAbsent("5", k -> "Five"));
        assertEquals(3, map.size());
        assertEquals(2, consumed.size());
        assertFalse(map.containsKey("2"));
        assertTrue(consumed.contains("Two"));
        assertEquals("Five", map.computeIfAbsent("5", k -> null));
        assertEquals(3, map.size());
        assertEquals(2, consumed.size());
    }

    @Test
    void computeIfPresent() {
        assertEquals(0, map.size());
        map.putIfAbsent("1", "One");
        assertEquals(1, map.size());
        map.putIfAbsent("2", "Two");
        assertEquals(2, map.size());
        map.putIfAbsent("3", "Three");
        assertEquals(3, map.size());
        assertEquals(0, consumed.size());
        assertNull(map.computeIfPresent("4", (k, v) -> "Four"));
        assertEquals(3, map.size());
        assertEquals(0, consumed.size());
        assertFalse(map.containsKey("4"));
        assertEquals("One v2", map.computeIfPresent("1", (k, v) -> "One v2"));
        assertEquals(3, map.size());
        assertEquals(0, consumed.size());
        assertTrue(map.containsKey("1"));
        assertEquals("One v2", map.get("1"));
        assertNull(map.computeIfPresent("1", (k, v) -> null));
        assertEquals(2, map.size());
        assertEquals(0, consumed.size());
        assertFalse(map.containsKey("1"));
    }

    @Test
    void compute() {
        assertEquals(0, map.size());
        assertEquals("One", map.compute("1", (k, v) -> "One"));
        assertEquals(1, map.size());
        assertEquals("Two", map.compute("2", (k, v) -> "Two"));
        assertEquals(2, map.size());
        assertEquals("Three", map.compute("3", (k, v) -> "Three"));
        assertEquals(3, map.size());
        assertEquals(0, consumed.size());
        assertEquals("Four", map.compute("4", (k, v) -> "Four"));
        assertEquals(3, map.size());
        assertEquals(1, consumed.size());
        assertFalse(map.containsKey("1"));
        assertTrue(consumed.contains("One"));
        assertEquals("Two v2", map.compute("2", (k, v) -> "Two v2"));
        assertEquals(3, map.size());
        assertEquals(1, consumed.size());
        assertTrue(map.containsKey("2"));
        assertEquals("Two v2", map.get("2"));
        assertNull(map.compute("2", (k, v) -> null));
        assertEquals(2, map.size());
        assertEquals(1, consumed.size());
        assertFalse(map.containsKey("2"));
    }

    @Test
    void merge() {
        assertEquals(0, map.size());
        assertEquals("One", map.merge("1", "One", String::concat));
        assertEquals(1, map.size());
        assertEquals("Two", map.merge("2", "Two", String::concat));
        assertEquals(2, map.size());
        assertEquals("Three", map.merge("3", "Three", String::concat));
        assertEquals(3, map.size());
        assertEquals(0, consumed.size());
        assertEquals("Four", map.merge("4", "Four", String::concat));
        assertEquals(3, map.size());
        assertEquals(1, consumed.size());
        assertFalse(map.containsKey("1"));
        assertTrue(consumed.contains("One"));
        assertEquals("TwoV2", map.merge("2", "V2", String::concat));
        assertEquals(3, map.size());
        assertEquals(1, consumed.size());
        assertNull(map.merge("2", "V2", (v1, v2) -> null));
        assertEquals(2, map.size());
        assertEquals(1, consumed.size());
    }

    @Test
    void replace() {
        assertEquals(0, map.size());
        assertNull(map.replace("1", "One"));
        assertEquals(0, map.size());
        map.put("1", "One");
        assertEquals(1, map.size());
        map.put("2", "Two");
        assertEquals(2, map.size());
        map.put("3", "Three");
        assertEquals(3, map.size());
        assertEquals(0, consumed.size());
        assertEquals("One", map.replace("1", "One v2"));
        assertEquals(3, map.size());
        assertEquals(0, consumed.size());
        assertEquals("Three", map.replace("3", "Three v2"));
        assertEquals("Three v2", map.get("3"));
        assertEquals(3, map.size());
        assertEquals(0, consumed.size());
    }

    @Test
    void replaceWithOldValue() {
        assertEquals(0, map.size());
        map.put("1", "One");
        map.put("2", "Two");
        map.put("3", "Three");
        assertEquals(3, map.size());
        assertFalse(map.replace("1", "foo", "One"));
        assertEquals(3, map.size());
        assertFalse(map.replace("1", "foo", "One v2"));
        assertEquals(3, map.size());
        assertEquals(0, consumed.size());
        assertEquals("One", map.get("1"));
        assertTrue(map.replace("1", "One", "One v2"));
        assertEquals(3, map.size());
        assertEquals(0, consumed.size());
        assertEquals("One v2", map.get("1"));
        assertFalse(map.replace("3", "foo", "Three v2"));
        assertEquals(3, map.size());
        assertEquals(0, consumed.size());
        assertTrue(map.replace("3", "Three", "Three v2"));
        assertEquals("Three v2", map.get("3"));
        assertEquals(3, map.size());
        assertEquals(0, consumed.size());
    }
}
