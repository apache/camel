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

import java.util.HashMap;
import java.util.Map;

import net.sf.ehcache.CacheManager;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class CacheConfigurationFileTest extends CamelTestSupport {

    private CacheComponent cache;

    @Test
    public void testConfigurationFile() throws Exception {
        getMockEndpoint("mock:foo").expectedMessageCount(1);

        Map<String, Object> map = new HashMap<String, Object>();
        map.put(CacheConstants.CACHE_KEY, "myKey");
        map.put(CacheConstants.CACHE_OPERATION, "ADD");
        template.sendBodyAndHeaders("direct:start", "Hello World", map);

        assertMockEndpointsSatisfied();

        CacheManager cacheManager = cache.getCacheManagerFactory().getInstance();
        assertNotNull(cacheManager);

        assertEquals("target/mytemp", cacheManager.getConfiguration().getDiskStoreConfiguration().getPath());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                cache = context.getComponent("cache", CacheComponent.class);
                cache.setConfigurationFile("classpath:test-ehcache.xml");

                from("direct:start")
                    .to("cache:foo");

                from("cache:foo").to("mock:foo");
            }
        };
    }
}
