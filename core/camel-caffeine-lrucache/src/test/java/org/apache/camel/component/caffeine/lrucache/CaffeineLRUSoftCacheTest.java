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
package org.apache.camel.component.caffeine.lrucache;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class CaffeineLRUSoftCacheTest {

    @Test
    public void testLRUSoftCacheGetAndPut() throws Exception {
        CaffeineLRUSoftCache<Integer, Object> cache = new CaffeineLRUSoftCache<>(1000);

        cache.put(1, "foo");
        cache.put(2, "bar");

        assertEquals("foo", cache.get(1));
        assertEquals("bar", cache.get(2));
        assertEquals(null, cache.get(3));
        assertEquals(2, cache.size());
    }

    @Test
    public void testLRUSoftCacheHitsAndMisses() throws Exception {
        CaffeineLRUSoftCache<Integer, Object> cache = new CaffeineLRUSoftCache<>(1000);

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

    @Test
    public void testLRUSoftCachePutOverride() throws Exception {
        CaffeineLRUSoftCache<Integer, Object> cache = new CaffeineLRUSoftCache<>(1000);

        Object old = cache.put(1, "foo");
        Assert.assertNull(old);
        old = cache.put(2, "bar");
        Assert.assertNull(old);

        assertEquals("foo", cache.get(1));
        assertEquals("bar", cache.get(2));

        old = cache.put(1, "changed");
        assertEquals("foo", old);
        assertEquals("changed", cache.get(1));
        assertEquals(2, cache.size());
    }

    @Test
    public void testLRUSoftCachePutAll() throws Exception {
        CaffeineLRUSoftCache<Integer, Object> cache = new CaffeineLRUSoftCache<>(1000);

        Map<Integer, Object> map = new HashMap<>();
        map.put(1, "foo");
        map.put(2, "bar");

        cache.putAll(map);

        assertEquals("foo", cache.get(1));
        assertEquals("bar", cache.get(2));
        assertEquals(null, cache.get(3));
        assertEquals(2, cache.size());
    }

    @Test
    public void testLRUSoftCachePutAllAnotherLRUSoftCache() throws Exception {
        CaffeineLRUSoftCache<Integer, Object> cache = new CaffeineLRUSoftCache<>(1000);

        CaffeineLRUSoftCache<Integer, Object> cache2 = new CaffeineLRUSoftCache<>(1000);
        cache2.put(1, "foo");
        cache2.put(2, "bar");

        cache.putAll(cache2);

        assertEquals("foo", cache.get(1));
        assertEquals("bar", cache.get(2));
        assertEquals(null, cache.get(3));
        assertEquals(2, cache.size());
    }

    @Test
    public void testLRUSoftCacheRemove() throws Exception {
        CaffeineLRUSoftCache<Integer, Object> cache = new CaffeineLRUSoftCache<>(1000);

        cache.put(1, "foo");
        cache.put(2, "bar");

        assertEquals("bar", cache.get(2));
        cache.remove(2);
        assertEquals(null, cache.get(2));
    }

    @Test
    public void testLRUSoftCacheValues() throws Exception {
        CaffeineLRUSoftCache<Integer, Object> cache = new CaffeineLRUSoftCache<>(1000);

        cache.put(1, "foo");
        cache.put(2, "bar");

        Collection<Object> col = cache.values();
        assertEquals(2, col.size());

        Iterator<Object> it = col.iterator();
        assertEquals("foo", it.next());
        assertEquals("bar", it.next());
    }

    @Test
    public void testLRUSoftCacheEmpty() throws Exception {
        CaffeineLRUSoftCache<Integer, Object> cache = new CaffeineLRUSoftCache<>(1000);

        Assert.assertTrue(cache.isEmpty());

        cache.put(1, "foo");
        Assert.assertFalse(cache.isEmpty());

        cache.put(2, "bar");
        Assert.assertFalse(cache.isEmpty());

        cache.remove(2);
        Assert.assertFalse(cache.isEmpty());

        cache.clear();
        Assert.assertTrue(cache.isEmpty());
    }

    @Test
    public void testLRUSoftCacheContainsKey() throws Exception {
        CaffeineLRUSoftCache<Integer, Object> cache = new CaffeineLRUSoftCache<>(1000);

        Assert.assertFalse(cache.containsKey(1));
        cache.put(1, "foo");
        Assert.assertTrue(cache.containsKey(1));

        Assert.assertFalse(cache.containsKey(2));
        cache.put(2, "foo");
        Assert.assertTrue(cache.containsKey(2));

        cache.clear();
        Assert.assertFalse(cache.containsKey(1));
        Assert.assertFalse(cache.containsKey(2));
    }

    @Test
    public void testLRUSoftCacheKeySet() throws Exception {
        CaffeineLRUSoftCache<Integer, Object> cache = new CaffeineLRUSoftCache<>(1000);

        cache.put(1, "foo");
        cache.put(2, "foo");

        Set<Integer> keys = cache.keySet();
        assertEquals(2, keys.size());

        Iterator<Integer> it = keys.iterator();
        assertEquals(1, it.next().intValue());
        assertEquals(2, it.next().intValue());
    }
}
