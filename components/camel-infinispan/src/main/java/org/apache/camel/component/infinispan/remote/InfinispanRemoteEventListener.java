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

import java.util.Set;

import org.apache.camel.component.infinispan.InfinispanConstants;
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
import org.infinispan.client.hotrod.event.ClientEvent;

@ClientListener
public class InfinispanRemoteEventListener extends InfinispanEventListener<ClientEvent.Type> {
    public InfinispanRemoteEventListener(Set<ClientEvent.Type> events) {
        super(events);
    }

    @ClientCacheEntryCreated
    public void processEvent(ClientCacheEntryCreatedEvent<Object> event) {
        if (isAccepted(event.getType())) {
            getEventProcessor().processEvent(event.getType().toString(), getCacheName(), event.getKey(), null, e -> {
                e.getMessage().setHeader(InfinispanConstants.ENTRY_VERSION, event.getVersion());
                e.getMessage().setHeader(InfinispanConstants.COMMAND_RETRIED, event.isCommandRetried());
            });
        }
    }

    @ClientCacheEntryModified
    public void processEvent(ClientCacheEntryModifiedEvent<Object> event) {
        if (isAccepted(event.getType())) {
            getEventProcessor().processEvent(event.getType().toString(), getCacheName(), event.getKey(), null, e -> {
                e.getMessage().setHeader(InfinispanConstants.ENTRY_VERSION, event.getVersion());
                e.getMessage().setHeader(InfinispanConstants.COMMAND_RETRIED, event.isCommandRetried());
            });
        }
    }

    @ClientCacheEntryRemoved
    public void processEvent(ClientCacheEntryRemovedEvent<Object> event) {
        if (isAccepted(event.getType())) {
            getEventProcessor().processEvent(event.getType().toString(), getCacheName(), event.getKey(), null, e -> {
                e.getMessage().setHeader(InfinispanConstants.COMMAND_RETRIED, event.isCommandRetried());
            });
        }
    }

    @ClientCacheFailover
    public void processEvent(ClientCacheFailoverEvent event) {
        if (isAccepted(event.getType())) {
            getEventProcessor().processEvent(event.getType().toString(), getCacheName(), null, null, null);
        }
    }

    @ClientCacheEntryExpired
    public void processEvent(ClientCacheEntryExpiredEvent<Object> event) {
        if (isAccepted(event.getType())) {
            getEventProcessor().processEvent(event.getType().toString(), getCacheName(), event.getKey(), null, null);
        }
    }
}
