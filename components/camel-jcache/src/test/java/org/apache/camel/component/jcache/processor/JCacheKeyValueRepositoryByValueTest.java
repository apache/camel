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
package org.apache.camel.component.jcache.processor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import javax.cache.Cache;

import org.apache.camel.component.jcache.JCacheConfiguration;
import org.apache.camel.component.jcache.JCacheHelper;
import org.apache.camel.component.jcache.JCacheManager;
import org.apache.camel.component.jcache.support.HazelcastTest;
import org.apache.camel.support.KeyValueTtlValue;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * A JCache that stores values by value (the default of {@link JCacheConfiguration}) returns copies of the entries. A
 * provider may compare them with {@code equals()} in {@code replace(key, old, new)} and {@code remove(key, old)}, as
 * the Ehcache provider does, and a byte array value has no value equality: the compare-and-swap operations must still
 * match the stored entry. The Hazelcast provider of the tests compares the serialized form, so the cache is wrapped to
 * compare with {@code equals()}.
 */
@HazelcastTest
class JCacheKeyValueRepositoryByValueTest extends CamelTestSupport {

    private JCacheManager<String, KeyValueTtlValue> cacheManager;
    private Cache<String, KeyValueTtlValue> cache;
    private JCacheKeyValueRepository repository;

    @Override
    public void doPostSetup() throws Exception {
        cacheManager = JCacheHelper.createManager(context, new JCacheConfiguration("kvrepo-by-value"));
        cache = cacheManager.getCache();

        repository = new JCacheKeyValueRepository();
        repository.setCamelContext(context);
        repository.setCache(comparingWithEquals(cache));
        repository.start();
    }

    @Override
    public void doPostTearDown() throws Exception {
        if (repository != null) {
            repository.stop();
        }
        if (cacheManager != null) {
            cacheManager.close();
        }
    }

    @Test
    void testPutIfAbsentReplacesExpiredByteArrayValue() {
        repository.put("key1", new byte[] { 1 }, Duration.ofMillis(100));
        // wait on the cache itself, as reading through the repository would remove the expired entry
        await().atMost(2, TimeUnit.SECONDS).until(() -> cache.get("key1").isExpired());

        Object previous = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> repository.putIfAbsent("key1", new byte[] { 2 }, null));

        assertThat(previous).isNull();
        assertThat((byte[]) repository.get("key1")).containsExactly(2);
    }

    @Test
    void testGetRemovesExpiredByteArrayValue() {
        repository.put("key1", new byte[] { 1 }, Duration.ofMillis(100));
        await().atMost(2, TimeUnit.SECONDS).until(() -> cache.get("key1").isExpired());

        assertThat(repository.get("key1")).isNull();
        assertThat(cache.get("key1")).isNull();
    }

    @Test
    void testReplaceAndDeleteMatchTheStoredCopy() {
        repository.put("key1", "value1", null);

        assertThat(repository.replace("key1", "value1", "value2", null)).isTrue();
        assertThat(repository.get("key1")).isEqualTo("value2");
        assertThat(repository.delete("key1", "value2")).isTrue();
        assertThat(cache.get("key1")).isNull();
    }

    /**
     * Wraps the cache so that {@code replace(key, old, new)} and {@code remove(key, old)} compare the stored copy with
     * {@code equals()}. The tests use a single thread, so the check and the write need no lock.
     */
    @SuppressWarnings("unchecked")
    private static Cache<String, KeyValueTtlValue> comparingWithEquals(Cache<String, KeyValueTtlValue> delegate) {
        return (Cache<String, KeyValueTtlValue>) Proxy.newProxyInstance(
                JCacheKeyValueRepositoryByValueTest.class.getClassLoader(), new Class<?>[] { Cache.class },
                (proxy, method, args) -> {
                    if ("replace".equals(method.getName()) && args.length == 3) {
                        String key = (String) args[0];
                        if (!args[1].equals(delegate.get(key))) {
                            return false;
                        }
                        delegate.put(key, (KeyValueTtlValue) args[2]);
                        return true;
                    }
                    if ("remove".equals(method.getName()) && args != null && args.length == 2) {
                        String key = (String) args[0];
                        if (!args[1].equals(delegate.get(key))) {
                            return false;
                        }
                        delegate.remove(key);
                        return true;
                    }
                    return invoke(delegate, method, args);
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
