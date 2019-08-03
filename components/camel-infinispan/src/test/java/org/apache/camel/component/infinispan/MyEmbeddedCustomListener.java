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
package org.apache.camel.component.infinispan;

import org.apache.camel.component.infinispan.embedded.InfinispanEmbeddedCustomListener;
import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryCreated;
import org.infinispan.notifications.cachelistener.event.CacheEntryEvent;

import static org.junit.Assert.assertEquals;

@Listener(sync = true)
public class MyEmbeddedCustomListener extends InfinispanEmbeddedCustomListener {

    private final String cacheName;

    public MyEmbeddedCustomListener(String cacheName) {
        this.cacheName = cacheName;
    }

    @Override
    @CacheEntryCreated
    public void processEvent(CacheEntryEvent<Object, Object> event) {
        if (isAccepted(event.getType().toString())) {
            infinispanConsumer.processEvent(event.getType().toString(), event.isPre(), event.getCache().getName(), event.getKey());
            assertEquals(cacheName, event.getCache().getName());
        }
    }
}
