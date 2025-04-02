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
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.BootstrapRegistry;
import org.springframework.boot.ConfigurableBootstrapContext;
import org.springframework.boot.DefaultBootstrapContext;
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

public class SpringConfigProvider {

    public ConfigData getConfigData(CamelContext camelContext) {
        ConfigServerConfigDataLoader configServerConfigDataLoader = new ConfigServerConfigDataLoader(new DeferredLogs());

        Environment camelEnvironment = new StandardEnvironment();
        ConfigClientProperties configClientProperties = new ConfigClientProperties(camelEnvironment);
        configClientProperties.setName(camelContext.getName());

        String configServerUri = System.getProperty("spring.config.server.uri");
        if (configServerUri != null) {
            configClientProperties.setUri(new String[] { configServerUri });
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
