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

import org.apache.camel.spring.boot.cloud.CamelCloudAutoConfiguration;
import org.apache.camel.spring.boot.cloud.CamelCloudConfigurationProperties;
import org.apache.camel.spring.boot.cloud.CamelCloudServiceDiscovery;
import org.apache.camel.spring.boot.cloud.CamelCloudServiceDiscoveryAutoConfiguration;
import org.apache.camel.spring.boot.util.GroupCondition;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerAutoConfiguration;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnBean({ CamelCloudAutoConfiguration.class, LoadBalancerClient.class })
@AutoConfigureAfter({ LoadBalancerAutoConfiguration.class, CamelCloudServiceDiscoveryAutoConfiguration.class })
@EnableConfigurationProperties(CamelCloudConfigurationProperties.class)
@Conditional(CamelSpringCloudDiscoveryClientAutoConfiguration.LoadBalancerCondition.class)
public class CamelSpringCloudDiscoveryClientAutoConfiguration {

    @Bean(name = "load-balancer-discovery-client")
    @ConditionalOnMissingBean
    public DiscoveryClient serviceDiscoveryClient(CamelCloudServiceDiscovery serviceDiscovery) {
        return new CamelSpringCloudDiscoveryClient("service-discovery-client", serviceDiscovery);
    }

    // *******************************
    // Condition
    // *******************************

    public static class LoadBalancerCondition extends GroupCondition {
        public LoadBalancerCondition() {
            super(
                "camel.cloud",
                "camel.cloud.discovery-client"
            );
        }
    }
}
