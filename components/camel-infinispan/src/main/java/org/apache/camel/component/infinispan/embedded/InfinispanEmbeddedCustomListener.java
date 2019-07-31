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

import org.apache.camel.component.infinispan.InfinispanCustomListener;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryCreated;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryExpired;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryModified;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryRemoved;
import org.infinispan.notifications.cachelistener.event.CacheEntryEvent;

/**
 * This class is supposed to be extended by users and annotated with @Listener
 * and passed to the consumer endpoint through the 'customListener' parameter.
 */
public abstract class InfinispanEmbeddedCustomListener extends InfinispanCustomListener {

    public InfinispanEmbeddedCustomListener() {
        super(null, null);
    }

    @CacheEntryCreated
    @CacheEntryModified
    @CacheEntryRemoved
    @CacheEntryExpired
    public void processEvent(CacheEntryEvent<Object, Object> event) {
        if (isAccepted(event.getType().toString())) {
            infinispanConsumer.processEvent(event.getType().toString(), event.isPre(), event.getCache().getName(), event.getKey());
        }
    }

}
