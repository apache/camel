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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListSet;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Predicate;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.component.dynamicrouter.filter.PrioritizedFilter.PrioritizedFilterFactory;
import org.junit.jupiter.api.Assertions;
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

    @Mock
    PrioritizedFilterStatistics prioritizedFilterStatistics;

    @Mock
    Exchange exchange;

    @Mock
    Message message;

    @Mock
    Predicate predicate;

    PrioritizedFilterFactory prioritizedFilterFactory;

    DynamicRouterFilterService filterService;

    @BeforeEach
    void setup() {
        prioritizedFilterFactory = new PrioritizedFilterFactory() {
            @Override
            public PrioritizedFilter getInstance(
                    String id, int priority, Predicate predicate, String endpoint, PrioritizedFilterStatistics statistics) {
                return prioritizedFilter;
            }
        };
        this.filterService = new DynamicRouterFilterService(() -> prioritizedFilterFactory);
    }

    @Test
    void testDefaultConstruct() {
        assertNotNull(new DynamicRouterFilterService());
    }

    @Test
    void testConstruct() {
        assertNotNull(filterService);
    }

    @Test
    void testInitializeChannelFilters() {
        filterService.initializeChannelFilters(DYNAMIC_ROUTER_CHANNEL);
        Collection<PrioritizedFilter> filters = filterService.getFiltersForChannel(DYNAMIC_ROUTER_CHANNEL);
        assertNotNull(filters);
        assertTrue(filters.isEmpty());
    }

    @Test
    void testCreateFilter() {
        PrioritizedFilter filter = filterService.createFilter(
                DYNAMIC_ROUTER_CHANNEL, 1, PredicateBuilder.constant(true), "endpoint",
                prioritizedFilterStatistics);
        assertEquals(prioritizedFilter, filter);
    }

    @Test
    void testAddFilter() {
        Mockito.when(prioritizedFilter.id()).thenReturn("id");
        Mockito.when(prioritizedFilter.priority()).thenReturn(1);
        filterService.addFilterForChannel(
                "id", 1, PredicateBuilder.constant(true), "endpoint", DYNAMIC_ROUTER_CHANNEL, false);
        Collection<PrioritizedFilter> filters = filterService.getFiltersForChannel(DYNAMIC_ROUTER_CHANNEL);
        assertEquals(1, filters.size());
    }

    @Test
    void testAddFilterInstance() {
        Mockito.when(prioritizedFilter.id()).thenReturn("id");
        Mockito.when(prioritizedFilter.priority()).thenReturn(1);
        filterService.addFilterForChannel(prioritizedFilter, DYNAMIC_ROUTER_CHANNEL, false);
        Collection<PrioritizedFilter> filters = filterService.getFiltersForChannel(DYNAMIC_ROUTER_CHANNEL);
        assertEquals(1, filters.size());
    }

    @Test
    void testGetFilter() {
        Mockito.when(prioritizedFilter.id()).thenReturn("id");
        filterService.addFilterForChannel(prioritizedFilter, DYNAMIC_ROUTER_CHANNEL, false);
        PrioritizedFilter filter = filterService.getFilterById("id", DYNAMIC_ROUTER_CHANNEL);
        assertEquals(prioritizedFilter, filter);
    }

    @Test
    void testGetFilterWithoutChannel() {
        Mockito.when(prioritizedFilter.id()).thenReturn("id");
        filterService.addFilterForChannel(prioritizedFilter, DYNAMIC_ROUTER_CHANNEL, false);
        PrioritizedFilter filter = filterService.getFilterById("id", null);
        assertEquals(prioritizedFilter, filter);
    }

    @Test
    void testRemoveFilter() {
        Mockito.when(prioritizedFilter.id()).thenReturn("id");
        filterService.addFilterForChannel(prioritizedFilter, DYNAMIC_ROUTER_CHANNEL, false);
        assertEquals(1, filterService.getFiltersForChannel(DYNAMIC_ROUTER_CHANNEL).size());
        filterService.removeFilterById("id", DYNAMIC_ROUTER_CHANNEL);
        assertTrue(filterService.getFiltersForChannel(DYNAMIC_ROUTER_CHANNEL).isEmpty());
    }

    @Test
    void testRemoveNonexistentFilter() {
        Mockito.when(prioritizedFilter.id()).thenReturn("id");
        filterService.addFilterForChannel(prioritizedFilter, DYNAMIC_ROUTER_CHANNEL, false);
        assertEquals(1, filterService.getFiltersForChannel(DYNAMIC_ROUTER_CHANNEL).size());
        filterService.removeFilterById("camel", DYNAMIC_ROUTER_CHANNEL);
        assertEquals(1, filterService.getFiltersForChannel(DYNAMIC_ROUTER_CHANNEL).size());
    }

    @Test
    void testRemoveFilterWithoutChannel() {
        Mockito.when(prioritizedFilter.id()).thenReturn("id");
        filterService.addFilterForChannel(prioritizedFilter, DYNAMIC_ROUTER_CHANNEL, false);
        assertEquals(1, filterService.getFiltersForChannel(DYNAMIC_ROUTER_CHANNEL).size());
        filterService.removeFilterById("id", null);
        assertTrue(filterService.getFiltersForChannel(DYNAMIC_ROUTER_CHANNEL).isEmpty());
    }

    @Test
    void testGetChannelFilters() {
        String channel = "test";
        Mockito.when(prioritizedFilter.id()).thenReturn("id");
        Mockito.when(prioritizedFilter.priority()).thenReturn(1);
        filterService.addFilterForChannel(prioritizedFilter, DYNAMIC_ROUTER_CHANNEL, false);
        Collection<PrioritizedFilter> result = filterService.getFiltersForChannel(channel);
        assertEquals(1, result.size());
    }

    @Test
    void testGetFilters() {
        Mockito.when(prioritizedFilter.id()).thenReturn("id");
        Mockito.when(prioritizedFilter.priority()).thenReturn(1);
        filterService.addFilterForChannel(prioritizedFilter, DYNAMIC_ROUTER_CHANNEL, false);
        Map<String, ConcurrentSkipListSet<PrioritizedFilter>> result = filterService.getFilterMap();
        assertEquals(1, result.size());
    }

    @Test
    void testGetChannelStatistics() {
        String channel = "test";
        Mockito.when(prioritizedFilter.id()).thenReturn("id");
        Mockito.when(prioritizedFilter.priority()).thenReturn(1);
        Mockito.when(prioritizedFilter.statistics()).thenReturn(prioritizedFilterStatistics);
        filterService.addFilterForChannel(prioritizedFilter, DYNAMIC_ROUTER_CHANNEL, false);
        List<PrioritizedFilterStatistics> result = filterService.getStatisticsForChannel(channel);
        assertEquals(1, result.size());
    }

    @Test
    void testGetStatistics() {
        Mockito.when(prioritizedFilter.id()).thenReturn("id");
        Mockito.when(prioritizedFilter.priority()).thenReturn(1);
        Mockito.when(prioritizedFilter.statistics()).thenReturn(prioritizedFilterStatistics);
        filterService.addFilterForChannel(prioritizedFilter, DYNAMIC_ROUTER_CHANNEL, false);
        Map<String, List<PrioritizedFilterStatistics>> result = filterService.getFilterStatisticsMap();
        assertEquals(1, result.size());
    }

    @Test
    void testGetMatchingEndpointsForExchangeByChannel() {
        String channel = "test";
        Mockito.when(prioritizedFilter.id()).thenReturn("id");
        Mockito.when(prioritizedFilter.priority()).thenReturn(1);
        Mockito.when(prioritizedFilter.predicate()).thenReturn(predicate);
        Mockito.when(prioritizedFilter.statistics()).thenReturn(prioritizedFilterStatistics);
        Mockito.when(prioritizedFilter.endpoint()).thenReturn("testEndpoint");
        Mockito.doNothing().when(prioritizedFilterStatistics).incrementCount();
        Mockito.when(predicate.matches(exchange)).thenReturn(true);
        filterService.addFilterForChannel(prioritizedFilter, DYNAMIC_ROUTER_CHANNEL, false);
        String result = filterService.getMatchingEndpointsForExchangeByChannel(exchange, channel, true, false);
        Assertions.assertEquals("testEndpoint", result);
    }

    @Test
    void testGetMatchingEndpointsForExchangeByChannelWithNoMatchingRecipients() {
        String channel = "test";
        Mockito.when(exchange.getMessage()).thenReturn(message);
        Mockito.when(prioritizedFilter.id()).thenReturn("id");
        Mockito.when(prioritizedFilter.priority()).thenReturn(1);
        Mockito.when(prioritizedFilter.predicate()).thenReturn(predicate);
        Mockito.when(prioritizedFilter.statistics()).thenReturn(prioritizedFilterStatistics);
        Mockito.when(predicate.matches(exchange)).thenReturn(false);
        filterService.addFilterForChannel(prioritizedFilter, DYNAMIC_ROUTER_CHANNEL, false);
        String result = filterService.getMatchingEndpointsForExchangeByChannel(exchange, channel, false, false);
        Assertions.assertTrue(result.startsWith("log:"));
    }
}
