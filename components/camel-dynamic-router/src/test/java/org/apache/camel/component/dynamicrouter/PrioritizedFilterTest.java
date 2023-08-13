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
package org.apache.camel.component.dynamicrouter;

import org.apache.camel.component.dynamicrouter.support.DynamicRouterTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PrioritizedFilterTest extends DynamicRouterTestSupport {

    @Test
    void testCompareToAndEqual() {
        PrioritizedFilter testProcessor
                = new PrioritizedFilter(TEST_ID, TEST_PRIORITY, predicate, endpoint.getEndpointUri());
        assertEquals(0, testProcessor.compareTo(prioritizedFilter));
    }

    @Test
    void testCompareToAndNotEqualById() {
        PrioritizedFilter testProcessor
                = new PrioritizedFilter("differentId", TEST_PRIORITY, predicate, endpoint.getEndpointUri());
        assertNotEquals(0, testProcessor.compareTo(prioritizedFilter));
    }

    @Test
    void testCompareToAndNotEqualByPriority() {
        PrioritizedFilter testProcessor = new PrioritizedFilter(TEST_ID, 1, predicate, endpoint.getEndpointUri());
        assertNotEquals(0, testProcessor.compareTo(prioritizedFilter));
    }

    @Test
    void testToString() {
        PrioritizedFilter testProcessor
                = new PrioritizedFilter(TEST_ID, TEST_PRIORITY, predicate, endpoint.getEndpointUri());
        String expected = String.format("PrioritizedFilterProcessor [id: %s, priority: %s, predicate: %s]",
                TEST_ID, TEST_PRIORITY, TEST_PREDICATE);
        String result = testProcessor.toString();
        assertEquals(expected, result);
    }
}
