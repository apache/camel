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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.model.cloud.AggregatingServiceCallServiceDiscoveryConfiguration;
import org.apache.camel.model.cloud.StaticServiceCallServiceDiscoveryConfiguration;
import org.junit.Assert;
import org.junit.Test;

public class AggregatingServiceDiscoveryTest extends ContextTestSupport {
    @Test
    public void testMultiServiceDiscovery() throws Exception {
        StaticServiceDiscovery discovery1 = new StaticServiceDiscovery();
        discovery1.addServer(new DefaultServiceDefinition("discovery1", "localhost", 1111));
        discovery1.addServer(new DefaultServiceDefinition("discovery1", "localhost", 1112));

        StaticServiceDiscovery discovery2 = new StaticServiceDiscovery();
        discovery2.addServer(new DefaultServiceDefinition("discovery1", "localhost", 1113));
        discovery2.addServer(new DefaultServiceDefinition("discovery2", "localhost", 1114));

        AggregatingServiceDiscovery discovery = AggregatingServiceDiscovery.wrap(discovery1, discovery2);
        Assert.assertEquals(3, discovery.getServices("discovery1").size());
        Assert.assertEquals(1, discovery.getServices("discovery2").size());
    }

    @Test
    public void testMultiServiceDiscoveryConfiguration() throws Exception {
        StaticServiceCallServiceDiscoveryConfiguration staticConf1 = new StaticServiceCallServiceDiscoveryConfiguration();
        staticConf1.setServers(Arrays.asList("discovery1@localhost:1111", "discovery1@localhost:1112"));

        StaticServiceCallServiceDiscoveryConfiguration staticConf2 = new StaticServiceCallServiceDiscoveryConfiguration();
        staticConf2.setServers(Arrays.asList("discovery1@localhost:1113", "discovery2@localhost:1114"));

        AggregatingServiceCallServiceDiscoveryConfiguration multiConf = new AggregatingServiceCallServiceDiscoveryConfiguration();
        multiConf.setServiceDiscoveryConfigurations(Arrays.asList(staticConf1, staticConf2));

        AggregatingServiceDiscovery discovery = (AggregatingServiceDiscovery)multiConf.newInstance(context);
        Assert.assertEquals(2, discovery.getDelegates().size());
        Assert.assertEquals(3, discovery.getServices("discovery1").size());
        Assert.assertEquals(1, discovery.getServices("discovery2").size());
    }

    @Test
    public void testMultiServiceDiscoveryConfigurationDsl() throws Exception {
        AggregatingServiceCallServiceDiscoveryConfiguration multiConf = new AggregatingServiceCallServiceDiscoveryConfiguration();
        multiConf.staticServiceDiscovery().setServers(Arrays.asList("discovery1@localhost:1111", "discovery1@localhost:1112"));
        multiConf.staticServiceDiscovery().setServers(Arrays.asList("discovery1@localhost:1113", "discovery2@localhost:1114"));

        AggregatingServiceDiscovery discovery = (AggregatingServiceDiscovery)multiConf.newInstance(context);
        Assert.assertEquals(2, discovery.getDelegates().size());
        Assert.assertEquals(3, discovery.getServices("discovery1").size());
        Assert.assertEquals(1, discovery.getServices("discovery2").size());
    }

    @Test
    public void testMultiServiceDiscoveryConfigurationWithPlaceholders() throws Exception {
        System.setProperty("svc-list-1", "discovery1@localhost:1111,discovery1@localhost:1112");
        System.setProperty("svc-list-2", "discovery1@localhost:1113,discovery2@localhost:1114");

        AggregatingServiceCallServiceDiscoveryConfiguration multiConf = new AggregatingServiceCallServiceDiscoveryConfiguration();
        multiConf.staticServiceDiscovery().servers("{{svc-list-1}}");
        multiConf.staticServiceDiscovery().servers("{{svc-list-2}}");

        AggregatingServiceDiscovery discovery = (AggregatingServiceDiscovery)multiConf.newInstance(context);
        Assert.assertEquals(2, discovery.getDelegates().size());
        Assert.assertEquals(3, discovery.getServices("discovery1").size());
        Assert.assertEquals(1, discovery.getServices("discovery2").size());
    }
}
