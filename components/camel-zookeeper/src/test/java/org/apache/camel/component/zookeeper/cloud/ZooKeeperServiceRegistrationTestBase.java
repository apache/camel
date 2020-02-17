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
package org.apache.camel.component.zookeeper.cloud;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import org.apache.camel.CamelContext;
import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.component.zookeeper.ZooKeeperContainer;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.utils.CloseableUtils;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.details.JsonInstanceSerializer;
import org.junit.After;
import org.junit.Test;

public abstract class ZooKeeperServiceRegistrationTestBase extends CamelTestSupport {
    protected static final String SERVICE_ID = UUID.randomUUID().toString();
    protected static final String SERVICE_NAME = "my-service";
    protected static final String SERVICE_HOST = "localhost";
    protected static final String SERVICE_PATH = "/camel";
    protected static final int SERVICE_PORT = AvailablePortFinder.getNextAvailable();

    protected ZooKeeperContainer container;
    protected CuratorFramework curator;
    protected ServiceDiscovery<ZooKeeperServiceRegistry.MetaData> discovery;

    // ***********************
    // Lifecycle
    // ***********************

    @Override
    protected void doPreSetup() throws Exception {
        super.doPreSetup();

        container = new ZooKeeperContainer();
        container.start();

        curator = CuratorFrameworkFactory.builder()
            .connectString(container.getConnectionString())
            .retryPolicy(new ExponentialBackoffRetry(1000, 3))
            .build();

        discovery = ServiceDiscoveryBuilder.builder(ZooKeeperServiceRegistry.MetaData.class)
            .client(curator)
            .basePath(SERVICE_PATH)
            .serializer(new JsonInstanceSerializer<>(ZooKeeperServiceRegistry.MetaData.class))
            .build();

        curator.start();
        discovery.start();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();

        CloseableUtils.closeQuietly(discovery);
        CloseableUtils.closeQuietly(curator);

        if (container != null) {
            container.stop();
        }
    }


    protected Map<String, String> getMetadata() {
        return Collections.emptyMap();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final CamelContext context = super.createCamelContext();

        ZooKeeperServiceRegistry registry = new ZooKeeperServiceRegistry();
        registry.setId(context.getUuidGenerator().generateUuid());
        registry.setCamelContext(context());
        registry.setNodes(container.getConnectionString());
        registry.setBasePath(SERVICE_PATH);
        registry.setServiceHost(SERVICE_HOST);
        registry.setOverrideServiceHost(true);

        context.addService(registry, true, false);

        return context;
    }

    @Test
    public void testRegistrationFromRoute() throws Exception {

        // the service should not be registered as the route is not running
        assertTrue(discovery.queryForInstances(SERVICE_NAME).isEmpty());

        // let start the route
        context().getRouteController().startRoute(SERVICE_ID);

        // check that service has been registered
        Collection<ServiceInstance<ZooKeeperServiceRegistry.MetaData>> services = discovery.queryForInstances(SERVICE_NAME);
        assertEquals(1, services.size());

        ServiceInstance<ZooKeeperServiceRegistry.MetaData> instance = services.iterator().next();
        assertEquals(SERVICE_PORT, (int)instance.getPort());
        assertEquals("localhost", instance.getAddress());
        assertEquals("http", instance.getPayload().get(ServiceDefinition.SERVICE_META_PROTOCOL));
        assertEquals("/service/endpoint", instance.getPayload().get(ServiceDefinition.SERVICE_META_PATH));

        getMetadata().forEach(
            (k, v) -> {
                assertEquals(v, instance.getPayload().get(k));
            }
        );

        // let stop the route
        context().getRouteController().stopRoute(SERVICE_ID);

        // the service should be removed once the route is stopped
        assertTrue(discovery.queryForInstances(SERVICE_NAME).isEmpty());
    }
}
