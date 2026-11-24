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
package org.apache.camel.component.jcache;

import org.apache.camel.component.jcache.processor.JCacheKeyValueRepository;
import org.apache.camel.component.jcache.processor.aggregate.JCacheAggregationRepository;
import org.apache.camel.component.jcache.processor.idempotent.JCacheIdempotentRepository;
import org.apache.camel.component.jcache.support.HazelcastTest;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JCache repositories tell from the caching provider and the configuration of their cache whether they keep their
 * data only in memory. The JCache API cannot ask a cache whether it persists its entries, so only the providers that
 * keep them in the heap count.
 */
@HazelcastTest
class JCacheRepositoriesInMemoryTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void providerThatCannotTellIsNotInMemory() {
        // the caching provider of the tests is Hazelcast, which keeps the entries in its cluster
        JCacheIdempotentRepository idempotent = new JCacheIdempotentRepository();
        idempotent.setCamelContext(context);
        idempotent.setCacheName("in-memory-idempotent");
        JCacheKeyValueRepository keyValue = new JCacheKeyValueRepository();
        keyValue.setCamelContext(context);
        keyValue.setCacheName("in-memory-key-value");
        JCacheAggregationRepository aggregation = new JCacheAggregationRepository();
        aggregation.setCamelContext(context);
        aggregation.setCacheName("in-memory-aggregation");
        try {
            idempotent.start();
            keyValue.start();
            aggregation.start();

            assertFalse(idempotent.isInMemory());
            assertFalse(keyValue.isInMemory());
            assertFalse(aggregation.isInMemory());
        } finally {
            idempotent.stop();
            keyValue.stop();
            aggregation.stop();
        }
    }

    @Test
    void notStartedCannotTell() {
        assertFalse(new JCacheIdempotentRepository().isInMemory());
        assertFalse(new JCacheKeyValueRepository().isInMemory());
        assertFalse(new JCacheAggregationRepository().isInMemory());
    }

    @Test
    void heapProvidersAreInMemoryUnlessWriteThrough() {
        assertTrue(JCacheHelper.isInMemory("org.jsr107.ri.spi.RICachingProvider", false));
        assertTrue(JCacheHelper.isInMemory(JCacheProviders.caffeine.className(), false));
        // a cache writer keeps the entries elsewhere too
        assertFalse(JCacheHelper.isInMemory(JCacheProviders.caffeine.className(), true));
        // these providers can keep the entries on disk or in a cluster
        assertFalse(JCacheHelper.isInMemory(JCacheProviders.hazelcast.className(), false));
        assertFalse(JCacheHelper.isInMemory(JCacheProviders.ehcache.className(), false));
        assertFalse(JCacheHelper.isInMemory(JCacheProviders.ispnEmbedded.className(), false));
    }
}
