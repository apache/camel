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

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

import javax.cache.Cache;
import javax.cache.configuration.CompleteConfiguration;
import javax.cache.configuration.Factory;
import javax.cache.configuration.FactoryBuilder;
import javax.cache.expiry.AccessedExpiryPolicy;
import javax.cache.expiry.Duration;
import javax.cache.expiry.ExpiryPolicy;
import javax.cache.integration.CacheLoader;
import javax.cache.integration.CacheLoaderException;
import javax.cache.integration.CacheWriter;
import javax.cache.integration.CacheWriterException;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JCacheConfigurationTest extends JCacheComponentTestSupport {

    @BindToRegistry("myExpiryPolicyFactory")
    public static final Factory<ExpiryPolicy> EXPIRY_POLICY_FACTORY = AccessedExpiryPolicy.factoryOf(Duration.ONE_MINUTE);
    @BindToRegistry("myCacheWriterFactory")
    public static final Factory<CacheWriter<Object, Object>> CACHE_WRITER_FACTORY = MyCacheWriter.factory();
    @BindToRegistry("myCacheLoaderFactory")
    public static final Factory<CacheLoader<Object, Object>> CACHE_LOADER_FACTORY = MyCacheLoader.factory();

    @EndpointInject(value = "jcache://test-cache" + "?expiryPolicyFactory=#myExpiryPolicyFactory"
                            + "&cacheWriterFactory=#myCacheWriterFactory"
                            + "&cacheLoaderFactory=#myCacheLoaderFactory")
    JCacheEndpoint from;

    @EndpointInject("mock:to")
    MockEndpoint to;

    @Test
    public void testConfigurations() throws Exception {
        final Cache<Object, Object> cache = from.getManager().getCache();
        final CompleteConfiguration<Object, Object> conf = cache.getConfiguration(CompleteConfiguration.class);

        assertEquals(EXPIRY_POLICY_FACTORY, conf.getExpiryPolicyFactory());
        assertEquals(CACHE_WRITER_FACTORY, conf.getCacheWriterFactory());
        assertEquals(CACHE_LOADER_FACTORY, conf.getCacheLoaderFactory());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(from).to(to);
            }
        };
    }

    private static final class MyCacheLoader implements CacheLoader<Object, Object>, Serializable {
        @Override
        public Object load(Object key) throws CacheLoaderException {
            return null;
        }

        @Override
        public Map<Object, Object> loadAll(Iterable<?> keys) throws CacheLoaderException {
            return null;
        }

        public static Factory<CacheLoader<Object, Object>> factory() {
            return new FactoryBuilder.SingletonFactory(new MyCacheLoader());
        }
    }

    private static final class MyCacheWriter implements CacheWriter<Object, Object>, Serializable {
        @Override
        public void write(Cache.Entry<?, ?> entry) throws CacheWriterException {
        }

        @Override
        public void writeAll(Collection<Cache.Entry<?, ?>> entries) throws CacheWriterException {
        }

        @Override
        public void delete(Object key) throws CacheWriterException {
        }

        @Override
        public void deleteAll(Collection<?> keys) throws CacheWriterException {
        }

        public static Factory<CacheWriter<Object, Object>> factory() {
            return new FactoryBuilder.SingletonFactory(new MyCacheWriter());
        }
    }
}
