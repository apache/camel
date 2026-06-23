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
package org.apache.camel.processor.aggregate.cassandra;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CassandraAggregationRepositoryDefaultFilterTest {

    @Test
    public void testDefaultFilterContainsGraphShapeLimits() {
        String filter = CassandraAggregationRepository.DEFAULT_DESERIALIZATION_FILTER;
        assertTrue(filter.contains("maxdepth="), "Expected maxdepth in filter: " + filter);
        assertTrue(filter.contains("maxrefs="), "Expected maxrefs in filter: " + filter);
        assertTrue(filter.contains("maxbytes="), "Expected maxbytes in filter: " + filter);
    }

    @Test
    public void testNewInstanceUsesDefaultFilter() {
        CassandraAggregationRepository repo = new CassandraAggregationRepository();
        assertEquals(CassandraAggregationRepository.DEFAULT_DESERIALIZATION_FILTER, repo.getDeserializationFilter());
    }
}
