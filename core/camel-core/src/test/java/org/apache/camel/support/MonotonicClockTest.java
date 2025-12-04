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

package org.apache.camel.support;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MonotonicClockTest {

    @Test
    public void testElapsed() throws Exception {
        MonotonicClock clock = new MonotonicClock();
        long e = clock.elapsed();
        long c = clock.getCreated();
        Assertions.assertNotEquals(e, c);

        // elapse a tiny bit of time
        Thread.sleep(2);

        long e2 = clock.elapsed();
        long c2 = clock.getCreated();
        Assertions.assertNotEquals(e2, c2);
        Assertions.assertNotEquals(e, e2);
        Assertions.assertTrue(e2 > e);
        Assertions.assertEquals(c, c2);
    }
}
