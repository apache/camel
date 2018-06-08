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
package org.apache.camel.component.kubernetes.springboot.test.cloud;

import org.apache.camel.cloud.ServiceDiscovery;
import org.apache.camel.model.cloud.springboot.KubernetesServiceCallServiceDiscoveryConfigurationProperties;
import org.junit.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

public class KubernetesServiceDiscoveryAutoConfigurationTest {

    @Test
    public void testServiceDiscoveryDisabled() {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(TestConfiguration.class)
            .web(false)
            .run(
                "--debug=false",
                "--spring.main.banner-mode=OFF",
                "--camel.cloud.kubernetes.service-discovery.enabled=false"
            );

        try {
            assertThat(context.getBeansOfType(KubernetesServiceCallServiceDiscoveryConfigurationProperties.class)).isEmpty();
            assertThat(context.getBeansOfType(ServiceDiscovery.class)).doesNotContainKeys("kubernetes-service-discovery");
        } finally {
            context.close();
        }
    }


    @Test
    public void testServiceDiscoveryEnabled() {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(TestConfiguration.class)
            .web(false)
            .run(
                "--debug=false",
                "--spring.main.banner-mode=OFF",
                "--camel.cloud.kubernetes.service-discovery.enabled=true"
            );

        try {
            assertThat(context.getBeansOfType(KubernetesServiceCallServiceDiscoveryConfigurationProperties.class)).hasSize(1);
            assertThat(context.getBeansOfType(ServiceDiscovery.class)).containsKeys("kubernetes-service-discovery");
        } finally {
            context.close();
        }
    }

    @EnableAutoConfiguration
    @Configuration
    public static class TestConfiguration {
    }
}
