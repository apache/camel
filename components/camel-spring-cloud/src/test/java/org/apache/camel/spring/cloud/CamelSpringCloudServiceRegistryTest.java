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
package org.apache.camel.spring.cloud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.impl.cloud.DefaultServiceDefinition;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.apache.camel.spring.boot.cloud.CamelCloudAutoConfiguration;
import org.junit.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;

import static org.assertj.core.api.Assertions.assertThat;

public class CamelSpringCloudServiceRegistryTest {

    // *************************************
    // Test Auto Configuration
    // *************************************

    @Test
    public void testAutoConfiguration() {
        new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    CamelAutoConfiguration.class,
                    CamelCloudAutoConfiguration.class,
                    CamelSpringCloudServiceRegistryAutoConfiguration.class
                ))
            .withUserConfiguration(
                TestConfiguration.class)
            .withPropertyValues(
                "spring.main.banner-mode=off",
                "ribbon.eureka.enabled=false",
                "ribbon.enabled=false")
            .run(
                context -> {
                    // spring cloud registry
                    assertThat(context).hasSingleBean(org.springframework.cloud.client.serviceregistry.ServiceRegistry.class);
                    assertThat(context).getBean(org.springframework.cloud.client.serviceregistry.ServiceRegistry.class).isInstanceOf(MyServiceRegistry.class);

                    // camel registry
                    assertThat(context).hasSingleBean(org.apache.camel.cloud.ServiceRegistry.class);
                    assertThat(context).getBean(org.apache.camel.cloud.ServiceRegistry.class).isInstanceOf(CamelSpringCloudServiceRegistry.class);

                    assertThat(context).getBean(org.apache.camel.cloud.ServiceRegistry.class).hasFieldOrPropertyWithValue(
                        "nativeServiceRegistry",
                        context.getBean(org.springframework.cloud.client.serviceregistry.ServiceRegistry.class)
                    );
                }
            );
    }

    @Test
    public void testDisabledCamelCloud() {
        new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    CamelAutoConfiguration.class,
                    CamelCloudAutoConfiguration.class,
                    CamelSpringCloudServiceRegistryAutoConfiguration.class
                ))
            .withUserConfiguration(
                TestConfiguration.class)
            .withPropertyValues(
                "spring.main.banner-mode=off",
                "ribbon.eureka.enabled=false",
                "ribbon.enabled=false",
                "camel.cloud.enabled=false")
            .run(
                context -> {
                    // spring cloud registry
                    assertThat(context).hasSingleBean(org.springframework.cloud.client.serviceregistry.ServiceRegistry.class);
                    assertThat(context).getBean(org.springframework.cloud.client.serviceregistry.ServiceRegistry.class).isInstanceOf(MyServiceRegistry.class);

                    // camel registry
                    assertThat(context).doesNotHaveBean(org.apache.camel.cloud.ServiceRegistry.class);
                }
            );
    }

    @Test
    public void testDisabledCamelServiceRegistry() {
        new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    CamelAutoConfiguration.class,
                    CamelCloudAutoConfiguration.class,
                    CamelSpringCloudServiceRegistryAutoConfiguration.class
                ))
            .withUserConfiguration(
                TestConfiguration.class)
            .withPropertyValues(
                "spring.main.banner-mode=off",
                "ribbon.eureka.enabled=false",
                "ribbon.enabled=false",
                "camel.cloud.enabled=true",
                "camel.cloud.service-registry.enabled=false")
            .run(
                context -> {
                    // spring cloud registry
                    assertThat(context).hasSingleBean(org.springframework.cloud.client.serviceregistry.ServiceRegistry.class);
                    assertThat(context).getBean(org.springframework.cloud.client.serviceregistry.ServiceRegistry.class).isInstanceOf(MyServiceRegistry.class);

                    // camel registry
                    assertThat(context).doesNotHaveBean(org.apache.camel.cloud.ServiceRegistry.class);
                }
            );
    }

    @Test
    public void testEnabledCamelServiceRegistry() {
        new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    CamelAutoConfiguration.class,
                    CamelCloudAutoConfiguration.class,
                    CamelSpringCloudServiceRegistryAutoConfiguration.class
                ))
            .withUserConfiguration(
                TestConfiguration.class)
            .withPropertyValues(
                "spring.main.banner-mode=off",
                "ribbon.eureka.enabled=false",
                "ribbon.enabled=false",
                "camel.cloud.enabled=false",
                "camel.cloud.service-registry.enabled=true")
            .run(
                context -> {
                    // spring cloud registry
                    assertThat(context).hasSingleBean(org.springframework.cloud.client.serviceregistry.ServiceRegistry.class);
                    assertThat(context).getBean(org.springframework.cloud.client.serviceregistry.ServiceRegistry.class).isInstanceOf(MyServiceRegistry.class);

                    // camel registry
                    assertThat(context).hasSingleBean(org.apache.camel.cloud.ServiceRegistry.class);
                    assertThat(context).getBean(org.apache.camel.cloud.ServiceRegistry.class).isInstanceOf(CamelSpringCloudServiceRegistry.class);

                    assertThat(context).getBean(org.apache.camel.cloud.ServiceRegistry.class).hasFieldOrPropertyWithValue(
                        "nativeServiceRegistry",
                        context.getBean(org.springframework.cloud.client.serviceregistry.ServiceRegistry.class)
                    );
                }
            );
    }

    // *************************************
    // Test Registry
    // *************************************

    @Test
    public void testServiceRegistry() {
        new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    CamelAutoConfiguration.class,
                    CamelCloudAutoConfiguration.class,
                    CamelSpringCloudServiceRegistryAutoConfiguration.class
                ))
            .withUserConfiguration(
                TestConfiguration.class)
            .withPropertyValues(
                "spring.main.banner-mode=off",
                "ribbon.eureka.enabled=false",
                "ribbon.enabled=false")
            .run(
                context -> {
                    CamelSpringCloudServiceRegistry camelRgistry = context.getBean(CamelSpringCloudServiceRegistry.class);

                    final String serviceName = "my-.service";
                    final String serviceId = UUID.randomUUID().toString();
                    final int port = ThreadLocalRandom.current().nextInt();

                    camelRgistry.register(
                        DefaultServiceDefinition.builder()
                            .withHost("localhost")
                            .withPort(port)
                            .withName(serviceName)
                            .withId(serviceId)
                            .build()
                    );

                    MyServiceRegistry cloudRegistry = camelRgistry.getNativeServiceRegistry(MyServiceRegistry.class);

                    assertThat(cloudRegistry.registrations).hasSize(1);
                    assertThat(cloudRegistry.registrations.get(0)).hasFieldOrPropertyWithValue("serviceId", serviceName);
                    assertThat(cloudRegistry.registrations.get(0)).hasFieldOrPropertyWithValue("host", "localhost");
                    assertThat(cloudRegistry.registrations.get(0)).hasFieldOrPropertyWithValue("port", port);

                }
            );
    }

    // *************************************
    // Config
    // *************************************

    @Configuration
    public static class TestConfiguration {
        @Bean
        public org.springframework.cloud.client.serviceregistry.ServiceRegistry<MyServiceRegistration> myServiceRegistry() {
            return new MyServiceRegistry();
        }

        @Bean
        public Converter<ServiceDefinition, MyServiceRegistration> definitionToRegistration() {
            return new Converter<ServiceDefinition, MyServiceRegistration>() {
                @Override
                public MyServiceRegistration convert(ServiceDefinition source) {
                    return new MyServiceRegistration(
                        source.getName(),
                        source.getHost(),
                        source.getPort()
                    );
                }
            };
        }

    }

    // *************************************
    // Service Registry Impl
    // *************************************

    public static class MyServiceRegistry implements org.springframework.cloud.client.serviceregistry.ServiceRegistry<MyServiceRegistration> {
        List<MyServiceRegistration> registrations;

        public MyServiceRegistry() {
            this.registrations = new ArrayList<>();
        }

        @Override
        public void register(MyServiceRegistration registration) {
            this.registrations.add(registration);
        }

        @Override
        public void deregister(MyServiceRegistration registration) {
            this.registrations.remove(registration);
        }

        @Override
        public void close() {
            this.registrations.clear();
        }

        @Override
        public void setStatus(MyServiceRegistration registration, String status) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object getStatus(MyServiceRegistration registration) {
            throw new UnsupportedOperationException();
        }
    }

    public static class MyServiceRegistration extends DefaultServiceInstance implements Registration {
        public MyServiceRegistration(String serviceId, String host, int port) {
            super(serviceId, host, port, false, Collections.emptyMap());
        }
    }
}
