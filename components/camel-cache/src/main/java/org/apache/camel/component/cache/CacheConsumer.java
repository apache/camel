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

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;

import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class CacheConsumer extends DefaultConsumer {

    private static final transient Log LOG = LogFactory.getLog(CacheConsumer.class);
    private CacheConfiguration config;
    private Ehcache cache;
    private CacheManager cacheManager;

    public CacheConsumer(Endpoint endpoint, Processor processor, CacheConfiguration config) {
        super(endpoint, processor);
        this.config = config;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        createConsumerCacheConnection();
    }

    @Override
    protected void doStop() throws Exception {
        removeConsumerCacheConnection();
        super.doStop();
    }

    @Override
    public CacheEndpoint getEndpoint() {
        return (CacheEndpoint) super.getEndpoint();
    }
    
    protected void createConsumerCacheConnection() {
        cacheManager = getEndpoint().getCacheManagerFactory().instantiateCacheManager();
        CacheEventListener cacheEventListener = new CacheEventListenerFactory().createCacheEventListener(null);
        cacheEventListener.setCacheConsumer(this);

        if (cacheManager.cacheExists(config.getCacheName())) {
            cache = cacheManager.getCache(config.getCacheName());
            cache.getCacheEventNotificationService().registerListener(cacheEventListener);
        } else {
            cache = new Cache(config.getCacheName(), 
                    config.getMaxElementsInMemory(),
                    config.getMemoryStoreEvictionPolicy(), 
                    config.isOverflowToDisk(), 
                    config.getDiskStorePath(), 
                    config.isEternal(), 
                    config.getTimeToLiveSeconds(), 
                    config.getTimeToIdleSeconds(), 
                    config.isDiskPersistent(), 
                    config.getDiskExpiryThreadIntervalSeconds(), 
                    null);
            cache.getCacheEventNotificationService().registerListener(cacheEventListener);
            cacheManager.addCache(cache);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Added a new cache: " + cache.getName());
            }
        }
    }
    
    protected void removeConsumerCacheConnection() {
        cacheManager.removeCache(config.getCacheName());
    }

}
