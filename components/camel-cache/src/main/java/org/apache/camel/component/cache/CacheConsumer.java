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
import org.apache.camel.component.cache.factory.CacheManagerFactory;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class CacheConsumer extends DefaultConsumer {

    private static final transient Log LOG = LogFactory.getLog(CacheConsumer.class);
    CacheEndpoint endpoint;
    CacheConfiguration config;
    Ehcache cache;
    CacheManager cacheManager;
    
    public CacheConsumer(Endpoint endpoint, Processor processor, CacheConfiguration config) {
        super(endpoint, processor);
        this.endpoint = (CacheEndpoint) endpoint;
        this.config = config;
    }

    @Override
    protected void doStart() throws Exception {
        // TODO Auto-generated method stub
        super.doStart();
        createConsumerCacheConnection();
    }

    @Override
    protected void doStop() throws Exception {
        // TODO Auto-generated method stub
        super.doStop();
        removeConsumerCacheConnection();
    }

    @Override
    public CacheEndpoint getEndpoint() {
        return endpoint;
    }
    
    private void createConsumerCacheConnection() {
        cacheManager = new CacheManagerFactory().instantiateCacheManager();
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
            LOG.info("Added a new cache: " + cache.getName());  
        }
    }
    
    private void removeConsumerCacheConnection() {
        cacheManager.removeCache(config.getCacheName());
        if (cacheManager.getCacheNames().length == 0) {
            cacheManager.shutdown();
        }
    }

}
