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
package org.apache.camel.component.properties.springboot;

import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.component.properties.PropertiesParser;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.apache.camel.spring.boot.util.ConditionalOnCamelContextAndAutoConfigurationBeans;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
@Conditional({ConditionalOnCamelContextAndAutoConfigurationBeans.class})
@AutoConfigureAfter(CamelAutoConfiguration.class)
@EnableConfigurationProperties({PropertiesComponentConfiguration.class})
public class PropertiesComponentAutoConfiguration {

    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private CamelContext camelContext;

    @Lazy
    @Bean(name = "properties-component")
    @ConditionalOnMissingBean(PropertiesComponent.class)
    public PropertiesComponent configurePropertiesComponent(PropertiesComponentConfiguration configuration) throws Exception {
        PropertiesComponent component = new PropertiesComponent();
        component.setCamelContext(camelContext);

        if (configuration.getAutoDiscoverPropertiesSources() != null) {
            component.setAutoDiscoverPropertiesSources(configuration.getAutoDiscoverPropertiesSources());
        }
        if (configuration.getDefaultFallbackEnabled() != null) {
            component.setDefaultFallbackEnabled(configuration.getDefaultFallbackEnabled());
        }
        if (configuration.getEncoding() != null) {
            component.setEncoding(configuration.getEncoding());
        }
        if (configuration.getEnvironmentVariableMode() != null) {
            component.setEnvironmentVariableMode(configuration.getEnvironmentVariableMode());
        }
        if (configuration.getSystemPropertiesMode() != null) {
            component.setSystemPropertiesMode(configuration.getSystemPropertiesMode());
        }
        if (configuration.getIgnoreMissingLocation() != null) {
            component.setIgnoreMissingLocation(configuration.getIgnoreMissingLocation());
        }
        if (configuration.getLocation() != null) {
            component.setLocation(configuration.getLocation());
        }
        if (configuration.getInitialProperties() != null) {
            Properties prop = camelContext.getRegistry().lookupByNameAndType(configuration.getInitialProperties(), Properties.class);
            component.setInitialProperties(prop);
        }
        if (configuration.getOverrideProperties() != null) {
            Properties prop = camelContext.getRegistry().lookupByNameAndType(configuration.getOverrideProperties(), Properties.class);
            component.setOverrideProperties(prop);
        }
        if (configuration.getPropertiesParser() != null) {
            PropertiesParser parser = camelContext.getRegistry().lookupByNameAndType(configuration.getPropertiesParser(), PropertiesParser.class);
            component.setPropertiesParser(parser);
        }
        return component;
    }
}