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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
        assertDoesNotThrow(() -> new DynamicRouterFilterService());
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
    void testAddFilterInstanceAlreadyExists() {
        Mockito.when(prioritizedFilter.id()).thenReturn("id");
        Mockito.when(prioritizedFilter.priority()).thenReturn(1);
        filterService.addFilterForChannel(prioritizedFilter, DYNAMIC_ROUTER_CHANNEL, false);
        Collection<PrioritizedFilter> filters = filterService.getFiltersForChannel(DYNAMIC_ROUTER_CHANNEL);
        assertEquals(1, filters.size());
        String result = filterService.addFilterForChannel(prioritizedFilter, DYNAMIC_ROUTER_CHANNEL, false);
        assertEquals("Error: Filter could not be added -- existing filter found with matching ID: true", result);
    }

    @Test
    void testUpdateFilter() {
        Mockito.when(prioritizedFilter.id()).thenReturn("id");
        Mockito.when(prioritizedFilter.priority()).thenReturn(1);
        filterService.addFilterForChannel(prioritizedFilter, DYNAMIC_ROUTER_CHANNEL, false);
        Collection<PrioritizedFilter> filters = filterService.getFiltersForChannel(DYNAMIC_ROUTER_CHANNEL);
        assertEquals(1, filters.size());
        PrioritizedFilter filter = filters.stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("Could not get added filter"));
        // Verify filter priority is (originally) 1
        assertEquals(1, filter.priority());
        // Update filter (change priority from 1 to 10)
        Mockito.when(prioritizedFilter.priority()).thenReturn(10);
        filterService.addFilterForChannel(prioritizedFilter, DYNAMIC_ROUTER_CHANNEL, true);
        filters = filterService.getFiltersForChannel(DYNAMIC_ROUTER_CHANNEL);
        assertEquals(1, filters.size());
        filter = filters.stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("Could not get added filter"));
        // Verify filter priority is now 10
        assertEquals(10, filter.priority());
    }

    @Test
    void testUpdateFilterDoesNotExist() {
        String result = filterService.addFilterForChannel(prioritizedFilter, DYNAMIC_ROUTER_CHANNEL, true);
        assertEquals("Error: Filter could not be updated -- existing filter found with matching ID: false", result);
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
        assertEquals("log:org.apache.camel.component.dynamicrouter.filter.DynamicRouterFilterService.test" +
                     "?level=DEBUG" +
                     "&showAll=true" +
                     "&multiline=true",
                result);
    }

    @Test
    void testGetMatchingEndpointsForExchangeByChannelWithNoMatchingRecipientsWithWarnDroppedMessage() {
        String channel = "test";
        Mockito.when(exchange.getMessage()).thenReturn(message);
        Mockito.when(prioritizedFilter.id()).thenReturn("id");
        Mockito.when(prioritizedFilter.priority()).thenReturn(1);
        Mockito.when(prioritizedFilter.predicate()).thenReturn(predicate);
        Mockito.when(prioritizedFilter.statistics()).thenReturn(prioritizedFilterStatistics);
        Mockito.when(predicate.matches(exchange)).thenReturn(false);
        filterService.addFilterForChannel(prioritizedFilter, DYNAMIC_ROUTER_CHANNEL, false);
        String result = filterService.getMatchingEndpointsForExchangeByChannel(exchange, channel, false, true);
        assertEquals("log:org.apache.camel.component.dynamicrouter.filter.DynamicRouterFilterService.test" +
                     "?level=WARN" +
                     "&showAll=true" +
                     "&multiline=true",
                result);
    }
}
