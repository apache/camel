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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TimeUtilsTest {

    @Test
    public void testPrintDuration() throws Exception {
        assertEquals("123ms", TimeUtils.printDuration(123));
        assertEquals("1s250ms", TimeUtils.printDuration(1250));
        assertEquals("33s", TimeUtils.printDuration(33000));
        assertEquals("33s1ms", TimeUtils.printDuration(33001));
        assertEquals("33s444ms", TimeUtils.printDuration(33444));
        assertEquals("1m0s", TimeUtils.printDuration(60000));
        assertEquals("1m1s", TimeUtils.printDuration(61000));
        assertEquals("1m1s", TimeUtils.printDuration(61002));
        assertEquals("1m1s2ms", TimeUtils.printDuration(61002, true));
        assertEquals("30m55s", TimeUtils.printDuration(1855123));
        assertEquals("30m55s123ms", TimeUtils.printDuration(1855123, true));
        assertEquals("1h30m0s", TimeUtils.printDuration(5400000));
        assertEquals("1h30m1s", TimeUtils.printDuration(5401000));
        assertEquals("2d23h57m12s", TimeUtils.printDuration(259032000));
    }

    @Test
    public void testReverse() throws Exception {
        long time = 259032000;
        long time2 = TimeUtils.toMilliSeconds(TimeUtils.printDuration(time));
        assertEquals(time, time2);
    }
}
