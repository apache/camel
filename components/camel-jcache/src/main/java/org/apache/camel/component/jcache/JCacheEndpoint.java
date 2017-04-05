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
package org.apache.camel.component.jcache;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

/**
 * The jcache component enables you to perform caching operations using JSR107/JCache as cache implementation.
 */
@UriEndpoint(firstVersion = "2.17.0", scheme = "jcache", title = "JCache", syntax = "jcache:cacheName", consumerClass = JCacheConsumer.class, label = "cache,datagrid,clustering")
public class JCacheEndpoint extends DefaultEndpoint {

    @UriPath(description = "The name of the cache")
    @Metadata(required = "true")
    private final String cacheName;
    @UriParam
    private final JCacheConfiguration cacheConfiguration;

    private volatile JCacheManager<Object, Object> cacheManager;

    public JCacheEndpoint(String uri, JCacheComponent component, JCacheConfiguration configuration) {
        super(uri, component);

        this.cacheName = configuration.getCacheName();
        this.cacheConfiguration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new JCacheProducer(this, cacheConfiguration);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return new JCacheConsumer(this, processor);
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    protected void doStart() throws Exception {
        cacheManager = JCacheHelper.createManager(cacheConfiguration);
    }

    @Override
    protected void doStop() throws Exception {
        if (cacheManager != null) {
            cacheManager.close();
        }
    }

    JCacheManager getManager() {
        return cacheManager;
    }
}
