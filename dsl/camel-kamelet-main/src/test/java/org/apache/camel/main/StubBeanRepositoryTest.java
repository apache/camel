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
package org.apache.camel.main;

import org.apache.camel.main.download.StubBeanRepository;
import org.apache.camel.spi.AggregationRepository;
import org.apache.camel.spi.IdempotentRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class StubBeanRepositoryTest {

    @Test
    public void testWildcardPatternStubsBeanReferences() {
        StubBeanRepository repo = new StubBeanRepository("*");

        assertNotNull(repo.lookupByNameAndType("myAggRepo", AggregationRepository.class));
        assertNotNull(repo.lookupByNameAndType("myIdempotentRepo", IdempotentRepository.class));
    }

    @Test
    public void testComponentPatternDoesNotStubBeanReferences() {
        StubBeanRepository repo = new StubBeanRepository("component:*");

        assertNull(repo.lookupByNameAndType("myAggRepo", AggregationRepository.class));
        assertNull(repo.lookupByNameAndType("myIdempotentRepo", IdempotentRepository.class));
    }
}
