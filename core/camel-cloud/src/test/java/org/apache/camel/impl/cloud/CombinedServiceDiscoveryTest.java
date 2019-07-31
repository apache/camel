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
package org.apache.camel.impl.cloud;

import java.util.Arrays;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.model.cloud.CombinedServiceCallServiceDiscoveryConfiguration;
import org.apache.camel.model.cloud.StaticServiceCallServiceDiscoveryConfiguration;
import org.junit.Assert;
import org.junit.Test;

public class CombinedServiceDiscoveryTest extends ContextTestSupport {
    @Test
    public void testCombinedServiceDiscovery() throws Exception {
        StaticServiceDiscovery discovery1 = new StaticServiceDiscovery();
        discovery1.addServer(new DefaultServiceDefinition("discovery1", "localhost", 1111));
        discovery1.addServer(new DefaultServiceDefinition("discovery1", "localhost", 1112));

        StaticServiceDiscovery discovery2 = new StaticServiceDiscovery();
        discovery2.addServer(new DefaultServiceDefinition("discovery1", "localhost", 1113));
        discovery2.addServer(new DefaultServiceDefinition("discovery2", "localhost", 1114));

        CombinedServiceDiscovery discovery = CombinedServiceDiscovery.wrap(discovery1, discovery2);
        Assert.assertEquals(3, discovery.getServices("discovery1").size());
        Assert.assertEquals(1, discovery.getServices("discovery2").size());
    }

    @Test
    public void testCombinedServiceDiscoveryConfiguration() throws Exception {
        StaticServiceCallServiceDiscoveryConfiguration staticConf1 = new StaticServiceCallServiceDiscoveryConfiguration();
        staticConf1.setServers(Arrays.asList("discovery1@localhost:1111", "discovery1@localhost:1112"));

        StaticServiceCallServiceDiscoveryConfiguration staticConf2 = new StaticServiceCallServiceDiscoveryConfiguration();
        staticConf2.setServers(Arrays.asList("discovery1@localhost:1113", "discovery2@localhost:1114"));

        CombinedServiceCallServiceDiscoveryConfiguration multiConf = new CombinedServiceCallServiceDiscoveryConfiguration();
        multiConf.setServiceDiscoveryConfigurations(Arrays.asList(staticConf1, staticConf2));

        CombinedServiceDiscovery discovery = (CombinedServiceDiscovery)multiConf.newInstance(context);
        Assert.assertEquals(2, discovery.getDelegates().size());
        Assert.assertEquals(3, discovery.getServices("discovery1").size());
        Assert.assertEquals(1, discovery.getServices("discovery2").size());
    }

    @Test
    public void testCombinedServiceDiscoveryConfigurationDsl() throws Exception {
        CombinedServiceCallServiceDiscoveryConfiguration multiConf = new CombinedServiceCallServiceDiscoveryConfiguration();
        multiConf.staticServiceDiscovery().setServers(Arrays.asList("discovery1@localhost:1111", "discovery1@localhost:1112"));
        multiConf.staticServiceDiscovery().setServers(Arrays.asList("discovery1@localhost:1113", "discovery2@localhost:1114"));

        CombinedServiceDiscovery discovery = (CombinedServiceDiscovery)multiConf.newInstance(context);
        Assert.assertEquals(2, discovery.getDelegates().size());
        Assert.assertEquals(3, discovery.getServices("discovery1").size());
        Assert.assertEquals(1, discovery.getServices("discovery2").size());
    }

    @Test
    public void testCombinedServiceDiscoveryConfigurationWithPlaceholders() throws Exception {
        System.setProperty("svc-list-1", "discovery1@localhost:1111,discovery1@localhost:1112");
        System.setProperty("svc-list-2", "discovery1@localhost:1113,discovery2@localhost:1114");

        CombinedServiceCallServiceDiscoveryConfiguration multiConf = new CombinedServiceCallServiceDiscoveryConfiguration();
        multiConf.staticServiceDiscovery().servers("{{svc-list-1}}");
        multiConf.staticServiceDiscovery().servers("{{svc-list-2}}");

        CombinedServiceDiscovery discovery = (CombinedServiceDiscovery)multiConf.newInstance(context);
        Assert.assertEquals(2, discovery.getDelegates().size());
        Assert.assertEquals(3, discovery.getServices("discovery1").size());
        Assert.assertEquals(1, discovery.getServices("discovery2").size());
    }
}
