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
package org.apache.camel.component.infinispan;

import java.util.Set;

import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryActivated;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryCreated;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryInvalidated;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryLoaded;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryModified;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryPassivated;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryRemoved;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryVisited;
import org.infinispan.notifications.cachelistener.event.CacheEntryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Listener(sync = true)
public class InfinispanSyncEventListener {
    private final transient Logger logger = LoggerFactory.getLogger(this.getClass());
    private final InfinispanConsumer infinispanConsumer;
    private final Set<String> eventTypes;

    public InfinispanSyncEventListener(InfinispanConsumer infinispanConsumer, Set<String> eventTypes) {
        this.infinispanConsumer = infinispanConsumer;
        this.eventTypes = eventTypes;
    }

    @CacheEntryActivated
    @CacheEntryCreated
    @CacheEntryInvalidated
    @CacheEntryLoaded
    @CacheEntryModified
    @CacheEntryPassivated
    @CacheEntryRemoved
    @CacheEntryVisited
    public void processEvent(CacheEntryEvent<Object, Object> event) {
        logger.trace("Received CacheEntryEvent [{}]", event);

        if (eventTypes == null || eventTypes.isEmpty() || eventTypes.contains(event.getType().toString())) {
            infinispanConsumer.processEvent(event.getType().toString(), event.isPre(), event.getCache().getName(), event.getKey());
        }
    }
}

