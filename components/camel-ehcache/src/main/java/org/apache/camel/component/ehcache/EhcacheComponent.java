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

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ResourceHelper;
import org.ehcache.CacheManager;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.Configuration;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.xml.XmlConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the component that manages {@link DefaultComponent}.
 */
public class EhcacheComponent extends DefaultComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger(EhcacheComponent.class);

    private final ConcurrentMap<Object, EhcacheManager> managers = new ConcurrentHashMap<>();

    @Metadata(label = "advanced")
    private EhcacheConfiguration configuration = new EhcacheConfiguration();

    public EhcacheComponent() {
    }

    public EhcacheComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        EhcacheConfiguration configuration = this.configuration.copy();
        setProperties(configuration, parameters);

        return new EhcacheEndpoint(uri, this, remaining, createCacheManager(configuration), configuration);
    }

    // ****************************
    // Helpers
    // ****************************

    private EhcacheManager createCacheManager(EhcacheConfiguration configuration) throws IOException {
        ObjectHelper.notNull(configuration, "Camel Ehcache configuration");

        // Check if a cache manager has been configured
        if (configuration.hasCacheManager()) {
            LOGGER.info("EhcacheManager configured with supplied CacheManager");

            return managers.computeIfAbsent(
                configuration.getCacheManager(),
                m -> new EhcacheManager(
                    CacheManager.class.cast(m),
                    false,
                    configuration)
            );
        }

        // Check if a cache manager configuration has been provided
        if (configuration.hasCacheManagerConfiguration()) {
            LOGGER.info("EhcacheManager configured with supplied CacheManagerConfiguration");

            return managers.computeIfAbsent(
                configuration.getCacheManagerConfiguration(),
                c -> new EhcacheManager(
                    CacheManagerBuilder.newCacheManager(Configuration.class.cast(c)),
                    true,
                    configuration
                )
            );
        }

        // Check if a configuration file has been provided
        if (configuration.hasConfigurationUri()) {
            String configurationUri = configuration.getConfigurationUri();
            ClassResolver classResolver = getCamelContext().getClassResolver();

            URL url = ResourceHelper.resolveMandatoryResourceAsUrl(classResolver, configurationUri);

            LOGGER.info("EhcacheManager configured with supplied URI {}", url);

            return managers.computeIfAbsent(
                url,
                u -> new EhcacheManager(
                    CacheManagerBuilder.newCacheManager(new XmlConfiguration(URL.class.cast(u))),
                    true,
                    configuration
                )
            );
        }

        LOGGER.info("EhcacheManager configured with default builder");
        return new EhcacheManager(CacheManagerBuilder.newCacheManagerBuilder().build(), true, configuration);
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
        // The component configuration can't be null
        ObjectHelper.notNull(configuration, "EhcacheConfiguration");

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
    public void setCacheConfiguration(CacheConfiguration<?, ?> cacheConfiguration) {
        this.configuration.setConfiguration(cacheConfiguration);
    }

    public CacheConfiguration<?, ?> getCacheConfiguration() {
        return this.configuration.getConfiguration();
    }

    public Map<String, CacheConfiguration<?, ?>> getCachesConfigurations() {
        return configuration.getConfigurations();
    }

    /**
     * A map of caches configurations to be used to create caches.
     */
    public void setCachesConfigurations(Map<String, CacheConfiguration<?, ?>> configurations) {
        configuration.setConfigurations(configurations);
    }

    public void addCachesConfigurations(Map<String, CacheConfiguration<?, ?>> configurations) {
        configuration.addConfigurations(configurations);
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
