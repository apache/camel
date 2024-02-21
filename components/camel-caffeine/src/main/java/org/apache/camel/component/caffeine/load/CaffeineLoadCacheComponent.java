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
package org.apache.camel.component.caffeine.load;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.caffeine.CaffeineConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

import static org.apache.camel.component.caffeine.CaffeineHelper.defineBuilder;

/**
 * Represents the component that manages {@link DefaultComponent}.
 */
@Component("caffeine-loadcache")
public class CaffeineLoadCacheComponent extends DefaultComponent {
    private final Map<String, LoadingCache<?, ?>> caches = new ConcurrentHashMap<>();

    @Metadata(label = "advanced")
    private CaffeineConfiguration configuration = new CaffeineConfiguration();

    public CaffeineLoadCacheComponent() {
    }

    public CaffeineLoadCacheComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        CaffeineConfiguration configuration = this.configuration.copy();

        CaffeineLoadCacheEndpoint endpoint = new CaffeineLoadCacheEndpoint(uri, this, remaining, configuration);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    protected LoadingCache<?, ?> getOrCreateCache(String name, CaffeineConfiguration configuration) {
        return caches.computeIfAbsent(name, key -> {
            Caffeine<?, ?> builder = Caffeine.newBuilder();
            defineBuilder(builder, configuration);
            return builder.build(configuration.getCacheLoader());
        });
    }

    public CaffeineConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Sets the global component configuration
     */
    public void setConfiguration(CaffeineConfiguration configuration) {
        this.configuration = configuration;
    }
}
