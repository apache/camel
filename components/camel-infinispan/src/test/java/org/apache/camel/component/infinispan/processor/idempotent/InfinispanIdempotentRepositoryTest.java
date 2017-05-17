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
package org.apache.camel.component.infinispan.processor.idempotent;

import org.infinispan.commons.api.BasicCache;
import org.infinispan.commons.api.BasicCacheContainer;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.jgroups.util.Util.assertFalse;
import static org.jgroups.util.Util.assertTrue;

public class InfinispanIdempotentRepositoryTest {

    public static final GlobalConfiguration GLOBAL_CONFIGURATION = new GlobalConfigurationBuilder().build();

    protected BasicCacheContainer basicCacheContainer;
    protected InfinispanIdempotentRepository idempotentRepository;
    protected String cacheName = "default";

    @Before
    public void setUp() throws Exception {
        GlobalConfiguration global = new GlobalConfigurationBuilder().globalJmxStatistics().allowDuplicateDomains(true).build();
        Configuration conf = new ConfigurationBuilder().build();
        basicCacheContainer = new DefaultCacheManager(global, conf);
        basicCacheContainer.start();
        idempotentRepository = InfinispanIdempotentRepository.infinispanIdempotentRepository(basicCacheContainer, cacheName);
    }

    @After
    public void tearDown() throws Exception {
        basicCacheContainer.stop();
    }

    @Test
    public void addsNewKeysToCache() throws Exception {
        assertTrue(idempotentRepository.add("One"));
        assertTrue(idempotentRepository.add("Two"));

        assertTrue(getCache().containsKey("One"));
        assertTrue(getCache().containsKey("Two"));
    }

    @Test
    public void skipsAddingSecondTimeTheSameKey() throws Exception {
        assertTrue(idempotentRepository.add("One"));
        assertFalse(idempotentRepository.add("One"));
    }

    @Test
    public void containsPreviouslyAddedKey() throws Exception {
        assertFalse(idempotentRepository.contains("One"));

        idempotentRepository.add("One");

        assertTrue(idempotentRepository.contains("One"));
    }

    @Test
    public void removesAnExistingKey() throws Exception {
        idempotentRepository.add("One");

        assertTrue(idempotentRepository.remove("One"));

        assertFalse(idempotentRepository.contains("One"));
    }

    @Test
    public void doesntRemoveMissingKey() throws Exception {
        assertFalse(idempotentRepository.remove("One"));
    }
    
    @Test
    public void clearCache() throws Exception {
        assertTrue(idempotentRepository.add("One"));
        assertTrue(idempotentRepository.add("Two"));

        assertTrue(getCache().containsKey("One"));
        assertTrue(getCache().containsKey("Two"));
        
        idempotentRepository.clear();
        
        assertFalse(getCache().containsKey("One"));
        assertFalse(getCache().containsKey("Two"));
    }

    private BasicCache<Object, Object> getCache() {
        return basicCacheContainer.getCache(cacheName);
    }
}
