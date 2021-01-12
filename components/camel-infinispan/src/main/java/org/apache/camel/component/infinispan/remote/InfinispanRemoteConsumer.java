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

import java.util.HashSet;
import java.util.Set;

import org.apache.camel.Processor;
import org.apache.camel.Service;
import org.apache.camel.component.infinispan.InfinispanConstants;
import org.apache.camel.component.infinispan.InfinispanConsumer;
import org.apache.camel.component.infinispan.InfinispanEndpoint;
import org.apache.camel.component.infinispan.InfinispanEventListener;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.Search;
import org.infinispan.client.hotrod.event.ClientEvent;
import org.infinispan.query.api.continuous.ContinuousQuery;
import org.infinispan.query.api.continuous.ContinuousQueryListener;
import org.infinispan.query.dsl.Query;

public class InfinispanRemoteConsumer
        extends InfinispanConsumer<RemoteCacheManager, InfinispanRemoteManager, InfinispanRemoteConfiguration> {
    private Service handler;

    public InfinispanRemoteConsumer(
                                    InfinispanEndpoint endpoint,
                                    Processor processor,
                                    String cacheName,
                                    InfinispanRemoteManager manager,
                                    InfinispanRemoteConfiguration configuration) {

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
            RemoteCache<Object, Object> remoteCache = getCache(RemoteCache.class);
            Query<?> query = InfinispanRemoteUtil.buildQuery(getConfiguration().getQueryBuilder(), remoteCache);

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
        private InfinispanEventListener<ClientEvent.Type> listener;

        @SuppressWarnings("unchecked")
        @Override
        public void doStart() {
            final RemoteCache<?, ?> cache = getCache(RemoteCache.class);
            final InfinispanRemoteConfiguration configuration = getConfiguration();

            listener = configuration.getCustomListener();
            if (listener == null) {
                Set<ClientEvent.Type> events = new HashSet<>();
                if (configuration.getEventTypes() != null) {
                    String eventTypes = configuration.getEventTypes();
                    for (String event : eventTypes.split(",")) {
                        events.add(ClientEvent.Type.valueOf(event));
                    }
                }

                listener = new InfinispanRemoteEventListener(events);
            }

            listener.setCacheName(cache.getName());
            listener.setEventProcessor(InfinispanRemoteConsumer.this);

            cache.addClientListener(listener);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void doStop() {
            getCache(RemoteCache.class).removeClientListener(listener);
        }

    }
}
