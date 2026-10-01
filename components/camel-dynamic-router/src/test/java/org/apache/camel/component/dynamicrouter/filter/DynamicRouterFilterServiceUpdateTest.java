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

import org.apache.camel.builder.PredicateBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Updating a subscription replaces it, whatever its priority, with the filter instances the service creates.
 */
class DynamicRouterFilterServiceUpdateTest {

    static final String CHANNEL = "test";

    DynamicRouterFilterService filterService;

    @BeforeEach
    void setup() {
        filterService = new DynamicRouterFilterService();
        filterService.initializeChannelFilters(CHANNEL);
        filterService.addFilterForChannel("sub", 1, PredicateBuilder.constant(true), "mock:old", CHANNEL, false);
    }

    @Test
    void testUpdateWithSamePriorityReplacesTheSubscription() {
        String result
                = filterService.addFilterForChannel("sub", 1, PredicateBuilder.constant(true), "mock:new", CHANNEL, true);

        assertEquals("sub", result);
        assertEquals(1, filterService.getFiltersForChannel(CHANNEL).size());
        assertEquals("mock:new", filterService.getFilterById("sub", CHANNEL).endpoint());
        // the statistics of the replaced filter stay, as when a filter is removed
        assertEquals(2, filterService.getStatisticsForChannel(CHANNEL).size());
    }

    @Test
    void testUpdateWithOtherPriorityReplacesTheSubscription() {
        String result
                = filterService.addFilterForChannel("sub", 10, PredicateBuilder.constant(true), "mock:new", CHANNEL, true);

        assertEquals("sub", result);
        assertEquals(1, filterService.getFiltersForChannel(CHANNEL).size());
        PrioritizedFilter filter = filterService.getFilterById("sub", CHANNEL);
        assertEquals(10, filter.priority());
        assertEquals("mock:new", filter.endpoint());
        // the statistics of the replaced filter stay, as when a filter is removed
        assertEquals(2, filterService.getStatisticsForChannel(CHANNEL).size());
    }

    @Test
    void testSubscribeToAChannelWithoutFilters() {
        String result
                = filterService.addFilterForChannel("other", 1, PredicateBuilder.constant(true), "mock:other", "other", false);

        assertEquals("other", result);
        assertEquals(1, filterService.getFiltersForChannel("other").size());
    }
}
