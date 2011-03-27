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
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.impl.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CacheProducer extends DefaultProducer {
    private static final transient Logger LOG = LoggerFactory.getLogger(CacheProducer.class);
    private CacheConfiguration config;
    private CacheManager cacheManager;
    private Ehcache cache;

    public CacheProducer(Endpoint endpoint, CacheConfiguration config) throws Exception {
        super(endpoint);
        this.config = config;
    }

    @Override
    protected void doStart() throws Exception {
        cacheManager = getEndpoint().getCacheManagerFactory().instantiateCacheManager();
        super.doStart();
    }

    @Override
    public CacheEndpoint getEndpoint() {
        return (CacheEndpoint) super.getEndpoint();
    }

    public void process(Exchange exchange) throws Exception {
        LOG.trace("Cache Name: {}", config.getCacheName());

        if (cacheManager.cacheExists(config.getCacheName())) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Found an existing cache: {}", config.getCacheName());
                LOG.trace("Cache {} currently contains {} elements",
                        config.getCacheName(), cacheManager.getCache(config.getCacheName()).getSize());
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

        String key = exchange.getIn().getHeader(CacheConstants.CACHE_KEY, String.class);
        String operation = exchange.getIn().getHeader(CacheConstants.CACHE_OPERATION, String.class);

        if (operation == null) {
            throw new CacheException("Operation not specified in the message header [" + CacheConstants.CACHE_KEY + "]");
        }
        if ((key == null) && (!operation.equalsIgnoreCase(CacheConstants.CACHE_OPERATION_DELETEALL))) {
            throw new CacheException("Cache Key is not specified in message header header or endpoint URL.");
        }

        performCacheOperation(exchange, operation, key);
    }

    private void performCacheOperation(Exchange exchange, String operation, String key) throws Exception {
        Object element;

        if (operation.equalsIgnoreCase(CacheConstants.CACHE_OPERATION_ADD)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Adding an element with key " + key + " into the Cache");
            }
            element = createElementFromBody(exchange, CacheConstants.CACHE_OPERATION_ADD);
            cache.put(new Element(key, element), true);
        } else if (operation.equalsIgnoreCase(CacheConstants.CACHE_OPERATION_UPDATE)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Updating an element with key " + key + " into the Cache");
            }
            element = createElementFromBody(exchange, CacheConstants.CACHE_OPERATION_UPDATE);
            cache.put(new Element(key, element), true);
        } else if (operation.equalsIgnoreCase(CacheConstants.CACHE_OPERATION_DELETEALL)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Deleting All elements from the Cache");
            }
            cache.removeAll();
        } else if (operation.equalsIgnoreCase(CacheConstants.CACHE_OPERATION_DELETE)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Deleting an element with key " + key + " into the Cache");
            }
            cache.remove(key, true);
        } else if (operation.equalsIgnoreCase(CacheConstants.CACHE_OPERATION_GET)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Quering an element with key " + key + " from the Cache");
            }
            if (cache.isKeyInCache(key) && cache.get(key) != null) {
                exchange.getIn().setHeader(CacheConstants.CACHE_ELEMENT_WAS_FOUND, true);
                exchange.getIn().setBody(cache.get(key).getValue());
            } else {
                exchange.getIn().removeHeader(CacheConstants.CACHE_ELEMENT_WAS_FOUND);
            }
        } else if (operation.equalsIgnoreCase(CacheConstants.CACHE_OPERATION_CHECK)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Querying an element with key " + key + " from the Cache");
            }
            if (cache.isKeyInCache(key)) {
                exchange.getIn().setHeader(CacheConstants.CACHE_ELEMENT_WAS_FOUND, true);
            } else {
                exchange.getIn().removeHeader(CacheConstants.CACHE_ELEMENT_WAS_FOUND);
            }
        } else {
            throw new CacheException("Operation " + operation + " is not supported.");
        }
    }

    private Object createElementFromBody(Exchange exchange, String cacheOperation) throws NoTypeConversionAvailableException {
        Object element;
        Object body = exchange.getIn().getBody();
        if (body == null) {
            throw new CacheException("Body cannot be null for operation " + cacheOperation);
        } else if (body instanceof Serializable) {
            element = body;
        } else {
            InputStream is = exchange.getContext().getTypeConverter().mandatoryConvertTo(InputStream.class, body);
            // Read InputStream into a byte[] buffer
            element = exchange.getContext().getTypeConverter().mandatoryConvertTo(byte[].class, is);
        }
        return element;
    }

}
