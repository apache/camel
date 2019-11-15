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
package org.apache.camel.component.jcache.policy;

import java.util.concurrent.TimeUnit;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.CompleteConfiguration;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;

import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class JCachePolicyTest extends JCachePolicyTestBase {

    //Set cache - this use cases is also covered by tests in JCachePolicyProcessorTest
    @Test
    public void testSetCache() throws Exception {
        final String key = randomString();

        //Send exchange
        Object responseBody = this.template().requestBody("direct:policy-cache", key);

        //Verify the set cache was used
        assertEquals(generateValue(key), lookupCache("setCache").get(key));
        assertEquals(generateValue(key), responseBody);
        assertEquals(1, getMockEndpoint("mock:value").getExchanges().size());
    }

    //Set cacheManager, cacheName, cacheConfiguration
    @Test
    public void testSetManagerNameConfiguration() throws Exception {
        final String key = randomString();

        //Send exchange
        Object responseBody = this.template().requestBody("direct:policy-manager-name-configuration", key);

        //Verify cache was created with the set configuration and used in route
        Cache cache = lookupCache("setManagerNameConfiguration");
        CompleteConfiguration completeConfiguration = (CompleteConfiguration) cache.getConfiguration(CompleteConfiguration.class);
        assertEquals(String.class, completeConfiguration.getKeyType());
        assertEquals(String.class, completeConfiguration.getValueType());
        assertEquals(CreatedExpiryPolicy.factoryOf(new Duration(TimeUnit.MINUTES, 60)), completeConfiguration.getExpiryPolicyFactory());
        assertEquals(generateValue(key), cache.get(key));
        assertEquals(generateValue(key), responseBody);
        assertEquals(1, getMockEndpoint("mock:value").getExchanges().size());
    }

    //Set cacheManager, cacheName
    @Test
    public void testSetManagerName() throws Exception {
        final String key = randomString();

        //Send exchange
        Object responseBody = this.template().requestBody("direct:policy-manager-name", key);

        //Verify the cache was created with the name and used
        assertEquals(generateValue(key), lookupCache("setManagerName").get(key));
        assertEquals(generateValue(key), responseBody);
        assertEquals(1, getMockEndpoint("mock:value").getExchanges().size());
    }

    //Set cacheManager, cacheName - cache already exists
    @Test
    public void testSetManagerNameExists() throws Exception {
        final String key = randomString();

        //Send exchange
        Object responseBody = this.template().requestBody("direct:policy-manager-name-exists", key);

        //Verify the existing cache with name was used
        assertEquals(generateValue(key), lookupCache("setManagerNameExists").get(key));
        assertEquals("dummy", lookupCache("setManagerNameExists").get("test"));
        assertEquals(generateValue(key), responseBody);
        assertEquals(1, getMockEndpoint("mock:value").getExchanges().size());
    }

    //Set cacheManager, cacheConfiguration
    @Test
    public void testSetManagerConfiguration() throws Exception {
        final String key = randomString();

        //Send exchange
        Object responseBody = this.template().requestBody("direct:policy-manager-configuration", key);

        //Verify the cache was created with routeId and configuration
        Cache cache = lookupCache("direct-policy-manager-configuration");
        CompleteConfiguration completeConfiguration = (CompleteConfiguration) cache.getConfiguration(CompleteConfiguration.class);
        assertEquals(String.class, completeConfiguration.getKeyType());
        assertEquals(String.class, completeConfiguration.getValueType());
        assertEquals(CreatedExpiryPolicy.factoryOf(new Duration(TimeUnit.MINUTES, 61)), completeConfiguration.getExpiryPolicyFactory());

        assertEquals(generateValue(key), cache.get(key));
        assertEquals(generateValue(key), responseBody);
        assertEquals(1, getMockEndpoint("mock:value").getExchanges().size());
    }

    //Set cacheName - use CachingProvider to lookup CacheManager
    @Test
    public void testDefaultCacheManager() throws Exception {
        final String key = randomString();

        //Send exchange
        Object responseBody = this.template().requestBody("direct:policy-default-manager", key);

        //Verify the default cacheManager was used, despite it was not set
        Cache cache = lookupCache("contextCacheManager");

        assertEquals(generateValue(key), cache.get(key));
        assertEquals(generateValue(key), responseBody);
        assertEquals(1, getMockEndpoint("mock:value").getExchanges().size());
    }

    //Not enabled
    @Test
    public void testNotEnabled() throws Exception {
        final String key = randomString();

        //Send exchange
        Object responseBody = this.template().requestBody("direct:policy-not-enabled", key);

        //Verify the default cacheManager was used, despite it was not set
        Cache cache = lookupCache("notEnabled");

        assertNull(cache.get(key));
        assertEquals(generateValue(key), responseBody);
        assertEquals(1, getMockEndpoint("mock:value").getExchanges().size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                CacheManager cacheManager = Caching.getCachingProvider().getCacheManager();
                MutableConfiguration configuration;

                //Set cache
                Cache cache = cacheManager.createCache("setCache", new MutableConfiguration<>());
                JCachePolicy jcachePolicy = new JCachePolicy();
                jcachePolicy.setCache(cache);

                from("direct:policy-cache")
                        .policy(jcachePolicy)
                        .to("mock:value");

                //Set cacheManager, cacheName, cacheConfiguration
                configuration = new MutableConfiguration<>();
                configuration.setTypes(String.class, String.class);
                configuration.setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(new Duration(TimeUnit.MINUTES, 60)));
                jcachePolicy = new JCachePolicy();
                jcachePolicy.setCacheManager(cacheManager);
                jcachePolicy.setCacheName("setManagerNameConfiguration");
                jcachePolicy.setCacheConfiguration(configuration);

                from("direct:policy-manager-name-configuration")
                        .policy(jcachePolicy)
                        .to("mock:value");

                //Set cacheManager, cacheName
                jcachePolicy = new JCachePolicy();
                jcachePolicy.setCacheManager(cacheManager);
                jcachePolicy.setCacheName("setManagerName");

                from("direct:policy-manager-name")
                        .policy(jcachePolicy)
                        .to("mock:value");

                //Set cacheManager, cacheName - cache already exists
                cache = cacheManager.createCache("setManagerNameExists", new MutableConfiguration<>());
                cache.put("test", "dummy");
                jcachePolicy = new JCachePolicy();
                jcachePolicy.setCacheManager(cacheManager);
                jcachePolicy.setCacheName("setManagerNameExists");

                from("direct:policy-manager-name-exists")
                        .policy(jcachePolicy)
                        .to("mock:value");

                //Set cacheManager, cacheConfiguration
                configuration = new MutableConfiguration<>();
                configuration.setTypes(String.class, String.class);
                configuration.setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(new Duration(TimeUnit.MINUTES, 61)));
                jcachePolicy = new JCachePolicy();
                jcachePolicy.setCacheManager(cacheManager);
                jcachePolicy.setCacheConfiguration(configuration);

                from("direct:policy-manager-configuration").routeId("direct-policy-manager-configuration")
                        .policy(jcachePolicy)
                        .to("mock:value");

                //Set cacheName - use CachingProvider to lookup CacheManager
                jcachePolicy = new JCachePolicy();
                jcachePolicy.setCacheName("contextCacheManager");

                from("direct:policy-default-manager")
                        .policy(jcachePolicy)
                        .to("mock:value");

                //Not enabled
                jcachePolicy = new JCachePolicy();
                cache = cacheManager.createCache("notEnabled", new MutableConfiguration<>());
                jcachePolicy.setCache(cache);
                jcachePolicy.setEnabled(false);

                from("direct:policy-not-enabled")
                        .policy(jcachePolicy)
                        .to("mock:value");
            }
        };
    }


}
