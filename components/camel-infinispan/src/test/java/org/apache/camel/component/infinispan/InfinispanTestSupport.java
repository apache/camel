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
package org.apache.camel.component.infinispan;

import org.apache.camel.BindToRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.commons.api.BasicCacheContainer;
import org.infinispan.commons.time.TimeService;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.test.TestingUtil;
import org.infinispan.util.ControlledTimeService;
import org.junit.Before;

public class InfinispanTestSupport extends CamelTestSupport {
    protected static final String KEY_ONE = "keyOne";
    protected static final String VALUE_ONE = "valueOne";
    protected static final String KEY_TWO = "keyTwo";
    protected static final String VALUE_TWO = "valueTwo";

    @BindToRegistry("cacheContainer")
    protected BasicCacheContainer basicCacheContainer;
    protected ControlledTimeService ts;

    @Override
    @Before
    public void setUp() throws Exception {
        basicCacheContainer = new DefaultCacheManager(new GlobalConfigurationBuilder().defaultCacheName("default").build(), new ConfigurationBuilder().build());
        basicCacheContainer.start();
        super.setUp();
    }

    @Override
    public void tearDown() throws Exception {
        basicCacheContainer.stop();
        super.tearDown();
    }

    protected BasicCache<Object, Object> currentCache() {
        return basicCacheContainer.getCache();
    }

    protected BasicCache<Object, Object> namedCache(String name) {
        return basicCacheContainer.getCache(name);
    }

    protected void injectTimeService() {
        ts = new ControlledTimeService();
        TestingUtil.replaceComponent((DefaultCacheManager) basicCacheContainer, TimeService.class, ts, true);
    }
}
