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

import org.junit.Assert;
import org.junit.Test;

/**
 * @version 
 */
public class SynchronousExecutorServiceTest extends Assert {

    private static boolean invoked;
    private static String name1;
    private static String name2;

    @Test
    public void testSynchronousExecutorService() throws Exception {
        name1 = Thread.currentThread().getName();

        ExecutorService service = new SynchronousExecutorService();
        service.execute(new Runnable() {
            public void run() {
                invoked = true;
                name2 = Thread.currentThread().getName();
            }
        });

        assertTrue("Should have been invoked", invoked);
        assertEquals("Should use same thread", name1, name2);
    }

    @Test
    public void testSynchronousExecutorServiceShutdown() throws Exception {
        ExecutorService service = new SynchronousExecutorService();
        service.execute(new Runnable() {
            public void run() {
                invoked = true;
            }
        });
        service.shutdown();

        assertTrue(service.isShutdown());
        assertTrue(service.isTerminated());
    }
}
