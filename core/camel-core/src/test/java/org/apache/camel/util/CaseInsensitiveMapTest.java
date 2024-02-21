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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CaseInsensitiveMapTest {

    @Test
    public void testLookupCaseAgnostic() {
        Map<String, Object> map = new CaseInsensitiveMap();
        assertNull(map.get("foo"));

        map.put("foo", "cheese");

        assertEquals("cheese", map.get("foo"));
        assertEquals("cheese", map.get("Foo"));
        assertEquals("cheese", map.get("FOO"));
    }

    @Test
    public void testLookupCaseAgnosticAddHeader() {
        Map<String, Object> map = new CaseInsensitiveMap();
        assertNull(map.get("foo"));

        map.put("foo", "cheese");

        assertEquals("cheese", map.get("foo"));
        assertEquals("cheese", map.get("Foo"));
        assertEquals("cheese", map.get("FOO"));
        assertNull(map.get("unknown"));

        map.put("bar", "beer");

        assertEquals("beer", map.get("bar"));
        assertEquals("beer", map.get("Bar"));
        assertEquals("beer", map.get("BAR"));
        assertNull(map.get("unknown"));
    }

    @Test
    public void testLookupCaseAgnosticAddHeader2() {
        Map<String, Object> map = new CaseInsensitiveMap();
        assertNull(map.get("foo"));

        map.put("foo", "cheese");

        assertEquals("cheese", map.get("FOO"));
        assertEquals("cheese", map.get("foo"));
        assertEquals("cheese", map.get("Foo"));
        assertNull(map.get("unknown"));

        map.put("bar", "beer");

        assertEquals("beer", map.get("BAR"));
        assertEquals("beer", map.get("bar"));
        assertEquals("beer", map.get("Bar"));
        assertNull(map.get("unknown"));
    }

    @Test
    public void testLookupCaseAgnosticAddHeaderRemoveHeader() {
        Map<String, Object> map = new CaseInsensitiveMap();
        assertNull(map.get("foo"));

        map.put("foo", "cheese");

        assertEquals("cheese", map.get("foo"));
        assertEquals("cheese", map.get("Foo"));
        assertEquals("cheese", map.get("FOO"));
        assertNull(map.get("unknown"));

        map.put("bar", "beer");

        assertEquals("beer", map.get("bar"));
        assertEquals("beer", map.get("Bar"));
        assertEquals("beer", map.get("BAR"));
        assertNull(map.get("unknown"));

        map.remove("bar");
        assertNull(map.get("bar"));
        assertNull(map.get("unknown"));
    }

    @Test
    public void testSetWithDifferentCase() {
        Map<String, Object> map = new CaseInsensitiveMap();
        assertNull(map.get("foo"));

        map.put("foo", "cheese");
        map.put("Foo", "bar");

        assertEquals("bar", map.get("FOO"));
        assertEquals("bar", map.get("foo"));
        assertEquals("bar", map.get("Foo"));
    }

    @Test
    public void testRemoveWithDifferentCase() {
        Map<String, Object> map = new CaseInsensitiveMap();
        assertNull(map.get("foo"));

        map.put("foo", "cheese");
        map.put("Foo", "bar");

        assertEquals("bar", map.get("FOO"));
        assertEquals("bar", map.get("foo"));
        assertEquals("bar", map.get("Foo"));

        map.remove("FOO");

        assertNull(map.get("foo"));
        assertNull(map.get("Foo"));
        assertNull(map.get("FOO"));

        assertTrue(map.isEmpty());
    }

    @Test
    public void testPutAll() {
        Map<String, Object> map = new CaseInsensitiveMap();
        assertNull(map.get("foo"));

        Map<String, Object> other = new CaseInsensitiveMap();
        other.put("Foo", "cheese");
        other.put("bar", 123);

        map.putAll(other);

        assertEquals("cheese", map.get("FOO"));
        assertEquals("cheese", map.get("foo"));
        assertEquals("cheese", map.get("Foo"));

        assertEquals(123, map.get("BAR"));
        assertEquals(123, map.get("bar"));
        assertEquals(123, map.get("BaR"));

        // key case should be preserved
        Map<String, Object> keys = new HashMap<>(map);

        assertEquals("cheese", keys.get("Foo"));
        assertNull(keys.get("foo"));
        assertNull(keys.get("FOO"));

        assertEquals(123, keys.get("bar"));
        assertNull(keys.get("Bar"));
        assertNull(keys.get("BAR"));
    }

    @Test
    public void testPutAllOther() {
        Map<String, Object> map = new CaseInsensitiveMap();
        assertNull(map.get("foo"));

        Map<String, Object> other = new HashMap<>();
        other.put("Foo", "cheese");
        other.put("bar", 123);

        map.putAll(other);

        assertEquals("cheese", map.get("FOO"));
        assertEquals("cheese", map.get("foo"));
        assertEquals("cheese", map.get("Foo"));

        assertEquals(123, map.get("BAR"));
        assertEquals(123, map.get("bar"));
        assertEquals(123, map.get("BaR"));
    }

    @Test
    public void testPutAllEmpty() {
        Map<String, Object> map = new CaseInsensitiveMap();
        map.put("foo", "cheese");

        Map<String, Object> other = new HashMap<>();
        map.putAll(other);

        assertEquals("cheese", map.get("FOO"));
        assertEquals("cheese", map.get("foo"));
        assertEquals("cheese", map.get("Foo"));

        assertEquals(1, map.size());
    }

    @Test
    public void testConstructFromOther() {
        Map<String, Object> other = new HashMap<>();
        other.put("Foo", "cheese");
        other.put("bar", 123);

        Map<String, Object> map = new CaseInsensitiveMap(other);

        assertEquals("cheese", map.get("FOO"));
        assertEquals("cheese", map.get("foo"));
        assertEquals("cheese", map.get("Foo"));

        assertEquals(123, map.get("BAR"));
        assertEquals(123, map.get("bar"));
        assertEquals(123, map.get("BaR"));
    }

    @Test
    public void testKeySet() {
        Map<String, Object> map = new CaseInsensitiveMap();
        map.put("Foo", "cheese");
        map.put("BAR", 123);
        map.put("baZ", "beer");

        Set<String> keys = map.keySet();

        // we should be able to lookup no matter what case
        assertTrue(keys.contains("Foo"));
        assertTrue(keys.contains("foo"));
        assertTrue(keys.contains("FOO"));

        assertTrue(keys.contains("BAR"));
        assertTrue(keys.contains("bar"));
        assertTrue(keys.contains("Bar"));

        assertTrue(keys.contains("baZ"));
        assertTrue(keys.contains("baz"));
        assertTrue(keys.contains("Baz"));
        assertTrue(keys.contains("BAZ"));
    }

    @Test
    public void testRetainKeysCopyToAnotherMap() {
        Map<String, Object> map = new CaseInsensitiveMap();
        map.put("Foo", "cheese");
        map.put("BAR", 123);
        map.put("baZ", "beer");

        Map<String, Object> other = new HashMap<>(map);

        // we should retain the cases of the original keys
        // when its copied to another map
        assertTrue(other.containsKey("Foo"));
        assertFalse(other.containsKey("foo"));
        assertFalse(other.containsKey("FOO"));

        assertTrue(other.containsKey("BAR"));
        assertFalse(other.containsKey("bar"));
        assertFalse(other.containsKey("Bar"));

        assertTrue(other.containsKey("baZ"));
        assertFalse(other.containsKey("baz"));
        assertFalse(other.containsKey("Baz"));
        assertFalse(other.containsKey("BAZ"));
    }

    @Test
    public void testValues() {
        Map<String, Object> map = new CaseInsensitiveMap();
        map.put("Foo", "cheese");
        map.put("BAR", "123");
        map.put("baZ", "Beer");

        Iterator<Object> it = map.values().iterator();

        // should be String values
        assertEquals("String", it.next().getClass().getSimpleName());
        assertEquals("String", it.next().getClass().getSimpleName());
        assertEquals("String", it.next().getClass().getSimpleName());

        Collection<Object> values = map.values();
        assertEquals(3, values.size());
        assertTrue(values.contains("cheese"));
        assertTrue(values.contains("123"));
        assertTrue(values.contains("Beer"));
    }

    @Test
    public void testRomeks() {
        Map<String, Object> map = new CaseInsensitiveMap();
        map.put("foo", "cheese");

        assertEquals(1, map.size());
        assertEquals("cheese", map.get("fOo"));
        assertTrue(map.containsKey("foo"));
        assertTrue(map.containsKey("FOO"));

        assertTrue(map.keySet().contains("FOO"));
        assertTrue(map.keySet().contains("FoO"));
        assertTrue(map.keySet().contains("Foo"));
        assertTrue(map.keySet().contains("foo"));
        assertTrue(map.keySet().contains("fOO"));

        map.put("FOO", "cake");
        assertEquals(1, map.size());
        assertTrue(map.containsKey("foo"));
        assertTrue(map.containsKey("FOO"));

        assertEquals("cake", map.get("fOo"));
    }

    @Test
    public void testRomeksUsingRegularHashMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("foo", "cheese");

        assertEquals(1, map.size());
        assertNull(map.get("fOo"));
        assertTrue(map.containsKey("foo"));
        assertFalse(map.containsKey("FOO"));

        assertFalse(map.keySet().contains("FOO"));

        map.put("FOO", "cake");
        assertEquals(2, map.size());
        assertTrue(map.containsKey("foo"));
        assertTrue(map.containsKey("FOO"));

        assertNull(map.get("fOo"));
        assertEquals("cheese", map.get("foo"));
        assertEquals("cake", map.get("FOO"));
    }

    @Test
    public void testRomeksTransferredToHashMapAfterwards() {
        Map<String, Object> map = new CaseInsensitiveMap();
        map.put("Foo", "cheese");
        map.put("FOO", "cake");
        assertEquals(1, map.size());
        assertTrue(map.containsKey("foo"));
        assertTrue(map.containsKey("FOO"));

        Map<String, Object> other = new HashMap<>(map);
        assertFalse(other.containsKey("foo"));
        assertFalse(other.containsKey("FOO"));
        // CaseInsensitiveMap preserves the original keys, which would be the
        // 1st key we put
        assertTrue(other.containsKey("Foo"));
        assertEquals(1, other.size());
    }

    @Test
    public void testSerialization() throws Exception {
        CaseInsensitiveMap testMap = new CaseInsensitiveMap();
        testMap.put("key", "value");
        // force entry set to be created which could cause the map to be non
        // serializable
        testMap.entrySet();

        ByteArrayOutputStream bStream = new ByteArrayOutputStream();
        ObjectOutputStream objStream = new ObjectOutputStream(bStream);
        objStream.writeObject(testMap);

        ObjectInputStream inStream = new ObjectInputStream(new ByteArrayInputStream(bStream.toByteArray()));
        CaseInsensitiveMap testMapCopy = (CaseInsensitiveMap) inStream.readObject();

        assertTrue(testMapCopy.containsKey("key"));
    }

    @Test
    public void testCopyToAnotherMapPreserveKeyCaseEntrySet() {
        Map<String, Object> map = new CaseInsensitiveMap();
        map.put("Foo", "cheese");
        map.put("BAR", "cake");
        assertEquals(2, map.size());
        assertTrue(map.containsKey("foo"));
        assertTrue(map.containsKey("bar"));

        Map<String, Object> other = new HashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            other.put(key, value);
        }

        assertFalse(other.containsKey("foo"));
        assertTrue(other.containsKey("Foo"));
        assertFalse(other.containsKey("bar"));
        assertTrue(other.containsKey("BAR"));
        assertEquals(2, other.size());
    }

    @Test
    public void testCopyToAnotherMapPreserveKeyCasePutAll() {
        Map<String, Object> map = new CaseInsensitiveMap();
        map.put("Foo", "cheese");
        map.put("BAR", "cake");
        assertEquals(2, map.size());
        assertTrue(map.containsKey("foo"));
        assertTrue(map.containsKey("bar"));

        Map<String, Object> other = new HashMap<>(map);

        assertFalse(other.containsKey("foo"));
        assertTrue(other.containsKey("Foo"));
        assertFalse(other.containsKey("bar"));
        assertTrue(other.containsKey("BAR"));
        assertEquals(2, other.size());
    }

    @Test
    public void testCopyToAnotherMapPreserveKeyCaseCtr() {
        Map<String, Object> map = new CaseInsensitiveMap();
        map.put("Foo", "cheese");
        map.put("BAR", "cake");
        assertEquals(2, map.size());
        assertTrue(map.containsKey("foo"));
        assertTrue(map.containsKey("bar"));

        Map<String, Object> other = new HashMap<>(map);

        assertFalse(other.containsKey("foo"));
        assertTrue(other.containsKey("Foo"));
        assertFalse(other.containsKey("bar"));
        assertTrue(other.containsKey("BAR"));
        assertEquals(2, other.size());
    }

    @Test
    public void testCopyToAnotherMapPreserveKeyKeySet() {
        Map<String, Object> map = new CaseInsensitiveMap();
        map.put("Foo", "cheese");
        map.put("BAR", "cake");
        assertEquals(2, map.size());
        assertTrue(map.containsKey("foo"));
        assertTrue(map.containsKey("bar"));

        Map<String, Object> other = new HashMap<>(map);

        // the original case of the keys should be preserved
        assertFalse(other.containsKey("foo"));
        assertTrue(other.containsKey("Foo"));
        assertFalse(other.containsKey("bar"));
        assertTrue(other.containsKey("BAR"));
        assertEquals(2, other.size());
    }

    @Test
    public void testConcurrent() throws Exception {
        ExecutorService service = Executors.newFixedThreadPool(5);

        final CountDownLatch latch = new CountDownLatch(1000);
        final Map<String, Object> map = new CaseInsensitiveMap();

        // do some stuff concurrently
        for (int i = 0; i < 1000; i++) {
            final int count = i;
            service.submit(new Runnable() {
                public void run() {
                    Map<String, Object> foo = new CaseInsensitiveMap();
                    foo.put("counter" + count, count);
                    foo.put("foo", 123);
                    foo.put("bar", 456);
                    foo.put("cake", "cheese");

                    // copy foo to map as map is a shared resource
                    synchronized (map) {
                        map.putAll(foo);
                    }

                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));

        assertEquals(1003, map.size());
        assertTrue(map.containsKey("counter0"));
        assertTrue(map.containsKey("counter500"));
        assertTrue(map.containsKey("counter999"));

        assertEquals(123, map.get("FOO"));
        assertEquals(456, map.get("Bar"));
        assertEquals("cheese", map.get("cAKe"));
        service.shutdownNow();
    }

    @Disabled("Manual test")
    public void xxxTestCopyMapWithCamelHeadersTest() throws Exception {
        Map<String, Object> map = new CaseInsensitiveMap();
        map.put("CamelA", "A");
        map.put("CamelB", "B");
        map.put("CamelC", "C");

        // retain maps so we can profile that the map doesn't duplicate
        // camel keys as they are intern
        List<Map<?, ?>> maps = new ArrayList<>();

        for (int i = 0; i < 10000; i++) {
            Map<String, Object> copy = new CaseInsensitiveMap(map);
            assertEquals(3, copy.size());
            assertEquals("A", copy.get("CamelA"));
            assertEquals("B", copy.get("CamelB"));
            assertEquals("C", copy.get("CamelC"));

            maps.add(copy);
        }

        assertEquals(10000, maps.size());

        assertEquals(3, map.size());
        assertEquals("A", map.get("CamelA"));
        assertEquals("B", map.get("CamelB"));
        assertEquals("C", map.get("CamelC"));

        // use a memory profiler to see memory allocation
        // often you may want to give it time to run so you
        // have chance to capture memory snapshot in profiler
        Thread.sleep(9999999);
    }

}
