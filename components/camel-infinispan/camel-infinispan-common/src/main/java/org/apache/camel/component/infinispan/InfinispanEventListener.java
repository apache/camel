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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public abstract class InfinispanEventListener<T> {
    private final Set<T> events;

    private InfinispanEventProcessor eventProcessor;
    private String cacheName;

    protected InfinispanEventListener(Set<T> events) {
        this.events = Collections.unmodifiableSet(new HashSet<>(events));
    }

    public String getCacheName() {
        return cacheName;
    }

    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }

    public InfinispanEventProcessor getEventProcessor() {
        return eventProcessor;
    }

    public void setEventProcessor(InfinispanEventProcessor eventProcessor) {
        this.eventProcessor = eventProcessor;
    }

    public Set<T> getEvents() {
        return events;
    }

    protected boolean isAccepted(T eventType) {
        return events == null || events.isEmpty() || events.contains(eventType);
    }
}
