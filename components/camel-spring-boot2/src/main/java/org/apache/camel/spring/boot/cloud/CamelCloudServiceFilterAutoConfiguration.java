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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.cloud.BlacklistServiceFilter;
import org.apache.camel.impl.cloud.HealthyServiceFilter;
import org.apache.camel.spring.boot.util.GroupCondition;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
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
@Conditional(CamelCloudServiceFilterAutoConfiguration.Condition.class)
public class CamelCloudServiceFilterAutoConfiguration implements BeanFactoryAware {
    private static final Logger LOGGER = LoggerFactory.getLogger(CamelCloudServiceFilterAutoConfiguration.class);

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
    @Bean(name = "service-filter")
    public CamelCloudServiceFilter serviceFilter() {
        return createServiceFilter(configurationProperties.getServiceFilter());
    }

    @PostConstruct
    public void addServiceFilterConfigurations() {
        if (!(beanFactory instanceof ConfigurableBeanFactory)) {
            LOGGER.warn("BeanFactory is not of type ConfigurableBeanFactory");
            return;
        }

        final ConfigurableBeanFactory factory = (ConfigurableBeanFactory) beanFactory;

        configurationProperties.getServiceFilter().getConfigurations().entrySet().stream()
            .forEach(entry -> registerBean(factory, entry.getKey(), entry.getValue()));
    }

    // *******************************
    // Condition
    // *******************************

    public static class Condition extends GroupCondition {
        public Condition() {
            super(
                "camel.cloud",
                "camel.cloud.service-filter"
            );
        }
    }

    // *******************************
    // Helper
    // *******************************

    private void registerBean(ConfigurableBeanFactory factory, String name, CamelCloudConfigurationProperties.ServiceFilterConfiguration configuration) {
        factory.registerSingleton(
            name,
            createServiceFilter(configuration)
        );
    }

    private CamelCloudServiceFilter createServiceFilter(CamelCloudConfigurationProperties.ServiceFilterConfiguration configuration) {
        BlacklistServiceFilter blacklist = new BlacklistServiceFilter();

        Map<String, List<String>> services = configuration.getBlacklist();
        for (Map.Entry<String, List<String>> entry : services.entrySet()) {
            for (String part : entry.getValue()) {
                String host = StringHelper.before(part, ":");
                String port = StringHelper.after(part, ":");

                if (ObjectHelper.isNotEmpty(host) && ObjectHelper.isNotEmpty(port)) {
                    blacklist.addServer(entry.getKey(), host, Integer.parseInt(port));
                }
            }
        }

        return new CamelCloudServiceFilter(Arrays.asList(new HealthyServiceFilter(), blacklist));
    }
}
