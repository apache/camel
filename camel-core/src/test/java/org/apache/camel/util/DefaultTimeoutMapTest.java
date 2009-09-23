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
        DefaultTimeoutMap map = new DefaultTimeoutMap();
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
        DefaultTimeoutMap map = new DefaultTimeoutMap();
        assertTrue(map.currentTime() > 0);

        assertEquals(0, map.size());

        map.put("A", 123, 500);
        assertEquals(1, map.size());

        Thread.sleep(2000);

        assertEquals(123, map.get("A"));

        // will purge and remove old entries
        map.purge();

        // but we just used get to get it so its refreshed
        assertEquals(1, map.size());
    }

    public void testDefaultTimeoutMapGetRemove() throws Exception {
        DefaultTimeoutMap map = new DefaultTimeoutMap();
        assertTrue(map.currentTime() > 0);

        assertEquals(0, map.size());

        map.put("A", 123, 500);
        assertEquals(1, map.size());

        assertEquals(123, map.get("A"));

        map.remove("A");
        assertEquals(null, map.get("A"));
        assertEquals(0, map.size());
    }

    public void testDefaultTimeoutMapGetKeys() throws Exception {
        DefaultTimeoutMap map = new DefaultTimeoutMap();
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
        ScheduledExecutorService e = ExecutorServiceHelper.newScheduledThreadPool(1, "foo", true);

        DefaultTimeoutMap map = new DefaultTimeoutMap(e, 500);
        assertEquals(500, map.getPurgePollTime());

        map.put("A", 123, 1000);
        assertEquals(1, map.size());

        Thread.sleep(2000);

        // should be gone now
        assertEquals(0, map.size());

        assertSame(e, map.getExecutor());
    }

}
