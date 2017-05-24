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
package org.apache.camel.component.ehcache;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.ehcache.Cache;
import org.ehcache.event.CacheEvent;
import org.ehcache.event.CacheEventListener;

public class EhcacheConsumer extends DefaultConsumer implements CacheEventListener<Object, Object> {
    private final EhcacheConfiguration configuration;
    private final EhcacheManager manager;
    private final Cache cache;

    public EhcacheConsumer(EhcacheEndpoint endpoint, String cacheName, EhcacheConfiguration configuration, Processor processor) throws Exception {
        super(endpoint, processor);

        this.configuration = configuration;
        this.manager = endpoint.getManager();
        this.cache = manager.getCache(cacheName, configuration.getKeyType(), configuration.getValueType());
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        this.cache.getRuntimeConfiguration().registerCacheEventListener(
            this,
            configuration.getEventOrdering(),
            configuration.getEventFiring(),
            configuration.getEventTypes()
        );
    }

    @Override
    protected void doStop() throws Exception {
        cache.getRuntimeConfiguration().deregisterCacheEventListener(this);

        super.doStop();
    }

    @Override
    public void onEvent(CacheEvent<? extends Object, ? extends Object> event) {
        if (isRunAllowed()) {
            final Exchange exchange = getEndpoint().createExchange();
            final Message message = exchange.getIn();

            message.setHeader(EhcacheConstants.KEY, event.getKey());
            message.setHeader(EhcacheConstants.EVENT_TYPE, event.getType());
            message.setHeader(EhcacheConstants.OLD_VALUE, event.getOldValue());
            message.setBody(event.getNewValue());

            try {
                getProcessor().process(exchange);
            } catch (Exception e) {
                getExceptionHandler().handleException("Error processing exchange", exchange, e);
            }
        }
    }
}
