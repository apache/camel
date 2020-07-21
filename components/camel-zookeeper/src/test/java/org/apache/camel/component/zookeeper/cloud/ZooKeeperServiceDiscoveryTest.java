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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.component.zookeeper.ZooKeeperContainer;
import org.apache.camel.component.zookeeper.ZooKeeperCuratorConfiguration;
import org.apache.camel.component.zookeeper.ZooKeeperCuratorHelper;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.utils.CloseableUtils;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceInstance;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ZooKeeperServiceDiscoveryTest {

    @Test
    public void testServiceDiscovery() throws Exception {
        ZooKeeperCuratorConfiguration configuration  = new ZooKeeperCuratorConfiguration();
        ServiceDiscovery<ZooKeeperServiceDiscovery.MetaData> zkDiscovery = null;
        ZooKeeperContainer container = null;

        try {
            container = new ZooKeeperContainer();
            container.start();

            configuration.setBasePath("/camel");
            configuration.setCuratorFramework(CuratorFrameworkFactory.builder()
                .connectString(container.getConnectionString())
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .build()
            );

            zkDiscovery = ZooKeeperCuratorHelper.createServiceDiscovery(
                configuration,
                configuration.getCuratorFramework(),
                ZooKeeperServiceDiscovery.MetaData.class
            );

            configuration.getCuratorFramework().start();
            zkDiscovery.start();

            List<ServiceInstance<ZooKeeperServiceDiscovery.MetaData>> instances = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                ServiceInstance<ZooKeeperServiceDiscovery.MetaData> instance = ServiceInstance.<ZooKeeperServiceDiscovery.MetaData>builder()
                    .address("127.0.0.1")
                    .port(AvailablePortFinder.getNextAvailable())
                    .name("my-service")
                    .id("service-" + i)
                    .build();

                zkDiscovery.registerService(instance);
                instances.add(instance);
            }

            ZooKeeperServiceDiscovery discovery = new ZooKeeperServiceDiscovery(configuration);
            discovery.start();

            List<ServiceDefinition> services = discovery.getServices("my-service");
            assertNotNull(services);
            assertEquals(3, services.size());

            for (ServiceDefinition service : services) {
                Assert.assertEquals(
                    1,
                    instances.stream()
                        .filter(
                            i ->  {
                                return i.getPort() == service.getPort()
                                    && i.getAddress().equals(service.getHost())
                                    && i.getId().equals(service.getMetadata().get(ServiceDefinition.SERVICE_META_ID))
                                    && i.getName().equals(service.getName());
                            }
                        ).count()
                );
            }

        } finally {
            CloseableUtils.closeQuietly(zkDiscovery);
            CloseableUtils.closeQuietly(configuration.getCuratorFramework());

            if (container != null) {
                container.stop();
            }
        }
    }
}
