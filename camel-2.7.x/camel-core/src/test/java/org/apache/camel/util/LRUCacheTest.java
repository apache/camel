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

import junit.framework.TestCase;
import org.apache.camel.Service;

/**
 * @version 
 */
public class LRUCacheTest extends TestCase {

    private LRUCache<String, Service> cache;

    @Override
    protected void setUp() throws Exception {
        cache = new LRUCache<String, Service>(10);
    }

    public void testLRUCache() {
        MyService service1 = new MyService();
        MyService service2 = new MyService();

        cache.put("A", service1);
        cache.put("B", service2);

        assertEquals(2, cache.size());

        assertSame(service1, cache.get("A"));
        assertSame(service2, cache.get("B"));
    }

    public void testLRUCacheStop() throws Exception {
        MyService service1 = new MyService();
        MyService service2 = new MyService();

        cache.put("A", service1);
        cache.put("B", service2);

        assertEquals(false, service1.isStopped());
        assertEquals(false, service2.isStopped());

        cache.stop();

        assertEquals(0, cache.size());

        assertEquals(true, service1.isStopped());
        assertEquals(true, service2.isStopped());
    }

    private final class MyService implements Service {

        private boolean stopped;

        public void start() throws Exception {
        }

        public void stop() throws Exception {
            stopped = true;
        }

        public boolean isStopped() {
            return stopped;
        }
    }
}
