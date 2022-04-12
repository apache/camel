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
package org.apache.camel.component.jcache.processor.idempotent;

import javax.cache.Cache;

import org.apache.camel.component.jcache.JCacheConfiguration;
import org.apache.camel.component.jcache.JCacheHelper;
import org.apache.camel.component.jcache.JCacheManager;
import org.apache.camel.component.jcache.support.HazelcastTest;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@HazelcastTest
public class JCacheIdempotentRepositoryTest extends CamelTestSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(JCacheIdempotentRepositoryTest.class);

    private JCacheManager<String, Boolean> cacheManager;
    private Cache<String, Boolean> cache;
    private JCacheIdempotentRepository repository;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        cacheManager = JCacheHelper.createManager(context, new JCacheConfiguration("idempotent-repository"));
        cache = cacheManager.getCache();

        repository = new JCacheIdempotentRepository();
        repository.setCamelContext(context);
        repository.setCache(cache);
        repository.start();
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        repository.stop();
        cacheManager.close();
    }

    @Test
    public void addsNewKeysToCache() {
        assertTrue(repository.add("One"));
        assertTrue(repository.add("Two"));

        assertTrue(cache.containsKey("One"));
        assertTrue(cache.containsKey("Two"));
    }

    @Test
    public void skipsAddingSecondTimeTheSameKey() {
        assertTrue(repository.add("One"));
        assertFalse(repository.add("One"));
    }

    @Test
    public void containsPreviouslyAddedKey() {
        assertFalse(repository.contains("One"));
        repository.add("One");
        assertTrue(repository.contains("One"));
    }

    @Test
    public void removesAnExistingKey() {
        cache.clear();

        repository.add("One");

        assertTrue(repository.remove("One"));
        assertFalse(repository.contains("One"));
    }

    @Test
    public void doesNotRemoveMissingKey() {
        assertFalse(repository.remove("One"));
    }

    @Test
    public void clearCache() {
        assertTrue(repository.add("One"));
        assertTrue(repository.add("Two"));

        assertTrue(cache.containsKey("One"));
        assertTrue(cache.containsKey("Two"));

        repository.clear();

        assertFalse(cache.containsKey("One"));
        assertFalse(cache.containsKey("Two"));
    }
}
