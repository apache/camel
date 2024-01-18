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
package org.apache.camel.component.dynamicrouter.filter;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PrioritizedFilterStatisticsTest {

    @Test
    void testIncrementCount() {
        PrioritizedFilterStatistics stats = new PrioritizedFilterStatistics("test");
        long first = stats.getFirst();
        Assertions.assertEquals(0, stats.getCount());
        stats.incrementCount();
        Assertions.assertEquals(1, stats.getCount());
        long last = stats.getLast();
        Assertions.assertTrue(last > first);
    }

    @Test
    void testToStringInitial() {
        PrioritizedFilterStatistics stats = new PrioritizedFilterStatistics("test");
        String strVal = stats.toString();
        Assertions.assertEquals("PrioritizedFilterStatistics [id: test, count: 0, first: 0, last: 0]", strVal);
    }

    @Test
    void testToStringIncrement() {
        PrioritizedFilterStatistics stats = new PrioritizedFilterStatistics("test");
        stats.incrementCount();
        long first = stats.getFirst();
        long last = stats.getLast();
        String strVal = stats.toString();
        Assertions.assertEquals(
                String.format("PrioritizedFilterStatistics [id: test, count: 1, first: %d, last: %d]", first, last), strVal);
    }
}
