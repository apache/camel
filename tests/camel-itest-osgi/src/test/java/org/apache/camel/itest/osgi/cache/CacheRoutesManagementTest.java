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
package org.apache.camel.itest.osgi.cache;

import javax.naming.Context;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Status;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cache.CacheConstants;
import org.apache.camel.component.cache.CacheManagerFactory;
import org.apache.camel.itest.osgi.OSGiIntegrationTestSupport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;

import static org.ops4j.pax.exam.OptionUtils.combine;


@RunWith(PaxExam.class)
public class CacheRoutesManagementTest extends OSGiIntegrationTestSupport {
    private static final String CACHE_URI = "cache:foo?cacheManagerFactory=#cacheManagerFactory";
    private static final String ROUTE1_ID = "TEST_ROUTE_1";
    private static final String ROUTE2_ID = "TEST_ROUTE_2";
    private TestingCacheManagerFactory cmfRef = new TestingCacheManagerFactory("ehcache_test.xml");

    @Override
    protected Context createJndiContext() throws Exception {
        Context ctx = super.createJndiContext();
        ctx.bind("cacheManagerFactory", cmfRef);
        return ctx;
    }

    @Test
    public void testCache() throws Exception {

        // Now do some routes to let endpoints be initialized
        template.sendBody("direct:add1", "Hello World");
        template.sendBody("direct:add2", "Hello World");

        CacheManager cacheManager = cmfRef.getCacheManager();
        assertNotNull("CacheManager initialized", cacheManager);

        Cache cache = cmfRef.getCacheManager().getCache("foo");

        // Is cache alive
        assertEquals("Is cache still alive", Status.STATUS_ALIVE, cache.getStatus());

        context.stopRoute(ROUTE1_ID);

        // Is cache still alive
        assertEquals("Is cache still alive", Status.STATUS_ALIVE, cache.getStatus());

        context.stop();

        // Was the cache shutdowned with context?
        assertEquals("Is cache still alive", Status.STATUS_SHUTDOWN, cache.getStatus());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
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

    
    @Configuration
    public static Option[] configure() {
        Option[] options = combine(
            getDefaultCamelKarafOptions(),
            // using the features to install the other camel components             
            loadCamelFeatures("camel-cache"));
        
        return options;
    }

    public class TestingCacheManagerFactory extends CacheManagerFactory {
        private String xmlName;

        //Only for testing purpose, normally not needed
        private CacheManager cacheManager;

        public TestingCacheManagerFactory(String xmlName) {
            this.xmlName = xmlName;
        }

        @Override
        protected synchronized CacheManager createCacheManagerInstance() {
            //Singleton- only for testing purpose, normally not needed
            if (cacheManager == null) {
                cacheManager = CacheManager.create(getClass().getResourceAsStream(xmlName));
            }

            return cacheManager;
        }

        public CacheManager getCacheManager() {
            return cacheManager;
        }
    }

}