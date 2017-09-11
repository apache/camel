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

import java.util.Map;

import org.apache.camel.component.ehcache.EhcacheComponent;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.EntryUnit;
import org.ehcache.config.units.MemoryUnit;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class CacheConfigurationCustomizerNotEnabledTestBase {
    @Autowired
    Map<String, CacheConfiguration<?, ?>> configurations;
    @Autowired
    EhcacheComponent component;

    @Test
    public void testComponentConfiguration() throws Exception {
        Assert.assertNotNull(configurations);
        Assert.assertEquals(2, configurations.size());
        Assert.assertNotNull(component);
        Assert.assertNull(component.getCachesConfigurations());
    }

    @Configuration
    public static class TestConfiguration {
        @Bean
        public CacheConfiguration<?, ?> myConfig1() {
            return CacheConfigurationBuilder.newCacheConfigurationBuilder(
                String.class,
                String.class,
                ResourcePoolsBuilder.newResourcePoolsBuilder()
                    .heap(100, EntryUnit.ENTRIES)
                    .offheap(1, MemoryUnit.MB))
                .build();
        }
        @Bean
        public CacheConfiguration<?, ?> myConfig2() {
            return CacheConfigurationBuilder.newCacheConfigurationBuilder(
                String.class,
                String.class,
                ResourcePoolsBuilder.newResourcePoolsBuilder()
                    .heap(2100, EntryUnit.ENTRIES)
                    .offheap(2, MemoryUnit.MB))
                .build();
        }
    }
}