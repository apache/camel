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
package org.apache.camel.component.infinispan.embedded;

import java.util.Set;

import org.apache.camel.component.infinispan.InfinispanConsumer;
import org.apache.camel.component.infinispan.InfinispanEventListener;
import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryActivated;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryCreated;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryExpired;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryInvalidated;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryLoaded;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryModified;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryPassivated;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryRemoved;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryVisited;
import org.infinispan.notifications.cachelistener.event.CacheEntryEvent;

@Listener(clustered = false, sync = true)
public class InfinispanSyncLocalEventListener extends InfinispanEventListener {
    public InfinispanSyncLocalEventListener(InfinispanConsumer infinispanConsumer, Set<String> eventTypes) {
        super(infinispanConsumer, eventTypes);
    }

    @CacheEntryActivated
    @CacheEntryCreated
    @CacheEntryInvalidated
    @CacheEntryLoaded
    @CacheEntryModified
    @CacheEntryPassivated
    @CacheEntryRemoved
    @CacheEntryVisited
    @CacheEntryExpired
    public void processEvent(CacheEntryEvent<Object, Object> event) {
        dispatch(event.getType().toString(), event.isPre(), event.getCache().getName(), event.getKey());
    }

    private void dispatch(String eventType, boolean isPre, String cacheName, Object key) {
        if (isAccepted(eventType)) {
            infinispanConsumer.processEvent(eventType, isPre, cacheName, key, null);
        }
    }
}
