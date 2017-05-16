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
package org.apache.camel.component.ehcache;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.spi.Metadata;
import org.ehcache.CacheManager;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.Configuration;

/**
 * Represents the component that manages {@link DefaultComponent}.
 */
public class EhcacheComponent extends DefaultComponent {
    @Metadata(label = "advanced")
    private EhcacheConfiguration configuration = new EhcacheConfiguration();

    public EhcacheComponent() {
    }

    public EhcacheComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        EhcacheConfiguration configuration;
        if (this.configuration != null) {
            configuration = this.configuration.copy();
        } else {
            configuration = new EhcacheConfiguration();
        }

        setProperties(configuration, parameters);

        return new EhcacheEndpoint(uri, this, remaining, configuration);
    }

    // ****************************
    // Properties
    // ****************************

    public EhcacheConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Sets the global component configuration
     */
    public void setConfiguration(EhcacheConfiguration configuration) {
        this.configuration = configuration;
    }

    public CacheManager getCacheManager() {
        return configuration.getCacheManager();
    }

    /**
     * The cache manager
     */
    public void setCacheManager(CacheManager cacheManager) {
        this.configuration.setCacheManager(cacheManager);
    }

    public Configuration getCacheManagerConfiguration() {
        return configuration.getCacheManagerConfiguration();
    }

    /**
     * The cache manager configuration
     */
    public void setCacheManagerConfiguration(Configuration cacheManagerConfiguration) {
        this.configuration.setCacheManagerConfiguration(cacheManagerConfiguration);
    }

    /**
     * The default cache configuration to be used to create caches.
     */
    public <K, V> void setCacheConfiguration(CacheConfiguration<K, V> cacheConfiguration) {
        this.configuration.setConfiguration(cacheConfiguration);
    }

    public <K, V> CacheConfiguration<K, V> getCacheConfiguration() {
        return this.configuration.getConfiguration();
    }

    public String getCacheConfigurationUri() {
        return this.configuration.getConfigurationUri();
    }

    /**
     * URI pointing to the Ehcache XML configuration file's location
     */
    public void setCacheConfigurationUri(String configurationUri) {
        this.configuration.setConfigurationUri(configurationUri);
    }
}
