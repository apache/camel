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
package org.apache.camel.component.alibaba.eventbridge.models;

import java.util.Collections;
import java.util.Map;

/**
 * Java 16 record defining an event bus and its bus-scoped allowed event sources.
 */
public record AllowedEventBus(String eventBusName, Map<String, AllowedEventSource> allowedSources) {

    public AllowedEventBus {
        if (eventBusName != null) {
            eventBusName = eventBusName.trim();
        }
        if (allowedSources == null) {
            allowedSources = Collections.emptyMap();
        } else {
            allowedSources = Collections.unmodifiableMap(allowedSources);
        }
    }

    public AllowedEventBus(String eventBusName) {
        this(eventBusName, Collections.emptyMap());
    }

    /**
     * Checks whether the given event source is permitted on this bus. When {@link #allowedSources()} is empty, all
     * sources are permitted.
     */
    public boolean allowsSource(String source) {
        if (allowedSources == null || allowedSources.isEmpty()) {
            return true;
        }
        return source != null && allowedSources.containsKey(source.trim());
    }

    /**
     * Checks whether the given event source and event type combination is permitted on this bus.
     */
    public boolean allowsSourceAndType(String source, String eventType) {
        if (allowedSources == null || allowedSources.isEmpty()) {
            return true;
        }
        if (source == null) {
            return false;
        }
        AllowedEventSource sourceDef = allowedSources.get(source.trim());
        if (sourceDef == null) {
            return false;
        }
        return sourceDef.allowsType(eventType);
    }
}
