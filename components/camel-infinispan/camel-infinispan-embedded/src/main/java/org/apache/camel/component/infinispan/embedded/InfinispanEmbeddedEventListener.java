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

import java.util.Collections;
import java.util.Set;

import org.apache.camel.component.infinispan.InfinispanConstants;
import org.apache.camel.component.infinispan.InfinispanEventListener;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryActivated;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryCreated;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryExpired;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryInvalidated;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryLoaded;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryModified;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryPassivated;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryRemoved;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryVisited;
import org.infinispan.notifications.cachelistener.event.CacheEntryActivatedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryCreatedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryExpiredEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryInvalidatedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryLoadedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryModifiedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryPassivatedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryRemovedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryVisitedEvent;
import org.infinispan.notifications.cachelistener.event.Event;

public abstract class InfinispanEmbeddedEventListener extends InfinispanEventListener<Event.Type> {
    protected InfinispanEmbeddedEventListener() {
        super(Collections.emptySet());
    }

    protected InfinispanEmbeddedEventListener(Set<Event.Type> events) {
        super(events);
    }

    @CacheEntryActivated
    public void processEvent(CacheEntryActivatedEvent<?, ?> event) {
        if (isAccepted(event.getType())) {
            getEventProcessor().processEvent(
                    event.getType().toString(),
                    event.getCache().getName(),
                    event.getKey(),
                    event.getValue(),
                    e -> {
                        e.getMessage().setHeader(InfinispanConstants.IS_PRE, event.isPre());
                    });
        }
    }

    @CacheEntryCreated
    public void processEvent(CacheEntryCreatedEvent<?, ?> event) {
        if (isAccepted(event.getType())) {
            getEventProcessor().processEvent(
                    event.getType().toString(),
                    event.getCache().getName(),
                    event.getKey(),
                    event.getValue(),
                    e -> {
                        e.getMessage().setHeader(InfinispanConstants.IS_PRE, event.isPre());
                        e.getMessage().setHeader(InfinispanConstants.COMMAND_RETRIED, event.isCommandRetried());
                    });
        }
    }

    @CacheEntryInvalidated
    public void processEvent(CacheEntryInvalidatedEvent<?, ?> event) {
        if (isAccepted(event.getType())) {
            getEventProcessor().processEvent(
                    event.getType().toString(),
                    event.getCache().getName(),
                    event.getKey(),
                    event.getValue(),
                    e -> {
                        e.getMessage().setHeader(InfinispanConstants.IS_PRE, event.isPre());
                    });
        }
    }

    @CacheEntryLoaded
    public void processEvent(CacheEntryLoadedEvent<?, ?> event) {
        if (isAccepted(event.getType())) {
            getEventProcessor().processEvent(
                    event.getType().toString(),
                    event.getCache().getName(),
                    event.getKey(),
                    event.getValue(),
                    e -> {
                        e.getMessage().setHeader(InfinispanConstants.IS_PRE, event.isPre());
                    });
        }
    }

    @CacheEntryModified
    public void processEvent(CacheEntryModifiedEvent<?, ?> event) {
        if (isAccepted(event.getType())) {
            getEventProcessor().processEvent(
                    event.getType().toString(),
                    event.getCache().getName(),
                    event.getKey(),
                    event.getValue(),
                    e -> {
                        e.getMessage().setHeader(InfinispanConstants.IS_PRE, event.isPre());
                        e.getMessage().setHeader(InfinispanConstants.COMMAND_RETRIED, event.isCommandRetried());
                        e.getMessage().setHeader(InfinispanConstants.ENTRY_CREATED, event.isCreated());
                        e.getMessage().setHeader(InfinispanConstants.ORIGIN_LOCAL, event.isOriginLocal());
                        e.getMessage().setHeader(InfinispanConstants.CURRENT_STATE, event.isCurrentState());
                    });
        }
    }

    @CacheEntryPassivated
    public void processEvent(CacheEntryPassivatedEvent<?, ?> event) {
        if (isAccepted(event.getType())) {
            getEventProcessor().processEvent(
                    event.getType().toString(),
                    event.getCache().getName(),
                    event.getKey(),
                    event.getValue(),
                    e -> {
                        e.getMessage().setHeader(InfinispanConstants.IS_PRE, event.isPre());
                        e.getMessage().setHeader(InfinispanConstants.ORIGIN_LOCAL, event.isOriginLocal());
                        e.getMessage().setHeader(InfinispanConstants.CURRENT_STATE, event.isCurrentState());
                    });
        }
    }

    @CacheEntryRemoved
    public void processEvent(CacheEntryRemovedEvent<?, ?> event) {
        if (isAccepted(event.getType())) {
            getEventProcessor().processEvent(
                    event.getType().toString(),
                    event.getCache().getName(),
                    event.getKey(),
                    event.getValue(),
                    e -> {
                        e.getMessage().setHeader(InfinispanConstants.IS_PRE, event.isPre());
                        e.getMessage().setHeader(InfinispanConstants.COMMAND_RETRIED, event.isCommandRetried());
                        e.getMessage().setHeader(InfinispanConstants.ORIGIN_LOCAL, event.isOriginLocal());
                        e.getMessage().setHeader(InfinispanConstants.CURRENT_STATE, event.isCurrentState());
                        e.getMessage().setHeader(InfinispanConstants.OLD_VALUE, event.getOldValue());
                    });
        }
    }

    @CacheEntryVisited
    public void processEvent(CacheEntryVisitedEvent<?, ?> event) {
        if (isAccepted(event.getType())) {
            getEventProcessor().processEvent(
                    event.getType().toString(),
                    event.getCache().getName(),
                    event.getKey(),
                    event.getValue(),
                    e -> {
                        e.getMessage().setHeader(InfinispanConstants.IS_PRE, event.isPre());
                        e.getMessage().setHeader(InfinispanConstants.ORIGIN_LOCAL, event.isOriginLocal());
                        e.getMessage().setHeader(InfinispanConstants.CURRENT_STATE, event.isCurrentState());
                    });
        }
    }

    @CacheEntryExpired
    public void processEvent(CacheEntryExpiredEvent<?, ?> event) {
        if (isAccepted(event.getType())) {
            getEventProcessor().processEvent(
                    event.getType().toString(),
                    event.getCache().getName(),
                    event.getKey(),
                    event.getValue(),
                    e -> {
                        e.getMessage().setHeader(InfinispanConstants.IS_PRE, event.isPre());
                        e.getMessage().setHeader(InfinispanConstants.ORIGIN_LOCAL, event.isOriginLocal());
                        e.getMessage().setHeader(InfinispanConstants.CURRENT_STATE, event.isCurrentState());
                    });
        }
    }
}
