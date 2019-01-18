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
package org.apache.camel.spring.cloud.consul;

import java.util.Map;
import java.util.UUID;

import org.junit.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;

import static org.assertj.core.api.Assertions.assertThat;

public class CamelCloudConsulAutoConfigurationTest {
    @Test
    public void testServiceDefinitionToConsulRegistration() throws Exception {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(TestConfiguration.class)
            .web(WebApplicationType.NONE)
            .run(
                "--debug=false",
                "--spring.main.banner-mode=OFF",
                "--spring.application.name=" + UUID.randomUUID().toString(),
                "--ribbon.enabled=false",
                "--ribbon.eureka.enabled=false",
                "--management.endpoint.enabled=false",
                "--spring.cloud.consul.enabled=true",
                "--spring.cloud.consul.config.enabled=false",
                "--spring.cloud.consul.discovery.enabled=true",
                "--spring.cloud.service-registry.auto-registration.enabled=false",
                "--spring.main.allow-bean-definition-overriding=true"
            );

        // TODO: Remove --spring.main.allow-bean-definition-overriding=true when new version of spring-cloud
        //  is released that supports Spring Boot 2.1 more properly

        try {
            Map<String, Converter> converters = context.getBeansOfType(Converter.class);

            assertThat(converters).isNotNull();
            assertThat(converters.values().stream().anyMatch(ServiceDefinitionToConsulRegistration.class::isInstance)).isTrue();
        } finally {
            context.close();
        }
    }

    @Test
    public void testConsulServerToServiceDefinition() throws Exception {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(TestConfiguration.class)
            .web(WebApplicationType.NONE)
            .run(
                "--debug=false",
                "--spring.main.banner-mode=OFF",
                "--spring.application.name=" + UUID.randomUUID().toString(),
                "--ribbon.enabled=false",
                "--ribbon.eureka.enabled=false",
                "--management.endpoint.enabled=false",
                "--spring.cloud.consul.enabled=true",
                "--spring.cloud.consul.config.enabled=false",
                "--spring.cloud.consul.discovery.enabled=true",
                "--spring.cloud.service-registry.auto-registration.enabled=false",
                "--spring.main.allow-bean-definition-overriding=true"
            );

        // TODO: Remove --spring.main.allow-bean-definition-overriding=true when new version of spring-cloud
        //  is released that supports Spring Boot 2.1 more properly

        try {
            Map<String, Converter> converters = context.getBeansOfType(Converter.class);

            assertThat(converters).isNotNull();
            assertThat(converters.values().stream().anyMatch(ConsulServerToServiceDefinition.class::isInstance)).isTrue();
        } finally {
            context.close();
        }
    }

    // *************************************
    // Config
    // *************************************

    @EnableAutoConfiguration
    @Configuration
    public static class TestConfiguration {
    }
}

