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
package org.apache.camel.component.jcache;

import javax.cache.Cache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JCacheManagerTest extends JCacheComponentTestSupport {

    @Test
    public void testCacheCreation() throws Exception {
        JCacheConfiguration conf = new JCacheConfiguration();
        conf.setCacheName(randomString());

        JCacheManager<Object, Object> manager = new JCacheManager<>(conf);
        assertNotNull(manager.getCache());

        manager.close();
    }

    @Test
    public void testCacheCreationFailure() {
        JCacheConfiguration conf = new JCacheConfiguration();
        conf.setCacheName(randomString());
        conf.setCreateCacheIfNotExists(false);

        final JCacheManager<Object, Object> objectObjectJCacheManager = new JCacheManager<>(conf);

        assertThrows(IllegalStateException.class,
                () -> {
                    objectObjectJCacheManager.getCache();
                });
    }

    @Test
    void aManagerThatCouldNotGetItsCacheDoesNotCloseTheCacheManagerOfTheOthers() throws Exception {
        JCacheConfiguration conf = new JCacheConfiguration();
        conf.setCacheName(randomString());
        JCacheManager<Object, Object> user = new JCacheManager<>(conf);
        Cache<Object, Object> cache = user.getCache();

        JCacheConfiguration missing = new JCacheConfiguration();
        missing.setCacheName(randomString());
        missing.setCreateCacheIfNotExists(false);
        JCacheManager<Object, Object> failing = new JCacheManager<>(missing);
        assertThrows(IllegalStateException.class, failing::getCache);
        // such as when the endpoint stops after its route failed to start
        failing.close();

        assertFalse(cache.isClosed());
        cache.put("k", "v");
        assertEquals("v", cache.get("k"));

        user.close();
        assertTrue(cache.isClosed());
    }

    @Test
    void aClosedManagerDoesNotOpenItsCacheAgain() throws Exception {
        JCacheConfiguration conf = new JCacheConfiguration();
        conf.setCacheName(randomString());
        JCacheManager<Object, Object> manager = new JCacheManager<>(conf);
        Cache<Object, Object> cache = manager.getCache();

        manager.close();
        assertTrue(cache.isClosed());
        // nothing would close a cache opened again by a closed manager
        assertThrows(IllegalStateException.class, manager::getCache);
    }
}
