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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.apache.camel.test.infra.common.TestEntityNameGenerator;
import org.apache.camel.test.infra.hazelcast.services.HazelcastService;
import org.apache.camel.test.infra.hazelcast.services.HazelcastServiceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class HazelcastConfigurationTest {

    @RegisterExtension
    public static HazelcastService hazelcastService = HazelcastServiceFactory.createService();

    @RegisterExtension
    public static TestEntityNameGenerator nameGenerator = new TestEntityNameGenerator();

    @AfterEach
    public void tearDown() {
        Hazelcast.shutdownAll();
    }

    @Test
    void testNamedInstance() {
        DefaultCamelContext context = null;

        try (AvailablePortFinder.Port port = AvailablePortFinder.find()) {
            int portNumber = port.getPort();
            String instanceName = UUID.randomUUID().toString();
            Hazelcast.newHazelcastInstance(
                    hazelcastService.createConfiguration(null, portNumber, instanceName, "configuration"));

            context = new DefaultCamelContext();
            context.start();

            HazelcastDefaultEndpoint endpoint1 =
                    getHzEndpoint(context, "hazelcast-map:my-cache-1?hazelcastInstanceName=" + instanceName);
            HazelcastDefaultEndpoint endpoint2 =
                    getHzEndpoint(context, "hazelcast-map:my-cache-2?hazelcastInstanceName=" + instanceName);

            assertNotNull(endpoint1.getHazelcastInstance());
            assertNotNull(endpoint2.getHazelcastInstance());
            assertSame(endpoint1.getHazelcastInstance(), endpoint2.getHazelcastInstance());

            HazelcastMapComponent component = context.getComponent("hazelcast-map", HazelcastMapComponent.class);
            assertNull(component.getHazelcastInstance());

            for (HazelcastDefaultEndpoint endpoint : Arrays.asList(endpoint1, endpoint2)) {
                HazelcastInstance hz = endpoint.getHazelcastInstance();
                assertEquals(instanceName, hz.getName());
                assertFalse(hz.getConfig()
                        .getNetworkConfig()
                        .getJoin()
                        .getAwsConfig()
                        .isEnabled());
                assertTrue(hz.getConfig()
                        .getNetworkConfig()
                        .getJoin()
                        .getMulticastConfig()
                        .isEnabled());
                assertFalse(hz.getConfig()
                        .getNetworkConfig()
                        .getJoin()
                        .getTcpIpConfig()
                        .isEnabled());
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
                assertFalse(hz.getConfig()
                        .getNetworkConfig()
                        .getJoin()
                        .getAwsConfig()
                        .isEnabled());
                assertFalse(hz.getConfig()
                        .getNetworkConfig()
                        .getJoin()
                        .getMulticastConfig()
                        .isEnabled());
                assertFalse(hz.getConfig()
                        .getNetworkConfig()
                        .getJoin()
                        .getTcpIpConfig()
                        .isEnabled());
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

            HazelcastDefaultEndpoint endpoint1 =
                    getHzEndpoint(context, "hazelcast-map:my-cache-1?hazelcastConfigUri=classpath:hazelcast-named.xml");
            HazelcastDefaultEndpoint endpoint2 =
                    getHzEndpoint(context, "hazelcast-map:my-cache-2?hazelcastConfigUri=classpath:hazelcast-named.xml");

            assertNotNull(endpoint1.getHazelcastInstance());
            assertNotNull(endpoint2.getHazelcastInstance());
            assertSame(endpoint1.getHazelcastInstance(), endpoint2.getHazelcastInstance());

            HazelcastMapComponent component = context.getComponent("hazelcast-map", HazelcastMapComponent.class);
            assertNull(component.getHazelcastInstance());

            HazelcastInstance hz = endpoint1.getHazelcastInstance();
            assertFalse(
                    hz.getConfig().getNetworkConfig().getJoin().getAwsConfig().isEnabled());
            assertFalse(hz.getConfig()
                    .getNetworkConfig()
                    .getJoin()
                    .getMulticastConfig()
                    .isEnabled());
            assertFalse(
                    hz.getConfig().getNetworkConfig().getJoin().getTcpIpConfig().isEnabled());
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

            HazelcastDefaultEndpoint endpoint1 = getHzEndpoint(
                    context, "hazelcast-map:my-cache-1?hazelcastConfigUri=classpath:hazelcast-custom.xml");
            HazelcastDefaultEndpoint endpoint2 = getHzEndpoint(
                    context, "hazelcast-map:my-cache-2?hazelcastConfigUri=classpath:hazelcast-custom.xml");

            assertNotNull(endpoint1.getHazelcastInstance());
            assertNotNull(endpoint2.getHazelcastInstance());
            assertNotSame(endpoint1.getHazelcastInstance(), endpoint2.getHazelcastInstance());

            HazelcastMapComponent component = context.getComponent("hazelcast-map", HazelcastMapComponent.class);
            assertNull(component.getHazelcastInstance());

            for (HazelcastDefaultEndpoint endpoint : Arrays.asList(endpoint1, endpoint2)) {
                HazelcastInstance hz = endpoint.getHazelcastInstance();
                assertFalse(hz.getConfig()
                        .getNetworkConfig()
                        .getJoin()
                        .getAwsConfig()
                        .isEnabled());
                assertFalse(hz.getConfig()
                        .getNetworkConfig()
                        .getJoin()
                        .getMulticastConfig()
                        .isEnabled());
                assertFalse(hz.getConfig()
                        .getNetworkConfig()
                        .getJoin()
                        .getTcpIpConfig()
                        .isEnabled());
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
            int portNumber = port.getPort();

            SimpleRegistry reg = new SimpleRegistry();
            reg.bind("my-config", hazelcastService.createConfiguration(null, portNumber, null, "configuration"));

            context = new DefaultCamelContext(reg);
            context.start();
            context.getEndpoint("hazelcast-map:my-cache?hazelcastConfig=#my-config");

            HazelcastDefaultEndpoint endpoint =
                    getHzEndpoint(context, "hazelcast-map:my-cache?hazelcastConfig=#my-config");
            assertNotNull(endpoint.getHazelcastInstance());

            HazelcastMapComponent component = context.getComponent("hazelcast-map", HazelcastMapComponent.class);
            assertNull(component.getHazelcastInstance());

            HazelcastInstance hz = endpoint.getHazelcastInstance();
            assertFalse(
                    hz.getConfig().getNetworkConfig().getJoin().getAwsConfig().isEnabled());
            assertTrue(hz.getConfig()
                    .getNetworkConfig()
                    .getJoin()
                    .getMulticastConfig()
                    .isEnabled());
            assertFalse(
                    hz.getConfig().getNetworkConfig().getJoin().getTcpIpConfig().isEnabled());
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
            int portNumber1 = port1.getPort();
            int portNumber2 = port2.getPort();
            int portNumber3 = port3.getPort();
            int portNumber4 = port4.getPort();
            String instanceName = UUID.randomUUID().toString();

            Config namedConfig = new Config();
            namedConfig = hazelcastService.createConfiguration("named", portNumber1, instanceName, "configuration");

            Config customConfig = new Config();
            customConfig = hazelcastService.createConfiguration("custom", portNumber2, instanceName, "configuration");

            Config sharedConfig = new Config();
            sharedConfig = hazelcastService.createConfiguration("shared", portNumber3, instanceName, "configuration");

            Config componentConfig = new Config();
            componentConfig =
                    hazelcastService.createConfiguration("component", portNumber4, instanceName, "configuration");

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

            HazelcastDefaultEndpoint endpoint1 = getHzEndpoint(
                    context, "hazelcast-map:my-cache-1?hazelcastInstanceName=" + namedConfig.getInstanceName());
            HazelcastDefaultEndpoint endpoint2 = getHzEndpoint(
                    context, "hazelcast-map:my-cache-2?hazelcastConfig=#" + customConfig.getInstanceName());
            HazelcastDefaultEndpoint endpoint3 = getHzEndpoint(
                    context, "hazelcast-map:my-cache-2?hazelcastInstance=#" + sharedConfig.getInstanceName());
            HazelcastDefaultEndpoint endpoint4 = getHzEndpoint(context, "hazelcast-map:my-cache-4");

            assertNotNull(endpoint1.getHazelcastInstance());
            assertNotNull(endpoint2.getHazelcastInstance());
            assertNotNull(endpoint3.getHazelcastInstance());
            assertNotNull(endpoint4.getHazelcastInstance());

            assertEquals(4, Hazelcast.getAllHazelcastInstances().size());

            assertSame(hzNamed, endpoint1.getHazelcastInstance());
            assertSame(
                    Hazelcast.getHazelcastInstanceByName(customConfig.getInstanceName()),
                    endpoint2.getHazelcastInstance());
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
