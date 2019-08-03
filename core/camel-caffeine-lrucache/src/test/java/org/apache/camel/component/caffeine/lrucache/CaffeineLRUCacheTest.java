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

import org.apache.camel.Service;
import org.apache.camel.support.LRUCache;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class CaffeineLRUCacheTest {

    private LRUCache<String, Service> cache;

    @Before
    public void setUp() throws Exception {
        // for testing use sync listener
        cache = new CaffeineLRUCache<>(10, 10, true, false, false, true);
    }

    @Test
    public void testLRUCache() {
        MyService service1 = new MyService();
        MyService service2 = new MyService();

        cache.put("A", service1);
        cache.put("B", service2);

        assertEquals(2, cache.size());

        assertSame(service1, cache.get("A"));
        assertSame(service2, cache.get("B"));
    }

    @Test
    public void testLRUCacheEviction() throws Exception {
        MyService service1 = new MyService();
        MyService service2 = new MyService();
        MyService service3 = new MyService();
        MyService service4 = new MyService();
        MyService service5 = new MyService();
        MyService service6 = new MyService();
        MyService service7 = new MyService();
        MyService service8 = new MyService();
        MyService service9 = new MyService();
        MyService service10 = new MyService();
        MyService service11 = new MyService();
        MyService service12 = new MyService();

        cache.put("A", service1);
        assertNull(service1.getStopped());
        cache.put("B", service2);
        assertNull(service2.getStopped());
        cache.put("C", service3);
        assertNull(service3.getStopped());
        cache.put("D", service4);
        assertNull(service4.getStopped());
        cache.put("E", service5);
        assertNull(service5.getStopped());
        cache.put("F", service6);
        assertNull(service6.getStopped());
        cache.put("G", service7);
        assertNull(service7.getStopped());
        cache.put("H", service8);
        assertNull(service8.getStopped());
        cache.put("I", service9);
        assertNull(service9.getStopped());
        cache.put("J", service10);
        assertNull(service10.getStopped());

        // we are now full
        assertEquals(10, cache.size());

        cache.put("K", service11);
        assertNull(service11.getStopped());

        // the eviction is async so force cleanup
        cache.cleanUp();

        cache.put("L", service12);

        // the eviction is async so force cleanup
        cache.cleanUp();

        assertEquals(10, cache.size());
    }

    @Test
    public void testLRUCacheHitsAndMisses() {
        MyService service1 = new MyService();
        MyService service2 = new MyService();

        cache.put("A", service1);
        cache.put("B", service2);

        assertEquals(0, cache.getHits());
        assertEquals(0, cache.getMisses());

        cache.get("A");
        assertEquals(1, cache.getHits());
        assertEquals(0, cache.getMisses());

        cache.get("A");
        assertEquals(2, cache.getHits());
        assertEquals(0, cache.getMisses());

        cache.get("B");
        assertEquals(3, cache.getHits());
        assertEquals(0, cache.getMisses());

        cache.get("C");
        assertEquals(3, cache.getHits());
        assertEquals(1, cache.getMisses());

        cache.get("D");
        assertEquals(3, cache.getHits());
        assertEquals(2, cache.getMisses());

        cache.resetStatistics();
        assertEquals(0, cache.getHits());
        assertEquals(0, cache.getMisses());

        cache.get("B");
        assertEquals(1, cache.getHits());
        assertEquals(0, cache.getMisses());

        cache.clear();
        assertEquals(0, cache.getHits());
        assertEquals(0, cache.getMisses());

        cache.get("B");
        assertEquals(0, cache.getHits());
        assertEquals(1, cache.getMisses());
    }

    private static final class MyService implements Service {

        private Boolean stopped;

        @Override
        public void start() {
        }

        @Override
        public void stop() {
            stopped = true;
        }

        public Boolean getStopped() {
            return stopped;
        }
    }
}
