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

import java.util.EnumSet;
import java.util.Set;

import org.apache.camel.Processor;
import org.apache.camel.Service;
import org.apache.camel.component.infinispan.InfinispanConstants;
import org.apache.camel.component.infinispan.InfinispanConsumer;
import org.apache.camel.component.infinispan.InfinispanEndpoint;
import org.apache.camel.component.infinispan.InfinispanEventListener;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.infinispan.Cache;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.notifications.cachelistener.event.Event;
import org.infinispan.query.Search;
import org.infinispan.query.api.continuous.ContinuousQuery;
import org.infinispan.query.api.continuous.ContinuousQueryListener;
import org.infinispan.query.dsl.Query;

public class InfinispanEmbeddedConsumer
        extends InfinispanConsumer<EmbeddedCacheManager, InfinispanEmbeddedManager, InfinispanEmbeddedConfiguration> {
    private Service handler;

    public InfinispanEmbeddedConsumer(
                                      InfinispanEndpoint endpoint,
                                      Processor processor,
                                      String cacheName,
                                      InfinispanEmbeddedManager manager,
                                      InfinispanEmbeddedConfiguration configuration) {
        super(endpoint, processor, cacheName, manager, configuration);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (getConfiguration().hasQueryBuilder()) {
            handler = new ContinuousQueryHandler();
        } else {
            handler = new ConsumerHandler();
        }

        ServiceHelper.startService(handler);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        ServiceHelper.stopService(handler);
    }

    // *********************************
    //
    // Handlers
    //
    // *********************************

    private class ContinuousQueryHandler extends ServiceSupport implements ContinuousQueryListener<Object, Object> {
        private ContinuousQuery<Object, Object> continuousQuery;

        @Override
        public void resultJoining(Object key, Object value) {
            processEvent(InfinispanConstants.CACHE_ENTRY_JOINING, cacheName, key, value, null);
        }

        @Override
        public void resultUpdated(Object key, Object value) {
            processEvent(InfinispanConstants.CACHE_ENTRY_UPDATED, cacheName, key, value, null);
        }

        @Override
        public void resultLeaving(Object key) {
            processEvent(InfinispanConstants.CACHE_ENTRY_LEAVING, cacheName, key, null, null);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void doStart() {
            Cache<Object, Object> remoteCache = getCache(Cache.class);
            Query<?> query = InfinispanEmbeddedUtil.buildQuery(getConfiguration().getQueryBuilder(), remoteCache);

            continuousQuery = Search.getContinuousQuery(remoteCache);
            continuousQuery.addContinuousQueryListener(query, this);
        }

        @Override
        public void doStop() {
            if (continuousQuery != null) {
                continuousQuery.removeAllListeners();
            }
        }
    }

    private class ConsumerHandler extends ServiceSupport {
        private InfinispanEventListener<Event.Type> listener;

        @SuppressWarnings("unchecked")
        @Override
        public void doStart() {
            final Cache<?, ?> cache = getCache(Cache.class);
            final InfinispanEmbeddedConfiguration configuration = getConfiguration();

            this.listener = configuration.getCustomListener();

            if (this.listener == null) {
                Set<Event.Type> events = EnumSet.noneOf(Event.Type.class);
                if (configuration.getEventTypes() != null) {
                    String eventTypes = configuration.getEventTypes();
                    for (String event : eventTypes.split(",")) {
                        events.add(Event.Type.valueOf(event));
                    }
                }

                if (configuration.isClusteredListener()) {
                    listener = configuration.isSync()
                            ? new InfinispanEmbeddedEventListeners.ClusteredSync(events)
                            : new InfinispanEmbeddedEventListeners.ClusteredAsync(events);
                } else {
                    listener = configuration.isSync()
                            ? new InfinispanEmbeddedEventListeners.LocalSync(events)
                            : new InfinispanEmbeddedEventListeners.LocalAsync(events);
                }
            }

            listener.setCacheName(cache.getName());
            listener.setEventProcessor(InfinispanEmbeddedConsumer.this);

            cache.addListener(listener);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void doStop() {
            getCache(Cache.class).removeListener(listener);
        }
    }

}
