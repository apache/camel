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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheException;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.Status;
import net.sf.ehcache.event.CacheEventListener;
import net.sf.ehcache.loader.CacheLoader;

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

public class CacheRegistryRefTest extends BaseCacheTest {
    private static final String CACHE_ENDPOINT_URI = "cache://foo"
        + "?eventListenerRegistry=#eventListenerRegistry&cacheLoaderRegistry=#cacheLoaderRegistry";

    @EndpointInject(uri = CACHE_ENDPOINT_URI)
    protected CacheEndpoint cacheEndpoint;

    @Produce(uri = "direct:start")
    protected ProducerTemplate producerTemplate;

    private CacheEventListenerRegistry eventListenerRegistry = new CacheEventListenerRegistry();
    private CacheLoaderRegistry loaderRegistry = new CacheLoaderRegistry();

    @Override
    public void setUp() throws Exception {
        eventListenerRegistry.addCacheEventListener(new TestCacheEventListener());
        loaderRegistry.addCacheLoader(new TestLoader());
        super.setUp();
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("eventListenerRegistry", eventListenerRegistry);
        jndi.bind("cacheLoaderRegistry", loaderRegistry);
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
        
        CacheManager cm = cacheEndpoint.getCacheManagerFactory().getInstance();
        Cache cache = cm.getCache(cacheEndpoint.getConfig().getCacheName());
        Set<CacheEventListener> ehcacheEventListners = cache.getCacheEventNotificationService().getCacheEventListeners();
        List<CacheLoader> cacheLoaders = cache.getRegisteredCacheLoaders();
        CacheEventListenerRegistry configuredEventRegistry = cacheEndpoint.getConfig().getEventListenerRegistry();
        CacheLoaderRegistry configuredLoaderRegistry = cacheEndpoint.getConfig().getCacheLoaderRegistry();

        //Test if CacheEventListenerRegistry was referenced correctly
        assertEquals("CacheEventListenerRegistry size", 1, configuredEventRegistry.size());

        //Expecting 1 loader to be configured via spring
        assertEquals("configuredLoaderRegistry size", 1, configuredLoaderRegistry.size());

        //Expecting 1 listener added by us: TestCacheEventListener
        assertEquals("Number of registered listeners", 1, ehcacheEventListners.size());

        assertEquals("Number of registered loaders", 1, cacheLoaders.size());


        //Is our TestCacheEventListener really invoked?
        int puts = 0;
        for (Object listener : ehcacheEventListners) {
            if (listener instanceof TestCacheEventListener) {
                puts = ((TestCacheEventListener)listener).getNumberOfPuts();
                break;
            }
        }
        assertEquals("TestCacheEventListener put invocations", 1, puts);

        //Is cache loader initialized by ehcache
        assertEquals("loader initialized", cacheLoaders.get(0).getStatus(), Status.STATUS_ALIVE);
    }

    public static class TestingCacheManagerFactory extends CacheManagerFactory {
        @Override
        protected CacheManager createCacheManagerInstance() {
            return CacheManager.create(getClass().getResourceAsStream("/ehcache.xml"));
        }
    }

    public static class TestLoader implements CacheLoaderWrapper {

        protected Ehcache cache;
        private Status status;

        public TestLoader() {
            status = Status.STATUS_UNINITIALISED;
        }

        @Override
        public CacheLoader clone(Ehcache arg0) throws CloneNotSupportedException {
            return null;
        }

        @Override
        public void dispose() throws CacheException {
            status = Status.STATUS_SHUTDOWN;
        }

        @Override
        public String getName() {
            return "Testing cache loader";
        }

        @Override
        public Status getStatus() {
            return status;
        }

        @Override
        public void init() {
            status = Status.STATUS_ALIVE;
        }

        @Override
        public Object load(Object arg0) throws CacheException {
            return null;
        }

        @Override
        public Object load(Object arg0, Object arg1) {
            return null;
        }

        @Override
        @SuppressWarnings("rawtypes")
        public Map loadAll(Collection arg0) {
            return null;
        }

        @Override
        @SuppressWarnings("rawtypes")
        public Map loadAll(Collection arg0, Object arg1) {
            return null;
        }

        @Override
        public void init(Ehcache cache) {
            this.cache = cache;
        }
        
    }

    //Test event lister that will help us to count put method invocations.
    public static class TestCacheEventListener implements CacheEventListener {
        private int numberOfPuts;

        @Override
        public void dispose() {
        }

        @Override
        public void notifyElementEvicted(Ehcache arg0, Element arg1) {
        }

        @Override
        public void notifyElementExpired(Ehcache arg0, Element arg1) {
        }

        @Override
        public void notifyElementPut(Ehcache arg0, Element arg1) throws CacheException {
            numberOfPuts++;
        }

        @Override
        public void notifyElementRemoved(Ehcache arg0, Element arg1) throws CacheException {
        }

        @Override
        public void notifyElementUpdated(Ehcache arg0, Element arg1) throws CacheException {
        }
        
        @Override
        public void notifyRemoveAll(Ehcache arg0) {
        }

        @Override
        public TestCacheEventListener clone() {
            return this.clone();
        }

        public int getNumberOfPuts() {
            return numberOfPuts;
        }
    }
}
