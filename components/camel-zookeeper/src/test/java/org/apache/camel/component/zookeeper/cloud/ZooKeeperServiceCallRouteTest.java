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

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
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
import org.junit.Test;

public class ZooKeeperServiceCallRouteTest extends CamelTestSupport {
    private static final int SERVER_PORT = AvailablePortFinder.getNextAvailable();
    private static final String SERVICE_NAME = "http-service";
    private static final int SERVICE_COUNT = 5;
    private static final String SERVICE_PATH = "/camel";

    protected ZooKeeperContainer container;
    private CuratorFramework curator;
    private ServiceDiscovery<ZooKeeperServiceDiscovery.MetaData> discovery;
    private List<ServiceInstance<ZooKeeperServiceDiscovery.MetaData>> instances;
    private List<String> expectedBodies;

    // *************************************************************************
    // Setup / tear down
    // *************************************************************************

    @Override
    protected void doPreSetup() throws Exception {
        super.doPreSetup();

        container = new ZooKeeperContainer();
        container.start();

        curator = CuratorFrameworkFactory.builder()
            .connectString(container.getConnectionString())
            .retryPolicy(new ExponentialBackoffRetry(1000, 3))
            .build();

        discovery = ServiceDiscoveryBuilder.builder(ZooKeeperServiceDiscovery.MetaData.class)
            .client(curator)
            .basePath(SERVICE_PATH)
            .serializer(new JsonInstanceSerializer<>(ZooKeeperServiceDiscovery.MetaData.class))
            .build();

        curator.start();
        discovery.start();

        instances = new ArrayList<>(SERVICE_COUNT);
        expectedBodies = new ArrayList<>(SERVICE_COUNT);

        for (int i = 0; i < SERVICE_COUNT; i++) {
            ServiceInstance<ZooKeeperServiceDiscovery.MetaData> instance = ServiceInstance.<ZooKeeperServiceDiscovery.MetaData>builder()
                .address("127.0.0.1")
                .port(AvailablePortFinder.getNextAvailable())
                .name(SERVICE_NAME)
                .id("service-" + i)
                .build();

            discovery.registerService(instance);
            instances.add(instance);
            expectedBodies.add("ping on " + instance.getPort());
        }
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        for (ServiceInstance<ZooKeeperServiceDiscovery.MetaData> instace : instances) {
            try {
                discovery.unregisterService(instace);
            } catch (Exception e) {
                // Ignore
            }
        }

        CloseableUtils.closeQuietly(discovery);
        CloseableUtils.closeQuietly(curator);

        if (container != null) {
            container.stop();
        }
    }

    // *************************************************************************
    // Test
    // *************************************************************************

    @Test
    public void testServiceCall() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(SERVICE_COUNT);
        getMockEndpoint("mock:result").expectedBodiesReceivedInAnyOrder(expectedBodies);

        instances.forEach(r -> template.sendBody("direct:start", "ping"));

        assertMockEndpointsSatisfied();
    }

    // *************************************************************************
    // Route
    // *************************************************************************

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .serviceCall()
                        .name(SERVICE_NAME)
                        .component("http")
                        .defaultLoadBalancer()
                        .zookeeperServiceDiscovery(container.getConnectionString(), SERVICE_PATH)
                        .end()
                    .to("log:org.apache.camel.component.zookeeper.cloud?level=INFO&showAll=true&multiline=true")
                    .to("mock:result");

                instances.forEach(r ->
                    fromF("jetty:http://%s:%d", r.getAddress(), r.getPort())
                        .transform().simple("${in.body} on " + r.getPort())
                );
            }
        };
    }
}

