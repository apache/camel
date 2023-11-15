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
package org.apache.camel.support.cache;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The test class for {@link SimpleSoftCache}.
 */
class SimpleSoftCacheTest {

    private final SimpleSoftCache<Integer, Object> cache = new SimpleSoftCache<>(new ConcurrentHashMap<>());

    @Test
    void testSoftCacheGetAndPut() {

        cache.put(1, "foo");
        cache.put(2, "bar");

        assertEquals("foo", cache.get(1));
        assertEquals("bar", cache.get(2));
        assertNull(cache.get(3));

        assertEquals(2, cache.size());

        cache.getInnerCache().get(1).clear();
        assertEquals(2, cache.size());
        assertNull(cache.get(1));
        assertEquals(1, cache.size());
    }

    @Test
    void testSoftCacheContainsValue() {
        cache.put(1, "foo");

        assertTrue(cache.containsValue("foo"));
        assertFalse(cache.containsValue("bar"));

        assertFalse(cache.isEmpty());
        cache.getInnerCache().get(1).clear();
        assertFalse(cache.containsValue("foo"));
        assertTrue(cache.isEmpty());
    }

    @Test
    void testSoftCacheForEach() {
        cache.put(1, "foo");
        cache.put(2, "bar");

        Map<Integer, Object> tmp = new HashMap<>();
        cache.forEach(tmp::put);

        assertEquals("foo", tmp.get(1));
        assertEquals("bar", tmp.get(2));
        assertNull(tmp.get(3));

        assertEquals(2, tmp.size());

        cache.getInnerCache().get(1).clear();

        tmp = new HashMap<>();
        cache.forEach(tmp::put);

        assertNull(tmp.get(1));
        assertEquals("bar", tmp.get(2));
        assertNull(tmp.get(3));

        assertEquals(1, tmp.size());
    }

    @Test
    void testSoftCacheReplaceAll() {
        cache.put(1, "foo");
        cache.put(2, "bar");

        cache.replaceAll((k, v) -> v + "2");

        assertEquals("foo2", cache.get(1));
        assertEquals("bar2", cache.get(2));

        assertEquals(2, cache.size());
    }

    @Test
    void testSoftCachePutIfAbsent() {
        cache.put(1, "foo");

        assertEquals("foo", cache.putIfAbsent(1, "bar"));
        assertEquals("foo", cache.get(1));

        assertNull(cache.putIfAbsent(2, "bar"));
        assertEquals("bar", cache.get(2));
    }

    @Test
    void testSoftCacheRemove() {
        cache.put(1, "foo");
        assertFalse(cache.remove(2, "foo"));
        assertFalse(cache.remove(1, "bar"));
        assertEquals("foo", cache.get(1));
        assertFalse(cache.isEmpty());
        assertTrue(cache.remove(1, "foo"));
        assertNull(cache.get(1));
        assertTrue(cache.isEmpty());
    }

    @Test
    void testSoftCacheReplaceSpecific() {
        cache.put(1, "foo");
        assertFalse(cache.replace(2, "foo", "bar"));
        assertFalse(cache.replace(1, "bar", "foo"));
        assertEquals("foo", cache.get(1));
        assertTrue(cache.replace(1, "foo", "bar"));
        assertEquals("bar", cache.get(1));
    }

    @Test
    void testSoftCacheReplace() {
        cache.put(1, "foo");
        assertNull(cache.replace(2, "bar"));
        assertEquals("foo", cache.get(1));
        assertEquals("foo", cache.replace(1, "bar"));
        assertEquals("bar", cache.get(1));
    }

    @Test
    void testSoftCacheComputeIfAbsent() {
        cache.put(1, "foo");
        assertEquals("foo", cache.computeIfAbsent(1, k -> "bar"));
        assertEquals("foo", cache.get(1));
        assertEquals("bar", cache.computeIfAbsent(2, k -> "bar"));
        assertEquals("bar", cache.get(2));
    }

    @Test
    void testSoftCacheComputeIfPresent() {
        cache.put(1, "foo");
        assertEquals("bar", cache.computeIfPresent(1, (k, v) -> "bar"));
        assertEquals("bar", cache.get(1));
        assertNull(cache.computeIfPresent(1, (k, v) -> null));
        assertNull(cache.get(1));
        assertNull(cache.computeIfPresent(1, (k, v) -> "bar"));
    }

