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
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.CacheContainer;
import org.infinispan.manager.DefaultCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component("infinispan")
public class InfinispanComponent extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(InfinispanComponent.class);

    private transient CacheContainer defaultCacheManager;

    @Metadata(description = "Component configuration")
    private InfinispanConfiguration configuration = new InfinispanConfiguration();

    public InfinispanComponent() {
    }

    public InfinispanComponent(CamelContext camelContext) {
        super(camelContext);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        InfinispanConfiguration conf = configuration.copy();
        // init default embedded cache if config parameters aren't specified or cacheContainer is set using setMethod
        if (!isConfigProvided(parameters, conf)) {
            if (defaultCacheManager == null) {
                defaultCacheManager = new DefaultCacheManager(new GlobalConfigurationBuilder().defaultCacheName("default").build(),
                        new org.infinispan.configuration.cache.ConfigurationBuilder().build());
                LOG.debug("Default cacheContainer has been created");
            }
            conf.setCacheContainer(defaultCacheManager);
        }

        InfinispanEndpoint endpoint = new InfinispanEndpoint(uri, remaining, this, conf);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public void setConfiguration(InfinispanConfiguration configuration) {
        this.configuration = configuration;
    }

    public InfinispanConfiguration getConfiguration() {
        return configuration;
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

    @Override
    protected void doShutdown() throws Exception {
        super.doShutdown();

        if (defaultCacheManager != null) {
            defaultCacheManager.stop();
            defaultCacheManager = null;
        }
    }
}
