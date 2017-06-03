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
package org.apache.camel.component.ehcache.springboot.customizer;

import org.apache.camel.component.ehcache.EhcacheComponent;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class CacheManagerCustomizerNotEnabledTestBase {
    @Autowired
    CacheManager cacheManager;
    @Autowired
    EhcacheComponent component;

    @Test
    public void testComponentConfiguration() throws Exception {
        Assert.assertNotNull(cacheManager);
        Assert.assertNotNull(component);
        Assert.assertNull(component.getCacheManager());
    }

    @Configuration
    public static class TestConfiguration {
        @Bean(initMethod = "init", destroyMethod = "close")
        public CacheManager cacheManager() {
            return CacheManagerBuilder.newCacheManagerBuilder().build();
        }
    }
}