    @Test
    void testSoftCacheCompute() {
        cache.put(1, "foo");
        assertEquals("bar", cache.compute(1, (k, v) -> "bar"));
        assertEquals("bar", cache.get(1));
        assertNull(cache.compute(1, (k, v) -> null));
        assertNull(cache.get(1));
        assertEquals("bar", cache.compute(1, (k, v) -> "bar"));
        assertEquals("bar", cache.get(1));
        assertNull(cache.compute(2, (k, v) -> null));
        assertNull(cache.get(2));
    }

    @Test
    void testSoftCacheMerge() {
        cache.put(1, "foo");
        assertEquals("foo-2", cache.merge(1, "2", (v1, v2) -> v1 + "-" + v2));
        assertEquals("foo-2", cache.get(1));
        assertNull(cache.merge(1, "2", (v1, v2) -> null));
        assertNull(cache.get(1));
        assertEquals("2", cache.merge(1, "2", (v1, v2) -> "bar"));
        assertEquals("2", cache.get(1));
        assertEquals("2", cache.merge(2, "2", (v1, v2) -> null));
        assertEquals("2", cache.get(2));
    }

    @Test
    void testSimpleSoftCachePutOverride() {
        Object old = cache.put(1, "foo");
        assertNull(old);
        old = cache.put(2, "bar");
        assertNull(old);

        assertEquals("foo", cache.get(1));
        assertEquals("bar", cache.get(2));

        old = cache.put(1, "changed");
        assertEquals("foo", old);
        assertEquals("changed", cache.get(1));

        assertEquals(2, cache.size());
    }

    @Test
    void testSimpleSoftCachePutAll() {
        Map<Integer, Object> map = new HashMap<>();
        map.put(1, "foo");
        map.put(2, "bar");

        cache.putAll(map);

        assertEquals("foo", cache.get(1));
        assertEquals("bar", cache.get(2));
        assertNull(cache.get(3));
        assertEquals(2, cache.size());
    }

    @Test
    void testSimpleSoftCacheRemove() {
        cache.put(1, "foo");
        cache.put(2, "bar");

        assertEquals("bar", cache.get(2));
        cache.remove(2);
        assertNull(cache.get(2));
    }

    @Test
    void testSimpleSoftCacheValues() {
        cache.put(1, "foo");
        cache.put(2, "bar");

        Collection<Object> col = cache.values();
        assertEquals(2, col.size());

        Iterator<Object> it = col.iterator();
        assertEquals("foo", it.next());
        assertEquals("bar", it.next());
    }

    @Test
    void testSimpleSoftCacheEmpty() {
        assertTrue(cache.isEmpty());

        cache.put(1, "foo");
        assertFalse(cache.isEmpty());

        cache.put(2, "bar");
        assertFalse(cache.isEmpty());

        cache.remove(2);
        assertFalse(cache.isEmpty());

        cache.clear();
        assertTrue(cache.isEmpty());

    }

    @Test
    void testSimpleSoftCacheContainsKey() {
        assertFalse(cache.containsKey(1));
        cache.put(1, "foo");
        assertTrue(cache.containsKey(1));

        assertFalse(cache.containsKey(2));
        cache.put(2, "foo");
        assertTrue(cache.containsKey(2));
    }

    @Test
    void testSimpleSoftCacheKeySet() {
        cache.put(1, "foo");
        cache.put(2, "foo");

        Set<Integer> keys = cache.keySet();
        assertEquals(2, keys.size());

        Iterator<Integer> it = keys.iterator();
        assertEquals(1, it.next().intValue());
        assertEquals(2, it.next().intValue());
    }

    @Test
    void testSimpleSoftCacheNotRunOutOfMemory() {
        // we should not run out of memory using the soft cache
        // if you run this test with a regular cache then you will run out of memory
        int maximumCacheSize = 1024;
        for (int i = 0; i < maximumCacheSize; i++) {
            Object data = new LargeObject();
            Integer key = Integer.valueOf(i);
            cache.put(key, data);
        }

        Map<Integer, Object> tmp = new HashMap<>(cache);
        int size = tmp.size();
        assertTrue(size < maximumCacheSize, "Cache size should not be max, was: " + size);
    }

    public static class LargeObject {

        byte[] data;

        public LargeObject() {
            this.data = new byte[100 * 1024 * 1024]; // 100 MB
        }
    }
}
