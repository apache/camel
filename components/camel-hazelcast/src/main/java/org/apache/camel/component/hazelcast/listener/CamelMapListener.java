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
package org.apache.camel.component.hazelcast.listener;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.map.MapEvent;
import org.apache.camel.component.hazelcast.HazelcastConstants;
import org.apache.camel.component.hazelcast.HazelcastDefaultConsumer;

/**
 *
 */
public class CamelMapListener extends CamelListener implements MapEntryListener<Object, Object> {

    public CamelMapListener(HazelcastDefaultConsumer consumer, String cacheName) {
        super(consumer, cacheName);
    }

    @Override
    public void entryAdded(EntryEvent<Object, Object> event) {
        this.sendExchange(HazelcastConstants.ADDED, event.getKey(), event.getValue());
    }

    @Override
    public void entryEvicted(EntryEvent<Object, Object> event) {
        this.sendExchange(HazelcastConstants.EVICTED, event.getKey(), event.getValue());
    }

    @Override
    public void entryMerged(EntryEvent<Object, Object> event) {
        // noop
    }

    @Override
    public void entryRemoved(EntryEvent<Object, Object> event) {
        this.sendExchange(HazelcastConstants.REMOVED, event.getKey(), event.getValue());
    }

    @Override
    public void entryUpdated(EntryEvent<Object, Object> event) {
        this.sendExchange(HazelcastConstants.UPDATED, event.getKey(), event.getValue());
    }

    @Override
    public void mapCleared(MapEvent event) {
        // noop
    }

    @Override
    public void mapEvicted(MapEvent event) {
        // noop
    }
}
