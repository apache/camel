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
package org.apache.camel.component.zookeeper.cloud.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.component.zookeeper.ZooKeeperCuratorConfiguration;
import org.apache.camel.component.zookeeper.ZooKeeperCuratorHelper;
import org.apache.camel.component.zookeeper.cloud.MetaData;
import org.apache.camel.component.zookeeper.cloud.ZooKeeperServiceDiscovery;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.infra.zookeeper.services.ZooKeeperService;
import org.apache.camel.test.infra.zookeeper.services.ZooKeeperServiceFactory;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ZooKeeperServiceDiscoveryIT {
    @RegisterExtension
    static ZooKeeperService service = ZooKeeperServiceFactory.createService();

    @Test
    void testServiceDiscovery() throws Exception {
        try (CuratorFramework curatorFramework = CuratorFrameworkFactory.builder()
                .connectString(service.getConnectionString())
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .build()) {

            ZooKeeperCuratorConfiguration configuration = new ZooKeeperCuratorConfiguration();
            configuration.setBasePath("/camel");
            configuration.setCuratorFramework(curatorFramework);

            try (ServiceDiscovery<MetaData> zkDiscovery
                    = ZooKeeperCuratorHelper.createServiceDiscovery(
                            configuration,
                            curatorFramework,
                            MetaData.class)) {

                curatorFramework.start();
                zkDiscovery.start();

                List<ServiceInstance<MetaData>> instances = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    ServiceInstance<MetaData> instance
                            = ServiceInstance.<MetaData> builder()
                                    .address("127.0.0.1")
                                    .port(AvailablePortFinder.getNextRandomAvailable())
                                    .name("my-service")
                                    .id("service-" + i)
                                    .build();

                    zkDiscovery.registerService(instance);
                    instances.add(instance);
                }

                try (ZooKeeperServiceDiscovery discovery = new ZooKeeperServiceDiscovery(configuration)) {
                    discovery.start();

                    await().atMost(1, TimeUnit.MINUTES).untilAsserted(
                            () -> assertEquals(3, discovery.getServices("my-service").size()));
                    List<ServiceDefinition> services = discovery.getServices("my-service");
                    assertNotNull(services);
                    assertEquals(3, services.size());

                    for (ServiceDefinition service : services) {
                        assertEquals(
                                1,
                                instances.stream()
                                        .filter(
                                                i -> i.getPort() == service.getPort()
                                                        && i.getAddress().equals(service.getHost())
                                                        && i.getId().equals(
                                                                service.getMetadata().get(ServiceDefinition.SERVICE_META_ID))
                                                        && i.getName().equals(service.getName()))
                                        .count());
                    }
                }
            }
        }
    }
}
