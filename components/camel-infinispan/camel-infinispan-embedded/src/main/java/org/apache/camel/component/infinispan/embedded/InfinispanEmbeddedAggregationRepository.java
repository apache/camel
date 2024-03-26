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
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.DefaultExchangeHolder;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.function.Suppliers;
import org.infinispan.commons.api.BasicCache;

@Metadata(label = "bean",
          description = "Aggregation repository that uses embedded Infinispan to store exchanges.",
          annotations = { "interfaceName=org.apache.camel.AggregationStrategy" })
@Configurer(metadataOnly = true)
public class InfinispanEmbeddedAggregationRepository extends InfinispanAggregationRepository {

    private Supplier<BasicCache<String, DefaultExchangeHolder>> cache;
    private InfinispanEmbeddedManager manager;

    // needed for metadata generation
    @Metadata(description = "Name of cache", required = true)
    private String cacheName;
    @Metadata(description = "Configuration for embedded Infinispan")
    private InfinispanEmbeddedConfiguration configuration;
    @Metadata(description = "Whether or not recovery is enabled", defaultValue = "true")
    private boolean useRecovery = true;
    @Metadata(description = "Sets an optional dead letter channel which exhausted recovered Exchange should be send to.")
    private String deadLetterUri;
    @Metadata(description = "Sets the interval between recovery scans", defaultValue = "5000")
    private long recoveryInterval = 5000;
    @Metadata(description = "Sets an optional limit of the number of redelivery attempt of recovered Exchange should be attempted, before its exhausted."
                            + " When this limit is hit, then the Exchange is moved to the dead letter channel.",
              defaultValue = "3")
    private int maximumRedeliveries = 3;
    @Metadata(label = "advanced",
              description = "Whether headers on the Exchange that are Java objects and Serializable should be included and saved to the repository")
    private boolean allowSerializedHeaders;

    public InfinispanEmbeddedAggregationRepository() {
    }

    /**
     * Creates new {@link InfinispanEmbeddedAggregationRepository} that defaults to non-optimistic locking with
     * recoverable behavior and a local Infinispan cache.
     *
     * @param cacheName cache name
     */
    public InfinispanEmbeddedAggregationRepository(String cacheName) {
        super(cacheName);
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
        this.cache = Suppliers.memorize(() -> manager.getCache(getCacheName()));

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
