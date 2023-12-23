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
package org.apache.camel.component.dynamicrouter.routing;

import java.util.concurrent.ExecutorService;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.component.dynamicrouter.PrioritizedFilter;
import org.apache.camel.component.dynamicrouter.PrioritizedFilter.PrioritizedFilterFactory;
import org.apache.camel.spi.ProducerCache;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DynamicRouterProcessorTest {

    static final String PROCESSOR_ID = "testProcessorId";

    static final String TEST_ID = "testId";

    @RegisterExtension
    static CamelContextExtension contextExtension = new DefaultCamelContextExtension();

    @Mock
    AsyncCallback asyncCallback;

    CamelContext context;

    DynamicRouterProcessor processor;

    @Mock
    PrioritizedFilter prioritizedFilter;

    @Mock
    ProducerCache producerCache;

    @Mock
    ExecutorService executorService;

    @Mock
    Predicate predicate;

    @Mock
    Exchange exchange;

    PrioritizedFilterFactory prioritizedFilterFactory;

    //    @BeforeEach
    //    void localSetup() throws Exception {
    //        context = contextExtension.getContext();
    //        prioritizedFilterFactory = new PrioritizedFilterFactory() {
    //            @Override
    //            public PrioritizedFilter getInstance(String id, int priority, Predicate predicate, String endpoint) {
    //                return prioritizedFilter;
    //            }
    //        };
    //        processor = new DynamicRouterProcessor(filters, recipientList, recipientMode, warnDroppedMessage, channel);
    //    }
    //
    //    @Test
    //    void matchFiltersMatches() {
    //        addFilterAsFilterProcessor();
    //        Mockito.when(prioritizedFilter.predicate()).thenReturn(predicate);
    //        Mockito.when(predicate.matches(any(Exchange.class))).thenReturn(true);
    //        PrioritizedFilter result = processor.matchFilters(exchange).get(0);
    //        assertEquals(TEST_ID, result.id());
    //    }
    //
    //    @Test
    //    void matchFiltersDoesNotMatch() {
    //        addFilterAsFilterProcessor();
    //        Mockito.when(prioritizedFilter.predicate()).thenReturn(predicate);
    //        Mockito.when(predicate.matches(any(Exchange.class))).thenReturn(false);
    //        assertTrue(processor.matchFilters(exchange).isEmpty());
    //    }
    //
    //    @Test
    //    void processMatching() {
    //        addFilterAsFilterProcessor();
    //        Mockito.when(prioritizedFilter.predicate()).thenReturn(predicate);
    //        Mockito.when(predicate.matches(any(Exchange.class))).thenReturn(true);
    //        assertTrue(processor.process(exchange, asyncCallback));
    //    }
    //
    //    @Test
    //    void processNotMatching() {
    //        addFilterAsFilterProcessor();
    //        Mockito.when(prioritizedFilter.predicate()).thenReturn(predicate);
    //        Mockito.when(predicate.matches(any(Exchange.class))).thenReturn(false);
    //        assertTrue(processor.process(exchange, asyncCallback));
    //    }
    //
    //    @Test
    //    void testStringIsId() {
    //        assertEquals(PROCESSOR_ID, processor.toString());
    //    }
}
