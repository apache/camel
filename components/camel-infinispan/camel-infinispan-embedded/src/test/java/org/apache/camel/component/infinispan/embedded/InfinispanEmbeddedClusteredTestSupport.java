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

import java.util.List;
import java.util.Objects;

import org.apache.camel.BindToRegistry;
import org.apache.camel.spi.ComponentCustomizer;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.infinispan.Cache;
import org.infinispan.commons.api.BasicCacheContainer;
import org.infinispan.commons.time.TimeService;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.test.MultipleCacheManagersTest;
import org.infinispan.test.TestDataSCI;
import org.infinispan.test.TestingUtil;
import org.infinispan.util.ControlledTimeService;

public class InfinispanEmbeddedClusteredTestSupport extends CamelTestSupport {
    protected List<EmbeddedCacheManager> clusteredCacheContainers;
    protected ControlledTimeService ts0;
    protected ControlledTimeService ts1;

    protected static class ClusteredCacheSupport extends MultipleCacheManagersTest {
        protected final CacheMode cacheMode;
        protected final int clusterSize;

        protected ConfigurationBuilder builderUsed;
        protected String cacheName;

        public ClusteredCacheSupport(CacheMode cacheMode, int clusterSize) {
            this.cacheMode = cacheMode;
            this.clusterSize = clusterSize;
        }

        @Override
        public void createCacheManagers() {
            builderUsed = new ConfigurationBuilder();
            builderUsed.clustering().cacheMode(cacheMode);
            if (cacheMode.isDistributed()) {
                builderUsed.clustering().hash().numOwners(1);
            }
            if (cacheName != null) {
                createClusteredCaches(clusterSize, cacheName, TestDataSCI.INSTANCE, builderUsed);
            } else {
                createClusteredCaches(clusterSize, TestDataSCI.INSTANCE, builderUsed);
            }
        }
    }

    @Override
    public void setupResources() throws Exception {
        ClusteredCacheSupport cluster = new ClusteredCacheSupport(CacheMode.DIST_SYNC, 2);
        cluster.createCacheManagers();
        clusteredCacheContainers = Objects.requireNonNull(cluster.getCacheManagers());

        super.setupResources();
    }

    @Override
    public void cleanupResources() throws Exception {
        super.cleanupResources();

        if (clusteredCacheContainers != null) {
            // Has to be done later, maybe CamelTestSupport should
            for (BasicCacheContainer container : clusteredCacheContainers) {
                container.stop();
            }
        }
    }

    protected Cache<Object, Object> getCache(int index) {
        return clusteredCacheContainers.get(index).getCache();
    }

    protected void injectTimeService() {
        ts0 = new ControlledTimeService();
        TestingUtil.replaceComponent(clusteredCacheContainers.get(0), TimeService.class, ts0, true);
        ts1 = new ControlledTimeService();
        TestingUtil.replaceComponent(clusteredCacheContainers.get(1), TimeService.class, ts1, true);
    }

    @BindToRegistry
    public ComponentCustomizer infinispanComponentCustomizer() {
        return ComponentCustomizer.forType(
                InfinispanEmbeddedComponent.class,
                component -> component.getConfiguration().setCacheContainer(clusteredCacheContainers.get(0)));
    }
}
