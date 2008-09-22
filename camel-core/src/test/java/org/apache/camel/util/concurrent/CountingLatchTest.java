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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

/**
 * Test case for {@link CountingLatch}
 */
public class CountingLatchTest extends TestCase {

    private static final int COUNT = 4;
    private CountingLatch latch;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        latch = new CountingLatch();
    }

    /**
     * Test for counting down with the latch (similar to {@link CountDownLatch}
     */
    public void testCountDown() throws Exception {
        for (int i = 0; i < COUNT; i++) {
            latch.increment();
        }
        Thread thread = new Thread(new Runnable() {
            public void run() {
                for (int i = 0; i < COUNT; i++) {
                    sleep();
                    latch.decrement();
                }
            }
        });
        thread.start();
        assertFalse("We can't be done in 100 ms", latch.await(100, TimeUnit.MILLISECONDS));
        // but we can block until we are done
        assertLatchDone();
    }

    /**
     * Test for counting up with the latch
     */
    public void testCountUp() throws Exception {
        for (int i = 0; i < COUNT; i++) {
            latch.decrement();
        }
        Thread thread = new Thread(new Runnable() {

            public void run() {
                for (int i = 0; i < COUNT; i++) {
                    sleep();
                    latch.increment();
                }
            }

        });
        thread.start();
        assertFalse("We can't be done in 100 ms", latch.await(100, TimeUnit.MILLISECONDS));
        // but we can block until we are done
        assertLatchDone();
    }

    /**
     * Test for two threads that are simultaneously incrementing and decrementing the latch
     */
    public void testCountDownAndUp() throws Exception {
        Thread up = new Thread(new Runnable() {
            public void run() {
                for (int i = 0; i < COUNT; i++) {
                    latch.increment();
                }
            }
        });
        Thread down = new Thread(new Runnable() {
            public void run() {
                for (int i = 0; i < COUNT; i++) {
                    latch.increment();
                    latch.decrement();
                    sleep();
                    latch.decrement();
                }
            }
        });
        up.start();
        down.start();        
        assertLatchDone();
    }

    /**
     * Await the latch and assert the count is 0 when it does return
     * @throws InterruptedException
     */
    private void assertLatchDone() throws InterruptedException {
        latch.await();
        assertEquals("The latch has been released, so the count should be 0 now", 0, latch.getCount());
    }

    /**
     * Helper method do a little nap
     */
    private void sleep() {
        try {
            Thread.sleep(75);
        } catch (InterruptedException e) {
            fail("Thread shouldn't get interrupted -- " + e.getMessage());
        }
    }
}
