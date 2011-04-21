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
package org.apache.camel.component.cache;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultMessage;
import org.apache.camel.util.ObjectHelper;

public class CacheEndpoint extends DefaultEndpoint {
    private CacheConfiguration config;
    private CacheManagerFactory cacheManagerFactory;

    public CacheEndpoint() {
    }

    public CacheEndpoint(String endpointUri, Component component, CacheConfiguration config,
                         CacheManagerFactory cacheManagerFactory) {
        super(endpointUri, component);
        this.config = config;
        this.cacheManagerFactory = cacheManagerFactory;
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        ObjectHelper.notNull(config, "config");
        ObjectHelper.notNull(cacheManagerFactory, "cacheManagerFactory");
        return new CacheConsumer(this, processor, config);
    }

    public Producer createProducer() throws Exception {
        ObjectHelper.notNull(config, "config");
        ObjectHelper.notNull(cacheManagerFactory, "cacheManagerFactory");
        return new CacheProducer(this, config);
    }

    public boolean isSingleton() {
        return true;
    }

    public CacheConfiguration getConfig() {
        return config;
    }

    public void setConfig(CacheConfiguration config) {
        this.config = config;
    }

    public CacheManagerFactory getCacheManagerFactory() {
        return cacheManagerFactory;
    }

    public void setCacheManagerFactory(CacheManagerFactory cacheManagerFactory) {
        this.cacheManagerFactory = cacheManagerFactory;
    }

    public Exchange createCacheExchange(String operation, String key, Object value) {
        Exchange exchange = new DefaultExchange(this.getCamelContext(), getExchangePattern());
        Message message = new DefaultMessage();
        message.setHeader(CacheConstants.CACHE_OPERATION, operation);
        message.setHeader(CacheConstants.CACHE_KEY, key);
        message.setBody(value);
        exchange.setIn(message);
        return exchange;
    }
    
}
