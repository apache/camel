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

import org.apache.camel.BindToRegistry;
import org.apache.camel.component.infinispan.InfinispanTestSupport;
import org.apache.camel.spi.ComponentCustomizer;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.commons.api.CacheContainerAdmin;
import org.infinispan.commons.time.TimeService;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.test.TestingUtil;
import org.infinispan.util.ControlledTimeService;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.MethodName.class)
public class InfinispanEmbeddedTestSupport extends InfinispanTestSupport {
    protected EmbeddedCacheManager cacheContainer;
    protected ControlledTimeService ts;

    @Override
    protected void setupResources() throws Exception {
        cacheContainer = new DefaultCacheManager();
        cacheContainer.administration()
                .withFlags(CacheContainerAdmin.AdminFlag.VOLATILE)
                .getOrCreateCache(
                        InfinispanTestSupport.TEST_CACHE,
                        getConfiguration().build());

        super.setupResources();
    }

    @Override
    protected void cleanupResources() throws Exception {
        if (cacheContainer != null) {
            cacheContainer.stop();
        }

        super.cleanupResources();
    }

    @Override
    protected BasicCache<Object, Object> getCache(String name) {
        return cacheContainer.getCache(name);
    }

    protected ConfigurationBuilder getConfiguration() {
        ConfigurationBuilder builder = new ConfigurationBuilder();
        builder.clustering().cacheMode(CacheMode.LOCAL);

        return builder;
    }

    protected void injectTimeService() {
        ts = new ControlledTimeService();
        TestingUtil.replaceComponent(cacheContainer, TimeService.class, ts, true);
    }

    @BindToRegistry
    public ComponentCustomizer infinispanComponentCustomizer() {
        return ComponentCustomizer.forType(
                InfinispanEmbeddedComponent.class,
                component -> component.getConfiguration().setCacheContainer(cacheContainer));
    }
}
