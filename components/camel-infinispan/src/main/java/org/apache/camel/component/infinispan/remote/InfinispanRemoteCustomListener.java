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

import java.util.Collections;
import java.util.Set;

import org.apache.camel.component.infinispan.InfinispanEventListener;
import org.infinispan.client.hotrod.event.ClientCacheEntryCreatedEvent;
import org.infinispan.client.hotrod.event.ClientCacheEntryCustomEvent;
import org.infinispan.client.hotrod.event.ClientCacheEntryModifiedEvent;
import org.infinispan.client.hotrod.event.ClientCacheEntryRemovedEvent;
import org.infinispan.client.hotrod.event.ClientEvent;

/**
 * This class is supposed to be extended by users and annotated with @ClientListener and passed to the consumer endpoint
 * through the 'customListener' parameter.
 */
public abstract class InfinispanRemoteCustomListener extends InfinispanEventListener<ClientEvent.Type> {
    protected InfinispanRemoteCustomListener() {
        super(Collections.emptySet());
    }

    protected InfinispanRemoteCustomListener(Set<ClientEvent.Type> events) {
        super(events);
    }

    protected void processEvent(ClientEvent event) {
        if (isAccepted(event.getType())) {
            getEventProcessor().processEvent(
                    event.getType().toString(),
                    getCacheName(),
                    getKey(event),
                    getEventData(event),
                    null);
        }
    }

    protected static Object getKey(ClientEvent event) {
        if (event instanceof ClientCacheEntryCreatedEvent) {
            return ((ClientCacheEntryCreatedEvent<?>) event).getKey();
        } else if (event instanceof ClientCacheEntryModifiedEvent) {
            return ((ClientCacheEntryModifiedEvent<?>) event).getKey();
        } else if (event instanceof ClientCacheEntryRemovedEvent) {
            return ((ClientCacheEntryRemovedEvent<?>) event).getKey();
        }
        return null;
    }

    protected static Object getEventData(ClientEvent e) {
        if (e instanceof ClientCacheEntryCustomEvent) {
            return ((ClientCacheEntryCustomEvent<?>) e).getEventData();
        }
        return null;
    }
}
