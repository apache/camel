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

package org.apache.camel.spring.boot.cloud;

import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;

import org.apache.camel.CamelContext;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.cloud.ServiceDiscovery;
import org.apache.camel.impl.cloud.StaticServiceDiscovery;
import org.apache.camel.spring.boot.util.GroupCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
@ConditionalOnBean(CamelCloudAutoConfiguration.class)
@EnableConfigurationProperties(CamelCloudConfigurationProperties.class)
@Conditional(CamelCloudServiceDiscoveryAutoConfiguration.Condition.class)
public class CamelCloudServiceDiscoveryAutoConfiguration implements BeanFactoryAware {
    private static final Logger LOGGER = LoggerFactory.getLogger(CamelCloudServiceDiscoveryAutoConfiguration.class);

    private BeanFactory beanFactory;

    @Autowired
    private CamelContext camelContext;
    @Autowired
    private CamelCloudConfigurationProperties configurationProperties;

    @Override
    public void setBeanFactory(BeanFactory factory) throws BeansException {
        beanFactory = factory;
    }

    @Lazy
    @Bean(name = "static-service-discovery")
    public ServiceDiscovery staticServiceDiscovery() {
        return createStaticServiceDiscovery(configurationProperties.getServiceDiscovery());
    }

    @Lazy
    @Bean(name = "service-discovery")
    public CamelCloudServiceDiscovery serviceDiscovery(List<ServiceDiscovery> serviceDiscoveryList) throws NoTypeConversionAvailableException {
        String cacheTimeout = configurationProperties.getServiceDiscovery().getCacheTimeout();
        Long timeout = null;

        if (cacheTimeout != null) {
            timeout = camelContext.getTypeConverter().mandatoryConvertTo(Long.class, timeout);
        }

        return new CamelCloudServiceDiscovery(timeout, serviceDiscoveryList);
    }

    @PostConstruct
    public void addServiceDiscoveryConfigurations() {
        if (!(beanFactory instanceof ConfigurableBeanFactory)) {
            LOGGER.warn("BeanFactory is not of type ConfigurableBeanFactory");
            return;
        }

        final ConfigurableBeanFactory factory = (ConfigurableBeanFactory) beanFactory;

        configurationProperties.getServiceDiscovery().getConfigurations().entrySet().stream()
            .forEach(entry -> registerBean(factory, entry.getKey(), entry.getValue()));
    }

    // *******************************
    // Condition
    // *******************************

    public static class Condition extends GroupCondition {
        public Condition() {
            super(
                "camel.cloud",
                "camel.cloud.service-discovery"
            );
        }
    }

    // *******************************
    // Helper
    // *******************************

    private void registerBean(ConfigurableBeanFactory factory, String name, CamelCloudConfigurationProperties.ServiceDiscoveryConfiguration configuration) {
        factory.registerSingleton(
            name,
            createStaticServiceDiscovery(configuration)
        );
    }

    private ServiceDiscovery createStaticServiceDiscovery(CamelCloudConfigurationProperties.ServiceDiscoveryConfiguration configuration) {
        StaticServiceDiscovery staticServiceDiscovery = new StaticServiceDiscovery();

        Map<String, List<String>> services = configuration.getServices();
        for (Map.Entry<String, List<String>> entry : services.entrySet()) {
            staticServiceDiscovery.addServers(entry.getKey(), entry.getValue());
        }

        return staticServiceDiscovery;
    }
}
