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

import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

/**
 * @version 
 */
public class TimeTest extends TestCase {

    public void testTimeSeconds() {
        Time time = Time.seconds(5);
        assertNotNull(time);

        assertEquals(5, time.getNumber());
        assertEquals(TimeUnit.SECONDS, time.getTimeUnit());

        assertNotNull(time.toDate());
        assertNotNull(time.toMillis());
        assertNotNull(time.toString());
    }

    public void testTimeMinutes() {
        Time time = Time.minutes(3);
        assertNotNull(time);

        assertNotNull(time.toDate());
        assertNotNull(time.toMillis());
        assertNotNull(time.toString());
    }

    public void testTimeHours() {
        Time time = Time.hours(4);
        assertNotNull(time);

        assertNotNull(time.toDate());
        assertNotNull(time.toMillis());
        assertNotNull(time.toString());
    }

    public void testTimeDays() {
        Time time = Time.days(2);
        assertNotNull(time);

        assertNotNull(time.toDate());
        assertNotNull(time.toMillis());
        assertNotNull(time.toString());
    }

}
