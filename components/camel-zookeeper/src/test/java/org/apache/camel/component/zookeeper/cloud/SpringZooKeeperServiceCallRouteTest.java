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

import java.util.Collections;

import org.apache.camel.component.zookeeper.ZooKeeperContainer;
import org.apache.camel.test.testcontainers.ContainerPropertiesFunction;
import org.apache.camel.test.testcontainers.spring.ContainerAwareSpringTestSupport;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.utils.CloseableUtils;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.details.JsonInstanceSerializer;
import org.junit.Test;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.containers.GenericContainer;

public class SpringZooKeeperServiceCallRouteTest extends ContainerAwareSpringTestSupport {
    private static final String SERVICE_NAME = "http-service";
    private static final String SERVICE_PATH = "/camel";

    private CuratorFramework curator;
    private ServiceDiscovery<ZooKeeperServiceDiscovery.MetaData> discovery;

    // ***********************
    // Setup / tear down
    // ***********************

    @Override
    public GenericContainer createContainer() {
        return new ZooKeeperContainer();
    }

    @Override
    public void doPreSetup() throws Exception {
        super.doPreSetup();

        curator = CuratorFrameworkFactory.builder()
            .connectString(getContainerHost(ZooKeeperContainer.CONTAINER_NAME) + ":" + getContainerPort(ZooKeeperContainer.CONTAINER_NAME, ZooKeeperContainer.CLIENT_PORT))
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
        GenericApplicationContext applicationContext = new GenericApplicationContext();
        applicationContext.getBeanFactory().registerSingleton(
            "zkProperties",
            new ContainerPropertiesFunction(Collections.singletonList(getContainer(ZooKeeperContainer.CONTAINER_NAME))));

        XmlBeanDefinitionReader xmlReader = new XmlBeanDefinitionReader(applicationContext);
        xmlReader.loadBeanDefinitions(new ClassPathResource("org/apache/camel/component/zookeeper/cloud/SpringZooKeeperServiceCallRouteTest.xml"));

        applicationContext.refresh();

        return applicationContext;
    }
}
