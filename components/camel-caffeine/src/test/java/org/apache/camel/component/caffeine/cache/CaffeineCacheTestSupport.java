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
package org.apache.camel.component.caffeine.cache;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.codahale.metrics.MetricRegistry;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;
import org.apache.camel.BindToRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CaffeineCacheTestSupport extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(CaffeineCacheTestSupport.class);
    @BindToRegistry("cache")
    private Cache cache = Caffeine.newBuilder().recordStats().build();
    @BindToRegistry("cacheRl")
    private Cache cacheRl = Caffeine.newBuilder().recordStats().removalListener(new DummyRemovalListener()).build();
    private MetricRegistry mRegistry = new MetricRegistry();
    @BindToRegistry("cacheSc")
    private Cache cacheSc = Caffeine.newBuilder().recordStats(() -> new MetricsStatsCounter(mRegistry)).build();

    protected Cache getTestCache() {
        return cache;
    }

    protected Cache getTestRemovalListenerCache() {
        return cacheRl;
    }
    
    protected Cache getTestStatsCounterCache() {
        return cacheSc;
    }
    
    protected MetricRegistry getMetricRegistry() {
        return mRegistry;
    }

    protected static int[] generateRandomArrayOfInt(int size, int lower, int upper) {
        Random random = new Random();
        int[] array = new int[size];

        Arrays.setAll(array, i -> random.nextInt(upper - lower) + lower);

        return array;
    }

    protected static String generateRandomString() {
        return UUID.randomUUID().toString();
    }

    protected static String[] generateRandomArrayOfStrings(int size) {
        String[] array = new String[size];
        Arrays.setAll(array, i -> generateRandomString());

        return array;
    }

    protected static List<String> generateRandomListOfStrings(int size) {
        return Arrays.asList(generateRandomArrayOfStrings(size));
    }

    protected static Map<String, String> generateRandomMapOfString(int size) {
        return IntStream.range(0, size).boxed().collect(Collectors.toMap(i -> i + "-" + generateRandomString(), i -> i + "-" + generateRandomString()));
    }

    class DummyRemovalListener implements RemovalListener<Object, Object> {

        @Override
        public void onRemoval(Object key, Object value, RemovalCause cause) {
            LOG.info("Key %s was removed (%s)%n", key, cause);
        }

    }
}
