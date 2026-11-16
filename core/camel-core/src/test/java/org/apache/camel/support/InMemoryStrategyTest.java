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
package org.apache.camel.support;

import org.apache.camel.processor.aggregate.MemoryAggregationRepository;
import org.apache.camel.saga.InMemorySagaService;
import org.apache.camel.spi.InMemoryStrategy;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.support.processor.state.MemoryStateRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The stores that keep their data only in memory say so, and a store that delegates answers as the store it delegates
 * to (CAMEL-27006).
 */
class InMemoryStrategyTest {

    /**
     * A key-value repository that keeps its data elsewhere, as a store configured with a disk tier or a database would.
     */
    private static class PersistentKeyValueRepository extends MemoryKeyValueRepository {
        @Override
        public boolean isInMemory() {
            return false;
        }
    }

    @Test
    void theMemoryStoresAreInMemory() {
        assertThat(new MemoryKeyValueRepository().isInMemory()).isTrue();
        assertThat(new MemoryIdempotentRepository().isInMemory()).isTrue();
        assertThat(new MemoryAggregationRepository().isInMemory()).isTrue();
        assertThat(new MemoryStateRepository().isInMemory()).isTrue();
        assertThat(new InMemorySagaService().isInMemory()).isTrue();
    }

    @Test
    void aKeyValueIdempotentRepositoryAnswersAsItsStore() {
        // without a store of its own it creates a MemoryKeyValueRepository when it starts
        assertThat(new KeyValueIdempotentRepository().isInMemory()).isTrue();
        assertThat(new KeyValueIdempotentRepository(new MemoryKeyValueRepository()).isInMemory()).isTrue();
        assertThat(new KeyValueIdempotentRepository(new PersistentKeyValueRepository()).isInMemory()).isFalse();
    }

    @Test
    void aKeyValueAggregationRepositoryAnswersAsItsStore() {
        assertThat(new KeyValueAggregationRepository().isInMemory()).isTrue();
        assertThat(new KeyValueAggregationRepository(new MemoryKeyValueRepository()).isInMemory()).isTrue();
        assertThat(new KeyValueAggregationRepository(new PersistentKeyValueRepository()).isInMemory()).isFalse();
    }

    @Test
    void aNamespacedRepositoryAnswersAsItsDelegate() {
        assertThat(new NamespacedKeyValueRepository(new MemoryKeyValueRepository(), "a").isInMemory()).isTrue();
        assertThat(new NamespacedKeyValueRepository(new PersistentKeyValueRepository(), "a").isInMemory()).isFalse();
        // a store that does not tell is not reported as in memory
        assertThat(new NamespacedKeyValueRepository().isInMemory()).isFalse();
    }

    @Test
    void theAnswerGoesThroughSeveralDelegates() {
        InMemoryStrategy store = new KeyValueIdempotentRepository(
                NamespacedKeyValueRepository.namespaced(new PersistentKeyValueRepository(), "orders"));
        assertThat(store.isInMemory()).isFalse();
    }
}
