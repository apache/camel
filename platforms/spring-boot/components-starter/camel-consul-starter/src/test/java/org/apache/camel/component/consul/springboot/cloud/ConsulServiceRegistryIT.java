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
package org.apache.camel.component.consul.springboot.cloud;

import java.util.List;
import java.util.UUID;

import com.orbitz.consul.Consul;
import com.orbitz.consul.model.catalog.CatalogService;
import org.apache.camel.CamelContext;
import org.apache.camel.cloud.ServiceRegistry;
import org.apache.camel.component.consul.springboot.cloud.support.ConsulContainerSupport;
import org.apache.camel.impl.cloud.DefaultServiceDefinition;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.SocketUtils;
import org.testcontainers.containers.GenericContainer;

import static org.assertj.core.api.Assertions.assertThat;

public class ConsulServiceRegistryIT {
    protected static final String SERVICE_ID = UUID.randomUUID().toString();
    protected static final String SERVICE_NAME = "my-service";
    protected static final String SERVICE_HOST = "localhost";
    protected static final int SERVICE_PORT = SocketUtils.findAvailableTcpPort();

    @Rule
    public GenericContainer container = ConsulContainerSupport.consulContainer();

    @Test
    public void testServiceRegistry() {
        new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                "debug=false",
                "spring.main.banner-mode=OFF",
                "spring.application.name=" + UUID.randomUUID().toString(),
                "camel.component.consul.service-registry.enabled=true",
                "camel.component.consul.service-registry.url=" + ConsulContainerSupport.consulUrl(container),
                "camel.component.consul.service-registry.id=" + UUID.randomUUID().toString(),
                "camel.component.consul.service-registry.service-host=localhost")
            .run(
                context -> {
                    assertThat(context).hasSingleBean(CamelContext.class);
                    assertThat(context).hasSingleBean(ServiceRegistry.class);

                    final CamelContext camelContext =  context.getBean(CamelContext.class);
                    final ServiceRegistry serviceRegistry = camelContext.hasService(ServiceRegistry.class);

                    assertThat(serviceRegistry).isNotNull();

                    serviceRegistry.register(
                        DefaultServiceDefinition.builder()
                            .withHost(SERVICE_HOST)
                            .withPort(SERVICE_PORT)
                            .withName(SERVICE_NAME)
                            .withId(SERVICE_ID)
                            .build()
                    );

                    final Consul client = Consul.builder().withUrl(ConsulContainerSupport.consulUrl(container)).build();
                    final List<CatalogService> services = client.catalogClient().getService(SERVICE_NAME).getResponse();

                    assertThat(services).hasSize(1);
                    assertThat(services).first().hasFieldOrPropertyWithValue("serviceId", SERVICE_ID);
                    assertThat(services).first().hasFieldOrPropertyWithValue("serviceName", SERVICE_NAME);
                    assertThat(services).first().hasFieldOrPropertyWithValue("serviceAddress", SERVICE_HOST);
                    assertThat(services).first().hasFieldOrPropertyWithValue("servicePort", SERVICE_PORT);
                }
            );
    }

    // *************************************
    // Config
    // *************************************

    @EnableAutoConfiguration
    @Configuration
    public static class TestConfiguration {
    }
}
