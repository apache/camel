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

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheException;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.cache.factory.CacheManagerFactory;
import org.apache.camel.impl.DefaultProducer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class CacheProducer extends DefaultProducer {
    private static final transient Log LOG = LogFactory.getLog(CacheProducer.class);
    Endpoint endpoint;
    CacheConfiguration config;
    CacheManager cacheManager;
    Ehcache cache;
    
    public CacheProducer(Endpoint endpoint, CacheConfiguration config) throws Exception {
        super(endpoint);
        this.endpoint = endpoint;
        this.config = config;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
    }

    public void process(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();
        InputStream is = exchange.getContext().getTypeConverter().convertTo(InputStream.class, body);
        
        // Read InputStream into a byte[] buffer
        byte[] buffer = new byte[is.available()];
        int n = is.available();
        for (int j = 0; j < n; j++) {
            buffer[j] = (byte)is.read();
        }        
        
        // Cache the buffer to the specified Cache against the specified key 
        cacheManager = new CacheManagerFactory().instantiateCacheManager();
        
        LOG.debug("Cache Name: " + config.getCacheName());
        if (cacheManager.cacheExists(config.getCacheName())) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Found an existing cache: " + config.getCacheName());
                LOG.debug("Cache " + config.getCacheName() + " currently contains " + cacheManager.getCache(config.getCacheName()).getSize() + " elements");
            }
            cache = cacheManager.getCache(config.getCacheName());
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
            cacheManager.addCache(cache);
            LOG.debug("Added a new cache: " + cache.getName());            
        }
       
        
        String key = (String) exchange.getIn().getHeader("CACHE_KEY");
        String operation = (String) exchange.getIn().getHeader("CACHE_OPERATION");
        if (operation == null) {
            throw new CacheException("Operation property is not specified in the incoming exchange header."
                + "A valid Operation property must be set to ADD, UPDATE, DELETE, DELETEALL");
        }
        if ((key == null) && (!operation.equalsIgnoreCase("DELETEALL"))) {
            throw new CacheException("Cache Key is not specified in exchange either header or URL. Unable to add objects to the cache without a Key");
        }
        
        performCacheOperation(operation, key, buffer);
    }

    private void performCacheOperation(String operation, String key, byte[] buffer) {
        if (operation.equalsIgnoreCase("DELETEALL")) {
            LOG.debug("Deleting All elements from the Cache");
            cache.removeAll();
        } else if (operation.equalsIgnoreCase("ADD")) {
            LOG.debug("Adding an element with key " + key + " into the Cache");
            cache.put(new Element(key, buffer), true);
        } else if (operation.equalsIgnoreCase("UPDATE")) {
            LOG.debug("Updating an element with key " + key + " into the Cache");
            cache.put(new Element(key, buffer), true);
        } else if (operation.equalsIgnoreCase("DELETE")) {
            LOG.debug("Deleting an element with key " + key + " into the Cache");
            cache.remove(key, true);
        }
    }
}
