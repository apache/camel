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
import org.apache.camel.util.URISupport;

public class CacheConfiguration implements Cloneable {
    private String cacheName;
    private int maxElementsInMemory = 1000;
    private MemoryStoreEvictionPolicy memoryStoreEvictionPolicy = MemoryStoreEvictionPolicy.LFU;
    private boolean overflowToDisk = true;
    private String diskStorePath;
    private boolean eternal;
    private long timeToLiveSeconds = 300;
    private long timeToIdleSeconds = 300;
    private boolean diskPersistent;
    private long diskExpiryThreadIntervalSeconds;

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
        
        Map cacheSettings = URISupport.parseParameters(uri);
        if (cacheSettings.containsKey("maxElementsInMemory")) {
            setMaxElementsInMemory(Integer.valueOf((String) cacheSettings.get("maxElementsInMemory")).intValue());
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
            setTimeToLiveSeconds(Long.valueOf((String) cacheSettings.get("timeToLiveSeconds")).longValue());
        }
        if (cacheSettings.containsKey("timeToIdleSeconds")) {
            setTimeToIdleSeconds(Long.valueOf((String) cacheSettings.get("timeToIdleSeconds")).longValue());
        }
        if (cacheSettings.containsKey("diskPersistent")) {
            setDiskPersistent(Boolean.valueOf((String) cacheSettings.get("diskPersistent")));
        }
        if (cacheSettings.containsKey("diskExpiryThreadIntervalSeconds")) {
            setDiskExpiryThreadIntervalSeconds(Long.valueOf((String) cacheSettings.get("diskExpiryThreadIntervalSeconds")).longValue());
        }
        if (cacheSettings.containsKey("memoryStoreEvictionPolicy")) {
            String policy = (String) cacheSettings.get("memoryStoreEvictionPolicy");
            // remove leading if any given as fromString uses LRU, LFU or FIFO
            policy = policy.replace("MemoryStoreEvictionPolicy.", "");
            setMemoryStoreEvictionPolicy(MemoryStoreEvictionPolicy.fromString(policy));
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
   
}
