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

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.util.ObjectHelper;
import org.ehcache.CacheManager;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.Configuration;
import org.ehcache.event.EventFiring;
import org.ehcache.event.EventOrdering;
import org.ehcache.event.EventType;

@UriParams
public class EhcacheConfiguration implements Cloneable {
    @UriParam(defaultValue = "true")
    private boolean createCacheIfNotExist = true;
    @UriParam(label = "producer")
    private String action;
    @UriParam(label = "producer")
    private Object key;
    @UriParam
    private CacheManager cacheManager;
    @UriParam
    private Configuration cacheManagerConfiguration;
    @UriParam
    private String configurationUri;
    @UriParam(label = "advanced")
    private CacheConfiguration<?, ?> configuration;
    @UriParam(label = "advanced")
    private Map<String, CacheConfiguration<?, ?>> configurations;
    @UriParam(label = "advanced", javaType = "java.lang.String", defaultValue = "java.lang.Object")
    private Class<?> keyType = Object.class;
    @UriParam(label = "advanced", javaType = "java.lang.String", defaultValue = "java.lang.Object")
    private Class<?> valueType = Object.class;
    @UriParam(label = "consumer", defaultValue = "ORDERED")
    private EventOrdering eventOrdering = EventOrdering.ORDERED;
    @UriParam(label = "consumer", defaultValue = "ASYNCHRONOUS")
    private EventFiring eventFiring = EventFiring.ASYNCHRONOUS;
    @UriParam(label = "consumer", enums = "EVICTED,EXPIRED,REMOVED,CREATED,UPDATED", defaultValue = "EVICTED,EXPIRED,REMOVED,CREATED,UPDATED")
    private Set<EventType> eventTypes = EnumSet.of(EventType.values()[0], EventType.values());

    public EhcacheConfiguration() {
    }

    /**
     * URI pointing to the Ehcache XML configuration file's location
     */
    public void setConfigurationUri(String configurationUri) {
        this.configurationUri = configurationUri;
    }

    public String getConfigurationUri() {
        return configurationUri;
    }

    public boolean hasConfigurationUri() {
        return ObjectHelper.isNotEmpty(configurationUri);
    }

    /**
     * @deprecated use {@link #getConfigurationUri()} instead
     */
    @Deprecated
    public String getConfigUri() {
        return getConfigurationUri();
    }

    /**
     * URI pointing to the Ehcache XML configuration file's location
     *
     * @deprecated use {@link #setConfigurationUri(String)} instead
     */
    @Deprecated
    public void setConfigUri(String configUri) {
        setConfigurationUri(configUri);
    }

    public boolean isCreateCacheIfNotExist() {
        return createCacheIfNotExist;
    }

    /**
     * Configure if a cache need to be created if it does exist or can't be
     * pre-configured.
     */
    public void setCreateCacheIfNotExist(boolean createCacheIfNotExist) {
        this.createCacheIfNotExist = createCacheIfNotExist;
    }

    public String getAction() {
        return action;
    }

    /**
     * To configure the default cache action. If an action is set in the message
     * header, then the operation from the header takes precedence.
     */
    public void setAction(String action) {
        this.action = action;
    }

    public Object getKey() {
        return key;
    }

    /**
     * To configure the default action key. If a key is set in the message
     * header, then the key from the header takes precedence.
     */
    public void setKey(Object key) {
        this.key = key;
    }

    public CacheManager getCacheManager() {
        return cacheManager;
    }

    /**
     * The cache manager
     */
    public void setCacheManager(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public boolean hasCacheManager() {
        return this.cacheManager != null;
    }

    public Configuration getCacheManagerConfiguration() {
        return cacheManagerConfiguration;
    }

    /**
     * The cache manager configuration
     */
    public void setCacheManagerConfiguration(Configuration cacheManagerConfiguration) {
        this.cacheManagerConfiguration = cacheManagerConfiguration;
    }

    public boolean hasCacheManagerConfiguration() {
        return this.cacheManagerConfiguration != null;
    }

    public EventOrdering getEventOrdering() {
        return eventOrdering;
    }

    /**
     * Set the the delivery mode (ordered, unordered)
     */
    public void setEventOrdering(String eventOrdering) {
        setEventOrdering(EventOrdering.valueOf(eventOrdering));
    }

    public void setEventOrdering(EventOrdering eventOrdering) {
        this.eventOrdering = eventOrdering;
    }

    public EventFiring getEventFiring() {
        return eventFiring;
    }

    /**
     * Set the the delivery mode (synchronous, asynchronous)
     */
    public void setEventFiring(String eventFiring) {
        setEventFiring(EventFiring.valueOf(eventFiring));
    }

    public void setEventFiring(EventFiring eventFiring) {
        this.eventFiring = eventFiring;
    }

    public Set<EventType> getEventTypes() {
        return eventTypes;
    }

    /**
     * Set the type of events to listen for
     */
    public void setEventTypes(String eventTypesString) {
        Set<EventType> eventTypes = new HashSet<>();
        String[] events = eventTypesString.split(",");
        for (String event : events) {
            eventTypes.add(EventType.valueOf(event));
        }

        setEventTypes(eventTypes);
    }

    public void setEventTypes(Set<EventType> eventTypes) {
        this.eventTypes = new HashSet<>(eventTypes);
    }

    // ****************************
    // Cache Configuration
    // ****************************

    /**
     * The default cache configuration to be used to create caches.
     */
    public void setConfiguration(CacheConfiguration<?, ?> configuration) {
        this.configuration = configuration;
    }

    public CacheConfiguration<?, ?> getConfiguration() {
        return configuration;
    }

    public boolean hasConfiguration() {
        return ObjectHelper.isNotEmpty(configuration);
    }

    public boolean hasConfiguration(String name) {
        return ObjectHelper.applyIfNotEmpty(configurations, c -> c.containsKey(name), () -> false);
    }

    /**
     * A map of cache configuration to be used to create caches.
     */
    public Map<String, CacheConfiguration<?, ?>> getConfigurations() {
        return configurations;
    }

    public void setConfigurations(Map<String, CacheConfiguration<?, ?>> configurations) {
        this.configurations = Map.class.cast(configurations);
    }

    public void addConfigurations(Map<String, CacheConfiguration<?, ?>> configurations) {
        if (this.configurations == null) {
            this.configurations = new HashMap<>();
        }

        this.configurations.putAll(configurations);
    }

    public Class<?> getKeyType() {
        return keyType;
    }

    /**
     * The cache key type, default "java.lang.Object"
     */
    public void setKeyType(Class<?> keyType) {
        this.keyType = keyType;
    }

    public Class<?> getValueType() {
        return valueType;
    }

    /**
     * The cache value type, default "java.lang.Object"
     */
    public void setValueType(Class<?> valueType) {
        this.valueType = valueType;
    }

    // ****************************
    // Cloneable
    // ****************************

    public EhcacheConfiguration copy() {
        try {
            return (EhcacheConfiguration)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
