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

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.camel.TestSupport;

/**
 *
 */
public class LRUSoftCacheTest extends TestSupport {

    public void testLRUSoftCacheGetAndPut() throws Exception {
        LRUSoftCache<Integer, Object> cache = new LRUSoftCache<Integer, Object>(1000);

        cache.put(1, "foo");
        cache.put(2, "bar");

        assertEquals("foo", cache.get(1));
        assertEquals("bar", cache.get(2));
        assertEquals(null, cache.get(3));
        assertEquals(2, cache.size());
    }

    public void testLRUSoftCacheHitsAndMisses() throws Exception {
        LRUSoftCache<Integer, Object> cache = new LRUSoftCache<Integer, Object>(1000);

        cache.put(1, "foo");
        cache.put(2, "bar");

        assertEquals(0, cache.getHits());
        assertEquals(0, cache.getMisses());

        cache.get(1);
        assertEquals(1, cache.getHits());
        assertEquals(0, cache.getMisses());

        cache.get(3);
        assertEquals(1, cache.getHits());
        assertEquals(1, cache.getMisses());

        cache.get(2);
        assertEquals(2, cache.getHits());
        assertEquals(1, cache.getMisses());
    }

    public void testLRUSoftCachePutOverride() throws Exception {
        LRUSoftCache<Integer, Object> cache = new LRUSoftCache<Integer, Object>(1000);

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

    public void testLRUSoftCachePutAll() throws Exception {
        LRUSoftCache<Integer, Object> cache = new LRUSoftCache<Integer, Object>(1000);

        Map<Integer, Object> map = new HashMap<Integer, Object>();
        map.put(1, "foo");
        map.put(2, "bar");

        cache.putAll(map);

        assertEquals("foo", cache.get(1));
        assertEquals("bar", cache.get(2));
        assertEquals(null, cache.get(3));
        assertEquals(2, cache.size());
    }

    public void testLRUSoftCachePutAllAnotherLRUSoftCache() throws Exception {
        LRUSoftCache<Integer, Object> cache = new LRUSoftCache<Integer, Object>(1000);

        LRUSoftCache<Integer, Object> cache2 = new LRUSoftCache<Integer, Object>(1000);
        cache2.put(1, "foo");
        cache2.put(2, "bar");

        cache.putAll(cache2);

        assertEquals("foo", cache.get(1));
        assertEquals("bar", cache.get(2));
        assertEquals(null, cache.get(3));
        assertEquals(2, cache.size());
    }

    public void testLRUSoftCacheRemove() throws Exception {
        LRUSoftCache<Integer, Object> cache = new LRUSoftCache<Integer, Object>(1000);

        cache.put(1, "foo");
        cache.put(2, "bar");

        assertEquals("bar", cache.get(2));
        cache.remove(2);
        assertEquals(null, cache.get(2));
    }

    public void testLRUSoftCacheValues() throws Exception {
        LRUSoftCache<Integer, Object> cache = new LRUSoftCache<Integer, Object>(1000);

        cache.put(1, "foo");
        cache.put(2, "bar");

        Collection<Object> col = cache.values();
        assertEquals(2, col.size());

        Iterator<Object> it = col.iterator();
        assertEquals("foo", it.next());
        assertEquals("bar", it.next());
    }

    public void testLRUSoftCacheEmpty() throws Exception {
        LRUSoftCache<Integer, Object> cache = new LRUSoftCache<Integer, Object>(1000);

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

    public void testLRUSoftCacheContainsKey() throws Exception {
        LRUSoftCache<Integer, Object> cache = new LRUSoftCache<Integer, Object>(1000);

        assertFalse(cache.containsKey(1));
        cache.put(1, "foo");
        assertTrue(cache.containsKey(1));

        assertFalse(cache.containsKey(2));
        cache.put(2, "foo");
        assertTrue(cache.containsKey(2));

        cache.clear();
        assertFalse(cache.containsKey(1));
        assertFalse(cache.containsKey(2));
    }

    public void testLRUSoftCacheKeySet() throws Exception {
        LRUSoftCache<Integer, Object> cache = new LRUSoftCache<Integer, Object>(1000);

        cache.put(1, "foo");
        cache.put(2, "foo");

        Set<Integer> keys = cache.keySet();
        assertEquals(2, keys.size());

        Iterator<Integer> it = keys.iterator();
        assertEquals(1, it.next().intValue());
        assertEquals(2, it.next().intValue());
    }
}
