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
package org.apache.camel.component.infinispan.remote;

import java.util.Set;
import org.apache.camel.component.infinispan.InfinispanConsumer;
import org.apache.camel.component.infinispan.InfinispanEventListener;
import org.infinispan.client.hotrod.annotation.ClientCacheEntryCreated;
import org.infinispan.client.hotrod.annotation.ClientCacheEntryExpired;
import org.infinispan.client.hotrod.annotation.ClientCacheEntryModified;
import org.infinispan.client.hotrod.annotation.ClientCacheEntryRemoved;
import org.infinispan.client.hotrod.annotation.ClientCacheFailover;
import org.infinispan.client.hotrod.annotation.ClientListener;
import org.infinispan.client.hotrod.event.ClientCacheEntryCreatedEvent;
import org.infinispan.client.hotrod.event.ClientCacheEntryExpiredEvent;
import org.infinispan.client.hotrod.event.ClientCacheEntryModifiedEvent;
import org.infinispan.client.hotrod.event.ClientCacheEntryRemovedEvent;
import org.infinispan.client.hotrod.event.ClientCacheFailoverEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ClientListener
public class InfinispanRemoteEventListener extends InfinispanEventListener {
    private final transient Logger logger = LoggerFactory.getLogger(this.getClass());

    public InfinispanRemoteEventListener(InfinispanConsumer infinispanConsumer, Set<String> eventTypes) {
        super(infinispanConsumer, eventTypes);
    }

    @ClientCacheEntryCreated
    public void processEvent(ClientCacheEntryCreatedEvent<Object> event) {
        logger.trace("Received ClientEvent [{}]", event);
        dispatch(event.getType().toString(), false, cacheName, event.getKey());
    }

    @ClientCacheEntryModified
    public void processEvent(ClientCacheEntryModifiedEvent<Object> event) {
        logger.trace("Received ClientEvent [{}]", event);
        dispatch(event.getType().toString(), false, cacheName, event.getKey());
    }

    @ClientCacheEntryRemoved
    public void processEvent(ClientCacheEntryRemovedEvent<Object> event) {
        logger.trace("Received ClientEvent [{}]", event);
        dispatch(event.getType().toString(), false, cacheName, event.getKey());
    }

    @ClientCacheFailover
    public void processEvent(ClientCacheFailoverEvent event) {
        logger.trace("Received ClientEvent [{}]", event);
        dispatch(event.getType().toString(), false, cacheName, null);
    }

    @ClientCacheEntryExpired
    public void processEvent(ClientCacheEntryExpiredEvent<Object> event) {
        logger.trace("Received ClientEvent [{}]", event);
        dispatch(event.getType().toString(), false, cacheName, event.getKey());
    }

    private void dispatch(String eventType, boolean isPre, String cacheName, Object key) {
        if (isAccepted(eventType)) {
            infinispanConsumer.processEvent(eventType, isPre, cacheName, key, null);
        }
    }
}
