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

import org.apache.camel.CamelContext;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.zookeeper.cloud.MetaData;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.test.AvailablePortFinderPropertiesFunction;
import org.apache.camel.test.infra.zookeeper.services.ZooKeeperService;
import org.apache.camel.test.infra.zookeeper.services.ZooKeeperServiceFactory;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
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
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringZooKeeperServiceCallRouteIT extends CamelSpringTestSupport {
    @RegisterExtension
    static ZooKeeperService service = ZooKeeperServiceFactory.createService();

    private static final String SERVICE_NAME = "http-service";
    private static final String SERVICE_PATH = "/camel";

    private CuratorFramework curator;
    private ServiceDiscovery<MetaData> discovery;
    private AvailablePortFinderPropertiesFunction function;

    // ***********************
    // Setup / tear down
    // ***********************

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final CamelContext context = super.createCamelContext();
        final PropertiesComponent pc = context.getPropertiesComponent();

        pc.addPropertiesFunction(function);

        return context;
    }

    @Override
    public void doPreSetup() throws Exception {
        super.doPreSetup();

        function = new AvailablePortFinderPropertiesFunction();

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

        discovery.registerService(
                ServiceInstance.<MetaData> builder()
                        .address("127.0.0.1")
                        .port(Integer.parseInt(function.apply("service-1")))
                        .name(SERVICE_NAME)
                        .id("service-1")
                        .build());

        discovery.registerService(
                ServiceInstance.<MetaData> builder()
                        .address("127.0.0.1")
                        .port(Integer.parseInt(function.apply("service-2")))
                        .name(SERVICE_NAME)
                        .id("service-2")
                        .build());

        discovery.registerService(
                ServiceInstance.<MetaData> builder()
                        .address("127.0.0.1")
                        .port(Integer.parseInt(function.apply("service-3")))
                        .name(SERVICE_NAME)
                        .id("service-3")
                        .build());
    }

    @Override
    public void doPostTearDown() throws Exception {
        super.doPostTearDown();

        CloseableUtils.closeQuietly(discovery);
        CloseableUtils.closeQuietly(curator);
    }

    // ***********************
    // Test
    // ***********************

    @Test
    public void testServiceCall() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(3);
        getMockEndpoint("mock:result").expectedBodiesReceivedInAnyOrder("ping svc1", "ping svc2", "ping svc3");

        template.sendBody("direct:start", "ping");
        template.sendBody("direct:start", "ping");
        template.sendBody("direct:start", "ping");

        MockEndpoint.assertIsSatisfied(context);
    }

    // ***********************
    // Routes
    // ***********************

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                "org/apache/camel/component/zookeeper/cloud/SpringZooKeeperServiceCallRouteTest.xml");
    }
}
