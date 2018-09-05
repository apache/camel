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
package org.apache.camel.component.ehcache.springboot;

import org.apache.camel.component.infinispan.InfinispanComponent;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class CacheManagerCustomizerNotEnabledTestBase {
    @Autowired
    EmbeddedCacheManager embeddedCacheManager;
    @Autowired
    RemoteCacheManager remoteCacheManager;
    @Autowired
    InfinispanComponent component;
    @Autowired
    ApplicationContext context;

    @Test
    public void testComponentConfiguration() throws Exception {
        Assert.assertNotNull(embeddedCacheManager);
        Assert.assertNotNull(remoteCacheManager);
        Assert.assertNotNull(component);
        Assert.assertNull(component.getCacheContainer());
    }

    @Configuration
    public static class TestConfiguration {
        @Bean
        public EmbeddedCacheManager embeddedCacheManagerInstance() {
            return CacheManagerCustomizerTestSupport.newEmbeddedCacheManagerInstance();
        }
        @Bean
        public RemoteCacheManager remoteCacheManagerInstance() {
            return CacheManagerCustomizerTestSupport.newRemoteCacheManagerInstance();
        }
    }
}