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
package org.apache.camel.component.ignite;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.cache.Cache.Entry;

import com.google.common.collect.ImmutableMap;
import org.apache.camel.CamelException;
import org.apache.camel.component.ignite.cache.IgniteCacheComponent;
import org.apache.camel.component.ignite.cache.IgniteCacheOperation;
import org.apache.camel.util.ObjectHelper;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CachePeekMode;
import org.apache.ignite.cache.query.Query;
import org.apache.ignite.cache.query.ScanQuery;
import org.apache.ignite.lang.IgniteBiPredicate;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.fail;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class IgniteCacheTest extends AbstractIgniteTest {

    @Override
    protected String getScheme() {
        return "ignite-cache";
    }

    @Override
    protected AbstractIgniteComponent createComponent() {
        return IgniteCacheComponent.fromConfiguration(createConfiguration());
    }

    @Test
    public void testAddEntry() {
        template.requestBodyAndHeader("ignite-cache:" + resourceUid + "?operation=PUT", "1234",
                IgniteConstants.IGNITE_CACHE_KEY, "abcd");

        Assertions.assertThat(ignite().cache(resourceUid).size(CachePeekMode.ALL)).isEqualTo(1);
        Assertions.assertThat(ignite().cache(resourceUid).get("abcd")).isEqualTo("1234");
    }

    @Test
    public void testAddEntrySet() {
        template.requestBody("ignite-cache:" + resourceUid + "?operation=PUT", ImmutableMap.of("abcd", "1234", "efgh", "5678"));

        Assertions.assertThat(ignite().cache(resourceUid).size(CachePeekMode.ALL)).isEqualTo(2);
        Assertions.assertThat(ignite().cache(resourceUid).get("abcd")).isEqualTo("1234");
        Assertions.assertThat(ignite().cache(resourceUid).get("efgh")).isEqualTo("5678");
    }

    @Test
    public void testGetOne() {
        testAddEntry();

        String result = template.requestBody("ignite-cache:" + resourceUid + "?operation=GET", "abcd", String.class);
        Assertions.assertThat(result).isEqualTo("1234");

        result = template.requestBodyAndHeader("ignite-cache:" + resourceUid + "?operation=GET", "this value won't be used",
                IgniteConstants.IGNITE_CACHE_KEY, "abcd",
                String.class);
        Assertions.assertThat(result).isEqualTo("1234");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetMany() {
        IgniteCache<String, String> cache = ignite().getOrCreateCache(resourceUid);
        Set<String> keys = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            cache.put("k" + i, "v" + i);
            keys.add("k" + i);
        }

        Map<String, String> result = template.requestBody("ignite-cache:" + resourceUid + "?operation=GET", keys, Map.class);
        for (String k : keys) {
            Assertions.assertThat(result.get(k)).isEqualTo(k.replace("k", "v"));
        }
    }

    @Test
    public void testGetSize() {
        IgniteCache<String, String> cache = ignite().getOrCreateCache(resourceUid);
        Set<String> keys = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            cache.put("k" + i, "v" + i);
            keys.add("k" + i);
        }

        Integer result = template.requestBody("ignite-cache:" + resourceUid + "?operation=SIZE", keys, Integer.class);
        Assertions.assertThat(result).isEqualTo(100);
    }

    @Test
    public void testQuery() {
        IgniteCache<String, String> cache = ignite().getOrCreateCache(resourceUid);
        Set<String> keys = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            cache.put("k" + i, "v" + i);
            keys.add("k" + i);
        }

        Query<Entry<String, String>> query = new ScanQuery<>(new IgniteBiPredicate<String, String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean apply(String key, String value) {
                return Integer.parseInt(key.replace("k", "")) >= 50;
            }
        });

        List<?> results = template.requestBodyAndHeader("ignite-cache:" + resourceUid + "?operation=QUERY", keys,
                IgniteConstants.IGNITE_CACHE_QUERY, query, List.class);
        Assertions.assertThat(results.size()).isEqualTo(50);
    }

    @Test
    public void testGetManyTreatCollectionsAsCacheObjects() {
        IgniteCache<Object, String> cache = ignite().getOrCreateCache(resourceUid);
        Set<String> keys = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            cache.put("k" + i, "v" + i);
            keys.add("k" + i);
        }

        // Also add a cache entry with the entire Set as a key.
        cache.put(keys, "---");

        String result = template.requestBody(
                "ignite-cache:" + resourceUid + "?operation=GET&treatCollectionsAsCacheObjects=true", keys, String.class);
        Assertions.assertThat(result).isEqualTo("---");
    }

    @Test
    public void testRemoveEntry() {
        IgniteCache<String, String> cache = ignite().getOrCreateCache(resourceUid);

        cache.put("abcd", "1234");
        cache.put("efgh", "5678");

        Assertions.assertThat(cache.size(CachePeekMode.ALL)).isEqualTo(2);

        template.requestBody("ignite-cache:" + resourceUid + "?operation=REMOVE", "abcd");

        Assertions.assertThat(cache.size(CachePeekMode.ALL)).isEqualTo(1);
        Assertions.assertThat(cache.get("abcd")).isNull();

        template.requestBodyAndHeader("ignite-cache:" + resourceUid + "?operation=REMOVE", "this value won't be used",
                IgniteConstants.IGNITE_CACHE_KEY, "efgh");

        Assertions.assertThat(cache.size(CachePeekMode.ALL)).isEqualTo(0);
        Assertions.assertThat(cache.get("efgh")).isNull();

    }

    @Test
    public void testReplaceEntry() {
        template.requestBodyAndHeader("ignite-cache:" + resourceUid + "?operation=REPLACE", "5678",
                IgniteConstants.IGNITE_CACHE_KEY, "abcd");
        IgniteCache<String, String> cache = ignite().getOrCreateCache(resourceUid);
        Assertions.assertThat(cache.size(CachePeekMode.ALL)).isEqualTo(0);

        cache.put("abcd", "1234");
        template.requestBodyAndHeader("ignite-cache:" + resourceUid + "?operation=REPLACE", "5678",
                IgniteConstants.IGNITE_CACHE_KEY, "abcd");
        Assertions.assertThat(cache.size(CachePeekMode.ALL)).isEqualTo(1);
        Assertions.assertThat(cache.get("abcd")).isEqualTo("5678");

        Map<String, Object> headers
                = Map.of(IgniteConstants.IGNITE_CACHE_KEY, "abcd", IgniteConstants.IGNITE_CACHE_OLD_VALUE, "1234");
        template.requestBodyAndHeaders("ignite-cache:" + resourceUid + "?operation=REPLACE", "9", headers);
        Assertions.assertThat(cache.size(CachePeekMode.ALL)).isEqualTo(1);
        Assertions.assertThat(cache.get("abcd")).isEqualTo("5678");

        headers = Map.of(IgniteConstants.IGNITE_CACHE_KEY, "abcd", IgniteConstants.IGNITE_CACHE_OLD_VALUE, "5678");
        template.requestBodyAndHeaders("ignite-cache:" + resourceUid + "?operation=REPLACE", "9", headers);
        Assertions.assertThat(cache.size(CachePeekMode.ALL)).isEqualTo(1);
        Assertions.assertThat(cache.get("abcd")).isEqualTo("9");
    }

    @Test
    public void testClearCache() {
        IgniteCache<String, String> cache = ignite().getOrCreateCache(resourceUid);
        for (int i = 0; i < 100; i++) {
            cache.put("k" + i, "v" + i);
        }

        Assertions.assertThat(cache.size(CachePeekMode.ALL)).isEqualTo(100);

        template.requestBody("ignite-cache:" + resourceUid + "?operation=CLEAR", "this value won't be used");

        Assertions.assertThat(cache.size(CachePeekMode.ALL)).isEqualTo(0);
    }

    @Test
    public void testHeaderSetRemoveEntry() {
        testAddEntry();

        String result = template.requestBody("ignite-cache:" + resourceUid + "?operation=GET", "abcd", String.class);
        Assertions.assertThat(result).isEqualTo("1234");

        result = template.requestBodyAndHeader("ignite-cache:" + resourceUid + "?operation=GET", "abcd",
                IgniteConstants.IGNITE_CACHE_OPERATION, IgniteCacheOperation.REMOVE,
                String.class);

        // The body has not changed, but the cache entry is gone.
        Assertions.assertThat(result).isEqualTo("abcd");
        Assertions.assertThat(ignite().cache(resourceUid).size(CachePeekMode.ALL)).isEqualTo(0);
    }

    @Test
    public void testAddEntryNoCacheCreation() {
        try {
            template.requestBodyAndHeader("ignite-cache:testcache2?operation=PUT&failIfInexistentCache=true", "1234",
                    IgniteConstants.IGNITE_CACHE_KEY, "abcd");
        } catch (Exception e) {
            Assertions.assertThat(ObjectHelper.getException(CamelException.class, e).getMessage())
                    .startsWith("Ignite cache testcache2 doesn't exist");
            return;
        }

        fail("Should have thrown an exception");
    }

    @Test
    public void testAddEntryDoNotPropagateIncomingBody() {
        Object result = template.requestBodyAndHeader(
                "ignite-cache:" + resourceUid + "?operation=PUT&propagateIncomingBodyIfNoReturnValue=false", "1234",
                IgniteConstants.IGNITE_CACHE_KEY, "abcd", Object.class);

        Assertions.assertThat(ignite().cache(resourceUid).size(CachePeekMode.ALL)).isEqualTo(1);
        Assertions.assertThat(ignite().cache(resourceUid).get("abcd")).isEqualTo("1234");

        Assertions.assertThat(result).isNull();
    }

    @AfterEach
    public void deleteCaches() {
        IgniteCache<?, ?> cache = ignite().cache(resourceUid);
        if (cache != null) {
            cache.clear();
        }
    }
}
