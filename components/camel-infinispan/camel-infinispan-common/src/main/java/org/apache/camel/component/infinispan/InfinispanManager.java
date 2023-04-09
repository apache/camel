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

import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Service;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.commons.api.BasicCacheContainer;

public interface InfinispanManager<C extends BasicCacheContainer> extends BasicCacheContainer, CamelContextAware, Service {
    <K, V> BasicCache<K, V> getCache(String cacheName);

    default <K, V, CacheType extends BasicCache<K, V>> CacheType getCache(String cacheName, Class<CacheType> type) {
        return type.cast(getCache(cacheName));
    }

    default <K, V> BasicCache<K, V> getCache(Message message, String defaultCache) {
        final String cacheName = message.getHeader(InfinispanConstants.CACHE_NAME, defaultCache, String.class);

        return getCache(cacheName);
    }

    default <K, V, CacheType extends BasicCache<K, V>> CacheType getCache(
            Message message, String defaultCache, Class<CacheType> type) {
        return type.cast(getCache(message, defaultCache));
    }

    default <K, V> BasicCache<K, V> getCache(Exchange exchange, String defaultCache) {
        return getCache(exchange.getMessage(), defaultCache);
    }

    default <K, V, CacheType extends BasicCache<K, V>> CacheType getCache(
            Exchange exchange, String defaultCache, Class<CacheType> type) {
        return type.cast(getCache(exchange, defaultCache));
    }

    C getCacheContainer();
}
