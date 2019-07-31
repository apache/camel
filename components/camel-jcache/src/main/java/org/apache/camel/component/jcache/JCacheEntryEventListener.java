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
package org.apache.camel.component.jcache;

import javax.cache.event.CacheEntryCreatedListener;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryExpiredListener;
import javax.cache.event.CacheEntryListenerException;
import javax.cache.event.CacheEntryRemovedListener;
import javax.cache.event.CacheEntryUpdatedListener;

class JCacheEntryEventListener implements CacheEntryCreatedListener<Object, Object>,
    CacheEntryUpdatedListener<Object, Object>,
    CacheEntryRemovedListener<Object, Object>,
    CacheEntryExpiredListener<Object, Object> {

    @Override
    public void onCreated(Iterable<CacheEntryEvent<?, ?>> events) throws CacheEntryListenerException {
        onEvents(events);
    }

    @Override
    public void onExpired(Iterable<CacheEntryEvent<?, ?>> events) throws CacheEntryListenerException {
        onEvents(events);
    }

    @Override
    public void onRemoved(Iterable<CacheEntryEvent<?, ?>> events) throws CacheEntryListenerException {
        onEvents(events);
    }

    @Override
    public void onUpdated(Iterable<CacheEntryEvent<?, ?>> events) throws CacheEntryListenerException {
        onEvents(events);
    }

    protected void onEvents(Iterable<CacheEntryEvent<?, ?>> events) {
    }
}
