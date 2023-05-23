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

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.infinispan.InfinispanComponent;
import org.apache.camel.component.infinispan.InfinispanConstants;
import org.apache.camel.component.infinispan.InfinispanEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.service.ServiceHelper;

import static org.apache.camel.component.infinispan.InfinispanConstants.SCHEME_INFINISPAN;

/**
 * Read and write from/to Infinispan distributed key/value store and data grid.
 */
@UriEndpoint(firstVersion = "2.13.0", scheme = SCHEME_INFINISPAN, title = "Infinispan", syntax = "infinispan:cacheName",
             category = { Category.CACHE, Category.CLUSTERING }, headersClass = InfinispanConstants.class)
public class InfinispanRemoteEndpoint extends InfinispanEndpoint {

    @UriPath(description = "The name of the cache to use. Use current to use the existing cache name from the currently configured cached manager. Or use default for the default cache manager name.")
    @Metadata(required = true)
    private final String cacheName;
    @UriParam
    private final InfinispanRemoteConfiguration configuration;

    private final InfinispanRemoteManager manager;

    public InfinispanRemoteEndpoint(String uri, String cacheName, InfinispanComponent component,
                                    InfinispanRemoteConfiguration configuration) {
        super(uri, component);

        this.cacheName = cacheName;
        this.configuration = configuration;
        this.manager = new InfinispanRemoteManager(component.getCamelContext(), configuration);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        ServiceHelper.startService(manager);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        ServiceHelper.stopService(manager);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new InfinispanRemoteProducer(this, cacheName, manager, configuration);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        InfinispanRemoteConsumer consumer = new InfinispanRemoteConsumer(this, processor, cacheName, manager, configuration);

        configureConsumer(consumer);
        return consumer;
    }

    public String getCacheName() {
        return cacheName;
    }

    public InfinispanRemoteConfiguration getConfiguration() {
        return configuration;
    }
}
