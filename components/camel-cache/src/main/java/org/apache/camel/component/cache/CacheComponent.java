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
package org.apache.camel.component.cache;

import java.io.InputStream;
import java.util.Map;

import net.sf.ehcache.store.MemoryStoreEvictionPolicy;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ResourceHelper;
import org.apache.camel.util.ServiceHelper;

public class CacheComponent extends UriEndpointComponent {
    private CacheConfiguration configuration;
    private CacheManagerFactory cacheManagerFactory;
    private String configurationFile;

    public CacheComponent() {
        super(CacheEndpoint.class);
        configuration = new CacheConfiguration();
    }

    public CacheComponent(CamelContext context) {
        super(context, CacheEndpoint.class);
        configuration = new CacheConfiguration();
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected Endpoint createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        // must use copy as each endpoint can have different options
        ObjectHelper.notNull(configuration, "configuration");

        CacheConfiguration config = configuration.copy();
        setProperties(this, parameters);
        setProperties(config, parameters);
        config.setCacheName(remaining);

        CacheEndpoint cacheEndpoint = new CacheEndpoint(uri, this, config, cacheManagerFactory);
        setProperties(cacheEndpoint, parameters);
        return cacheEndpoint;
    }

    public CacheManagerFactory getCacheManagerFactory() {
        return cacheManagerFactory;
    }

    /**
     * To use the given CacheManagerFactory for creating the CacheManager.
     * <p/>
     * By default the DefaultCacheManagerFactory is used.
     */
    public void setCacheManagerFactory(CacheManagerFactory cacheManagerFactory) {
        this.cacheManagerFactory = cacheManagerFactory;
    }

    public CacheConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Sets the Cache configuration. Properties of the shared configuration can also be set individually.
     *
     * @param configuration the configuration to use by default for endpoints
     */
    public void setConfiguration(CacheConfiguration configuration) {
        this.configuration = configuration;
    }

    public String getConfigurationFile() {
        return configurationFile;
    }

    /**
     * Sets the location of the <tt>ehcache.xml</tt> file to load from classpath or file system.
     * <p/>
     * By default the file is loaded from <tt>classpath:ehcache.xml</tt>
     */
    public void setConfigurationFile(String configurationFile) {
        this.configurationFile = configurationFile;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (cacheManagerFactory == null) {
            if (configurationFile != null) {
                InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext(), configurationFile);
                cacheManagerFactory = new DefaultCacheManagerFactory(is, configurationFile);
            } else {
                cacheManagerFactory = new DefaultCacheManagerFactory();
            }
        }
        ServiceHelper.startService(cacheManagerFactory);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(cacheManagerFactory);
        super.doStop();
    }

    public String getCacheName() {
        return configuration.getCacheName();
    }

    /**
     * Name of the cache
     * @param cacheName
     */
    public void setCacheName(String cacheName) {
        configuration.setCacheName(cacheName);
    }

    public int getMaxElementsInMemory() {
        return configuration.getMaxElementsInMemory();
    }

    /**
     * The number of elements that may be stored in the defined cache in memory.
     * @param maxElementsInMemory
     */
    public void setMaxElementsInMemory(int maxElementsInMemory) {
        configuration.setMaxElementsInMemory(maxElementsInMemory);
    }

    public MemoryStoreEvictionPolicy getMemoryStoreEvictionPolicy() {
        return configuration.getMemoryStoreEvictionPolicy();
    }

    /**
     * Which eviction strategy to use when maximum number of elements in memory is reached. The strategy defines
     * which elements to be removed.
     * <ul>
     *     <li>LRU - Lest Recently Used</li>
     *     <li>LFU - Lest Frequently Used</li>
     *     <li>FIFO - First In First Out</li>
     * </ul>
     * @param memoryStoreEvictionPolicy
     */
    public void setMemoryStoreEvictionPolicy(MemoryStoreEvictionPolicy memoryStoreEvictionPolicy) {
        configuration.setMemoryStoreEvictionPolicy(memoryStoreEvictionPolicy);
    }

    public boolean isOverflowToDisk() {
        return configuration.isOverflowToDisk();
    }

    /**
     * Specifies whether cache may overflow to disk
     * @param overflowToDisk
     */
    public void setOverflowToDisk(boolean overflowToDisk) {
        configuration.setOverflowToDisk(overflowToDisk);
    }

    public boolean isEternal() {
        return configuration.isEternal();
    }

    /**
     * Sets whether elements are eternal. If eternal, timeouts are ignored and the element never expires.
     * @param eternal
     */
    public void setEternal(boolean eternal) {
        configuration.setEternal(eternal);
    }

    public long getTimeToLiveSeconds() {
        return configuration.getTimeToLiveSeconds();
    }

    /**
     * The maximum time between creation time and when an element expires. Is used only if the element is not eternal
     * @param timeToLiveSeconds
     */
    public void setTimeToLiveSeconds(long timeToLiveSeconds) {
        configuration.setTimeToLiveSeconds(timeToLiveSeconds);
    }

    public long getTimeToIdleSeconds() {
        return configuration.getTimeToIdleSeconds();
    }

    /**
     * The maximum amount of time between accesses before an element expires
     * @param timeToIdleSeconds
     */
    public void setTimeToIdleSeconds(long timeToIdleSeconds) {
        configuration.setTimeToIdleSeconds(timeToIdleSeconds);
    }

    public boolean isDiskPersistent() {
        return configuration.isDiskPersistent();
    }

    /**
     * Whether the disk store persists between restarts of the application.
     * @param diskPersistent
     */
    public void setDiskPersistent(boolean diskPersistent) {
        configuration.setDiskPersistent(diskPersistent);
    }

    public long getDiskExpiryThreadIntervalSeconds() {
        return configuration.getDiskExpiryThreadIntervalSeconds();
    }

    /**
     * The number of seconds between runs of the disk expiry thread.
     * @param diskExpiryThreadIntervalSeconds
     */
    public void setDiskExpiryThreadIntervalSeconds(long diskExpiryThreadIntervalSeconds) {
        configuration.setDiskExpiryThreadIntervalSeconds(diskExpiryThreadIntervalSeconds);
    }

    /**
     * To configure event listeners using the CacheEventListenerRegistry
     * @param eventListenerRegistry
     */
    public void setEventListenerRegistry(CacheEventListenerRegistry eventListenerRegistry) {
        configuration.setEventListenerRegistry(eventListenerRegistry);
    }

    public CacheEventListenerRegistry getEventListenerRegistry() {
        return configuration.getEventListenerRegistry();
    }

    /**
     * To configure cache loader using the CacheLoaderRegistry
     * @param cacheLoaderRegistry
     */
    public void setCacheLoaderRegistry(CacheLoaderRegistry cacheLoaderRegistry) {
        configuration.setCacheLoaderRegistry(cacheLoaderRegistry);
    }

    public CacheLoaderRegistry getCacheLoaderRegistry() {
        return configuration.getCacheLoaderRegistry();
    }

    public boolean isObjectCache() {
        return configuration.isObjectCache();
    }

    /**
     * Whether to turn on allowing to store non serializable objects in the cache.
     * If this option is enabled then overflow to disk cannot be enabled as well.
     * @param objectCache
     */
    public void setObjectCache(boolean objectCache) {
        configuration.setObjectCache(objectCache);
    }
}
