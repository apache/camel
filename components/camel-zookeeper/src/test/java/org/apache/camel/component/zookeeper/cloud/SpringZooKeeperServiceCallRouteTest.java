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
package org.apache.camel.component.zookeeper.cloud;

import org.apache.camel.component.zookeeper.ZooKeeperTestSupport;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.utils.CloseableUtils;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.details.JsonInstanceSerializer;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringZooKeeperServiceCallRouteTest extends CamelSpringTestSupport {
    private static final int SERVER_PORT = 9001;
    private static final String SERVICE_NAME = "http-service";
    private static final String SERVICE_PATH = "/camel";

    private ZooKeeperTestSupport.TestZookeeperServer server;
    private CuratorFramework curator;
    private ServiceDiscovery<ZooKeeperServiceDiscovery.MetaData> discovery;

    // ***********************
    // Setup / tear down
    // ***********************

    @Override
    public void doPreSetup() throws Exception {
        super.doPreSetup();

        server = new ZooKeeperTestSupport.TestZookeeperServer(SERVER_PORT, true);
        ZooKeeperTestSupport.waitForServerUp("127.0.0.1:" + SERVER_PORT, 1000);

        curator = CuratorFrameworkFactory.builder()
            .connectString("127.0.0.1:" + SERVER_PORT)
            .retryPolicy(new ExponentialBackoffRetry(1000, 3))
            .build();

        discovery = ServiceDiscoveryBuilder.builder(ZooKeeperServiceDiscovery.MetaData.class)
            .client(curator)
            .basePath(SERVICE_PATH)
            .serializer(new JsonInstanceSerializer<>(ZooKeeperServiceDiscovery.MetaData.class))
            .build();

        curator.start();
        discovery.start();

        discovery.registerService(
            ServiceInstance.<ZooKeeperServiceDiscovery.MetaData>builder()
                .address("127.0.0.1")
                .port(9011)
                .name(SERVICE_NAME)
                .id("service-1")
                .build());

        discovery.registerService(
            ServiceInstance.<ZooKeeperServiceDiscovery.MetaData>builder()
                .address("127.0.0.1")
                .port(9012)
                .name(SERVICE_NAME)
                .id("service-2")
                .build());

        discovery.registerService(
            ServiceInstance.<ZooKeeperServiceDiscovery.MetaData>builder()
                .address("127.0.0.1")
                .port(9013)
                .name(SERVICE_NAME)
                .id("service-3")
                .build());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        CloseableUtils.closeQuietly(discovery);
        CloseableUtils.closeQuietly(curator);

        if (server != null) {
            server.shutdown();
        }
    }

    // ***********************
    // Test
    // ***********************

    @Test
    public void testServiceCall() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(3);
        getMockEndpoint("mock:result").expectedBodiesReceivedInAnyOrder("ping 9011", "ping 9012", "ping 9013");

        template.sendBody("direct:start", "ping");
        template.sendBody("direct:start", "ping");
        template.sendBody("direct:start", "ping");

        assertMockEndpointsSatisfied();
    }

    // ***********************
    // Routes
    // ***********************

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/zookeeper/cloud/SpringZooKeeperServiceCallRouteTest.xml");
    }
}
