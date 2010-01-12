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
import java.io.Serializable;

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

    public void process(Exchange exchange) throws Exception {
         
        cacheManager = new CacheManagerFactory().instantiateCacheManager();
        
        if (LOG.isTraceEnabled()) {
            LOG.trace("Cache Name: " + config.getCacheName());
        }
        if (cacheManager.cacheExists(config.getCacheName())) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Found an existing cache: " + config.getCacheName());
                LOG.trace("Cache " + config.getCacheName() + " currently contains " + cacheManager.getCache(config.getCacheName()).getSize() + " elements");
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
            if (LOG.isDebugEnabled()) {
                LOG.debug("Added a new cache: " + cache.getName());
            }
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
        
        performCacheOperation(exchange, operation, key);
    }
    
    private void performCacheOperation(Exchange exchange, String operation, String key) throws Exception {
        Object element;

        Object body = exchange.getIn().getBody();
        if (body instanceof Serializable) {
            element = body;
        } else {
            InputStream is = exchange.getContext().getTypeConverter().mandatoryConvertTo(InputStream.class, body);

            // Read InputStream into a byte[] buffer
            byte[] buffer = new byte[is.available()];
            int n = is.available();
            for (int j = 0; j < n; j++) {
                buffer[j] = (byte)is.read();
            }

            element = buffer;
        }

        if (operation.equalsIgnoreCase("ADD")) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Adding an element with key " + key + " into the Cache");
            }
            cache.put(new Element(key, element), true);
        } else if (operation.equalsIgnoreCase("UPDATE")) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Updating an element with key " + key + " into the Cache");
            }
            cache.put(new Element(key, element), true);
        } else if (operation.equalsIgnoreCase("DELETEALL")) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Deleting All elements from the Cache");
            }
            cache.removeAll();
        } else if (operation.equalsIgnoreCase("DELETE")) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Deleting an element with key " + key + " into the Cache");
            }
            cache.remove(key, true);
        }
    }

}
