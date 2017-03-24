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
package org.apache.camel.component.hystrix.springboot;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.PostConstruct;

import org.apache.camel.CamelContext;
import org.apache.camel.component.hystrix.processor.HystrixConstants;
import org.apache.camel.model.HystrixConfigurationCommon;
import org.apache.camel.model.HystrixConfigurationDefinition;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.apache.camel.util.IntrospectionSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Hystrix auto configuration.
 */
@Configuration
@ConditionalOnProperty(name = "camel.hystrix.enabled", matchIfMissing = true)
@ConditionalOnBean(value = CamelAutoConfiguration.class)
@AutoConfigureAfter(value = CamelAutoConfiguration.class)
@EnableConfigurationProperties(HystrixConfiguration.class)
public class HystrixAutoConfiguration implements BeanFactoryAware {
    private static final Logger LOGGER = LoggerFactory.getLogger(HystrixAutoConfiguration.class);

    private BeanFactory beanFactory;

    @Autowired
    private CamelContext camelContext;
    @Autowired
    private HystrixConfiguration config;

    @Override
    public void setBeanFactory(BeanFactory factory) throws BeansException {
        beanFactory = factory;
    }

    @Bean(name = HystrixConstants.DEFAULT_HYSTRIX_CONFIGURATION_ID)
    @ConditionalOnClass(CamelContext.class)
    @ConditionalOnMissingBean(name = HystrixConstants.DEFAULT_HYSTRIX_CONFIGURATION_ID)
    public HystrixConfigurationDefinition defaultHystrixConfigurationDefinition() throws Exception {
        Map<String, Object> properties = new HashMap<>();

        IntrospectionSupport.getProperties(config, properties, null, false);

        HystrixConfigurationDefinition definition = new HystrixConfigurationDefinition();
        IntrospectionSupport.setProperties(camelContext, camelContext.getTypeConverter(), definition, properties);

        return definition;
    }

    @PostConstruct
    public void addHystrixConfigurations() {
        if (!(beanFactory instanceof ConfigurableBeanFactory)) {
            LOGGER.warn("BeanFactory is not of type ConfigurableBeanFactory");
            return;
        }

        final ConfigurableBeanFactory factory = (ConfigurableBeanFactory) beanFactory;
        final Map<String, Object> properties = new HashMap<>();

        for (Map.Entry<String, HystrixConfigurationCommon> entry : config.getConfigurations().entrySet()) {

            // clear the properties map for reuse
            properties.clear();

            // extract properties
            IntrospectionSupport.getProperties(entry.getValue(), properties, null, false);

            try {
                HystrixConfigurationDefinition definition = new HystrixConfigurationDefinition();
                IntrospectionSupport.setProperties(camelContext, camelContext.getTypeConverter(), definition, properties);

                // Registry the definition
                factory.registerSingleton(entry.getKey(), definition);

            } catch (Exception e) {
                throw new BeanCreationException(entry.getKey(), e);
            }
        }
    }
}
