/*
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
package org.apache.camel.component.resilience.springboot;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.PostConstruct;

import org.apache.camel.CamelContext;
import org.apache.camel.component.resilience4j.ResilienceConstants;
import org.apache.camel.model.Resilience4jConfigurationDefinition;
import org.apache.camel.model.springboot.Resilience4jConfigurationDefinitionCommon;
import org.apache.camel.model.springboot.Resilience4jConfigurationDefinitionProperties;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.apache.camel.support.IntrospectionSupport;
import org.springframework.beans.factory.BeanCreationException;
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
 * Resilience auto configuration.
 */
@Configuration
@ConditionalOnProperty(name = "camel.resilience4j.enabled", matchIfMissing = true)
@ConditionalOnBean(value = CamelAutoConfiguration.class)
@AutoConfigureAfter(value = CamelAutoConfiguration.class)
@EnableConfigurationProperties(Resilience4jConfigurationDefinitionProperties.class)
public class ResilienceAutoConfiguration {
    @Autowired
    private ConfigurableBeanFactory beanFactory;
    @Autowired
    private CamelContext camelContext;
    @Autowired
    private Resilience4jConfigurationDefinitionProperties config;

    @Bean(name = ResilienceConstants.DEFAULT_RESILIENCE_CONFIGURATION_ID)
    @ConditionalOnClass(CamelContext.class)
    @ConditionalOnMissingBean(name = ResilienceConstants.DEFAULT_RESILIENCE_CONFIGURATION_ID)
    public Resilience4jConfigurationDefinition defaultResilienceConfigurationDefinition() throws Exception {
        Map<String, Object> properties = new HashMap<>();

        IntrospectionSupport.getProperties(config, properties, null, false);
        Resilience4jConfigurationDefinition definition = new Resilience4jConfigurationDefinition();
        IntrospectionSupport.setProperties(camelContext, camelContext.getTypeConverter(), definition, properties);

        return definition;
    }

    @PostConstruct
    public void postConstruct() {
        if (beanFactory == null) {
            return;
        }

        Map<String, Object> properties = new HashMap<>();

        for (Map.Entry<String, Resilience4jConfigurationDefinitionCommon> entry : config.getConfigurations().entrySet()) {

            // clear the properties map for reuse
            properties.clear();

            // extract properties
            IntrospectionSupport.getProperties(entry.getValue(), properties, null, false);

            try {
                Resilience4jConfigurationDefinition definition = new Resilience4jConfigurationDefinition();
                IntrospectionSupport.setProperties(camelContext, camelContext.getTypeConverter(), definition, properties);

                // Registry the definition
                beanFactory.registerSingleton(entry.getKey(), definition);

            } catch (Exception e) {
                throw new BeanCreationException(entry.getKey(), e);
            }
        }
    }
}
