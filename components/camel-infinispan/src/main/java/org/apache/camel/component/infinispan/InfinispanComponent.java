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
package org.apache.camel.component.infinispan;

import java.util.Arrays;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.infinispan.commons.api.BasicCacheContainer;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component("infinispan")
public class InfinispanComponent extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(InfinispanComponent.class);

    @Metadata(description = "Default configuration")
    private InfinispanConfiguration configuration;
    @Metadata(description = "Default Cache container")
    private BasicCacheContainer cacheContainer;
    private boolean setCacheFromComponent;

    public InfinispanComponent() {
    }

    public InfinispanComponent(CamelContext camelContext) {
        super(camelContext);
    }

    public InfinispanConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The default configuration shared among endpoints.
     */
    public void setConfiguration(InfinispanConfiguration configuration) {
        this.configuration = configuration;
    }

    public BasicCacheContainer getCacheContainer() {
        return cacheContainer;
    }

    /**
     * The default cache container.
     */
    public void setCacheContainer(BasicCacheContainer cacheContainer) {
        this.cacheContainer = cacheContainer;
        this.setCacheFromComponent = true;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        InfinispanConfiguration conf;
        if (configuration != null) {
            conf = configuration.copy();
        } else {
            conf = new InfinispanConfiguration();
        }
        //     init default embedded cache if config parameters aren't specified or cacheContainer is set using setMethod
        if (!isConfigProvided(parameters, conf) || setCacheFromComponent) {
            if (cacheContainer == null) {
                cacheContainer = new DefaultCacheManager(new GlobalConfigurationBuilder().defaultCacheName("default").build(),
                    new org.infinispan.configuration.cache.ConfigurationBuilder().build());

                setCacheFromComponent = false;
                LOG.debug("Default cacheContainer has been created");
            }
            conf.setCacheContainer(cacheContainer);

        } else {
            // cacheContainer  will be initialized in InfinispanManager according defined options.
            conf.setCacheContainer(null);
        }

        InfinispanEndpoint endpoint = new InfinispanEndpoint(uri, remaining, this, conf);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    /**
     * Determine if cache is configured
     */
    private boolean isConfigProvided(Map<String, Object> parameters, InfinispanConfiguration conf) {
        if (conf.getHosts() != null) {
            return true;
        }
        if (conf.getCacheContainer() != null) {
            return true;
        }
        if (conf.getCacheContainerConfiguration() != null) {
            return true;
        }
        if (conf.getConfigurationUri() != null) {
            return true;
        }

        String[] confParameters = new String[] {"hosts", "cacheContainerConfiguration", "configurationUri", "cacheContainer"};
        return Arrays.stream(confParameters).anyMatch(parameters::containsKey);
    }
}
