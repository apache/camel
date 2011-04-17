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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cache.CacheEndpoint;
import org.apache.camel.component.cache.CacheManagerFactory;
import org.apache.camel.itest.osgi.OSGiIntegrationTestSupport;
import org.apache.karaf.testing.Helper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;

import static org.ops4j.pax.exam.CoreOptions.equinox;
import static org.ops4j.pax.exam.CoreOptions.felix;
import static org.ops4j.pax.exam.OptionUtils.combine;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.scanFeatures;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.workingDirectory;

@RunWith(JUnit4TestRunner.class)
public class CacheManagerFactoryRefTest extends OSGiIntegrationTestSupport {
    private static final String CACHE_URI = "cache:foo?cacheManagerFactory=#cacheManagerFactory";
    private TestingCacheManagerFactory cmfRef = new TestingCacheManagerFactory("ehcache_test.xml");

    @Override
    protected Context createJndiContext() throws Exception {
        Context ctx = super.createJndiContext();
        ctx.bind("cacheManagerFactory", cmfRef);
        return ctx;
    }

    @Test
    public void testCache() throws Exception {
        CacheEndpoint endpoint = (CacheEndpoint) context.getEndpoint(CACHE_URI);

        // Is CacheManagerFactory really referenced?
        CacheManagerFactory cmf = endpoint.getCacheManagerFactory();
        assertEquals("Cache Manager Factory Referenced", cmfRef, cmf);

        // Is the right ehcache_test.xml config. loaded?
        Cache cache = cmfRef.getCacheManager().getCache("testingOne");
        assertNotNull("Is ehcache_test.xml loaded", cache);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:test").to(CACHE_URI);
            }
        };
    }

    @Configuration
    public static Option[] configure() throws Exception {
        Option[] options = combine(
        // Default karaf environment
                Helper.getDefaultOptions(
                    // this is how you set the default log level when using pax
                    // logging (logProfile)
                    Helper.setLogLevel("WARN")),

                // using the features to install the camel components
                scanFeatures(
                        getCamelKarafFeatureUrl(),
                        "camel-core", "camel-spring", "camel-test", "camel-cache"),

                workingDirectory("target/paxrunner/"),

                felix(), equinox());

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