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

import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;

public final class CacheManagerCustomizerTestSupport {
    private CacheManagerCustomizerTestSupport() {
    }

    public static EmbeddedCacheManager newEmbeddedCacheManagerInstance() {
        return new DefaultCacheManager(
            new org.infinispan.configuration.global.GlobalConfigurationBuilder()
                .build(),
            new org.infinispan.configuration.cache.ConfigurationBuilder()
                .build(),
            false
        );
    }

    public static RemoteCacheManager newRemoteCacheManagerInstance() {
        return new RemoteCacheManager(
            new org.infinispan.client.hotrod.configuration.ConfigurationBuilder()
                .build(),
            false);
    }
}
