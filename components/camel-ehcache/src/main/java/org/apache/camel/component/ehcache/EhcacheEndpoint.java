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
package org.apache.camel.component.ehcache;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The ehcache component enables you to perform caching operations using <a href="http://www.ehcache.org">Ehcache</a> as cache implementation.
 */
@UriEndpoint(firstVersion = "2.18.0", scheme = "ehcache", title = "Ehcache", syntax = "ehcache:cacheName", label = "cache,datagrid,clustering")
public class EhcacheEndpoint extends DefaultEndpoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(EhcacheComponent.class);

    @UriPath(description = "the cache name")
    @Metadata(required = true)
    private final String cacheName;
    @UriParam
    private final EhcacheConfiguration configuration;
    private EhcacheManager cacheManager;

    EhcacheEndpoint(String uri, EhcacheComponent component,  String cacheName, EhcacheConfiguration configuration) throws Exception {
        super(uri, component);
        this.cacheName = cacheName;
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new EhcacheProducer(this, this.cacheName, configuration);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        EhcacheConsumer consumer = new EhcacheConsumer(this, this.cacheName, configuration, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public EhcacheComponent getComponent() {
        return (EhcacheComponent) super.getComponent();
    }

    @Override
    protected void doStart() throws Exception {
        if (cacheManager == null) {
            cacheManager = getComponent().createCacheManager(configuration);
        }
        cacheManager.start();
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (cacheManager != null) {
            cacheManager.stop();
        }
    }

    EhcacheManager getManager() {
        return cacheManager;
    }

    EhcacheConfiguration getConfiguration() {
        return configuration;
    }

}
