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
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HazelcastConfigurationTest {
    @AfterEach
    public void tearDown() {
        Hazelcast.shutdownAll();
    }

    @Test
    void testNamedInstance() {
        DefaultCamelContext context = null;

        try (AvailablePortFinder.Port port = AvailablePortFinder.find()) {
            String instanceName = UUID.randomUUID().toString();
            Config config = new Config();
            config.setInstanceName(instanceName);
            config.getNetworkConfig().setPort(port.getPort());
            config.getNetworkConfig().getJoin().getAwsConfig().setEnabled(false);
            config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(true);
            config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);

            Hazelcast.newHazelcastInstance(config);

            context = new DefaultCamelContext();
            context.start();

            HazelcastDefaultEndpoint endpoint1
                    = getHzEndpoint(context, "hazelcast-map:my-cache-1?hazelcastInstanceName=" + instanceName);
            HazelcastDefaultEndpoint endpoint2
                    = getHzEndpoint(context, "hazelcast-map:my-cache-2?hazelcastInstanceName=" + instanceName);

            assertNotNull(endpoint1.getHazelcastInstance());
            assertNotNull(endpoint2.getHazelcastInstance());
            assertSame(endpoint1.getHazelcastInstance(), endpoint2.getHazelcastInstance());

            HazelcastMapComponent component = context.getComponent("hazelcast-map", HazelcastMapComponent.class);
            assertNull(component.getHazelcastInstance());

            for (HazelcastDefaultEndpoint endpoint : Arrays.asList(endpoint1, endpoint2)) {
                HazelcastInstance hz = endpoint.getHazelcastInstance();
                assertEquals(instanceName, hz.getName());
                assertFalse(hz.getConfig().getNetworkConfig().getJoin().getAwsConfig().isEnabled());
                assertTrue(hz.getConfig().getNetworkConfig().getJoin().getMulticastConfig().isEnabled());
                assertFalse(hz.getConfig().getNetworkConfig().getJoin().getTcpIpConfig().isEnabled());
                assertEquals(port.getPort(), hz.getConfig().getNetworkConfig().getPort());
            }
        } finally {
            if (context != null) {
                context.stop();
            }
        }
    }

    @Test
    void testDefaultConfiguration() {
        DefaultCamelContext context = null;

        try {
            context = new DefaultCamelContext();
            context.start();

            HazelcastDefaultEndpoint endpoint1 = getHzEndpoint(context, "hazelcast-map:my-cache-1");
            HazelcastDefaultEndpoint endpoint2 = getHzEndpoint(context, "hazelcast-map:my-cache-2");

            assertNotNull(endpoint1.getHazelcastInstance());
            assertNotNull(endpoint2.getHazelcastInstance());
            assertNotSame(endpoint1.getHazelcastInstance(), endpoint2.getHazelcastInstance());

            HazelcastMapComponent component = context.getComponent("hazelcast-map", HazelcastMapComponent.class);
            assertNull(component.getHazelcastInstance());

            for (HazelcastDefaultEndpoint endpoint : Arrays.asList(endpoint1, endpoint2)) {
                HazelcastInstance hz = endpoint.getHazelcastInstance();
                assertFalse(hz.getConfig().getNetworkConfig().getJoin().getAwsConfig().isEnabled());
                assertFalse(hz.getConfig().getNetworkConfig().getJoin().getMulticastConfig().isEnabled());
                assertFalse(hz.getConfig().getNetworkConfig().getJoin().getTcpIpConfig().isEnabled());
                assertEquals(5701, hz.getConfig().getNetworkConfig().getPort());
            }
        } finally {
            if (context != null) {
                context.stop();
            }
        }
    }

    @Test
    void testNamedInstanceWithConfigurationUri() {
        DefaultCamelContext context = null;

        try {
            context = new DefaultCamelContext();
            context.start();

            HazelcastDefaultEndpoint endpoint1
                    = getHzEndpoint(context, "hazelcast-map:my-cache-1?hazelcastConfigUri=classpath:hazelcast-named.xml");
            HazelcastDefaultEndpoint endpoint2
                    = getHzEndpoint(context, "hazelcast-map:my-cache-2?hazelcastConfigUri=classpath:hazelcast-named.xml");

            assertNotNull(endpoint1.getHazelcastInstance());
            assertNotNull(endpoint2.getHazelcastInstance());
            assertSame(endpoint1.getHazelcastInstance(), endpoint2.getHazelcastInstance());

            HazelcastMapComponent component = context.getComponent("hazelcast-map", HazelcastMapComponent.class);
            assertNull(component.getHazelcastInstance());

            HazelcastInstance hz = endpoint1.getHazelcastInstance();
            assertFalse(hz.getConfig().getNetworkConfig().getJoin().getAwsConfig().isEnabled());
            assertFalse(hz.getConfig().getNetworkConfig().getJoin().getMulticastConfig().isEnabled());
            assertFalse(hz.getConfig().getNetworkConfig().getJoin().getTcpIpConfig().isEnabled());
            assertEquals(9876, hz.getConfig().getNetworkConfig().getPort());

        } finally {
            if (context != null) {
                context.stop();
            }
        }
    }

    @Test
    void testCustomConfigurationUri() {
        DefaultCamelContext context = null;

        try {
            context = new DefaultCamelContext();
            context.start();

            HazelcastDefaultEndpoint endpoint1
                    = getHzEndpoint(context, "hazelcast-map:my-cache-1?hazelcastConfigUri=classpath:hazelcast-custom.xml");
            HazelcastDefaultEndpoint endpoint2
                    = getHzEndpoint(context, "hazelcast-map:my-cache-2?hazelcastConfigUri=classpath:hazelcast-custom.xml");

            assertNotNull(endpoint1.getHazelcastInstance());
            assertNotNull(endpoint2.getHazelcastInstance());
            assertNotSame(endpoint1.getHazelcastInstance(), endpoint2.getHazelcastInstance());

            HazelcastMapComponent component = context.getComponent("hazelcast-map", HazelcastMapComponent.class);
            assertNull(component.getHazelcastInstance());

            for (HazelcastDefaultEndpoint endpoint : Arrays.asList(endpoint1, endpoint2)) {
                HazelcastInstance hz = endpoint.getHazelcastInstance();
                assertFalse(hz.getConfig().getNetworkConfig().getJoin().getAwsConfig().isEnabled());
                assertFalse(hz.getConfig().getNetworkConfig().getJoin().getMulticastConfig().isEnabled());
                assertFalse(hz.getConfig().getNetworkConfig().getJoin().getTcpIpConfig().isEnabled());
                assertEquals(9876, hz.getConfig().getNetworkConfig().getPort());
            }

        } finally {
            if (context != null) {
                context.stop();
            }
        }
    }

    @Test
    void testCustomConfigurationReference() {
        DefaultCamelContext context = null;

        try (AvailablePortFinder.Port port = AvailablePortFinder.find()) {
            Config config = new Config();
            config.getNetworkConfig().setPort(port.getPort());
            config.getNetworkConfig().getJoin().getAwsConfig().setEnabled(false);
            config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(true);
            config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);

            SimpleRegistry reg = new SimpleRegistry();
            reg.bind("my-config", config);

            context = new DefaultCamelContext(reg);
            context.start();
            context.getEndpoint("hazelcast-map:my-cache?hazelcastConfig=#my-config");

            HazelcastDefaultEndpoint endpoint = getHzEndpoint(context, "hazelcast-map:my-cache?hazelcastConfig=#my-config");
            assertNotNull(endpoint.getHazelcastInstance());

            HazelcastMapComponent component = context.getComponent("hazelcast-map", HazelcastMapComponent.class);
            assertNull(component.getHazelcastInstance());

            HazelcastInstance hz = endpoint.getHazelcastInstance();
            assertFalse(hz.getConfig().getNetworkConfig().getJoin().getAwsConfig().isEnabled());
            assertTrue(hz.getConfig().getNetworkConfig().getJoin().getMulticastConfig().isEnabled());
            assertFalse(hz.getConfig().getNetworkConfig().getJoin().getTcpIpConfig().isEnabled());
            assertEquals(port.getPort(), hz.getConfig().getNetworkConfig().getPort());

        } finally {
            if (context != null) {
                context.stop();
            }
        }
    }

    @Test
    void testMix() {
        DefaultCamelContext context = null;

        try (AvailablePortFinder.Port port1 = AvailablePortFinder.find();
             AvailablePortFinder.Port port2 = AvailablePortFinder.find();
             AvailablePortFinder.Port port3 = AvailablePortFinder.find();
             AvailablePortFinder.Port port4 = AvailablePortFinder.find()) {
            String instanceName = UUID.randomUUID().toString();

            Config namedConfig = new Config();
            namedConfig.setInstanceName("named-" + instanceName);
            namedConfig.getMetricsConfig().setEnabled(false);
            namedConfig.getNetworkConfig().setPort(port1.getPort());
            namedConfig.getNetworkConfig().getJoin().getAutoDetectionConfig().setEnabled(false);

            Config customConfig = new Config();
            customConfig.setInstanceName("custom-" + instanceName);
            customConfig.getMetricsConfig().setEnabled(false);
            customConfig.getNetworkConfig().setPort(port2.getPort());
            customConfig.getNetworkConfig().getJoin().getAutoDetectionConfig().setEnabled(false);

            Config sharedConfig = new Config();
            sharedConfig.setInstanceName("shared-" + instanceName);
            sharedConfig.getMetricsConfig().setEnabled(false);
            sharedConfig.getNetworkConfig().setPort(port3.getPort());
            sharedConfig.getNetworkConfig().getJoin().getAutoDetectionConfig().setEnabled(false);

            Config componentConfig = new Config();
            componentConfig.setInstanceName("component-" + instanceName);
            componentConfig.getMetricsConfig().setEnabled(false);
            componentConfig.getNetworkConfig().setPort(port4.getPort());
            componentConfig.getNetworkConfig().getJoin().getAutoDetectionConfig().setEnabled(false);

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

            HazelcastDefaultEndpoint endpoint1
                    = getHzEndpoint(context, "hazelcast-map:my-cache-1?hazelcastInstanceName=" + namedConfig.getInstanceName());
            HazelcastDefaultEndpoint endpoint2
                    = getHzEndpoint(context, "hazelcast-map:my-cache-2?hazelcastConfig=#" + customConfig.getInstanceName());
            HazelcastDefaultEndpoint endpoint3
                    = getHzEndpoint(context, "hazelcast-map:my-cache-2?hazelcastInstance=#" + sharedConfig.getInstanceName());
            HazelcastDefaultEndpoint endpoint4 = getHzEndpoint(context, "hazelcast-map:my-cache-4");

            assertNotNull(endpoint1.getHazelcastInstance());
            assertNotNull(endpoint2.getHazelcastInstance());
            assertNotNull(endpoint3.getHazelcastInstance());
            assertNotNull(endpoint4.getHazelcastInstance());

            assertEquals(4, Hazelcast.getAllHazelcastInstances().size());

            assertSame(hzNamed, endpoint1.getHazelcastInstance());
            assertSame(Hazelcast.getHazelcastInstanceByName(customConfig.getInstanceName()), endpoint2.getHazelcastInstance());
            assertSame(hzShared, endpoint3.getHazelcastInstance());
            assertSame(hzComponent, endpoint4.getHazelcastInstance());

            assertNotNull(component.getHazelcastInstance());
            assertSame(hzComponent, component.getHazelcastInstance());

            context.stop();

            assertEquals(3, Hazelcast.getAllHazelcastInstances().size());

        } finally {
            if (context != null && context.isStarted()) {
                context.stop();
            }
        }
    }

    private HazelcastDefaultEndpoint getHzEndpoint(CamelContext context, String uri) {
        return (HazelcastDefaultEndpoint) context.getEndpoint(uri);
    }
}
