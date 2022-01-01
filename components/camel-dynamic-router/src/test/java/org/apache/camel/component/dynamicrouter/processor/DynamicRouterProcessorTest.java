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
package org.apache.camel.component.dynamicrouter.processor;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.component.dynamicrouter.support.CamelDynamicRouterTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

class DynamicRouterProcessorTest extends CamelDynamicRouterTestSupport {

    @BeforeEach
    void localSetup() throws Exception {
        super.setup();
        processor = new DynamicRouterProcessor(PROCESSOR_ID, context, false, () -> filterProcessorFactory);
    }

    @Test
    void createFilter() {
        when(controlMessage.getPriority()).thenReturn(1);
        when(controlMessage.getPredicate()).thenReturn(e -> true);
        PrioritizedFilterProcessor result = processor.createFilter(controlMessage);
        Assertions.assertEquals(filterProcessor, result);
    }

    @Test
    void addFilterAsControlMessage() {
        processor.addFilter(controlMessage);
        Assertions.assertNotNull(processor.getFilter(MESSAGE_ID));
    }

    @Test
    void addFilterAsFilterProcessor() {
        processor.addFilter(filterProcessor);
        PrioritizedFilterProcessor result = processor.getFilter(MESSAGE_ID);
        Assertions.assertEquals(filterProcessor, result);
    }

    @Test
    void removeFilter() {
        addFilterAsFilterProcessor();
        processor.removeFilter(MESSAGE_ID);
        PrioritizedFilterProcessor result = processor.getFilter(MESSAGE_ID);
        Assertions.assertNull(result);
    }

    @Test
    void matchFiltersMatches() {
        addFilterAsFilterProcessor();
        PrioritizedFilterProcessor result = processor.matchFilters(exchange);
        Assertions.assertEquals(MESSAGE_ID, result.getId());
    }

    @Test
    void matchFiltersDoesNotMatch() {
        PrioritizedFilterProcessor result = processor.matchFilters(exchange);
        Assertions.assertEquals(Integer.MAX_VALUE, result.getPriority());
    }

    @Test
    void processMatching() {
        addFilterAsFilterProcessor();
        when(filterProcessor.matches(exchange)).thenReturn(true);
        lenient().when(filterProcessor.process(any(Exchange.class), any(AsyncCallback.class))).thenReturn(true);
        Assertions.assertTrue(processor.process(exchange, asyncCallback));
    }

    @Test
    void processNotMatching() {
        addFilterAsFilterProcessor();
        when(filterProcessor.matches(exchange)).thenReturn(false);
        Assertions.assertFalse(processor.process(exchange, asyncCallback));
    }
}
