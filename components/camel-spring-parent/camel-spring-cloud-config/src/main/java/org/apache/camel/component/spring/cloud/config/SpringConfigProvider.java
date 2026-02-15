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
import org.apache.camel.vault.SpringCloudConfigConfiguration;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.bootstrap.BootstrapRegistry;
import org.springframework.boot.bootstrap.ConfigurableBootstrapContext;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.context.config.ConfigData;
import org.springframework.boot.context.config.ConfigDataLoaderContext;
import org.springframework.boot.logging.DeferredLogs;
import org.springframework.cloud.config.client.ConfigClientProperties;
import org.springframework.cloud.config.client.ConfigClientRequestTemplateFactory;
import org.springframework.cloud.config.client.ConfigServerConfigDataLoader;
import org.springframework.cloud.config.client.ConfigServerConfigDataResource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.web.client.RestTemplate;

/**
 * Provider class that retrieves configuration data from a Spring Cloud Config Server.
 * <p>
 * This class serves as a bridge between Apache Camel and Spring Cloud Config, allowing Camel contexts to fetch their
 * configuration from a centralized Spring Cloud Config Server. It handles the authentication and connection details
 * necessary to communicate with the config server.
 * <p>
 * The configuration parameters are sourced from {@link SpringCloudConfigConfiguration} which is obtained from the Camel
 * context's vault configuration.
 *
 * @see org.apache.camel.vault.SpringCloudConfigConfiguration
 * @see org.springframework.cloud.config.client.ConfigServerConfigDataLoader
 */
public class SpringConfigProvider {

    /**
     * Retrieves configuration data from a Spring Cloud Config Server for the given Camel context.
     * <p>
     * This method sets up the necessary Spring Cloud Config components to connect to a config server using the
     * parameters defined in the Camel context's Spring Cloud Config configuration. It handles authentication
     * credentials, connection URIs, and other configuration properties.
     *
     * @param  camelContext The Camel context for which to retrieve configuration data. This context is used to obtain
     *                      configuration parameters and to identify the application name to be used when fetching
     *                      configuration from the server.
     * @return              A {@link ConfigData} object containing the configuration properties retrieved from the
     *                      Spring Cloud Config Server.
     *
     * @see                 org.springframework.boot.context.config.ConfigData
     * @see                 org.apache.camel.CamelContext
     */
    public ConfigData getConfigData(CamelContext camelContext) {
        SpringCloudConfigConfiguration configuration = camelContext.getVaultConfiguration().springConfig();
        ConfigServerConfigDataLoader configServerConfigDataLoader = new ConfigServerConfigDataLoader(new DeferredLogs());

        Environment camelEnvironment = new StandardEnvironment();
        ConfigClientProperties configClientProperties = new ConfigClientProperties(camelEnvironment);
        configClientProperties.setName(camelContext.getName());
        configClientProperties.setUsername(configuration.getUsername());
        configClientProperties.setPassword(configuration.getPassword());
        configClientProperties.setToken(configuration.getToken());

        if (configuration.getLabel() != null) {
            configClientProperties.setLabel(configuration.getLabel());
        }
        if (configuration.getProfile() != null) {
            configClientProperties.setProfile(configuration.getProfile());
        }
        if (configuration.getUris() != null && !configuration.getUris().isEmpty()) {
            configClientProperties.setUri(configuration.getUris().split(","));
        }

        // Spring Cloud Config does not expose a plain Spring API easy to use
        // This code implements a similar behaviour to Spring Boot Config usage
        ConfigurableBootstrapContext configurableBootstrapContext = new DefaultBootstrapContext();
        configurableBootstrapContext.register(RestTemplate.class,
                BootstrapRegistry.InstanceSupplier.of(new RestTemplate()));
        configurableBootstrapContext.register(ConfigClientProperties.class,
                BootstrapRegistry.InstanceSupplier.of(configClientProperties));
        configurableBootstrapContext.register(ConfigClientRequestTemplateFactory.class,
                BootstrapRegistry.InstanceSupplier.of(new ConfigClientRequestTemplateFactory(
                        LogFactory.getLog(SpringCloudConfigPropertiesFunction.class),
                        configClientProperties)));

        ConfigDataLoaderContext configDataLoaderContext = () -> configurableBootstrapContext;

        ConfigServerConfigDataResource configServerConfigDataResource
                = new ConfigServerConfigDataResource(configClientProperties, true, null);

        ConfigData configData = configServerConfigDataLoader.doLoad(configDataLoaderContext,
                configServerConfigDataResource);

        return configData;
    }
}
