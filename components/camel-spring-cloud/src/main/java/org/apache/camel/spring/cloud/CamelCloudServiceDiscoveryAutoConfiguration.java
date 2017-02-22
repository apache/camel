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

import org.apache.camel.CamelContext;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.cloud.ServiceDiscovery;
import org.apache.camel.impl.cloud.StaticServiceDiscovery;
import org.apache.camel.spring.boot.util.GroupCondition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
@EnableConfigurationProperties(CamelCloudConfigurationProperties.class)
@Conditional(CamelCloudServiceDiscoveryAutoConfiguration.ServiceDiscoveryCondition.class)
public class CamelCloudServiceDiscoveryAutoConfiguration {

    @Lazy
    @Bean(name = "static-service-discovery")
    public ServiceDiscovery staticServiceDiscovery(CamelCloudConfigurationProperties properties) {
        StaticServiceDiscovery staticServiceDiscovery = new StaticServiceDiscovery();

        Map<String, List<String>> services = properties.getServiceDiscovery().getServices();
        for (Map.Entry<String, List<String>> entry : services.entrySet()) {
            staticServiceDiscovery.addServers(entry.getKey(), entry.getValue());
        }

        return staticServiceDiscovery;
    }

    @Lazy
    @Bean(name = "service-discovery")
    public CamelCloudServiceDiscovery serviceDiscovery(
            CamelContext camelContext, CamelCloudConfigurationProperties properties, List<ServiceDiscovery> serviceDiscoveryList) throws NoTypeConversionAvailableException {

        String cacheTimeout = properties.getServiceDiscovery().getCacheTimeout();
        Long timeout = null;

        if (cacheTimeout != null) {
            timeout = camelContext.getTypeConverter().mandatoryConvertTo(Long.class, timeout);
        }

        return new CamelCloudServiceDiscovery(timeout, serviceDiscoveryList);
    }

    // *******************************
    // Condition
    // *******************************

    public static class ServiceDiscoveryCondition extends GroupCondition {
        public ServiceDiscoveryCondition() {
            super(
                "camel.cloud",
                "camel.cloud.service-discovery"
            );
        }
    }
}
