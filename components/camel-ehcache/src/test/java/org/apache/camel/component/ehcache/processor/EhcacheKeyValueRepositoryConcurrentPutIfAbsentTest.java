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
package org.apache.camel.component.ehcache.processor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.camel.support.KeyValueIdempotentRepository;
import org.apache.camel.support.KeyValueTtlValue;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Idempotent Consumer calls {@code add} without a lock, so {@link KeyValueIdempotentRepository} relies on an atomic
 * {@code putIfAbsent} of the key-value repository.
 */
class EhcacheKeyValueRepositoryConcurrentPutIfAbsentTest {

    private static final String CACHE_NAME = "test-kvrepo-concurrent";

    private final CyclicBarrier bothRead = new CyclicBarrier(2);

    private CacheManager cacheManager;
    private EhcacheKeyValueRepository repository;
    private ExecutorService executor;

    @BeforeEach
    void setUp() throws Exception {
        cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
                .withCache(CACHE_NAME,
                        CacheConfigurationBuilder.newCacheConfigurationBuilder(
                                String.class,
                                KeyValueTtlValue.class,
                                ResourcePoolsBuilder.heap(100)))
                .build(true);

        repository = new EhcacheKeyValueRepository(readersMeetCacheManager(cacheManager), CACHE_NAME);
        repository.start();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() throws Exception {
        executor.shutdownNow();
        repository.stop();
        cacheManager.close();
    }

    @Test
    void testConcurrentAddOfTheSameKeyAddsItOnce() throws Exception {
        KeyValueIdempotentRepository idempotentRepository = new KeyValueIdempotentRepository(repository);
        idempotentRepository.start();

        Future<Boolean> first = executor.submit(() -> idempotentRepository.add("message-1"));
        Future<Boolean> second = executor.submit(() -> idempotentRepository.add("message-1"));

        assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true, false);
    }

    /**
     * A cache manager whose caches let a read of a key return only when a second thread has read it as well (or after a
     * timeout), so two check-then-put sequences both check before either puts.
     */
    @SuppressWarnings("unchecked")
    private CacheManager readersMeetCacheManager(CacheManager delegate) {
        return (CacheManager) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { CacheManager.class },
                (proxy, method, args) -> {
                    Object answer = invoke(delegate, method, args);
                    if ("getCache".equals(method.getName()) && answer != null) {
                        return readersMeetCache((Cache<String, KeyValueTtlValue>) answer);
                    }
                    return answer;
                });
    }

    private Cache<?, ?> readersMeetCache(Cache<String, KeyValueTtlValue> delegate) {
        return (Cache<?, ?>) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Cache.class },
                (proxy, method, args) -> {
                    Object answer = invoke(delegate, method, args);
                    if ("get".equals(method.getName())) {
                        try {
                            bothRead.await(2, TimeUnit.SECONDS);
                        } catch (TimeoutException | BrokenBarrierException e) {
                            // only one reader, nothing to wait for
                        }
                    }
                    return answer;
                });
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
