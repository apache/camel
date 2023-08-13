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

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.component.dynamicrouter.support.DynamicRouterTestSupport;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.dynamicrouter.DynamicRouterConstants.MODE_ALL_MATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DynamicRouterProcessorTest extends DynamicRouterTestSupport {

    @BeforeEach
    void localSetup() throws Exception {
        super.setup();
        processor = new DynamicRouterMulticastProcessor(
                "testProcessorId", context, null, MODE_ALL_MATCH, false,
                () -> filterProcessorFactory, producerCache,
                new UseLatestAggregationStrategy(), false, executorService, false,
                false, false, -1, exchange -> {
                }, false, false);
        processor.doInit();
    }

    @Test
    void createFilter() {
        when(controlMessage.getPriority()).thenReturn(1);
        when(controlMessage.getPredicate()).thenReturn(e -> true);
        PrioritizedFilter result = processor.createFilter(controlMessage);
        assertEquals(filterProcessorLowPriority, result);
    }

    @Test
    void addFilterAsControlMessage() {
        processor.addFilter(controlMessage);
        Assertions.assertNotNull(processor.getFilter(TEST_ID));
    }

    @Test
    void addFilterAsFilterProcessor() {
        processor.addFilter(filterProcessorLowPriority);
        PrioritizedFilter result = processor.getFilter(TEST_ID);
        assertEquals(filterProcessorLowPriority, result);
    }

    @Test
    void addMultipleFiltersWithSameId() {
        processor.addFilter(filterProcessorLowPriority);
        processor.addFilter(filterProcessorLowPriority);
        processor.addFilter(filterProcessorLowPriority);
        processor.addFilter(filterProcessorLowPriority);
        when(predicate.matches(any(Exchange.class))).thenReturn(true);
        List<PrioritizedFilter> matchingFilters = processor.matchFilters(exchange);
        assertEquals(1, matchingFilters.size());
    }

    @Test
    void testMultipleFilterOrderByPriorityNotIdKey() {
        when(predicate.matches(any(Exchange.class))).thenReturn(true);
        when(filterProcessorLowestPriority.getId()).thenReturn("anIdThatComesLexicallyBeforeTestId");
        when(filterProcessorLowestPriority.getPredicate()).thenReturn(predicate);
        processor.addFilter(filterProcessorLowestPriority);
        addFilterAsFilterProcessor();
        List<PrioritizedFilter> matchingFilters = processor.matchFilters(exchange);
        assertEquals(2, matchingFilters.size());
        PrioritizedFilter matchingFilter = matchingFilters.get(0);
        assertEquals(TEST_ID, matchingFilter.getId());
    }

    @Test
    void removeFilter() {
        addFilterAsFilterProcessor();
        processor.removeFilter(TEST_ID);
        PrioritizedFilter result = processor.getFilter(TEST_ID);
        Assertions.assertNull(result);
    }

    @Test
    void matchFiltersMatches() {
        addFilterAsFilterProcessor();
        when(predicate.matches(any(Exchange.class))).thenReturn(true);
        PrioritizedFilter result = processor.matchFilters(exchange).get(0);
        assertEquals(TEST_ID, result.getId());
    }

    @Test
    void matchFiltersDoesNotMatch() {
        addFilterAsFilterProcessor();
        when(predicate.matches(any(Exchange.class))).thenReturn(false);
        assertTrue(processor.matchFilters(exchange).isEmpty());
    }

    @Test
    void processMatching() {
        addFilterAsFilterProcessor();
        when(predicate.matches(any(Exchange.class))).thenReturn(true);
        assertTrue(processor.process(exchange, asyncCallback));
    }

    @Test
    void processNotMatching() {
        addFilterAsFilterProcessor();
        when(predicate.matches(any(Exchange.class))).thenReturn(false);
        assertTrue(processor.process(exchange, asyncCallback));
    }

    @Test
    void testStringIsId() {
        assertEquals(PROCESSOR_ID, processor.toString());
    }

    @Test
    void testTraceLabelIsId() {
        assertEquals(PROCESSOR_ID, processor.getTraceLabel());
    }
}
