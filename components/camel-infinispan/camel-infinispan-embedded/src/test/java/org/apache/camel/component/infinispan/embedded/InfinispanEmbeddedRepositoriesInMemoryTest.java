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
package org.apache.camel.component.infinispan.embedded;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.support.service.ServiceSupport;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.testing.TestResourceTracker;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The embedded Infinispan repositories tell from the configuration of their cache whether they keep their data only in
 * memory.
 */
class InfinispanEmbeddedRepositoriesInMemoryTest {

    @TempDir
    Path dir;

    private final List<ServiceSupport> repositories = new ArrayList<>();
    private final List<EmbeddedCacheManager> cacheManagers = new ArrayList<>();

    @BeforeAll
    static void testStarted() {
        // the clustered cache managers of the Infinispan test framework need a test name
        TestResourceTracker.testStarted(InfinispanEmbeddedRepositoriesInMemoryTest.class.getName());
    }

    @AfterAll
    static void testFinished() {
        TestResourceTracker.testFinished(InfinispanEmbeddedRepositoriesInMemoryTest.class.getName());
    }

    @AfterEach
    void stop() {
        repositories.forEach(ServiceSupport::stop);
        cacheManagers.forEach(EmbeddedCacheManager::stop);
    }

    @Test
    void localCacheIsInMemory() {
        EmbeddedCacheManager cacheManager = localCacheManager();
        cacheManager.defineConfiguration("local", new ConfigurationBuilder().build());

        assertTrue(idempotent(cacheManager, "local").isInMemory());
        assertTrue(aggregation(cacheManager, "local").isInMemory());
    }

    @Test
    void fileStoreIsNotInMemory() {
        EmbeddedCacheManager cacheManager = localCacheManager();
        ConfigurationBuilder builder = new ConfigurationBuilder();
        builder.persistence().addSoftIndexFileStore()
                .dataLocation("data")
                .indexLocation("index");
        cacheManager.defineConfiguration("file", builder.build());

        assertFalse(idempotent(cacheManager, "file").isInMemory());
        assertFalse(aggregation(cacheManager, "file").isInMemory());
    }

    @Test
    void fileStoreThatPurgesOnStartupIsInMemory() {
        EmbeddedCacheManager cacheManager = localCacheManager();
        ConfigurationBuilder builder = new ConfigurationBuilder();
        builder.persistence().addSoftIndexFileStore()
                .dataLocation("data")
                .indexLocation("index")
                .purgeOnStartup(true);
        cacheManager.defineConfiguration("purged", builder.build());

        assertTrue(idempotent(cacheManager, "purged").isInMemory());
        assertTrue(aggregation(cacheManager, "purged").isInMemory());
    }

    @Test
    void replicatedCacheWithTwoNodesIsNotInMemory() {
        EmbeddedCacheManager cacheManager = clusteredCacheManagers(CacheMode.REPL_SYNC);

        assertFalse(idempotent(cacheManager, "clustered").isInMemory());
        assertFalse(aggregation(cacheManager, "clustered").isInMemory());
    }

    @Test
    void distributedCacheWithOneOwnerIsInMemory() {
        // each entry is on one node only, so the entries of this node are lost when it stops
        EmbeddedCacheManager cacheManager = clusteredCacheManagers(CacheMode.DIST_SYNC);

        assertTrue(idempotent(cacheManager, "clustered").isInMemory());
    }

    @Test
    void notStartedCannotTell() {
        assertFalse(new InfinispanEmbeddedIdempotentRepository("local").isInMemory());
        assertFalse(new InfinispanEmbeddedAggregationRepository("local").isInMemory());
    }

    private EmbeddedCacheManager localCacheManager() {
        GlobalConfigurationBuilder global = new GlobalConfigurationBuilder().nonClusteredDefault();
        // the locations of the file stores are relative to the persistent location
        global.globalState().enable().persistentLocation(dir.toString());
        EmbeddedCacheManager cacheManager = new DefaultCacheManager(global.build());
        cacheManagers.add(cacheManager);
        return cacheManager;
    }

    private EmbeddedCacheManager clusteredCacheManagers(CacheMode mode) {
        InfinispanEmbeddedClusteredTestSupport.ClusteredCacheSupport cluster
                = new InfinispanEmbeddedClusteredTestSupport.ClusteredCacheSupport(mode, 2);
        cluster.cacheName = "clustered";
        cluster.createCacheManagers();
        cacheManagers.addAll(cluster.getCacheManagers());
        return cacheManagers.get(0);
    }

    private InfinispanEmbeddedIdempotentRepository idempotent(EmbeddedCacheManager cacheManager, String cacheName) {
        InfinispanEmbeddedIdempotentRepository repository = new InfinispanEmbeddedIdempotentRepository(cacheName);
        repository.setCacheContainer(cacheManager);
        return started(repository);
    }

    private InfinispanEmbeddedAggregationRepository aggregation(EmbeddedCacheManager cacheManager, String cacheName) {
        InfinispanEmbeddedConfiguration configuration = new InfinispanEmbeddedConfiguration();
        configuration.setCacheContainer(cacheManager);
        InfinispanEmbeddedAggregationRepository repository = new InfinispanEmbeddedAggregationRepository(cacheName);
        repository.setConfiguration(configuration);
        return started(repository);
    }

    private <T extends ServiceSupport> T started(T repository) {
        repository.start();
        repositories.add(repository);
        return repository;
    }
}
