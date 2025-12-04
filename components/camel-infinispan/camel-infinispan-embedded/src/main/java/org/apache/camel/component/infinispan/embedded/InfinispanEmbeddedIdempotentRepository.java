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

package org.apache.camel.component.infinispan.embedded;

import java.util.function.Supplier;

import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.infinispan.InfinispanIdempotentRepository;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.function.Suppliers;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.manager.EmbeddedCacheManager;

@Metadata(
        label = "bean",
        description = "Idempotent repository that uses embedded Infinispan to store message ids.",
        annotations = {"interfaceName=org.apache.camel.spi.IdempotentRepository"})
@Configurer(metadataOnly = true)
@ManagedResource(description = "Infinispan Embedded message id repository")
public class InfinispanEmbeddedIdempotentRepository extends InfinispanIdempotentRepository {

    private Supplier<BasicCache<String, Boolean>> cache;
    private InfinispanEmbeddedManager manager;

    @Metadata(description = "Name of cache", required = true)
    private String cacheName;

    @Metadata(description = "Configuration for embedded Infinispan")
    private InfinispanEmbeddedConfiguration configuration;

    public InfinispanEmbeddedIdempotentRepository() {}

    public InfinispanEmbeddedIdempotentRepository(String cacheName) {
        this.cacheName = cacheName;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        ObjectHelper.notNull(cacheName, "cacheName", this);

        if (this.configuration == null) {
            this.configuration = new InfinispanEmbeddedConfiguration();
        }

        this.manager = new InfinispanEmbeddedManager(configuration);
        this.manager.setCamelContext(getCamelContext());
        this.cache = Suppliers.memorize(() -> manager.getCache(getCacheName()));
        ServiceHelper.startService(manager);
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownService(manager);
        super.doShutdown();
    }

    @Override
    protected BasicCache<String, Boolean> getCache() {
        return cache.get();
    }

    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }

    @Override
    public String getCacheName() {
        return this.cacheName;
    }

    public InfinispanEmbeddedConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(InfinispanEmbeddedConfiguration configuration) {
        this.configuration = configuration;
    }

    public InfinispanEmbeddedManager getManager() {
        return manager;
    }

    public void setManager(InfinispanEmbeddedManager manager) {
        this.manager = manager;
    }

    public EmbeddedCacheManager getCacheContainer() {
        return configuration != null ? configuration.getCacheContainer() : null;
    }

    public void setCacheContainer(EmbeddedCacheManager cacheContainer) {
        if (this.configuration == null) {
            this.configuration = new InfinispanEmbeddedConfiguration();
        }

        this.configuration.setCacheContainer(cacheContainer);
    }
}
