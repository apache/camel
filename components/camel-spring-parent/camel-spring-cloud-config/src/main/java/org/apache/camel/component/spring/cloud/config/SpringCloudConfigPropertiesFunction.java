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
package org.apache.camel.component.spring.cloud.config;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.support.service.ServiceSupport;
import org.springframework.boot.context.config.ConfigData;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

/**
 * Properties Function implementation that provides integration with Spring Cloud Config. This class allows Camel to
 * resolve property placeholders using values from Spring Cloud Config.
 * <p>
 * When a property placeholder with the prefix "spring-config:" is encountered in Camel routes or configuration, this
 * function will be called to resolve the property value from Spring Cloud Config sources.
 * <p>
 * The implementation first attempts to resolve properties through the Spring {@link Environment} if available. If not
 * available or the property is not found, it falls back to retrieving the configuration from Spring Config directly.
 * <p>
 * Usage example in Camel routes or configuration:
 *
 * <pre>
 * {{spring-config:my.property.name}}
 * </pre>
 *
 */
@org.apache.camel.spi.annotations.PropertiesFunction("spring-config")
public class SpringCloudConfigPropertiesFunction extends ServiceSupport
        implements PropertiesFunction, CamelContextAware, EnvironmentAware {

    private CamelContext camelContext;

    private Environment environment;

    /**
     * Resolves the specified property from Spring Cloud Config.
     * <p>
     * The resolution process follows these steps:
     * <ol>
     * <li>If a Spring Environment is available, attempt to resolve the property from it</li>
     * <li>Otherwise, retrieve the configuration from Spring Config directly</li>
     * <li>Search through all available property sources for the property</li>
     * <li>Cache the result for future lookups by {@link SpringCloudConfigReloadTriggerTask}</li>
     * </ol>
     *
     * @param  remainder             the property name to resolve (without the "spring-config:" prefix)
     * @return                       the property value, or null if not found
     * @throws RuntimeCamelException if the Spring Config data cannot be retrieved
     */
    @Override
    public String apply(String remainder) {
        if (environment != null) {
            return environment.getProperty(remainder);
        }

        ConfigData configData = new SpringConfigProvider().getConfigData(getCamelContext());
        if (configData == null) {
            throw new RuntimeCamelException("Cannot retrieve any config data from Spring Config for property " + remainder);
        }

        String result = null;
        for (PropertySource propertySource : configData.getPropertySources()) {
            if (propertySource.containsProperty(remainder)) {
                result = propertySource.getProperty(remainder).toString();
            }
        }

        SpringConfigRemaindersCache.put(remainder, result);

        return result;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return this.camelContext;
    }

    @Override
    public String getName() {
        return "spring-config";
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
}
