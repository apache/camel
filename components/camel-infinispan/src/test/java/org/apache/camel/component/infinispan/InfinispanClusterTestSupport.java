/**
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
package org.apache.camel.component.infinispan;

import java.util.List;

import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.infinispan.Cache;
import org.infinispan.commons.api.BasicCacheContainer;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.test.MultipleCacheManagersTest;
import org.infinispan.test.TestingUtil;
import org.infinispan.transaction.TransactionMode;
import org.infinispan.util.ControlledTimeService;
import org.infinispan.util.TimeService;
import org.junit.Before;

public class InfinispanClusterTestSupport extends CamelTestSupport {

    protected static final String KEY_ONE = "keyOne";
    protected static final String VALUE_ONE = "valueOne";

    protected List<EmbeddedCacheManager> clusteredCacheContainers;

    protected ControlledTimeService ts0;
    protected ControlledTimeService ts1;

    protected static class ClusteredCacheSupport extends MultipleCacheManagersTest {

        protected ConfigurationBuilder builderUsed;
        protected final boolean tx;
        protected final CacheMode cacheMode;
        protected String cacheName;
        protected final int clusterSize;

        public ClusteredCacheSupport(CacheMode cacheMode, boolean tx, int clusterSize) {
            this.tx = tx;
            this.cacheMode = cacheMode;
            this.clusterSize = clusterSize;
        }

        public ClusteredCacheSupport(CacheMode cacheMode, String cacheName, boolean tx, int clusterSize) {
            this.tx = tx;
            this.cacheMode = cacheMode;
            this.cacheName = cacheName;
            this.clusterSize = clusterSize;
        }

        @Override
        public void createCacheManagers() throws Throwable {
            builderUsed = new ConfigurationBuilder();
            builderUsed.clustering().cacheMode(cacheMode);
            if (tx) {
                builderUsed.transaction().transactionMode(TransactionMode.TRANSACTIONAL);
            }
            if (cacheMode.isDistributed()) {
                builderUsed.clustering().hash().numOwners(1);
            }
            if (cacheName != null) {
                createClusteredCaches(clusterSize, cacheName, builderUsed);
            } else {
                createClusteredCaches(clusterSize, builderUsed);
            }
        }
    }

    @Override
    @Before
    public void setUp() throws Exception {
        ClusteredCacheSupport cluster = new ClusteredCacheSupport(CacheMode.DIST_SYNC, false, 2);
        try {
            cluster.createCacheManagers();
            clusteredCacheContainers = cluster.getCacheManagers();
        } catch (Throwable ex) {
            throw new Exception(ex);
        }

        super.setUp();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        // Has to be done later, maybe CamelTestSupport should
        for (BasicCacheContainer container: clusteredCacheContainers) {
            container.stop();
        }
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("cacheContainer", clusteredCacheContainers.get(0));
        return registry;
    }

    protected Cache<Object, Object> defaultCache() {
        return clusteredCacheContainers.get(0).getCache();
    }

    protected Cache<Object, Object> defaultCache(int index) {
        return clusteredCacheContainers.get(index).getCache();
    }

    protected Cache<Object, Object> namedCache(String name) {
        return clusteredCacheContainers.get(0).getCache(name);
    }

    protected Cache<Object, Object> namedCache(int index, String name) {
        return clusteredCacheContainers.get(index).getCache(name);
    }

    protected void injectTimeService() {
        ts0 = new ControlledTimeService(0);
        TestingUtil.replaceComponent(clusteredCacheContainers.get(0), TimeService.class, ts0, true);
        ts1 = new ControlledTimeService(0);
        TestingUtil.replaceComponent(clusteredCacheContainers.get(1), TimeService.class, ts1, true);
    }
}
