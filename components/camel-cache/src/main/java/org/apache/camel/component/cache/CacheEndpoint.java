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
import net.sf.ehcache.event.CacheEventListener;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The cache component enables you to perform caching operations using EHCache as the Cache Implementation.
 *
 * The cache itself is created on demand or if a cache of that name already exists then it is simply utilized with its original settings.
 * This component supports producer and event based consumer endpoints.
 * The Cache consumer is an event based consumer and can be used to listen and respond to specific cache activities.
 * If you need to perform selections from a pre-existing cache, use the processors defined for the cache component.
 */
@UriEndpoint(firstVersion = "2.1.0", scheme = "cache", title = "EHCache", syntax = "cache:cacheName", consumerClass = CacheConsumer.class, label = "cache")
public class CacheEndpoint extends DefaultEndpoint {
    private static final Logger LOG = LoggerFactory.getLogger(CacheEndpoint.class);
    @UriParam
    private CacheConfiguration config;
    @UriParam(label = "advanced")
    private CacheManagerFactory cacheManagerFactory;
    @UriParam
    private String key;
    @UriParam
    private String operation;

    public CacheEndpoint() {
    }

    public CacheEndpoint(String endpointUri, Component component,
            CacheConfiguration config, CacheManagerFactory cacheManagerFactory) {
        super(endpointUri, component);
        this.config = config;
        this.cacheManagerFactory = cacheManagerFactory;
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        ObjectHelper.notNull(config, "config");
        ObjectHelper.notNull(cacheManagerFactory, "cacheManagerFactory");
        CacheConsumer answer = new CacheConsumer(this, processor, config);
        configureConsumer(answer);
        return answer;
    }

    public Producer createProducer() throws Exception {
        ObjectHelper.notNull(config, "config");
        ObjectHelper.notNull(cacheManagerFactory, "cacheManagerFactory");
        return new CacheProducer(this, config);
    }

    public boolean isSingleton() {
        return true;
    }

    public CacheConfiguration getConfig() {
        return config;
    }

    public void setConfig(CacheConfiguration config) {
        this.config = config;
    }

    public CacheManagerFactory getCacheManagerFactory() {
        return cacheManagerFactory;
    }

    /**
     * To use a custom CacheManagerFactory for creating the CacheManager to be used by this endpoint.
     * <p/>
     * By default the CacheManagerFactory configured on the component is used.
     */
    public void setCacheManagerFactory(CacheManagerFactory cacheManagerFactory) {
        this.cacheManagerFactory = cacheManagerFactory;
    }

    public Exchange createCacheExchange(String operation, String key, Object value) {
        Exchange exchange = super.createExchange();
        Message message = exchange.getIn();
        message.setHeader(CacheConstants.CACHE_OPERATION, operation);
        message.setHeader(CacheConstants.CACHE_KEY, key);
        message.setBody(value);
        return exchange;
    }

    /**
     * Returns {@link Cache} instance or create new one if not exists.
     * 
     * @return {@link Cache}
     */
    public Ehcache initializeCache() {
        CacheManager cacheManager = getCacheManagerFactory().getInstance();
        Ehcache cache;
        if (cacheManager.cacheExists(config.getCacheName())) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Found an existing cache: {}", config.getCacheName());
                LOG.trace("Cache {} currently contains {} elements",
                        config.getCacheName(),
                        cacheManager.getEhcache(config.getCacheName()).getSize());
            }
            cache = cacheManager.getEhcache(config.getCacheName());
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

            for (CacheEventListener listener : config.getEventListenerRegistry().getEventListeners()) {
                cache.getCacheEventNotificationService().registerListener(listener);
            }

            for (CacheLoaderWrapper loader : config.getCacheLoaderRegistry().getCacheLoaders()) {
                loader.init(cache);
                cache.registerCacheLoader(loader);
            }

            cacheManager.addCache(cache);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Added a new cache: " + cache.getName());
            }
        }

        return cache;
    }

    @Override
    public void stop() {
        CacheManager cacheManager = getCacheManagerFactory().getInstance();
        cacheManager.removeCache(config.getCacheName());
    }

    public String getOperation() {
        return operation;
    }


    /**
     * The default cache operation to use.
     * If an operation in the message header, then the operation from the header takes precedence.
     */
    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getKey() {
        return key;
    }

    /**
     * The default key to use.
     * If a key is provided in the message header, then the key from the header takes precedence.
     */
    public void setKey(String key) {
        this.key = key;
    }
}
