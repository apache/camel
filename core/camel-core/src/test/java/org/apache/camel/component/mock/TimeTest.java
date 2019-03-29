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
package org.apache.camel.component.mock;

import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

public class TimeTest extends Assert {

    @Test
    public void testTimeSeconds() {
        Time time = new Time(5, TimeUnit.SECONDS);
        assertNotNull(time);

        assertEquals(5, time.getNumber());
        assertEquals(TimeUnit.SECONDS, time.getTimeUnit());

        assertTrue(time.toMillis() > 0);
        assertNotNull(time.toString());
    }

    @Test
    public void testTimeMinutes() {
        Time time = new Time(3, TimeUnit.MINUTES);
        assertNotNull(time);

        assertTrue(time.toMillis() > 0);
        assertNotNull(time.toString());
    }

    @Test
    public void testTimeHours() {
        Time time = new Time(4, TimeUnit.HOURS);
        assertNotNull(time);

        assertTrue(time.toMillis() > 0);
        assertNotNull(time.toString());
    }

    @Test
    public void testTimeDays() {
        Time time = new Time(2, TimeUnit.DAYS);
        assertNotNull(time);

        assertTrue(time.toMillis() > 0);
        assertNotNull(time.toString());
    }

}
