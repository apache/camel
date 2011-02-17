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

/**
 * @version 
 */
public class StopWatchTest extends TestCase {

    public void testStopWatch() throws Exception {
        StopWatch watch = new StopWatch();
        Thread.sleep(200);
        long taken = watch.stop();

        assertEquals(taken, watch.taken());
        assertTrue("Should take approx 200 millis, was: " + taken, taken > 150);
    }

    public void testStopWatchNotStarted() throws Exception {
        StopWatch watch = new StopWatch(false);
        long taken = watch.stop();
        assertEquals(0, taken);

        watch.restart();
        Thread.sleep(200);
        taken = watch.stop();

        assertEquals(taken, watch.taken());
        assertTrue("Should take approx 200 millis, was: " + taken, taken > 150);
    }

    public void testStopWatchRestart() throws Exception {
        StopWatch watch = new StopWatch();
        Thread.sleep(200);
        long taken = watch.stop();

        assertEquals(taken, watch.taken());
        assertTrue("Should take approx 200 millis, was: " + taken, taken > 150);

        watch.restart();
        Thread.sleep(100);
        taken = watch.stop();

        assertEquals(taken, watch.taken());
        assertTrue("Should take approx 100 millis, was: " + taken, taken > 50);
    }

    public void testStopWatchTaken() throws Exception {
        StopWatch watch = new StopWatch();
        Thread.sleep(100);
        long taken = watch.taken();
        Thread.sleep(100);
        long taken2 = watch.taken();
        assertNotSame(taken, taken2);
        assertTrue(taken2 > taken);
    }

}
