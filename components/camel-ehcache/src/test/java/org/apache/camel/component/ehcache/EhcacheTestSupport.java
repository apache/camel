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
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.camel.builder.FluentProducerTemplate;
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

    @Rule
    public final TestName testName = new TestName();
    public CacheManager cacheManager;

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

    protected String generateRandomString() {
        return UUID.randomUUID().toString();
    }

    protected String[] generateRandomArrayOfStrings(int size) {
        String[] array = new String[size];
        Arrays.setAll(array, i -> generateRandomString());

        return array;
    }

    protected List<String> generateRandomListOfStrings(int size) {
        return Arrays.asList(generateRandomArrayOfStrings(size));
    }

    protected Map<String, String> generateRandomMapOfString(int size) {
        return IntStream.range(0, size).boxed().collect(Collectors.toMap(
            i -> i + "-" + generateRandomString(),
            i -> i + "-" + generateRandomString()
        ));
    }

    FluentProducerTemplate fluentTemplate() {
        return FluentProducerTemplate.on(context());
    }
}
