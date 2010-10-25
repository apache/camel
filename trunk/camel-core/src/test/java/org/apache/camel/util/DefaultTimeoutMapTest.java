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
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import junit.framework.TestCase;
import org.apache.camel.util.concurrent.ExecutorServiceHelper;

/**
 * @version $Revision$
 */
public class DefaultTimeoutMapTest extends TestCase {

    public void testDefaultTimeoutMap() {
        DefaultTimeoutMap map = new DefaultTimeoutMap();
        assertTrue(map.currentTime() > 0);

        assertEquals(0, map.size());
    }

    public void testDefaultTimeoutMapPurge() throws Exception {
        DefaultTimeoutMap<String, Integer> map = new DefaultTimeoutMap<String, Integer>();
        assertTrue(map.currentTime() > 0);

        assertEquals(0, map.size());

        map.put("A", 123, 500);
        assertEquals(1, map.size());

        Thread.sleep(2000);

        // will purge and remove old entries
        map.purge();

        assertEquals(0, map.size());
    }

    public void testDefaultTimeoutMapGetPurge() throws Exception {
        DefaultTimeoutMap<String, Integer> map = new DefaultTimeoutMap<String, Integer>();
        assertTrue(map.currentTime() > 0);

        assertEquals(0, map.size());

        map.put("A", 123, 500);
        assertEquals(1, map.size());

        Thread.sleep(2000);

        assertEquals(123, (int)map.get("A"));

        // will purge and remove old entries
        map.purge();

        // but we just used get to get it so its refreshed
        assertEquals(1, map.size());
    }

    public void testDefaultTimeoutMapGetRemove() throws Exception {
        DefaultTimeoutMap<String, Integer> map = new DefaultTimeoutMap<String, Integer>();
        assertTrue(map.currentTime() > 0);

        assertEquals(0, map.size());

        map.put("A", 123, 500);
        assertEquals(1, map.size());

        assertEquals(123, (int)map.get("A"));

        Object old = map.remove("A");
        assertEquals(123, old);
        assertEquals(null, map.get("A"));
        assertEquals(0, map.size());
    }

    public void testDefaultTimeoutMapGetKeys() throws Exception {
        DefaultTimeoutMap<String, Integer> map = new DefaultTimeoutMap<String, Integer>();
        assertTrue(map.currentTime() > 0);

        assertEquals(0, map.size());

        map.put("A", 123, 500);
        map.put("B", 456, 500);
        assertEquals(2, map.size());

        Object[] keys = map.getKeys();
        assertNotNull(keys);
        assertEquals(2, keys.length);
    }

    public void testExecutor() throws Exception {
        ScheduledExecutorService e = ExecutorServiceHelper.newScheduledThreadPool(2, null, "foo", true);

        DefaultTimeoutMap<String, Integer> map = new DefaultTimeoutMap<String, Integer>(e, 500);
        assertEquals(500, map.getPurgePollTime());

        map.put("A", 123, 1000);
        assertEquals(1, map.size());

        Thread.sleep(2000);

        // should be gone now
        assertEquals(0, map.size());

        assertSame(e, map.getExecutor());
    }

    public void testExpiredInCorrectOrder() throws Exception {
        final List<String> keys = new ArrayList<String>();
        final List<Integer> values = new ArrayList<Integer>();

        DefaultTimeoutMap<String, Integer> map = new DefaultTimeoutMap<String, Integer>() {
            @Override
            public boolean onEviction(String key, Integer value) {
                keys.add(key);
                values.add(value);
                return true;
            }
        };
        assertEquals(0, map.size());

        map.put("A", 1, 500);
        map.put("B", 2, 300);
        map.put("C", 3, 400);
        map.put("D", 4, 200);
        map.put("E", 5, 400);
        // is not expired
        map.put("F", 6, 8000);

        Thread.sleep(2000);

        // force purge
        map.purge();

        assertEquals("D", keys.get(0));
        assertEquals(4, values.get(0).intValue());
        assertEquals("B", keys.get(1));
        assertEquals(2, values.get(1).intValue());
        assertEquals("C", keys.get(2));
        assertEquals(3, values.get(2).intValue());
        assertEquals("E", keys.get(3));
        assertEquals(5, values.get(3).intValue());
        assertEquals("A", keys.get(4));
        assertEquals(1, values.get(4).intValue());

        assertEquals(1, map.size());
    }

    public void testExpiredNotEvicted() throws Exception {
        final List<String> keys = new ArrayList<String>();
        final List<Integer> values = new ArrayList<Integer>();

        DefaultTimeoutMap<String, Integer> map = new DefaultTimeoutMap<String, Integer>() {
            @Override
            public boolean onEviction(String key, Integer value) {
                // do not evict special key
                if ("gold".equals(key)) {
                    return false;
                }
                keys.add(key);
                values.add(value);
                return true;
            }
        };
        assertEquals(0, map.size());

        map.put("A", 1, 900);
        map.put("B", 2, 1000);
        map.put("gold", 9, 1100);
        map.put("C", 3, 1200);

        Thread.sleep(2000);

        // force purge
        map.purge();

        assertEquals("A", keys.get(0));
        assertEquals(1, values.get(0).intValue());
        assertEquals("B", keys.get(1));
        assertEquals(2, values.get(1).intValue());
        assertEquals("C", keys.get(2));
        assertEquals(3, values.get(2).intValue());

        // and keep the gold in the map
        assertEquals(1, map.size());
        assertEquals(Integer.valueOf(9), map.get("gold"));
    }

}
