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

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.component.dynamicrouter.support.DynamicRouterTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.dynamicrouter.DynamicRouterConstants.MODE_ALL_MATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

class DynamicRouterProcessorTest extends DynamicRouterTestSupport {

    @BeforeEach
    void localSetup() throws Exception {
        super.setup();
        processor = new DynamicRouterProcessor(PROCESSOR_ID, context, MODE_ALL_MATCH, false, () -> filterProcessorFactory);
        processor.doInit();
    }

    @Test
    void createFilter() {
        when(controlMessage.getPriority()).thenReturn(1);
        when(controlMessage.getPredicate()).thenReturn(e -> true);
        PrioritizedFilterProcessor result = processor.createFilter(controlMessage);
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
        PrioritizedFilterProcessor result = processor.getFilter(TEST_ID);
        assertEquals(filterProcessorLowPriority, result);
    }

    @Test
    void addMultipleFiltersWithSameId() {
        processor.addFilter(filterProcessorLowPriority);
        processor.addFilter(filterProcessorLowPriority);
        processor.addFilter(filterProcessorLowPriority);
        processor.addFilter(filterProcessorLowPriority);
        List<PrioritizedFilterProcessor> matchingFilters = processor.matchFilters(exchange);
        assertEquals(1, matchingFilters.size());
    }

    @Test
    void testMultipleFilterOrderByPriorityNotIdKey() {
        when(filterProcessorLowestPriority.getId()).thenReturn("anIdThatComesLexicallyBeforeTestId");
        processor.addFilter(filterProcessorLowestPriority);
        processor.addFilter(filterProcessorLowPriority);
        List<PrioritizedFilterProcessor> matchingFilters = processor.matchFilters(exchange);
        assertEquals(1, matchingFilters.size());
        PrioritizedFilterProcessor matchingFilter = matchingFilters.get(0);
        assertEquals(TEST_ID, matchingFilter.getId());
    }

    @Test
    void removeFilter() {
        addFilterAsFilterProcessor();
        processor.removeFilter(TEST_ID);
        PrioritizedFilterProcessor result = processor.getFilter(TEST_ID);
        Assertions.assertNull(result);
    }

    @Test
    void matchFiltersMatches() {
        addFilterAsFilterProcessor();
        PrioritizedFilterProcessor result = processor.matchFilters(exchange).get(0);
        assertEquals(TEST_ID, result.getId());
    }

    @Test
    void matchFiltersDoesNotMatch() {
        PrioritizedFilterProcessor result = processor.matchFilters(exchange).get(0);
        assertEquals(Integer.MAX_VALUE - 1000, result.getPriority());
    }

    @Test
    void processMatching() {
        addFilterAsFilterProcessor();
        when(filterProcessorLowPriority.matches(exchange)).thenReturn(true);
        lenient().when(filterProcessorLowPriority.process(any(Exchange.class), any(AsyncCallback.class))).thenReturn(true);
        Assertions.assertFalse(processor.process(exchange, asyncCallback));
    }

    @Test
    void processNotMatching() {
        addFilterAsFilterProcessor();
        when(filterProcessorLowPriority.matches(exchange)).thenReturn(false);
        Assertions.assertFalse(processor.process(exchange, asyncCallback));
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
