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
package org.apache.camel.component.infinispan.remote;

import java.util.function.Supplier;

import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.infinispan.InfinispanIdempotentRepository;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.function.Suppliers;
import org.infinispan.client.hotrod.Flag;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.commons.api.BasicCache;

import static org.apache.camel.component.infinispan.remote.InfinispanRemoteUtil.getCacheWithFlags;

@Metadata(label = "bean",
          description = "Idempotent repository that uses remote Infinispan to store message ids.",
          annotations = { "interfaceName=org.apache.camel.spi.IdempotentRepository" })
@Configurer(metadataOnly = true)
@ManagedResource(description = "Infinispan Remote message id repository")
public class InfinispanRemoteIdempotentRepository extends InfinispanIdempotentRepository {

    private Supplier<RemoteCache<String, Boolean>> cache;
    private InfinispanRemoteManager manager;

    @Metadata(description = "Name of cache", required = true)
    private String cacheName;
    @Metadata(description = "Configuration for remote Infinispan")
    private InfinispanRemoteConfiguration configuration;

    public InfinispanRemoteIdempotentRepository() {
    }

    public InfinispanRemoteIdempotentRepository(String cacheName) {
        this.cacheName = cacheName;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        ObjectHelper.notNull(cacheName, "cacheName", this);

        if (this.configuration == null) {
            this.configuration = new InfinispanRemoteConfiguration();
        }

        this.manager = new InfinispanRemoteManager(getCamelContext(), configuration);
        this.cache = Suppliers.memorize(() -> getCacheWithFlags(manager, cacheName, Flag.FORCE_RETURN_VALUE));
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

    @Override
    public String getCacheName() {
        return this.cacheName;
    }

    public InfinispanRemoteConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(InfinispanRemoteConfiguration configuration) {
        this.configuration = configuration;
    }

    public InfinispanRemoteManager getManager() {
        return manager;
    }

    public void setManager(InfinispanRemoteManager manager) {
        this.manager = manager;
    }

    public RemoteCacheManager getCacheContainer() {
        return configuration != null ? configuration.getCacheContainer() : null;
    }

    public void setCacheContainer(RemoteCacheManager cacheContainer) {
        if (this.configuration == null) {
            this.configuration = new InfinispanRemoteConfiguration();
        }
        this.configuration.setCacheContainer(cacheContainer);
    }
}
