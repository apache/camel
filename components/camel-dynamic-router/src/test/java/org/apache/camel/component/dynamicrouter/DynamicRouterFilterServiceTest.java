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

import java.util.concurrent.ConcurrentSkipListSet;

import org.apache.camel.Predicate;
import org.apache.camel.builder.PredicateBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class DynamicRouterFilterServiceTest {

    static final String DYNAMIC_ROUTER_CHANNEL = "test";

    @Mock
    PrioritizedFilter prioritizedFilter;

    PrioritizedFilter.PrioritizedFilterFactory prioritizedFilterFactory;

    @BeforeEach
    void setup() {
        prioritizedFilterFactory = new PrioritizedFilter.PrioritizedFilterFactory() {
            @Override
            public PrioritizedFilter getInstance(String id, int priority, Predicate predicate, String endpoint) {
                return prioritizedFilter;
            }
        };
    }

    @Test
    void testDefaultConstruct() {
        DynamicRouterFilterService service = new DynamicRouterFilterService();
        assertNotNull(service);
    }

    @Test
    void testConstruct() {
        DynamicRouterFilterService service = new DynamicRouterFilterService(() -> prioritizedFilterFactory);
        assertNotNull(service);
    }

    @Test
    void testInitializeChannelFilters() {
        DynamicRouterFilterService service = new DynamicRouterFilterService(() -> prioritizedFilterFactory);
        service.initializeChannelFilters(DYNAMIC_ROUTER_CHANNEL);
        ConcurrentSkipListSet<PrioritizedFilter> filters = service.getFiltersForChannel(DYNAMIC_ROUTER_CHANNEL);
        assertNotNull(filters);
        assertTrue(filters.isEmpty());
    }

    @Test
    void testCreateFilter() {
        DynamicRouterFilterService service = new DynamicRouterFilterService(() -> prioritizedFilterFactory);
        PrioritizedFilter filter = service.createFilter(DYNAMIC_ROUTER_CHANNEL, 1, PredicateBuilder.constant(true), "endpoint");
        assertEquals(prioritizedFilter, filter);
    }

    @Test
    void testAddFilter() {
        Mockito.when(prioritizedFilter.id()).thenReturn("id");
        Mockito.when(prioritizedFilter.priority()).thenReturn(1);
        DynamicRouterFilterService service = new DynamicRouterFilterService(() -> prioritizedFilterFactory);
        service.addFilterForChannel("id", 1, PredicateBuilder.constant(true), "endpoint", DYNAMIC_ROUTER_CHANNEL);
        ConcurrentSkipListSet<PrioritizedFilter> filters = service.getFiltersForChannel(DYNAMIC_ROUTER_CHANNEL);
        assertEquals(1, filters.size());
    }

    @Test
    void testAddFilterInstance() {
        Mockito.when(prioritizedFilter.id()).thenReturn("id");
        Mockito.when(prioritizedFilter.priority()).thenReturn(1);
        DynamicRouterFilterService service = new DynamicRouterFilterService(() -> prioritizedFilterFactory);
        service.addFilterForChannel(prioritizedFilter, DYNAMIC_ROUTER_CHANNEL);
        ConcurrentSkipListSet<PrioritizedFilter> filters = service.getFiltersForChannel(DYNAMIC_ROUTER_CHANNEL);
        assertEquals(1, filters.size());
    }

    @Test
    void testGetFilter() {
        Mockito.when(prioritizedFilter.id()).thenReturn("id");
        DynamicRouterFilterService service = new DynamicRouterFilterService(() -> prioritizedFilterFactory);
        service.addFilterForChannel(prioritizedFilter, DYNAMIC_ROUTER_CHANNEL);
        PrioritizedFilter filter = service.getFilterById("id", DYNAMIC_ROUTER_CHANNEL);
        assertEquals(prioritizedFilter, filter);
    }

    @Test
    void testGetFilterWithoutChannel() {
        Mockito.when(prioritizedFilter.id()).thenReturn("id");
        DynamicRouterFilterService service = new DynamicRouterFilterService(() -> prioritizedFilterFactory);
        service.addFilterForChannel(prioritizedFilter, DYNAMIC_ROUTER_CHANNEL);
        PrioritizedFilter filter = service.getFilterById("id", null);
        assertEquals(prioritizedFilter, filter);
    }

    @Test
    void testRemoveFilter() {
        Mockito.when(prioritizedFilter.id()).thenReturn("id");
        DynamicRouterFilterService service = new DynamicRouterFilterService(() -> prioritizedFilterFactory);
        service.addFilterForChannel(prioritizedFilter, DYNAMIC_ROUTER_CHANNEL);
        assertEquals(1, service.getFiltersForChannel(DYNAMIC_ROUTER_CHANNEL).size());
        service.removeFilterById("id", DYNAMIC_ROUTER_CHANNEL);
        assertTrue(service.getFiltersForChannel(DYNAMIC_ROUTER_CHANNEL).isEmpty());
    }

    @Test
    void testRemoveNonexistentFilter() {
        Mockito.when(prioritizedFilter.id()).thenReturn("id");
        DynamicRouterFilterService service = new DynamicRouterFilterService(() -> prioritizedFilterFactory);
        service.addFilterForChannel(prioritizedFilter, DYNAMIC_ROUTER_CHANNEL);
        assertEquals(1, service.getFiltersForChannel(DYNAMIC_ROUTER_CHANNEL).size());
        service.removeFilterById("camel", DYNAMIC_ROUTER_CHANNEL);
        assertEquals(1, service.getFiltersForChannel(DYNAMIC_ROUTER_CHANNEL).size());
    }

    @Test
    void testRemoveFilterWithoutChannel() {
        Mockito.when(prioritizedFilter.id()).thenReturn("id");
        DynamicRouterFilterService service = new DynamicRouterFilterService(() -> prioritizedFilterFactory);
        service.addFilterForChannel(prioritizedFilter, DYNAMIC_ROUTER_CHANNEL);
        assertEquals(1, service.getFiltersForChannel(DYNAMIC_ROUTER_CHANNEL).size());
        service.removeFilterById("id", null);
        assertTrue(service.getFiltersForChannel(DYNAMIC_ROUTER_CHANNEL).isEmpty());
    }
}
