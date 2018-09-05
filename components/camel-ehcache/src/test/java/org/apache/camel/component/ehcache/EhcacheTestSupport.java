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

package org.apache.camel.component.ehcache;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.camel.component.ehcache.processor.aggregate.EhcacheAggregationRepository;
import org.apache.camel.impl.DefaultExchangeHolder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.Configuration;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.xml.XmlConfiguration;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EhcacheTestSupport extends CamelTestSupport  {
    public static final Logger LOGGER = LoggerFactory.getLogger(EhcacheTestSupport.class);
    public static final String EHCACHE_CONFIG = "/ehcache/ehcache-config.xml";
    public static final String TEST_CACHE_NAME = "mycache";
    public static final String IDEMPOTENT_TEST_CACHE_NAME = "idempotent";
    public static final String AGGREGATE_TEST_CACHE_NAME = "aggregate";

    @Rule
    public final TestName testName = new TestName();
    protected CacheManager cacheManager;

    @Override
    protected void doPreSetup() throws Exception {
        final URL url = this.getClass().getResource(EHCACHE_CONFIG);
        final Configuration xmlConfig = new XmlConfiguration(url);

        cacheManager = CacheManagerBuilder.newCacheManager(xmlConfig);
        cacheManager.init();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        if (cacheManager != null) {
            cacheManager.close();
        }
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("cacheManager", cacheManager);

        return registry;
    }

    protected Cache<Object, Object> getCache(String name) {
        return cacheManager.getCache(name, Object.class, Object.class);
    }

    protected Cache<Object, Object> getTestCache() {
        return cacheManager.getCache(TEST_CACHE_NAME, Object.class, Object.class);
    }

    protected Cache<String, Boolean> getIdempotentCache() {
        return cacheManager.getCache(IDEMPOTENT_TEST_CACHE_NAME, String.class, Boolean.class);
    }

    protected Cache<String, DefaultExchangeHolder> getAggregateCache() {
        return cacheManager.getCache(AGGREGATE_TEST_CACHE_NAME, String.class, DefaultExchangeHolder.class);
    }

    protected EhcacheAggregationRepository createAggregateRepository() throws Exception {
        EhcacheAggregationRepository repository = new EhcacheAggregationRepository();
        repository.setCache(getAggregateCache());
        repository.setCacheName("aggregate");

        return repository;
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
        return IntStream.range(0, size).boxed().collect(Collectors.toMap(
            i -> i + "-" + generateRandomString(),
            i -> i + "-" + generateRandomString()
        ));
    }
}
