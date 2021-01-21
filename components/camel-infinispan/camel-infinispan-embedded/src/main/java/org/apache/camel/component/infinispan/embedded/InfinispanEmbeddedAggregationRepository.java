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

import org.apache.camel.component.infinispan.InfinispanAggregationRepository;
import org.apache.camel.support.DefaultExchangeHolder;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.function.Suppliers;
import org.infinispan.commons.api.BasicCache;

public class InfinispanEmbeddedAggregationRepository extends InfinispanAggregationRepository {
    private final Supplier<BasicCache<String, DefaultExchangeHolder>> cache;

    private InfinispanEmbeddedConfiguration configuration;
    private InfinispanEmbeddedManager manager;

    /**
     * Creates new {@link InfinispanEmbeddedAggregationRepository} that defaults to non-optimistic locking with
     * recoverable behavior and a local Infinispan cache.
     *
     * @param cacheName cache name
     */
    public InfinispanEmbeddedAggregationRepository(String cacheName) {
        super(cacheName);

        this.cache = Suppliers.memorize(() -> manager.getCache(getCacheName()));
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (ObjectHelper.isEmpty(configuration)) {
            configuration = new InfinispanEmbeddedConfiguration();
        }
        if (ObjectHelper.isEmpty(manager)) {
            manager = new InfinispanEmbeddedManager(configuration);
        }

        manager.setCamelContext(getCamelContext());

        ServiceHelper.startService(manager);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        ServiceHelper.stopService(manager);
    }

    @Override
    protected BasicCache<String, DefaultExchangeHolder> getCache() {
        return cache.get();
    }

    public InfinispanEmbeddedConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(InfinispanEmbeddedConfiguration configuration) {
        this.configuration = configuration;
    }
}
