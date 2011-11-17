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
package org.apache.camel.util.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

/**
 * @version 
 */
public class ExecutorServiceHelperTest extends TestCase {

    public void testGetThreadName() {
        String name = ExecutorServiceHelper.getThreadName("Camel Thread ${counter} - ${name}", "foo");

        assertTrue(name.startsWith("Camel Thread"));
        assertTrue(name.endsWith("foo"));
    }

    public void testNewScheduledThreadPool() {
        ScheduledExecutorService pool = ExecutorServiceHelper.newScheduledThreadPool(1, "MyPool ${name}", "foo", true);
        assertNotNull(pool);
    }

    public void testNewThreadPool() {
        ExecutorService pool = ExecutorServiceHelper.newThreadPool("MyPool ${name}", "foo", 1, 1);
        assertNotNull(pool);
    }

    public void testNewThreadPool2() {
        ExecutorService pool = ExecutorServiceHelper.newThreadPool("MyPool ${name}", "foo", 1, 1, 20);
        assertNotNull(pool);
    }

    public void testNewThreadPool3() {
        ExecutorService pool = ExecutorServiceHelper.newThreadPool("MyPool ${name}", "foo", 1, 1,
                30, TimeUnit.SECONDS, 20, null, true);
        assertNotNull(pool);
    }

    public void testNewCachedThreadPool() {
        ExecutorService pool = ExecutorServiceHelper.newCachedThreadPool("MyPool ${name}", "foo", true);
        assertNotNull(pool);
    }

    public void testNewFixedThreadPool() {
        ExecutorService pool = ExecutorServiceHelper.newFixedThreadPool(1, "MyPool ${name}", "foo", true);
        assertNotNull(pool);
    }

    public void testNewSynchronousThreadPool() {
        ExecutorService pool = ExecutorServiceHelper.newSynchronousThreadPool();
        assertNotNull(pool);
    }

    public void testNewSingleThreadPool() {
        ExecutorService pool = ExecutorServiceHelper.newSingleThreadExecutor("MyPool ${name}", "foo", true);
        assertNotNull(pool);
    }

}
