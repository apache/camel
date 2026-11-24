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

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;

import javax.cache.Cache;
import javax.cache.configuration.CompleteConfiguration;

import org.apache.camel.CamelContext;
import org.apache.camel.support.CamelContextHelper;

public final class JCacheHelper {

    /**
     * The caching providers that keep the entries of a cache in the heap of this JVM: the reference implementation and
     * Caffeine. The others can keep them elsewhere, such as Hazelcast in its cluster, or Ehcache and Infinispan on
     * disk, and the JCache API cannot ask a cache for that.
     */
    private static final Set<String> IN_MEMORY_PROVIDERS
            = Set.of("org.jsr107.ri.spi.RICachingProvider", JCacheProviders.caffeine.className());

    private JCacheHelper() {
    }

    /**
     * Whether the cache keeps its entries only in the memory of this JVM, so they are lost when the application stops:
     * when its caching provider is one that keeps them in the heap, and the cache does not write them through to a
     * cache writer. False when the cache is not known yet, as the repository is not started.
     */
    public static boolean isInMemory(Cache<?, ?> cache) {
        if (cache == null || cache.isClosed()) {
            return false;
        }
        String provider = cache.getCacheManager().getCachingProvider().getClass().getName();
        // a provider that may keep the entries elsewhere is not asked for the configuration of the cache
        return IN_MEMORY_PROVIDERS.contains(provider)
                && isInMemory(provider, cache.getConfiguration(CompleteConfiguration.class).isWriteThrough());
    }

    static boolean isInMemory(String cachingProvider, boolean writeThrough) {
        // a write-through cache keeps its entries in the store of its cache writer too
        return IN_MEMORY_PROVIDERS.contains(cachingProvider) && !writeThrough;
    }

    public static <K, V> JCacheManager<K, V> createManager(CamelContext camelContext, JCacheConfiguration configuration) {
        JCacheManagerFactory factory = CamelContextHelper.findSingleByType(camelContext, JCacheManagerFactory.class);
        if (factory == null) {
            factory = new DefaultJCacheManagerFactory();
        }
        return factory.createManager(configuration);
    }

    @SuppressWarnings("unchecked")
    public static <T> T tcclProxy(final T instance, Class<T> type, final ClassLoader classLoader) {
        return (T) Proxy.newProxyInstance(
                JCacheHelper.class.getClassLoader(),
                new Class<?>[] {
                        type
                },
                (Object proxy, Method method, Object[] args) -> {
                    final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
                    try {
                        Thread.currentThread().setContextClassLoader(classLoader);
                        return method.invoke(instance, args);
                    } finally {
                        Thread.currentThread().setContextClassLoader(tccl);
                    }
                });
    }

}
