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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.TestSupport;

/**
 *
 */
public class LRUSoftCacheTest extends TestSupport {

    public void testLRUSoftCacheGetAndPut() throws Exception {
        LRUSoftCache<Integer, Object> cache = new LRUSoftCache<Integer, Object>(1000);
        cache.start();

        cache.put(1, "foo");
        cache.put(2, "bar");

        assertEquals("foo", cache.get(1));
        assertEquals("bar", cache.get(2));
        assertEquals(null, cache.get(3));

        assertEquals(2, cache.size());

        cache.stop();
    }

    public void testLRUSoftCachePutOverride() throws Exception {
        LRUSoftCache<Integer, Object> cache = new LRUSoftCache<Integer, Object>(1000);
        cache.start();

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

        cache.stop();
    }

    public void testLRUSoftCachePutAll() throws Exception {
        LRUSoftCache<Integer, Object> cache = new LRUSoftCache<Integer, Object>(1000);
        cache.start();

        Map<Integer, Object> map = new HashMap<Integer, Object>();
        map.put(1, "foo");
        map.put(2, "bar");

        cache.putAll(map);

        assertEquals("foo", cache.get(1));
        assertEquals("bar", cache.get(2));
        assertEquals(null, cache.get(3));
        assertEquals(2, cache.size());

        cache.stop();
    }

    public void testLRUSoftCacheRemove() throws Exception {
        LRUSoftCache<Integer, Object> cache = new LRUSoftCache<Integer, Object>(1000);
        cache.start();

        cache.put(1, "foo");
        cache.put(2, "bar");

        assertEquals("bar", cache.get(2));
        cache.remove(2);
        assertEquals(null, cache.get(2));

        cache.stop();
    }

    public void testLRUSoftCacheValues() throws Exception {
        LRUSoftCache<Integer, Object> cache = new LRUSoftCache<Integer, Object>(1000);
        cache.start();

        cache.put(1, "foo");
        cache.put(2, "bar");

        Collection col = cache.values();
        assertEquals(2, col.size());

        Iterator it = col.iterator();
        assertEquals("foo", it.next());
        assertEquals("bar", it.next());

        cache.stop();
    }

    public void testLRUSoftCacheEmpty() throws Exception {
        LRUSoftCache<Integer, Object> cache = new LRUSoftCache<Integer, Object>(1000);
        cache.start();

        assertTrue(cache.isEmpty());

        cache.put(1, "foo");
        assertFalse(cache.isEmpty());

        cache.put(2, "bar");
        assertFalse(cache.isEmpty());

        cache.remove(2);
        assertFalse(cache.isEmpty());

        cache.clear();
        assertTrue(cache.isEmpty());

        cache.stop();
        assertTrue(cache.isEmpty());
    }

    public void testLRUSoftCacheStopEmpty() throws Exception {
        LRUSoftCache<Integer, Object> cache = new LRUSoftCache<Integer, Object>(1000);
        cache.start();

        assertTrue(cache.isEmpty());

        cache.put(1, "foo");
        cache.put(2, "bar");
        assertFalse(cache.isEmpty());

        cache.stop();
        assertTrue(cache.isEmpty());
    }

    public void testLRUSoftCacheContainsKey() throws Exception {
        LRUSoftCache<Integer, Object> cache = new LRUSoftCache<Integer, Object>(1000);
        cache.start();

        assertFalse(cache.containsKey(1));
        cache.put(1, "foo");
        assertTrue(cache.containsKey(1));

        assertFalse(cache.containsKey(2));
        cache.put(2, "foo");
        assertTrue(cache.containsKey(2));

        cache.stop();
        assertFalse(cache.containsKey(1));
        assertFalse(cache.containsKey(2));
    }

    public void testLRUSoftCacheKeySet() throws Exception {
        LRUSoftCache<Integer, Object> cache = new LRUSoftCache<Integer, Object>(1000);
        cache.start();

        cache.put(1, "foo");
        cache.put(2, "foo");

        Set<Integer> keys = cache.keySet();
        assertEquals(2, keys.size());

        Iterator<Integer> it = keys.iterator();
        assertEquals(1, it.next().intValue());
        assertEquals(2, it.next().intValue());

        cache.stop();
    }

    public void testLRUSoftCacheNotRunOutOfMemory() throws Exception {
        // we should not run out of memory using the soft cache
        // if you run this test with LRUCache then you will run out of memory
        LRUSoftCache<Integer, Object> cache = new LRUSoftCache<Integer, Object>(250);
        cache.start();

        for (int i = 0; i < 1000; i++) {
            Object data = createData();
            Integer key = new Integer(i);
            log.info("Putting {}", key);
            cache.put(key, data);
        }

        int size = cache.size();
        log.info("Cache size {}", size);
        assertTrue("Cache size should not be max, was: " + size, size < cache.getMaxCacheSize());

        // should be the last keys
        List<Integer> list = new ArrayList<Integer>(cache.keySet());
        log.info("Keys: " + list);

        assertTrue("Cache size should not be max, was: " + list.size(), list.size() < cache.getMaxCacheSize());

        // first key should not be 0
        int first = list.get(0).intValue();
        assertTrue("First key should not be 0, was: " + first, first != 0);

        // last key should be 999
        assertEquals(999, list.get(list.size() - 1).intValue());

        cache.stop();
    }

    private Object createData() {
        // 1mb data
        byte[] buf = new byte[1 * 1024 * 1024];
        return buf;
    }
}
