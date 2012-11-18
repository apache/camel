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
package org.apache.camel.component.cache;

import net.sf.ehcache.CacheManager;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class CacheManagerFactoryRefTest extends CamelTestSupport {
    private static final String CACHE_ENDPOINT_URI =
            "cache://myname1?cacheManagerFactory=#testCacheManagerFactory";

    @EndpointInject(uri = CACHE_ENDPOINT_URI)
    protected CacheEndpoint cacheEndpoint;

    @Produce(uri = "direct:start")
    protected ProducerTemplate producerTemplate;

    protected CacheManagerFactory testingCacheManagerFactory;

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        testingCacheManagerFactory = new TestingCacheManagerFactory();
        jndi.bind("testCacheManagerFactory", testingCacheManagerFactory);
        return jndi;
    }

    @Override
    public RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to(CACHE_ENDPOINT_URI);
            }
        };
    }

    @Test
    public void testConfig() throws Exception {
        producerTemplate.send(new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");
                Message in = exchange.getIn();
                in.setHeader(CacheConstants.CACHE_OPERATION, CacheConstants.CACHE_OPERATION_ADD);
                in.setHeader(CacheConstants.CACHE_KEY, "greeting");
                in.setBody("Hello World");
            }
        });

        assertEquals("Is CacheManagerFactory set", testingCacheManagerFactory,
                cacheEndpoint.getCacheManagerFactory());
    }

    public static class TestingCacheManagerFactory extends CacheManagerFactory {
        @Override
        protected CacheManager createCacheManagerInstance() {
            return CacheManager.create(getClass().getResourceAsStream("/test-ehcache.xml"));
        }
    }
}
