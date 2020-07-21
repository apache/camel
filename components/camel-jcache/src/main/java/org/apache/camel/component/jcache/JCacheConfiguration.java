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

import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.Configuration;
import javax.cache.configuration.Factory;
import javax.cache.event.CacheEntryEventFilter;
import javax.cache.expiry.ExpiryPolicy;
import javax.cache.integration.CacheLoader;
import javax.cache.integration.CacheWriter;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.support.EndpointHelper;

@UriParams
public class JCacheConfiguration {
    @UriParam(label = "common")
    private String cachingProvider;

    @UriParam
    private Configuration cacheConfiguration;

    @UriParam
    private Properties cacheConfigurationProperties;

    @UriParam
    private String configurationUri;

    @UriParam(label = "advanced")
    private Factory<CacheLoader> cacheLoaderFactory;

    @UriParam(label = "advanced")
    private Factory<CacheWriter> cacheWriterFactory;

    @UriParam(label = "advanced")
    private Factory<ExpiryPolicy> expiryPolicyFactory;

    @UriParam
    private boolean readThrough;

    @UriParam
    private boolean writeThrough;

    @UriParam(defaultValue = "true")
    private boolean storeByValue = true;

    @UriParam
    private boolean statisticsEnabled;

    @UriParam
    private boolean managementEnabled;

    @UriParam(label = "consumer", enums = "CREATED,UPDATED,REMOVED,EXPIRED")
    private String filteredEvents;

    @UriParam(label = "consumer,advanced")
    private List<CacheEntryEventFilter> eventFilters;

    @UriParam(label = "consumer")
    private boolean oldValueRequired;

    @UriParam(label = "consumer")
    private boolean synchronous;

    @UriParam(label = "producer")
    private String action;

    @UriParam(label = "advanced", defaultValue = "true")
    private boolean createCacheIfNotExists = true;

    @UriParam(label = "advanced")
    private boolean lookupProviders;

    private CamelContext camelContext;
    private String cacheName;


    public JCacheConfiguration() {
        this(null, null);
    }

    public JCacheConfiguration(String cacheName) {
        this(null, cacheName);
    }

    public JCacheConfiguration(CamelContext camelContext, String cacheName) {
        this.camelContext = camelContext;
        this.cacheName = cacheName;
    }

    public CamelContext getCamelContext() {
        return this.camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public String getCacheName() {
        return this.cacheName;
    }

    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }

    public ClassLoader getApplicationContextClassLoader() {
        return this.camelContext != null
            ? this.camelContext.getApplicationContextClassLoader()
            : null;
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

    /**
     * The {@link CacheLoader} factory
     */
    public Factory<CacheLoader> getCacheLoaderFactory() {
        return cacheLoaderFactory;
    }

    public void setCacheLoaderFactory(Factory<CacheLoader> cacheLoaderFactory) {
        this.cacheLoaderFactory = cacheLoaderFactory;
    }

    /**
     * The {@link CacheWriter} factory
     */
    public Factory<CacheWriter> getCacheWriterFactory() {
        return cacheWriterFactory;
    }

    public void setCacheWriterFactory(Factory<CacheWriter> cacheWriterFactory) {
        this.cacheWriterFactory = cacheWriterFactory;
    }

    /**
     * The {@link ExpiryPolicy} factory
     */
    public Factory<ExpiryPolicy> getExpiryPolicyFactory() {
        return expiryPolicyFactory;
    }

    public void setExpiryPolicyFactory(Factory<ExpiryPolicy> expiryPolicyFactory) {
        this.expiryPolicyFactory = expiryPolicyFactory;
    }

    /**
     * If read-through caching should be used
     */
    public boolean isReadThrough() {
        return readThrough;
    }

    public void setReadThrough(boolean readThrough) {
        this.readThrough = readThrough;
    }

    /**
     * If write-through caching should be used
     */
    public boolean isWriteThrough() {
        return writeThrough;
    }

    public void setWriteThrough(boolean writeThrough) {
        this.writeThrough = writeThrough;
    }

    /**
     * If cache should use store-by-value or store-by-reference semantics
     */
    public boolean isStoreByValue() {
        return storeByValue;
    }

    public void setStoreByValue(boolean storeByValue) {
        this.storeByValue = storeByValue;
    }

    /**
     * Whether statistics gathering is enabled
     */
    public boolean isStatisticsEnabled() {
        return statisticsEnabled;
    }

    public void setStatisticsEnabled(boolean statisticsEnabled) {
        this.statisticsEnabled = statisticsEnabled;
    }

    /**
     * Whether management gathering is enabled
     */
    public boolean isManagementEnabled() {
        return managementEnabled;
    }

    public void setManagementEnabled(boolean managementEnabled) {
        this.managementEnabled = managementEnabled;
    }

    /**
     * Events a consumer should filter (multiple events can be separated by comma).
     * If using filteredEvents option, then eventFilters one will be ignored
     */
    public String getFilteredEvents() {
        return filteredEvents;
    }

    public void setFilteredEvents(String filteredEvents) {
        this.filteredEvents = filteredEvents;
    }

    /**
     * The CacheEntryEventFilter. If using eventFilters option, then filteredEvents one will be ignored
     */
    public List<CacheEntryEventFilter> getEventFilters() {
        return eventFilters;
    }

    public void setEventFilters(List<CacheEntryEventFilter> eventFilters) {
        if (eventFilters != null) {
            this.eventFilters = new LinkedList<>(eventFilters);
        }
    }

    public void setEventFilters(String eventFilter) {
        this.eventFilters = EndpointHelper.resolveReferenceListParameter(camelContext, eventFilter, CacheEntryEventFilter.class);
    }

    /**
     * if the old value is required for events
     */
    public boolean isOldValueRequired() {
        return oldValueRequired;
    }

    public void setOldValueRequired(boolean oldValueRequired) {
        this.oldValueRequired = oldValueRequired;
    }

    /**
     * if the event listener should block the thread causing the event
     */
    public boolean isSynchronous() {
        return synchronous;
    }

    public void setSynchronous(boolean synchronous) {
        this.synchronous = synchronous;
    }

    public String getAction() {
        return action;
    }

    /**
     * To configure using a cache operation by default. If an operation in the
     * message header, then the operation from the header takes precedence.
     */
    public void setAction(String action) {
        this.action = action;
    }

    public boolean isCreateCacheIfNotExists() {
        return createCacheIfNotExists;
    }

    /**
     * Configure if a cache need to be created if it does exist or can't be
     * pre-configured.
     */
    public void setCreateCacheIfNotExists(boolean createCacheIfNotExists) {
        this.createCacheIfNotExists = createCacheIfNotExists;
    }

    public boolean isLookupProviders() {
        return lookupProviders;
    }

    /**
     * Configure if a camel-cache should try to find implementations of jcache
     * api in runtimes like OSGi.
     */
    public void setLookupProviders(boolean lookupProviders) {
        this.lookupProviders = lookupProviders;
    }
}
