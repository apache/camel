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

import java.util.List;
import java.util.Map;

import org.apache.camel.cloud.ServiceDiscovery;
import org.apache.camel.spring.boot.util.GroupCondition;
import org.apache.camel.util.StringHelper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;

@Configuration
@EnableConfigurationProperties(ServiceCallConfigurationProperties.class)
public class ServiceCallServiceDiscoveryAutoConfiguration {
    @Lazy
    @Scope("prototype")
    @Bean(name = "service-discovery-client")
    @Conditional(ServiceCallServiceDiscoveryAutoConfiguration.ServiceDiscoveryCondition.class)
    public DiscoveryClient serviceDiscoveryClient(ServiceCallConfigurationProperties properties) {
        CamelCloudDiscoveryClient client = new CamelCloudDiscoveryClient("service-discovery-client");

        Map<String, String> services = properties.getServiceDiscovery().getServices();
        for (Map.Entry<String, String> entry : services.entrySet()) {

            String[] parts = entry.getValue().split(",");
            for (String part : parts) {
                String host = StringHelper.before(part, ":");
                String port = StringHelper.after(part, ":");

                client.addServiceInstance(entry.getKey(), host, Integer.parseInt(port));
            }
        }

        return client;
    }

    @Lazy
    @Scope("prototype")
    @Bean(name = "service-discovery")
    @Conditional(ServiceCallServiceDiscoveryAutoConfiguration.ServiceDiscoveryCondition.class)
    public ServiceDiscovery serviceDiscovery(List<DiscoveryClient> clients) {
        return new CamelCloudServiceDiscovery(clients);
    }

    public static class ServiceDiscoveryCondition extends GroupCondition {
        public ServiceDiscoveryCondition() {
            super(
                "camel.cloud.servicecall",
                "camel.cloud.servicecall.service-discovery"
            );
        }
    }
}
