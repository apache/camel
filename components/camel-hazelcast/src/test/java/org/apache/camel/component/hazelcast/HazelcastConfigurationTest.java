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
package org.apache.camel.component.hazelcast;

import java.util.Arrays;
import java.util.UUID;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.CamelContext;
import org.apache.camel.component.hazelcast.map.HazelcastMapComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.SimpleRegistry;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class HazelcastConfigurationTest {
    @After
    public void tearDown() throws Exception {
        Hazelcast.shutdownAll();
    }

    @Test
    public void testNamedInstance() throws Exception {
        DefaultCamelContext context = null;

        try {
            String instanceName = UUID.randomUUID().toString();
            Config config = new Config();
            config.setInstanceName(instanceName);
            config.getNetworkConfig().setPort(6789);
            config.getNetworkConfig().getJoin().getAwsConfig().setEnabled(false);
            config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(true);
            config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);

            Hazelcast.newHazelcastInstance(config);

            context = new DefaultCamelContext();
            context.start();

            HazelcastDefaultEndpoint endpoint1 = getHzEndpoint(context, "hazelcast-map:my-cache-1?hazelcastInstanceName=" + instanceName);
            HazelcastDefaultEndpoint endpoint2 = getHzEndpoint(context, "hazelcast-map:my-cache-2?hazelcastInstanceName=" + instanceName);

            Assert.assertNotNull(endpoint1.getHazelcastInstance());
            Assert.assertNotNull(endpoint2.getHazelcastInstance());
            Assert.assertTrue(endpoint1.getHazelcastInstance() == endpoint2.getHazelcastInstance());

            HazelcastMapComponent component = context.getComponent("hazelcast-map", HazelcastMapComponent.class);
            Assert.assertNull(component.getHazelcastInstance());

            for (HazelcastDefaultEndpoint endpoint : Arrays.asList(endpoint1, endpoint2)) {
                HazelcastInstance hz = endpoint.getHazelcastInstance();
                Assert.assertEquals(instanceName, hz.getName());
                Assert.assertFalse(hz.getConfig().getNetworkConfig().getJoin().getAwsConfig().isEnabled());
                Assert.assertTrue(hz.getConfig().getNetworkConfig().getJoin().getMulticastConfig().isEnabled());
                Assert.assertFalse(hz.getConfig().getNetworkConfig().getJoin().getTcpIpConfig().isEnabled());
                Assert.assertEquals(6789, hz.getConfig().getNetworkConfig().getPort());
            }
        } finally {
            if (context != null) {
                context.stop();
            }
        }
    }

    @Test
    public void testDefaultConfiguration() throws Exception {
        DefaultCamelContext context = null;

        try {
            context = new DefaultCamelContext();
            context.start();

            HazelcastDefaultEndpoint endpoint1 = getHzEndpoint(context, "hazelcast-map:my-cache-1");
            HazelcastDefaultEndpoint endpoint2 = getHzEndpoint(context, "hazelcast-map:my-cache-2");

            Assert.assertNotNull(endpoint1.getHazelcastInstance());
            Assert.assertNotNull(endpoint2.getHazelcastInstance());
            Assert.assertTrue(endpoint1.getHazelcastInstance() != endpoint2.getHazelcastInstance());

            HazelcastMapComponent component = context.getComponent("hazelcast-map", HazelcastMapComponent.class);
            Assert.assertNull(component.getHazelcastInstance());

            for (HazelcastDefaultEndpoint endpoint : Arrays.asList(endpoint1, endpoint2)) {
                HazelcastInstance hz = endpoint.getHazelcastInstance();
                Assert.assertFalse(hz.getConfig().getNetworkConfig().getJoin().getAwsConfig().isEnabled());
                Assert.assertFalse(hz.getConfig().getNetworkConfig().getJoin().getMulticastConfig().isEnabled());
                Assert.assertTrue(hz.getConfig().getNetworkConfig().getJoin().getTcpIpConfig().isEnabled());
                Assert.assertEquals(5701, hz.getConfig().getNetworkConfig().getPort());
            }
        } finally {
            if (context != null) {
                context.stop();
            }
        }
    }

    @Test
    public void testNamedInstanceWithConfigurationUri() throws Exception {
        DefaultCamelContext context = null;

        try {
            context = new DefaultCamelContext();
            context.start();

            HazelcastDefaultEndpoint endpoint1 = getHzEndpoint(context, "hazelcast-map:my-cache-1?hazelcastConfigUri=classpath:hazelcast-named.xml");
            HazelcastDefaultEndpoint endpoint2 = getHzEndpoint(context, "hazelcast-map:my-cache-2?hazelcastConfigUri=classpath:hazelcast-named.xml");

            Assert.assertNotNull(endpoint1.getHazelcastInstance());
            Assert.assertNotNull(endpoint2.getHazelcastInstance());
            Assert.assertTrue(endpoint1.getHazelcastInstance() == endpoint2.getHazelcastInstance());

            HazelcastMapComponent component = context.getComponent("hazelcast-map", HazelcastMapComponent.class);
            Assert.assertNull(component.getHazelcastInstance());

            HazelcastInstance hz = endpoint1.getHazelcastInstance();
            Assert.assertFalse(hz.getConfig().getNetworkConfig().getJoin().getAwsConfig().isEnabled());
            Assert.assertFalse(hz.getConfig().getNetworkConfig().getJoin().getMulticastConfig().isEnabled());
            Assert.assertFalse(hz.getConfig().getNetworkConfig().getJoin().getTcpIpConfig().isEnabled());
            Assert.assertEquals(9876, hz.getConfig().getNetworkConfig().getPort());

        } finally {
            if (context != null) {
                context.stop();
            }
        }
    }

    @Test
    public void testCustomConfigurationUri() throws Exception {
        DefaultCamelContext context = null;

        try {
            context = new DefaultCamelContext();
            context.start();

            HazelcastDefaultEndpoint endpoint1 = getHzEndpoint(context, "hazelcast-map:my-cache-1?hazelcastConfigUri=classpath:hazelcast-custom.xml");
            HazelcastDefaultEndpoint endpoint2 = getHzEndpoint(context, "hazelcast-map:my-cache-2?hazelcastConfigUri=classpath:hazelcast-custom.xml");

            Assert.assertNotNull(endpoint1.getHazelcastInstance());
            Assert.assertNotNull(endpoint2.getHazelcastInstance());
            Assert.assertTrue(endpoint1.getHazelcastInstance() != endpoint2.getHazelcastInstance());

            HazelcastMapComponent component = context.getComponent("hazelcast-map", HazelcastMapComponent.class);
            Assert.assertNull(component.getHazelcastInstance());

            for (HazelcastDefaultEndpoint endpoint : Arrays.asList(endpoint1, endpoint2)) {
                HazelcastInstance hz = endpoint.getHazelcastInstance();
                Assert.assertFalse(hz.getConfig().getNetworkConfig().getJoin().getAwsConfig().isEnabled());
                Assert.assertFalse(hz.getConfig().getNetworkConfig().getJoin().getMulticastConfig().isEnabled());
                Assert.assertFalse(hz.getConfig().getNetworkConfig().getJoin().getTcpIpConfig().isEnabled());
                Assert.assertEquals(9876, hz.getConfig().getNetworkConfig().getPort());
            }

        } finally {
            if (context != null) {
                context.stop();
            }
        }
    }

    @Test
    public void testCustomConfigurationReference() throws Exception {
        DefaultCamelContext context = null;

        try {
            Config config = new Config();
            config.getNetworkConfig().setPort(6789);
            config.getNetworkConfig().getJoin().getAwsConfig().setEnabled(false);
            config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(true);
            config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);

            SimpleRegistry reg = new SimpleRegistry();
            reg.bind("my-config", config);

            context = new DefaultCamelContext(reg);
            context.start();
            context.getEndpoint("hazelcast-map:my-cache?hazelcastConfig=#my-config");

            HazelcastDefaultEndpoint endpoint = getHzEndpoint(context, "hazelcast-map:my-cache?hazelcastConfig=#my-config");
            Assert.assertNotNull(endpoint.getHazelcastInstance());

            HazelcastMapComponent component = context.getComponent("hazelcast-map", HazelcastMapComponent.class);
            Assert.assertNull(component.getHazelcastInstance());

            HazelcastInstance hz = endpoint.getHazelcastInstance();
            Assert.assertFalse(hz.getConfig().getNetworkConfig().getJoin().getAwsConfig().isEnabled());
            Assert.assertTrue(hz.getConfig().getNetworkConfig().getJoin().getMulticastConfig().isEnabled());
            Assert.assertFalse(hz.getConfig().getNetworkConfig().getJoin().getTcpIpConfig().isEnabled());
            Assert.assertEquals(6789, hz.getConfig().getNetworkConfig().getPort());

        } finally {
            if (context != null) {
                context.stop();
            }
        }
    }

    @Test
    public void testMix() throws Exception {
        DefaultCamelContext context = null;

        try {
            String instanceName = UUID.randomUUID().toString();

            Config namedConfig = new Config();
            namedConfig.setInstanceName("named-" + instanceName);
            namedConfig.getNetworkConfig().setPort(9001);
            namedConfig.getNetworkConfig().getJoin().getAwsConfig().setEnabled(false);
            namedConfig.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(true);
            namedConfig.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);

            Config customConfig = new Config();
            customConfig.setInstanceName("custom-" + instanceName);
            customConfig.getNetworkConfig().setPort(9002);
            customConfig.getNetworkConfig().getJoin().getAwsConfig().setEnabled(false);
            customConfig.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(true);
            customConfig.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);

            Config sharedConfig = new Config();
            sharedConfig.setInstanceName("custom-" + instanceName);
            sharedConfig.getNetworkConfig().setPort(9003);
            sharedConfig.getNetworkConfig().getJoin().getAwsConfig().setEnabled(false);
            sharedConfig.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(true);
            sharedConfig.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);

            Config componentConfig = new Config();
            sharedConfig.setInstanceName("component-" + instanceName);
            sharedConfig.getNetworkConfig().setPort(9004);
            sharedConfig.getNetworkConfig().getJoin().getAwsConfig().setEnabled(false);
            sharedConfig.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(true);
            sharedConfig.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);

            HazelcastInstance hzNamed = Hazelcast.newHazelcastInstance(namedConfig);
            HazelcastInstance hzShared = Hazelcast.newHazelcastInstance(sharedConfig);
            HazelcastInstance hzComponent = Hazelcast.newHazelcastInstance(componentConfig);

            SimpleRegistry reg = new SimpleRegistry();
            reg.bind(customConfig.getInstanceName(), customConfig);
            reg.bind(sharedConfig.getInstanceName(), hzShared);

            HazelcastMapComponent component = new HazelcastMapComponent();
            component.setHazelcastInstance(hzComponent);

            context = new DefaultCamelContext(reg);
            context.addComponent("hazelcast-map", component);
            context.start();

            HazelcastDefaultEndpoint endpoint1 = getHzEndpoint(context, "hazelcast-map:my-cache-1?hazelcastInstanceName=" + namedConfig.getInstanceName());
            HazelcastDefaultEndpoint endpoint2 = getHzEndpoint(context, "hazelcast-map:my-cache-2?hazelcastConfig=#" + customConfig.getInstanceName());
            HazelcastDefaultEndpoint endpoint3 = getHzEndpoint(context, "hazelcast-map:my-cache-2?hazelcastInstance=#" + sharedConfig.getInstanceName());
            HazelcastDefaultEndpoint endpoint4 = getHzEndpoint(context, "hazelcast-map:my-cache-4");

            Assert.assertNotNull(endpoint1.getHazelcastInstance());
            Assert.assertNotNull(endpoint2.getHazelcastInstance());
            Assert.assertNotNull(endpoint3.getHazelcastInstance());
            Assert.assertNotNull(endpoint4.getHazelcastInstance());

            Assert.assertEquals(4, Hazelcast.getAllHazelcastInstances().size());

            Assert.assertTrue(hzNamed == endpoint1.getHazelcastInstance());
            Assert.assertTrue(Hazelcast.getHazelcastInstanceByName(customConfig.getInstanceName()) == endpoint2.getHazelcastInstance());
            Assert.assertTrue(hzShared == endpoint3.getHazelcastInstance());
            Assert.assertTrue(hzComponent == endpoint4.getHazelcastInstance());

            Assert.assertNotNull(component.getHazelcastInstance());
            Assert.assertTrue(hzComponent == component.getHazelcastInstance());

            context.stop();

            Assert.assertEquals(3, Hazelcast.getAllHazelcastInstances().size());

        } finally {
            if (context != null && context.isStarted()) {
                context.stop();
            }
        }
    }

    private HazelcastDefaultEndpoint getHzEndpoint(CamelContext context, String uri) {
        return (HazelcastDefaultEndpoint)context.getEndpoint(uri);
    }
}
