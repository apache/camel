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

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.ehcache.Cache;
import org.ehcache.event.CacheEvent;
import org.ehcache.event.CacheEventListener;

public class EhcacheConsumer extends DefaultConsumer implements CacheEventListener<Object, Object> {
    private final EhcacheConfiguration configuration;
    private final String cacheName;
    private Cache cache;
    private EhcacheManager manager;

    public EhcacheConsumer(EhcacheEndpoint endpoint, String cacheName, EhcacheConfiguration configuration,
                           Processor processor) {
        super(endpoint, processor);
        this.configuration = configuration;
        this.cacheName = cacheName;
    }

    @Override
    public EhcacheEndpoint getEndpoint() {
        return (EhcacheEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        Class<?> kt = null;
        if (configuration.getKeyType() != null) {
            kt = getEndpoint().getCamelContext().getClassResolver().resolveClass(configuration.getKeyType());
        }
        Class<?> vt = null;
        if (configuration.getValueType() != null) {
            vt = getEndpoint().getCamelContext().getClassResolver().resolveClass(configuration.getValueType());
        }
        EhcacheManager endpointManager = getEndpoint().getManager();
        // the cache manager (and its caches) are kept while the consumer uses them; the reference comes first, as in
        // the producer, as it opens the manager again if it was closed
        endpointManager.incRef();
        try {
            this.cache = endpointManager.getCache(cacheName, kt, vt);
            this.cache.getRuntimeConfiguration().registerCacheEventListener(
                    this,
                    configuration.getEventOrdering(),
                    configuration.getEventFiring(),
                    configuration.getEventTypesSet());
        } catch (Exception e) {
            // a consumer that fails to start keeps no reference, and doStop has nothing to release
            this.cache = null;
            try {
                endpointManager.decRef();
            } catch (Exception e2) {
                e.addSuppressed(e2);
            }
            throw e;
        }
        this.manager = endpointManager;
    }

    @Override
    protected void doStop() throws Exception {
        try {
            // a consumer that never started (its route was removed before it started), or failed to start, has no cache
            if (cache != null) {
                cache.getRuntimeConfiguration().deregisterCacheEventListener(this);
            }
        } finally {
            if (manager != null) {
                manager.decRef();
                manager = null;
            }
        }

        super.doStop();
    }

    @Override
    public void onEvent(CacheEvent<?, ?> event) {
        if (isRunAllowed()) {
            final Exchange exchange = createExchange(false);
            final Message message = exchange.getIn();

            message.setHeader(EhcacheConstants.KEY, event.getKey());
            message.setHeader(EhcacheConstants.EVENT_TYPE, event.getType());
            message.setHeader(EhcacheConstants.OLD_VALUE, event.getOldValue());
            message.setBody(event.getNewValue());

            try {
                getProcessor().process(exchange);
            } catch (Exception e) {
                getExceptionHandler().handleException("Error processing exchange", exchange, e);
            } finally {
                releaseExchange(exchange, false);
            }
        }
    }
}
