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

import org.apache.camel.CamelContext;
import org.apache.camel.Predicate;
import org.apache.camel.component.dynamicrouter.filter.PrioritizedFilter.PrioritizedFilterFactory;
import org.apache.camel.component.dynamicrouter.routing.DynamicRouterEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@ExtendWith(MockitoExtension.class)
class PrioritizedFilterTest {

    @RegisterExtension
    static CamelContextExtension contextExtension = new DefaultCamelContextExtension();

    public static final String TEST_ID = "testId";

    public static final int TEST_PRIORITY = 10;

    public static final String TEST_PREDICATE = "testPredicate";

    public static final String TEST_URI = "testUri";

    @Mock
    Predicate predicate;

    @Mock
    DynamicRouterEndpoint endpoint;

    @Mock
    PrioritizedFilter prioritizedFilter;

    @Mock
    PrioritizedFilterStatistics prioritizedFilterStatistics;

    CamelContext context;

    @BeforeEach
    void localSetup() {
        context = contextExtension.getContext();
    }

    @Test
    void testCompareToAndEqual() {
        Mockito.when(endpoint.getEndpointUri()).thenReturn(TEST_URI);
        Mockito.when(prioritizedFilter.id()).thenReturn(TEST_ID);
        Mockito.when(prioritizedFilter.priority()).thenReturn(TEST_PRIORITY);
        PrioritizedFilter testProcessor
                = new PrioritizedFilter(
                        TEST_ID, TEST_PRIORITY, predicate, endpoint.getEndpointUri(), prioritizedFilterStatistics);
        assertEquals(0, testProcessor.compareTo(prioritizedFilter));
    }

    @Test
    void testCompareToAndNotEqualById() {
        PrioritizedFilter testProcessor
                = new PrioritizedFilter(
                        "differentId", TEST_PRIORITY, predicate, endpoint.getEndpointUri(), prioritizedFilterStatistics);
        assertNotEquals(0, testProcessor.compareTo(prioritizedFilter));
    }

    @Test
    void testCompareToAndNotEqualByPriority() {
        PrioritizedFilter testProcessor
                = new PrioritizedFilter(TEST_ID, 1, predicate, endpoint.getEndpointUri(), prioritizedFilterStatistics);
        assertNotEquals(0, testProcessor.compareTo(prioritizedFilter));
    }

    @Test
    void testToString() {
        Mockito.when(predicate.toString()).thenReturn(TEST_PREDICATE);
        Mockito.when(endpoint.getEndpointUri()).thenReturn(TEST_URI);
        PrioritizedFilter testProcessor
                = new PrioritizedFilter(
                        TEST_ID, TEST_PRIORITY, predicate, endpoint.getEndpointUri(), prioritizedFilterStatistics);
        String expected = String.format("PrioritizedFilterProcessor [id: %s, priority: %s, predicate: %s, endpoint: %s]",
                TEST_ID, TEST_PRIORITY, TEST_PREDICATE, TEST_URI);
        String result = testProcessor.toString();
        assertEquals(expected, result);
    }

    @Test
    void testGetInstance() {
        PrioritizedFilter instance = new PrioritizedFilterFactory()
                .getInstance(TEST_ID, TEST_PRIORITY, predicate, endpoint.getEndpointUri(), prioritizedFilterStatistics);
        Assertions.assertNotNull(instance);
    }
}
