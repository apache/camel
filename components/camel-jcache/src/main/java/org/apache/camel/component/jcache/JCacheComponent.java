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
package org.apache.camel.component.jcache;

import java.util.Map;
import java.util.Properties;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.Configuration;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultComponent;

@Component("jcache")
public class JCacheComponent extends DefaultComponent {

    private String cachingProvider;
    private Configuration cacheConfiguration;
    private String cacheConfigurationPropertiesRef;
    private Map cacheConfigurationProperties;
    private String configurationUri;

    public JCacheComponent() {
    }

    public JCacheComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String cacheName = remaining;
        JCacheConfiguration configuration = new JCacheConfiguration(getCamelContext(), cacheName);

        configuration.setCachingProvider(cachingProvider);
        configuration.setCacheConfiguration(cacheConfiguration);
        configuration.setCacheConfigurationProperties(loadProperties());
        configuration.setConfigurationUri(configurationUri);

        JCacheEndpoint endpoint = new JCacheEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    private Properties loadProperties() {
        Properties answer = null;
        if (cacheConfigurationProperties != null) {
            answer = new Properties();
            answer.putAll(cacheConfigurationProperties);
        }
        if (answer == null && cacheConfigurationPropertiesRef != null) {
            Map map = CamelContextHelper.mandatoryLookup(getCamelContext(), cacheConfigurationPropertiesRef, Map.class);
            answer = new Properties();
            answer.putAll(map);
        }
        return answer;
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
     * Properties to configure jcache
     */
    public Map getCacheConfigurationProperties() {
        return cacheConfigurationProperties;
    }

    public void setCacheConfigurationProperties(Map cacheConfigurationProperties) {
        this.cacheConfigurationProperties = cacheConfigurationProperties;
    }

    public String getCacheConfigurationPropertiesRef() {
        return cacheConfigurationPropertiesRef;
    }

    /**
     * References to an existing {@link Properties} or {@link Map} to lookup in the registry to use for configuring jcache.
     */
    public void setCacheConfigurationPropertiesRef(String cacheConfigurationPropertiesRef) {
        this.cacheConfigurationPropertiesRef = cacheConfigurationPropertiesRef;
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
