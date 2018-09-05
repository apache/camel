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
package org.apache.camel.spring.cloud.netflix;

import org.apache.camel.cloud.ServiceLoadBalancer;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.apache.camel.spring.boot.cloud.CamelCloudAutoConfiguration;
import org.apache.camel.spring.cloud.CamelSpringCloudServiceLoadBalancer;
import org.apache.camel.spring.cloud.CamelSpringCloudServiceRegistryAutoConfiguration;
import org.junit.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.netflix.ribbon.RibbonAutoConfiguration;
import org.springframework.cloud.netflix.ribbon.RibbonClientConfiguration;
import org.springframework.cloud.netflix.ribbon.RibbonLoadBalancerClient;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

public class CamelCloudNetflixServiceLoadBalancerAutoConfigurationTest {

    @Test
    public void testAutoConfiguration() {
        new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    CamelAutoConfiguration.class,
                    CamelCloudAutoConfiguration.class,
                    CamelSpringCloudServiceRegistryAutoConfiguration.class,
                    CamelCloudNetflixServiceLoadBalancerAutoConfiguration.class,
                    RibbonAutoConfiguration.class,
                    RibbonClientConfiguration.class
                ))
            .withUserConfiguration(
                TestConfiguration.class)
            .withPropertyValues(
                "debug=true",
                "spring.main.banner-mode=off",
                "ribbon.client.name=test")
            .run(
                context -> {
                    assertThat(context).hasSingleBean(LoadBalancerClient.class);
                    assertThat(context).getBean(LoadBalancerClient.class).isInstanceOf(RibbonLoadBalancerClient.class);

                    assertThat(context).hasSingleBean(CamelSpringCloudServiceLoadBalancer.LoadBalancerClientAdapter.class);

                    LoadBalancerClient client = context.getBean(LoadBalancerClient.class);
                    ServiceLoadBalancer balancer = context.getBean(CamelSpringCloudServiceLoadBalancer.LoadBalancerClientAdapter.class).adapt(client);

                    assertThat(balancer).isInstanceOf(CamelCloudNetflixServiceLoadBalancer.class);
                }
            );
    }


    @EnableAutoConfiguration
    @Configuration
    public static class TestConfiguration {

    }
}

