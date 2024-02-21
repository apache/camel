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

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.zookeeper.cloud.MetaData;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.infra.zookeeper.services.ZooKeeperService;
import org.apache.camel.test.infra.zookeeper.services.ZooKeeperServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.utils.CloseableUtils;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.details.JsonInstanceSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class ZooKeeperServiceCallRouteIT extends CamelTestSupport {
    @RegisterExtension
    static ZooKeeperService service = ZooKeeperServiceFactory.createService();

    private static final String SERVICE_NAME = "http-service";
    private static final int SERVICE_COUNT = 5;
    private static final String SERVICE_PATH = "/camel";

    private CuratorFramework curator;
    private ServiceDiscovery<MetaData> discovery;
    private List<ServiceInstance<MetaData>> instances;
    private List<String> expectedBodies;

    // *************************************************************************
    // Setup / tear down
    // *************************************************************************

    @Override
    protected void doPreSetup() throws Exception {
        super.doPreSetup();

        curator = CuratorFrameworkFactory.builder()
                .connectString(service.getConnectionString())
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .build();

        discovery = ServiceDiscoveryBuilder.builder(MetaData.class)
                .client(curator)
                .basePath(SERVICE_PATH)
                .serializer(new JsonInstanceSerializer<>(MetaData.class))
                .build();

        curator.start();
        discovery.start();

        instances = new ArrayList<>(SERVICE_COUNT);
        expectedBodies = new ArrayList<>(SERVICE_COUNT);

        for (int i = 0; i < SERVICE_COUNT; i++) {
            ServiceInstance<MetaData> instance
                    = ServiceInstance.<MetaData> builder()
                            .address("127.0.0.1")
                            .port(AvailablePortFinder.getNextRandomAvailable())
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

        for (ServiceInstance<MetaData> instace : instances) {
            try {
                discovery.unregisterService(instace);
            } catch (Exception e) {
                // Ignore
            }
        }

        CloseableUtils.closeQuietly(discovery);
        CloseableUtils.closeQuietly(curator);
    }

    // *************************************************************************
    // Test
    // *************************************************************************

    @Test
    public void testServiceCall() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(SERVICE_COUNT);
        getMockEndpoint("mock:result").expectedBodiesReceivedInAnyOrder(expectedBodies);

        instances.forEach(r -> template.sendBody("direct:start", "ping"));

        MockEndpoint.assertIsSatisfied(context);
    }

    // *************************************************************************
    // Route
    // *************************************************************************

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .serviceCall()
                        .name(SERVICE_NAME)
                        .component("http")
                        .defaultLoadBalancer()
                        .zookeeperServiceDiscovery(service.getConnectionString(), SERVICE_PATH)
                        .end()
                        .to("log:org.apache.camel.component.zookeeper.cloud?level=INFO&showAll=true&multiline=true")
                        .to("mock:result");

                instances.forEach(r -> fromF("jetty:http://%s:%d", r.getAddress(), r.getPort())
                        .transform().simple("${in.body} on " + r.getPort()));
            }
        };
    }
}
