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
package org.apache.camel.component.jcache;

import java.util.Map;
import java.util.Properties;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.Configuration;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;

/**
 * Represents the component that manages {@link JCacheEndpoint}.
 */
public class JCacheComponent extends UriEndpointComponent {

    private String cachingProvider;
    private Configuration cacheConfiguration;
    private Properties cacheConfigurationProperties;
    private String configurationUri;

    public JCacheComponent() {
        super(JCacheEndpoint.class);
    }

    public JCacheComponent(CamelContext context) {
        super(context, JCacheEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String cacheName = remaining;
        JCacheConfiguration configuration = new JCacheConfiguration(getCamelContext(), cacheName);

        configuration.setCachingProvider(cachingProvider);
        configuration.setCacheConfiguration(cacheConfiguration);
        configuration.setCacheConfigurationProperties(cacheConfigurationProperties);
        configuration.setConfigurationUri(configurationUri);

        setProperties(configuration, parameters);
        return new JCacheEndpoint(uri, this, configuration);
    }

    /**
     * The fully qualified class name of the {@link javax.cache.spi.CachingProvider}
     */
    public String getCachingProvider() {
        return cachingProvider;
    }

    public void setCachingProvider(String cachingProvider) {
        this.cachingProvider = cachingProvider;
    }

    /**
     * A {@link Configuration} for the {@link Cache}
     */
    public Configuration getCacheConfiguration() {
        return cacheConfiguration;
    }

    public void setCacheConfiguration(Configuration cacheConfiguration) {
        this.cacheConfiguration = cacheConfiguration;
    }

    /**
     * The {@link Properties} for the {@link javax.cache.spi.CachingProvider} to
     * create the {@link CacheManager}
     */
    public Properties getCacheConfigurationProperties() {
        return cacheConfigurationProperties;
    }

    public void setCacheConfigurationProperties(Properties cacheConfigurationProperties) {
        this.cacheConfigurationProperties = cacheConfigurationProperties;
    }

    /**
     * An implementation specific URI for the {@link CacheManager}
     */
    public String getConfigurationUri() {
        return configurationUri;
    }

    public void setConfigurationUri(String configurationUri) {
        this.configurationUri = configurationUri;
    }

}
