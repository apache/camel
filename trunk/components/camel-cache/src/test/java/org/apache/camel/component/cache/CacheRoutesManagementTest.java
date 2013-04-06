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

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Status;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.BaseCacheTest;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;

public class CacheRoutesManagementTest extends BaseCacheTest {
    private static final String CACHE_URI = "cache:foo?cacheManagerFactory=#cacheManagerFactory";
    private static final String ROUTE1_ID = "TEST_ROUTE_1";
    private static final String ROUTE2_ID = "TEST_ROUTE_2";
   
    @EndpointInject(uri = CACHE_URI)
    protected CacheEndpoint cacheEndpoint;

    @Produce(uri = "direct:route1")
    protected ProducerTemplate producerTemplate1;

    @Produce(uri = "direct:route2")
    protected ProducerTemplate producerTemplate2;

    private TestingCacheManagerFactory cmfRef = new TestingCacheManagerFactory();

    private Processor templateProcessor = new Processor() {
        public void process(Exchange exchange) throws Exception {
            exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");
            Message in = exchange.getIn();
            in.setBody("Hello World");
        }
    };

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("cacheManagerFactory", cmfRef);
        return jndi;
    }


    @Override
    public RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:add1")
                    .setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_ADD))
                    .setHeader(CacheConstants.CACHE_KEY, constant("foo"))
                    .to(CACHE_URI).setId(ROUTE1_ID);

                from("direct:add2")
                    .setHeader(CacheConstants.CACHE_OPERATION, constant(CacheConstants.CACHE_OPERATION_ADD))
                    .setHeader(CacheConstants.CACHE_KEY, constant("foo"))
                    .to(CACHE_URI).setId(ROUTE2_ID);
            }
        };
    }

    @Test
    public void testConfig() throws Exception {
        //do some routes
        producerTemplate1.send(templateProcessor);
        producerTemplate2.send(templateProcessor);

        // Now do some routes to let endpoints be initialized
        template.sendBody("direct:add1", "Hello World");
        template.sendBody("direct:add2", "Hello World");

        //Now should not be null
        CacheManager cacheManager = cmfRef.getCacheManager();
        assertNotNull("CacheManager initialized", cacheManager);

        Cache cache = cmfRef.getCacheManager().getCache("foo");

        // Is cache alive
        assertEquals("Is cache still alive", Status.STATUS_ALIVE, cache.getStatus());

        context.stopRoute(ROUTE1_ID);

        // Is cache still alive?
        assertEquals("Is cache still alive", Status.STATUS_ALIVE, cache.getStatus());

        context.stop();

        // Was the cache shutdowned with context?
        assertEquals("Is cache still alive", Status.STATUS_SHUTDOWN, cache.getStatus());
    }

    public class TestingCacheManagerFactory extends CacheManagerFactory {

        //Only for testing purpose, normally not needed
        private CacheManager cacheManager;

        @Override
        protected synchronized CacheManager createCacheManagerInstance() {
            //Singleton- only for testing purpose, normally not needed
            if (cacheManager == null) {
                cacheManager = CacheManager.create();
            }

            return cacheManager;
        }

        public CacheManager getCacheManager() {
            return cacheManager;
        }
    }
}
