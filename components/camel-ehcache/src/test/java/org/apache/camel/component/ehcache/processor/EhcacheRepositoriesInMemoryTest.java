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
package org.apache.camel.component.ehcache.processor;

import java.nio.file.Path;

import org.apache.camel.component.ehcache.processor.aggregate.EhcacheAggregationRepository;
import org.apache.camel.component.ehcache.processor.idempotent.EhcacheIdempotentRepository;
import org.apache.camel.support.DefaultExchangeHolder;
import org.apache.camel.support.KeyValueTtlValue;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.MemoryUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Ehcache repositories tell from the resource pools of their cache whether they keep their data only in memory.
 */
class EhcacheRepositoriesInMemoryTest {

    @TempDir
    Path dir;

    private CacheManager cacheManager;
    private EhcacheIdempotentRepository idempotent;
    private EhcacheKeyValueRepository keyValue;
    private EhcacheAggregationRepository aggregation;

    @AfterEach
    void stop() {
        if (idempotent != null) {
            idempotent.stop();
        }
        if (keyValue != null) {
            keyValue.stop();
        }
        if (aggregation != null) {
            aggregation.stop();
        }
        if (cacheManager != null) {
            cacheManager.close();
        }
    }

    @Test
    void heapOnlyIsInMemory() {
        startRepositories(ResourcePoolsBuilder.heap(100), false);

        assertTrue(idempotent.isInMemory());
        assertTrue(keyValue.isInMemory());
        assertTrue(aggregation.isInMemory());
    }

    @Test
    void heapAndOffHeapIsInMemory() {
        startRepositories(ResourcePoolsBuilder.heap(100).offheap(1, MemoryUnit.MB), false);

        assertTrue(idempotent.isInMemory());
        assertTrue(keyValue.isInMemory());
        assertTrue(aggregation.isInMemory());
    }

    @Test
    void diskTierThatIsNotPersistentIsInMemory() {
        // the entries on such a disk tier are removed when the cache manager closes
        startRepositories(ResourcePoolsBuilder.heap(100).disk(10, MemoryUnit.MB, false), true);

        assertTrue(idempotent.isInMemory());
        assertTrue(keyValue.isInMemory());
        assertTrue(aggregation.isInMemory());
    }

    @Test
    void persistentDiskTierIsNotInMemory() {
        startRepositories(ResourcePoolsBuilder.heap(100).disk(10, MemoryUnit.MB, true), true);

        assertFalse(idempotent.isInMemory());
        assertFalse(keyValue.isInMemory());
        assertFalse(aggregation.isInMemory());
    }

    @Test
    void notStartedCannotTell() {
        cacheManager = CacheManagerBuilder.newCacheManagerBuilder().build(true);

        assertFalse(new EhcacheIdempotentRepository(cacheManager, "ids").isInMemory());
        assertFalse(new EhcacheKeyValueRepository(cacheManager, "entries").isInMemory());
        assertFalse(new EhcacheAggregationRepository().isInMemory());
    }

    private void startRepositories(ResourcePoolsBuilder pools, boolean disk) {
        CacheManagerBuilder<? extends CacheManager> builder = disk
                ? CacheManagerBuilder.newCacheManagerBuilder().with(CacheManagerBuilder.persistence(dir.toFile()))
                : CacheManagerBuilder.newCacheManagerBuilder();
        cacheManager = builder
                .withCache("ids", CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, Boolean.class, pools))
                .withCache("entries",
                        CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, KeyValueTtlValue.class, pools))
                .withCache("aggregates",
                        CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, DefaultExchangeHolder.class,
                                pools))
                .build(true);

        idempotent = new EhcacheIdempotentRepository(cacheManager, "ids");
        idempotent.start();
        keyValue = new EhcacheKeyValueRepository(cacheManager, "entries");
        keyValue.start();
        aggregation = new EhcacheAggregationRepository();
        aggregation.setCacheManager(cacheManager);
        aggregation.setCacheName("aggregates");
        aggregation.start();
    }
}
