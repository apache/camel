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
import org.apache.camel.component.kubernetes.cloud.KubernetesClientServiceDiscovery;
import org.apache.camel.component.kubernetes.cloud.KubernetesDnsServiceDiscovery;
import org.apache.camel.component.kubernetes.cloud.KubernetesDnsSrvServiceDiscovery;
import org.apache.camel.component.kubernetes.cloud.KubernetesEnvServiceDiscovery;
import org.apache.camel.model.cloud.springboot.KubernetesServiceCallServiceDiscoveryConfigurationProperties;
import org.junit.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

public class KubernetesServiceDiscoveryAutoConfigurationTest {

    @Test
    public void testServiceDiscoveryDisabled() {
        new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                "spring.main.banner-mode=off",
                "camel.cloud.kubernetes.service-discovery.enabled=false")
            .run(
                context -> {
                    assertThat(context).doesNotHaveBean(KubernetesServiceCallServiceDiscoveryConfigurationProperties.class);
                    assertThat(context).getBeans(ServiceDiscovery.class).doesNotContainKeys("kubernetes-service-discovery");
                }
            );
    }

    @Test
    public void testServiceDiscoveryEnabled() {
        new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                "spring.main.banner-mode=off",
                "camel.cloud.kubernetes.service-discovery.enabled=true")
            .run(
                context -> {
                    assertThat(context).hasSingleBean(KubernetesServiceCallServiceDiscoveryConfigurationProperties.class);
                    assertThat(context).getBeans(ServiceDiscovery.class).containsKeys("kubernetes-service-discovery");
                    assertThat(context).getBean("kubernetes-service-discovery").isInstanceOf(KubernetesEnvServiceDiscovery.class);
                }
            );
    }

    @Test
    public void testServiceDiscoveryWithEnv() {
        new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                "spring.main.banner-mode=off",
                "camel.cloud.kubernetes.service-discovery.enabled=true",
                "camel.cloud.kubernetes.service-discovery.lookup=env")
            .run(
                context -> {
                    assertThat(context).hasSingleBean(KubernetesServiceCallServiceDiscoveryConfigurationProperties.class);
                    assertThat(context).getBeans(ServiceDiscovery.class).containsKeys("kubernetes-service-discovery");
                    assertThat(context).getBean("kubernetes-service-discovery").isInstanceOf(KubernetesEnvServiceDiscovery.class);
                }
            );
    }

    @Test
    public void testServiceDiscoveryWithDns() {
        new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                "spring.main.banner-mode=off",
                "camel.cloud.kubernetes.service-discovery.enabled=true",
                "camel.cloud.kubernetes.service-discovery.lookup=dns",
                "camel.cloud.kubernetes.service-discovery.dns-domain=mydomain",
                "camel.cloud.kubernetes.service-discovery.namespace=mynamespace")
            .run(
                context -> {
                    assertThat(context).hasSingleBean(KubernetesServiceCallServiceDiscoveryConfigurationProperties.class);
                    assertThat(context).getBeans(ServiceDiscovery.class).containsKeys("kubernetes-service-discovery");
                    assertThat(context).getBean("kubernetes-service-discovery").isInstanceOf(KubernetesDnsServiceDiscovery.class);
                }
            );
    }

    @Test
    public void testServiceDiscoveryWithDnsSrv() {
        new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                "spring.main.banner-mode=off",
                "camel.cloud.kubernetes.service-discovery.enabled=true",
                "camel.cloud.kubernetes.service-discovery.lookup=dnssrv",
                "camel.cloud.kubernetes.service-discovery.port-name=myportname",
                "camel.cloud.kubernetes.service-discovery.port-proocole=myportproto",
                "camel.cloud.kubernetes.service-discovery.dns-domain=mydomain",
                "camel.cloud.kubernetes.service-discovery.namespace=mynamespace")
            .run(
                context -> {
                    assertThat(context).hasSingleBean(KubernetesServiceCallServiceDiscoveryConfigurationProperties.class);
                    assertThat(context).getBeans(ServiceDiscovery.class).containsKeys("kubernetes-service-discovery");
                    assertThat(context).getBean("kubernetes-service-discovery").isInstanceOf(KubernetesDnsSrvServiceDiscovery.class);
                }
            );
    }

    @Test
    public void testServiceDiscoveryWithClient() {
        new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                "spring.main.banner-mode=off",
                "camel.cloud.kubernetes.service-discovery.enabled=true",
                "camel.cloud.kubernetes.service-discovery.lookup=client")
            .run(
                context -> {
                    assertThat(context).hasSingleBean(KubernetesServiceCallServiceDiscoveryConfigurationProperties.class);
                    assertThat(context).getBeans(ServiceDiscovery.class).containsKeys("kubernetes-service-discovery");
                    assertThat(context).getBean("kubernetes-service-discovery").isInstanceOf(KubernetesClientServiceDiscovery.class);
                }
            );
    }


    @EnableAutoConfiguration
    @Configuration
    public static class TestConfiguration {
    }
}
