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

import java.net.URI;
import java.util.Map;

import net.sf.ehcache.store.MemoryStoreEvictionPolicy;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.URISupport;

@UriParams
public class CacheConfiguration implements Cloneable {
    @UriPath
    private String cacheName;
    @UriParam(defaultValue = "1000")
    private int maxElementsInMemory = 1000;
    @UriParam(defaultValue = "LFU", enums = "LRU,LFU,FIFO,CLOCK")
    private MemoryStoreEvictionPolicy memoryStoreEvictionPolicy = MemoryStoreEvictionPolicy.LFU;
    @UriParam(defaultValue = "true")
    private boolean overflowToDisk = true;
    @UriParam
    private String diskStorePath;
    @UriParam(defaultValue = "false")
    private boolean eternal;
    @UriParam(defaultValue = "300")
    private long timeToLiveSeconds = 300;
    @UriParam(defaultValue = "300")
    private long timeToIdleSeconds = 300;
    @UriParam(defaultValue = "false")
    private boolean diskPersistent;
    @UriParam(defaultValue = "false")
    private long diskExpiryThreadIntervalSeconds;
    @UriParam
    private boolean objectCache;
    @UriParam
    private CacheEventListenerRegistry eventListenerRegistry = new CacheEventListenerRegistry();
    @UriParam
    private CacheLoaderRegistry cacheLoaderRegistry = new CacheLoaderRegistry();

    public CacheConfiguration() {
    }

    public CacheConfiguration(URI uri) throws Exception {
        parseURI(uri);
    }

    public CacheConfiguration copy() {
        try {
            CacheConfiguration copy = (CacheConfiguration) clone();
            // override any properties where a reference copy isn't what we want
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public void parseURI(URI uri) throws Exception {
        String protocol = uri.getScheme();
        
        if (!protocol.equalsIgnoreCase("cache")) {
            throw new IllegalArgumentException("Unrecognized Cache protocol: " + protocol + " for uri: " + uri);
        }
        
        setCacheName(uri.getHost());
        
        Map<String, Object> cacheSettings = URISupport.parseParameters(uri);
        if (cacheSettings.containsKey("maxElementsInMemory")) {
            setMaxElementsInMemory(Integer.valueOf((String) cacheSettings.get("maxElementsInMemory")));
        }
        if (cacheSettings.containsKey("overflowToDisk")) {
            setOverflowToDisk(Boolean.valueOf((String) cacheSettings.get("overflowToDisk")));
        }
        if (cacheSettings.containsKey("diskStorePath")) {
            setDiskStorePath((String)cacheSettings.get("diskStorePath"));
        }
        if (cacheSettings.containsKey("eternal")) {
            setEternal(Boolean.valueOf((String) cacheSettings.get("eternal")));
        }
        if (cacheSettings.containsKey("timeToLiveSeconds")) {
            setTimeToLiveSeconds(Long.valueOf((String) cacheSettings.get("timeToLiveSeconds")));
        }
        if (cacheSettings.containsKey("timeToIdleSeconds")) {
            setTimeToIdleSeconds(Long.valueOf((String) cacheSettings.get("timeToIdleSeconds")));
        }
        if (cacheSettings.containsKey("diskPersistent")) {
            setDiskPersistent(Boolean.valueOf((String) cacheSettings.get("diskPersistent")));
        }
        if (cacheSettings.containsKey("diskExpiryThreadIntervalSeconds")) {
            setDiskExpiryThreadIntervalSeconds(Long.valueOf((String) cacheSettings.get("diskExpiryThreadIntervalSeconds")));
        }
        if (cacheSettings.containsKey("memoryStoreEvictionPolicy")) {
            String policy = (String) cacheSettings.get("memoryStoreEvictionPolicy");
            // remove leading if any given as fromString uses LRU, LFU or FIFO
            policy = policy.replace("MemoryStoreEvictionPolicy.", "");
            setMemoryStoreEvictionPolicy(MemoryStoreEvictionPolicy.fromString(policy));
        }
        if (cacheSettings.containsKey("objectCache")) {
            setObjectCache(Boolean.valueOf((String) cacheSettings.get("objectCache")));
        }

        if (isObjectCache() && (isOverflowToDisk() || isDiskPersistent())) {
            throw new IllegalArgumentException("Unable to create object cache with disk access");
        }
    }
    
    public String getCacheName() {
        return cacheName;
    }

    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }

    public int getMaxElementsInMemory() {
        return maxElementsInMemory;
    }

    public void setMaxElementsInMemory(int maxElementsInMemory) {
        this.maxElementsInMemory = maxElementsInMemory;
    }

    public MemoryStoreEvictionPolicy getMemoryStoreEvictionPolicy() {
        return memoryStoreEvictionPolicy;
    }

    public void setMemoryStoreEvictionPolicy(
            MemoryStoreEvictionPolicy memoryStoreEvictionPolicy) {
        this.memoryStoreEvictionPolicy = memoryStoreEvictionPolicy;
    }

    public boolean isOverflowToDisk() {
        return overflowToDisk;
    }

    public void setOverflowToDisk(boolean overflowToDisk) {
        this.overflowToDisk = overflowToDisk;
    }

    public String getDiskStorePath() {
        return diskStorePath;
    }

    public void setDiskStorePath(String diskStorePath) {
        this.diskStorePath = diskStorePath;
    }

    public boolean isEternal() {
        return eternal;
    }

    public void setEternal(boolean eternal) {
        this.eternal = eternal;
    }

    public long getTimeToLiveSeconds() {
        return timeToLiveSeconds;
    }

    public void setTimeToLiveSeconds(long timeToLiveSeconds) {
        this.timeToLiveSeconds = timeToLiveSeconds;
    }

    public long getTimeToIdleSeconds() {
        return timeToIdleSeconds;
    }

    public void setTimeToIdleSeconds(long timeToIdleSeconds) {
        this.timeToIdleSeconds = timeToIdleSeconds;
    }

    public boolean isDiskPersistent() {
        return diskPersistent;
    }

    public void setDiskPersistent(boolean diskPersistent) {
        this.diskPersistent = diskPersistent;
    }

    public long getDiskExpiryThreadIntervalSeconds() {
        return diskExpiryThreadIntervalSeconds;
    }

    public void setDiskExpiryThreadIntervalSeconds(long diskExpiryThreadIntervalSeconds) {
        this.diskExpiryThreadIntervalSeconds = diskExpiryThreadIntervalSeconds;
    }

    public void setEventListenerRegistry(CacheEventListenerRegistry eventListenerRegistry) {
        this.eventListenerRegistry = eventListenerRegistry;
    }

    public CacheEventListenerRegistry getEventListenerRegistry() {
        return eventListenerRegistry;
    }

    public void setCacheLoaderRegistry(CacheLoaderRegistry cacheLoaderRegistry) {
        this.cacheLoaderRegistry = cacheLoaderRegistry;
    }

    public CacheLoaderRegistry getCacheLoaderRegistry() {
        return cacheLoaderRegistry;
    }

    public boolean isObjectCache() {
        return objectCache;
    }

    public void setObjectCache(boolean objectCache) {
        this.objectCache = objectCache;
    }
}
