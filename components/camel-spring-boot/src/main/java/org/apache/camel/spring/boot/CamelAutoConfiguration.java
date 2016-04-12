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
package org.apache.camel.spring.boot;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.component.properties.PropertiesParser;
import org.apache.camel.spring.CamelBeanPostProcessor;
import org.apache.camel.spring.SpringCamelContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@EnableConfigurationProperties(CamelConfigurationProperties.class)
@Import(TypeConversionConfiguration.class)
public class CamelAutoConfiguration {

    /**
     * Spring-aware Camel context for the application. Auto-detects and loads all routes available in the Spring context.
     */
    @Bean
    @ConditionalOnMissingBean(CamelContext.class)
    CamelContext camelContext(ApplicationContext applicationContext,
                              CamelConfigurationProperties config) {

        CamelContext camelContext = new SpringCamelContext(applicationContext);
        SpringCamelContext.setNoStart(true);

        if (!config.isJmxEnabled()) {
            camelContext.disableJMX();
        }

        if (config.getName() != null) {
            ((SpringCamelContext) camelContext).setName(config.getName());
        }

        if (config.getLogDebugMaxChars() > 0) {
            camelContext.getProperties().put(Exchange.LOG_DEBUG_BODY_MAX_CHARS, "" + config.getLogDebugMaxChars());
        }

        camelContext.setStreamCaching(config.isStreamCaching());
        camelContext.setTracing(config.isTracing());
        camelContext.setMessageHistory(config.isMessageHistory());
        camelContext.setLogExhaustedMessageBody(config.isLogExhaustedMessageBody());
        camelContext.setHandleFault(config.isHandleFault());
        camelContext.setAutoStartup(config.isAutoStartup());
        camelContext.setAllowUseOriginalMessage(config.isAllowUseOriginalMessage());

        if (camelContext.getManagementStrategy().getManagementAgent() != null) {
            camelContext.getManagementStrategy().getManagementAgent().setEndpointRuntimeStatisticsEnabled(config.isEndpointRuntimeStatisticsEnabled());
            camelContext.getManagementStrategy().getManagementAgent().setStatisticsLevel(config.getJmxManagementStatisticsLevel());
            camelContext.getManagementStrategy().getManagementAgent().setManagementNamePattern(config.getJmxManagementNamePattern());
            camelContext.getManagementStrategy().getManagementAgent().setCreateConnector(config.isJmxCreateConnector());
        }

        return camelContext;
    }

    @Bean
    CamelSpringBootApplicationController applicationController(ApplicationContext applicationContext, CamelContext camelContext) {
        return new CamelSpringBootApplicationController(applicationContext, camelContext);
    }

    @Bean
    @ConditionalOnMissingBean(RoutesCollector.class)
    RoutesCollector routesCollector(ApplicationContext applicationContext, CamelConfigurationProperties config) {
        Collection<CamelContextConfiguration> configurations = applicationContext.getBeansOfType(CamelContextConfiguration.class).values();
        return new RoutesCollector(applicationContext, new ArrayList<CamelContextConfiguration>(configurations), config);
    }

    /**
     * Default producer template for the bootstrapped Camel context.
     */
    @Bean(initMethod = "", destroyMethod = "")
    // Camel handles the lifecycle of this bean
    @ConditionalOnMissingBean(ProducerTemplate.class)
    ProducerTemplate producerTemplate(CamelContext camelContext,
                                      CamelConfigurationProperties config) {
        return camelContext.createProducerTemplate(config.getProducerTemplateCacheSize());
    }

    /**
     * Default consumer template for the bootstrapped Camel context.
     */
    @Bean(initMethod = "", destroyMethod = "")
    // Camel handles the lifecycle of this bean
    @ConditionalOnMissingBean(ConsumerTemplate.class)
    ConsumerTemplate consumerTemplate(CamelContext camelContext,
                                      CamelConfigurationProperties config) {
        return camelContext.createConsumerTemplate(config.getConsumerTemplateCacheSize());
    }

    // SpringCamelContext integration

    @Bean
    PropertiesParser propertiesParser() {
        return new SpringPropertiesParser();
    }

    @Bean(initMethod = "", destroyMethod = "")
    // Camel handles the lifecycle of this bean
    PropertiesComponent properties(CamelContext camelContext, PropertiesParser parser) {
        if (camelContext.hasComponent("properties") != null) {
            return camelContext.getComponent("properties", PropertiesComponent.class);
        } else {
            PropertiesComponent pc = new PropertiesComponent();
            pc.setPropertiesParser(parser);
            camelContext.addComponent("properties", pc);
            return pc;
        }
    }

    /**
     * Camel post processor - required to support Camel annotations.
     */
    @Bean
    CamelBeanPostProcessor camelBeanPostProcessor(ApplicationContext applicationContext) {
        CamelBeanPostProcessor processor = new CamelBeanPostProcessor();
        processor.setApplicationContext(applicationContext);
        return processor;
    }

}
