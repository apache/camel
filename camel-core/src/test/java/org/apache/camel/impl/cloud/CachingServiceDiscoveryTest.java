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
package org.apache.camel.impl.cloud;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.model.cloud.CachingServiceCallServiceDiscoveryConfiguration;
import org.apache.camel.model.cloud.StaticServiceCallServiceDiscoveryConfiguration;
import org.junit.Assert;
import org.junit.Test;

public class CachingServiceDiscoveryTest extends ContextTestSupport {

    @Test
    public void testCachingServiceDiscovery() throws Exception {
        StaticServiceDiscovery discovery = new StaticServiceDiscovery();
        CachingServiceDiscovery caching = CachingServiceDiscovery.wrap(discovery, 50, TimeUnit.MILLISECONDS);

        discovery.addServer(new DefaultServiceDefinition("noname", "localhost", 1111));
        Assert.assertEquals(1, caching.getServices("noname").size());
        discovery.addServer(new DefaultServiceDefinition("noname", "localhost", 1112));
        Assert.assertEquals(1, caching.getServices("noname").size());

        // Let the cache expire
        Thread.sleep(100);

        Assert.assertEquals(2, caching.getServices("noname").size());
    }

    @Test
    public void testCachingServiceDiscoveryConfiguration() throws Exception {
        StaticServiceCallServiceDiscoveryConfiguration staticConf = new StaticServiceCallServiceDiscoveryConfiguration();
        staticConf.setServers(Arrays.asList("no-name@localhost:1111"));

        CachingServiceCallServiceDiscoveryConfiguration cachingConf = new CachingServiceCallServiceDiscoveryConfiguration();
        cachingConf.setServiceDiscoveryConfiguration(staticConf);
        cachingConf.setTimeout(50);
        cachingConf.setUnits(TimeUnit.MILLISECONDS);

        CachingServiceDiscovery caching = (CachingServiceDiscovery)cachingConf.newInstance(context);
        StaticServiceDiscovery delegate = (StaticServiceDiscovery)caching.getDelegate();

        Assert.assertEquals(1, caching.getServices("no-name").size());
        delegate.addServer("no-name@localhost:1112");
        Assert.assertEquals(1, caching.getServices("no-name").size());

        // Let the cache expire
        Thread.sleep(100);

        Assert.assertEquals(2, caching.getServices("no-name").size());
    }
}
