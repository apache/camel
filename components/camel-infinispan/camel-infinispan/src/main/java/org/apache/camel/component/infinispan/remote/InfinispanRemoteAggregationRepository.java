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

import org.apache.camel.component.infinispan.InfinispanAggregationRepository;
import org.apache.camel.component.infinispan.remote.protostream.DefaultExchangeHolderContextInitializer;
import org.apache.camel.support.DefaultExchangeHolder;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.function.Suppliers;
import org.infinispan.client.hotrod.Flag;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.commons.configuration.Combine;

public class InfinispanRemoteAggregationRepository extends InfinispanAggregationRepository {
    private final Supplier<BasicCache<String, DefaultExchangeHolder>> cache;

    private InfinispanRemoteConfiguration configuration;
    private InfinispanRemoteManager manager;

    /**
     * Creates new {@link InfinispanRemoteAggregationRepository} that defaults to non-optimistic locking with
     * recoverable behavior and a local Infinispan cache.
     *
     * @param cacheName cache name
     */
    public InfinispanRemoteAggregationRepository(String cacheName) {
        super(cacheName);

        this.cache = Suppliers.memorize(
                // for optimization reason, a remote cache does not return the previous value for operation
                // such as Map::put and need to be explicitly forced
                () -> InfinispanRemoteUtil.getCacheWithFlags(manager, getCacheName(), Flag.FORCE_RETURN_VALUE));
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        InfinispanRemoteConfiguration conf = configuration != null ? configuration : new InfinispanRemoteConfiguration();

        if (conf.getCacheContainerConfiguration() == null) {
            conf.setCacheContainerConfiguration(
                    new ConfigurationBuilder()
                            .addContextInitializer(new DefaultExchangeHolderContextInitializer())
                            .build());
        } else {
            conf.setCacheContainerConfiguration(
                    new ConfigurationBuilder()
                            .read(conf.getCacheContainerConfiguration(), Combine.DEFAULT)
                            .addContextInitializer(new DefaultExchangeHolderContextInitializer())
                            .build());
        }

        manager = new InfinispanRemoteManager(conf);
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

    public InfinispanRemoteConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(InfinispanRemoteConfiguration configuration) {
        this.configuration = configuration;
    }
}
