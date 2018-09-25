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

import java.util.List;

import org.apache.camel.spring.boot.util.GroupCondition;
import org.apache.camel.spring.cloud.CamelSpringCloudServiceLoadBalancer;
import org.apache.camel.spring.cloud.CamelSpringCloudServiceRegistryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.netflix.ribbon.RibbonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;

@Configuration
@AutoConfigureAfter(RibbonAutoConfiguration.class)
@AutoConfigureBefore(CamelSpringCloudServiceRegistryAutoConfiguration.class)
@Conditional(CamelCloudNetflixServiceLoadBalancerAutoConfiguration.Condition.class)
public class CamelCloudNetflixServiceLoadBalancerAutoConfiguration {

    @ConditionalOnBean(LoadBalancerClient.class)
    @ConditionalOnMissingBean
    @Bean("netflix-client-load-balancer-adapter")
    public CamelSpringCloudServiceLoadBalancer.LoadBalancerClientAdapter netflixClientLoadBalancerAdapter(List<ConversionService> conversionServices) {
        return client -> new CamelCloudNetflixServiceLoadBalancer(client, conversionServices);
    }

    // *******************************
    // Condition
    // *******************************

    public static class Condition extends GroupCondition {
        public Condition() {
            super(
                "camel.cloud",
                "camel.cloud.netflix"
            );
        }
    }
}
