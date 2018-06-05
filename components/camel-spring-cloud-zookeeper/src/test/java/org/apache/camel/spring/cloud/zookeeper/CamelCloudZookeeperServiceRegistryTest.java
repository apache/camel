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
package org.apache.camel.spring.cloud.zookeeper;

import java.util.Collection;
import java.util.UUID;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cloud.ServiceRegistry;
import org.apache.camel.impl.cloud.DefaultServiceDefinition;
import org.apache.camel.impl.cloud.ServiceRegistrationRoutePolicy;
import org.apache.camel.spring.cloud.zookeeper.support.ZookeeperServer;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceInstance;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.zookeeper.discovery.ZookeeperInstance;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.SocketUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class CamelCloudZookeeperServiceRegistryTest {

    protected static final String SERVICE_ID = UUID.randomUUID().toString();
    protected static final String SERVICE_NAME = "my-service";
    protected static final String SERVICE_HOST = "localhost";
    protected static final int SERVICE_PORT = SocketUtils.findAvailableTcpPort();

    @Rule
    public final TestName testName = new TestName();
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testServiceRegistry() throws Exception {
        final ZookeeperServer server = new ZookeeperServer(temporaryFolder.newFolder(testName.getMethodName()));

        ConfigurableApplicationContext context = new SpringApplicationBuilder(TestConfiguration.class)
            .web(WebApplicationType.NONE)
            .run(
                "--debug=false",
                "--spring.main.banner-mode=OFF",
                "--spring.application.name=" + UUID.randomUUID().toString(),
                "--ribbon.enabled=false",
                "--ribbon.eureka.enabled=false",
                "--management.endpoint.enabled=false",
                "--spring.cloud.zookeeper.enabled=true",
                "--spring.cloud.zookeeper.connect-string=" + server.connectString(),
                "--spring.cloud.zookeeper.config.enabled=false",
                "--spring.cloud.zookeeper.discovery.enabled=true",
                "--spring.cloud.service-registry.auto-registration.enabled=false",
                "--camel.cloud.service-registry.service-host=" + SERVICE_HOST
            );

        try {
            final ServiceDiscovery client = context.getBean(ServiceDiscovery.class);
            final ServiceRegistry registry = context.getBean(ServiceRegistry.class);

            registry.register(
                DefaultServiceDefinition.builder()
                    .withHost(SERVICE_HOST)
                    .withPort(SERVICE_PORT)
                    .withName(SERVICE_NAME)
                    .withId(SERVICE_ID)
                    .build()
            );

            Collection<ServiceInstance<ZookeeperInstance>> services = client.queryForInstances(SERVICE_NAME);
            
            assertThat(services).hasSize(1);
            assertThat(services).first().hasFieldOrPropertyWithValue("address", SERVICE_HOST);
            assertThat(services).first().hasFieldOrPropertyWithValue("port", SERVICE_PORT);
            assertThat(services).first().extracting("payload").first().hasFieldOrPropertyWithValue("id", SERVICE_ID);
            assertThat(services).first().extracting("payload").first().hasFieldOrPropertyWithValue("name", SERVICE_NAME);

        } finally {
            // shutdown spring context
            context.close();

            // shutdown zookeeper
            server.shutdown();
        }
    }

    // *************************************
    // Config
    // *************************************

    @EnableAutoConfiguration
    @Configuration
    public static class TestConfiguration {
        @Bean
        public RouteBuilder routes() {
            return new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    fromF("jetty:http://0.0.0.0:%d/service/endpoint", SERVICE_PORT)
                        .routeId(SERVICE_ID)
                        .routeGroup(SERVICE_NAME)
                        .routePolicy(new ServiceRegistrationRoutePolicy())
                        .noAutoStartup()
                        .to("log:service-registry?level=INFO");
                }
            };
        }
    }

}

