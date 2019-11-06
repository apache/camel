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
package org.apache.camel.component.infinispan.processor.idempotent;

import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.junit.Test;

import static org.jgroups.util.Util.assertFalse;
import static org.jgroups.util.Util.assertTrue;

public class InfinispanDefaultIdempotentRepositoryTest {

    @Test
    public void createsRepositoryUsingInternalCache() throws Exception {
        GlobalConfiguration global = new GlobalConfigurationBuilder().defaultCacheName("default").build();
        DefaultCacheManager basicCacheContainer = new DefaultCacheManager(global, new ConfigurationBuilder().build());
        InfinispanIdempotentRepository repository = InfinispanIdempotentRepository.infinispanIdempotentRepository(basicCacheContainer, "default");

        assertFalse(repository.contains("One"));
        assertFalse(repository.remove("One"));

        assertTrue(repository.add("One"));

        assertTrue(repository.contains("One"));
        assertTrue(repository.remove("One"));

        assertFalse(repository.contains("One"));
        assertFalse(repository.remove("One"));
        
        assertTrue(repository.add("One"));
        assertTrue(repository.add("Two"));
        assertTrue(repository.add("Three"));
        assertTrue(repository.add("Four"));
        
        assertTrue(repository.contains("One"));
        assertTrue(repository.contains("Two"));
        assertTrue(repository.contains("Three"));
        assertTrue(repository.contains("Four"));
        
        repository.clear();
        
        assertFalse(repository.contains("One"));
        assertFalse(repository.contains("Two"));
        assertFalse(repository.contains("Three"));
        assertFalse(repository.contains("Four"));        
    }
}
