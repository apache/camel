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

import java.util.UUID;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;

import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;

public class JCachePolicyTestBase extends CamelTestSupport {

    @Before
    public void before() {
        //Setup mock
        getMockEndpoint("mock:value").whenAnyExchangeReceived(e ->
                e.getMessage().setBody(generateValue(e.getMessage().getBody(String.class))));
    }

    public static String randomString() {
        return UUID.randomUUID().toString();
    }

    public static Cache lookupCache(String cacheName) {
        //This will also open a closed cache
        return Caching.getCachingProvider().getCacheManager().getCache(cacheName);
    }

    public static String generateValue(String key) {
        return "value-" + key;
    }

    @After
    public void after() {
        //The RouteBuilder code is called for every test, so we destroy cache after each test
        CacheManager cacheManager = Caching.getCachingProvider().getCacheManager();
        cacheManager.getCacheNames().forEach(s -> cacheManager.destroyCache(s));
        Caching.getCachingProvider().close();
    }
}
