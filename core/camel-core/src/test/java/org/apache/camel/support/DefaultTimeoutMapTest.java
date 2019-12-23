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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.camel.TimeoutMap;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.awaitility.Awaitility.await;

public class DefaultTimeoutMapTest extends Assert {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultTimeoutMapTest.class);
    private ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1);

    @Test
    public void testDefaultTimeoutMap() throws Exception {
        DefaultTimeoutMap<?, ?> map = new DefaultTimeoutMap<>(executor);
        map.start();
        assertTrue(map.currentTime() > 0);

        assertEquals(0, map.size());

        map.stop();
    }

    @Test
    public void testDefaultTimeoutMapPurge() throws Exception {
        DefaultTimeoutMap<String, Integer> map = new DefaultTimeoutMap<>(executor, 100);
        map.start();
        assertTrue(map.currentTime() > 0);

        assertEquals(0, map.size());

        map.put("A", 123, 50);
        assertEquals(1, map.size());

        Thread.sleep(250);
        if (map.size() > 0) {
            LOG.warn("Waiting extra due slow CI box");
            Thread.sleep(1000);
        }

        assertEquals(0, map.size());

        map.stop();
    }

    @Test
    public void testDefaultTimeoutMapForcePurge() throws Exception {
        DefaultTimeoutMap<String, Integer> map = new DefaultTimeoutMap<>(executor, 100);
        // map.start(); // Do not start background purge
        assertTrue(map.currentTime() > 0);

        assertEquals(0, map.size());

        map.put("A", 123, 10);
        assertEquals(1, map.size());

        Thread.sleep(50);

        // will purge and remove old entries
        map.purge();

        assertEquals(0, map.size());
    }

    @Test
    public void testDefaultTimeoutMapGetRemove() throws Exception {
        DefaultTimeoutMap<String, Integer> map = new DefaultTimeoutMap<>(executor, 100);
        map.start();
        assertTrue(map.currentTime() > 0);

        assertEquals(0, map.size());

        map.put("A", 123, 50);
        assertEquals(1, map.size());

        assertEquals(123, (int)map.get("A"));

        Object old = map.remove("A");
        assertEquals(123, old);
        assertEquals(null, map.get("A"));
        assertEquals(0, map.size());

        map.stop();
    }

    @Test
    public void testExecutor() throws Exception {
        ScheduledExecutorService e = Executors.newScheduledThreadPool(2);

        DefaultTimeoutMap<String, Integer> map = new DefaultTimeoutMap<>(e, 50);
        map.start();
        assertEquals(50, map.getPurgePollTime());

        map.put("A", 123, 100);
        assertEquals(1, map.size());

        Thread.sleep(250);

        if (map.size() > 0) {
            LOG.warn("Waiting extra due slow CI box");
            Thread.sleep(1000);
        }
        // should have been timed out now
        assertEquals(0, map.size());

        assertSame(e, map.getExecutor());

        map.stop();
    }

    @Test
    public void testExpiredInCorrectOrder() throws Exception {
        final List<String> keys = new ArrayList<>();
        final List<Integer> values = new ArrayList<>();

        DefaultTimeoutMap<String, Integer> map = new DefaultTimeoutMap<>(executor, 100);
        map.addListener((type, key, value) -> {
            if (type == TimeoutMap.Listener.Type.Evict) {
                keys.add(key);
                values.add(value);
            }
        });
        map.start();
        assertEquals(0, map.size());

        map.put("A", 1, 50);
        map.put("B", 2, 30);
        map.put("C", 3, 40);
        map.put("D", 4, 20);
        map.put("E", 5, 40);
        // is not expired
        map.put("F", 6, 800);

        Thread.sleep(250);

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

        map.stop();
    }

    @Test
    public void testDefaultTimeoutMapStopStart() throws Exception {
        DefaultTimeoutMap<String, Integer> map = new DefaultTimeoutMap<>(executor, 100);
        map.start();
        map.put("A", 1, 500);

        assertEquals(1, map.size());
        map.stop();

        assertEquals(0, map.size());
        map.put("A", 1, 50);

        // should not timeout as the scheduler doesn't run
        Thread.sleep(250);
        assertEquals(1, map.size());

        // start
        map.start();

        // start and wait for scheduler to purge
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
        // now it should be gone
        assertEquals(0, map.size()));

        map.stop();
    }

}
