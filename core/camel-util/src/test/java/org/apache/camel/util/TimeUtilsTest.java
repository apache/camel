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

import java.time.Duration;
import java.util.Date;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TimeUtilsTest {

    @Test
    public void testPrintDuration() throws Exception {
        assertEquals("0s", TimeUtils.printDuration(123));
        assertEquals("123ms", TimeUtils.printDuration(123, true));
        assertEquals("1s", TimeUtils.printDuration(1250));
        assertEquals("1s250ms", TimeUtils.printDuration(1250, true));
        assertEquals("33s", TimeUtils.printDuration(33000));
        assertEquals("33s", TimeUtils.printDuration(33001));
        assertEquals("33s1ms", TimeUtils.printDuration(33001, true));
        assertEquals("33s", TimeUtils.printDuration(33444));
        assertEquals("33s444ms", TimeUtils.printDuration(33444, true));
        assertEquals("1m0s", TimeUtils.printDuration(60000));
        assertEquals("1m1s", TimeUtils.printDuration(61000));
        assertEquals("1m1s", TimeUtils.printDuration(61002));
        assertEquals("1m1s2ms", TimeUtils.printDuration(61002, true));
        assertEquals("30m55s", TimeUtils.printDuration(1855123));
        assertEquals("30m55s123ms", TimeUtils.printDuration(1855123, true));
        assertEquals("1h30m", TimeUtils.printDuration(5400000));
        assertEquals("1h30m1s", TimeUtils.printDuration(5401000, true));
        assertEquals("2d23h", TimeUtils.printDuration(259032000));
        assertEquals("2d23h57m12s", TimeUtils.printDuration(259032000, true));
    }

    @Test
    public void testPrintAge() throws Exception {
        assertEquals("0s", TimeUtils.printAge(123));
        assertEquals("1s", TimeUtils.printAge(1250));
        assertEquals("33s", TimeUtils.printAge(33000));
        assertEquals("33s", TimeUtils.printAge(33001));
        assertEquals("33s", TimeUtils.printAge(33444));
        assertEquals("1m0s", TimeUtils.printAge(60000));
        assertEquals("1m1s", TimeUtils.printAge(61000));
        assertEquals("1m1s", TimeUtils.printAge(61002));
        assertEquals("30m55s", TimeUtils.printAge(1855123));
        assertEquals("1h30m", TimeUtils.printAge(5400000));
        assertEquals("1h30m", TimeUtils.printAge(5401000));
        assertEquals("2d23h", TimeUtils.printAge(259032000));
    }

    @Test
    public void testReverse() throws Exception {
        long time = 259032000;
        long time2 = TimeUtils.toMilliSeconds(TimeUtils.printDuration(time, true));
        assertEquals(time, time2);
    }

    @Test
    void testDurationMatchesExpectWithDate() throws InterruptedException {
        Date startTime = new Date();

        Thread.sleep(Duration.ofSeconds(1).toMillis());

        long taken = TimeUtils.elapsedMillisSince(startTime.getTime());
        assertTrue(taken >= 1000, "Elapsed time should be equal to or greater than 1000 ms but was " + taken);
        assertTrue(taken < 1500, "Elapsed time should be smaller than 1500 ms but was " + taken);
    }
}
