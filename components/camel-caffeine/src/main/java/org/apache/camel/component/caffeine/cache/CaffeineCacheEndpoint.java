/*
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
package org.apache.camel.component.caffeine.cache;

import com.github.benmanes.caffeine.cache.Cache;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.caffeine.CaffeineConfiguration;
import org.apache.camel.component.caffeine.CaffeineConstants;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Perform caching operations using Caffeine Cache.
 */
@UriEndpoint(firstVersion = "2.20.0", scheme = "caffeine-cache", title = "Caffeine Cache",
             syntax = "caffeine-cache:cacheName", category = { Category.CACHE, Category.CLUSTERING },
             producerOnly = true, headersClass = CaffeineConstants.class)
public class CaffeineCacheEndpoint extends DefaultEndpoint {

    @UriPath(description = "Cache name")
    @Metadata(required = true)
    private final String cacheName;
    @UriParam
    private final CaffeineConfiguration configuration;

    private volatile Cache<?, ?> cache;

    CaffeineCacheEndpoint(String uri, Component component, String cacheName, CaffeineConfiguration configuration) {
        super(uri, component);
        this.cacheName = cacheName;
        this.configuration = configuration;
    }

    @Override
    public CaffeineCacheComponent getComponent() {
        return (CaffeineCacheComponent) super.getComponent();
    }

    @Override
    public Producer createProducer() throws Exception {
        return new CaffeineCacheProducer(this, configuration, cache);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        cache = CamelContextHelper.lookup(getCamelContext(), cacheName, Cache.class);
        if (cache == null) {
            if (configuration.isCreateCacheIfNotExist()) {
                cache = getComponent().getOrCreateCache(cacheName, configuration);
            } else {
                throw new IllegalArgumentException(
                        "Cache instance '" + cacheName + "' not found and createCacheIfNotExist is set to false");
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
    }

    CaffeineConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new IllegalArgumentException("The caffeine-cache component doesn't support consumer");
    }
}
