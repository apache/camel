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
import java.util.Set;

/**
 * Java 16 record defining an allowed event source and its source-scoped allowed event types.
 */
public record AllowedEventSource(String source, Set<String> allowedEventTypes) {

    public AllowedEventSource {
        if (source != null) {
            source = source.trim();
        }
        if (allowedEventTypes == null) {
            allowedEventTypes = Collections.emptySet();
        } else {
            allowedEventTypes = Collections.unmodifiableSet(allowedEventTypes);
        }
    }

    public AllowedEventSource(String source) {
        this(source, Collections.emptySet());
    }

    /**
     * Checks whether the given event type is permitted for this source. When {@link #allowedEventTypes()} is empty, all
     * event types are permitted for this source.
     */
    public boolean allowsType(String eventType) {
        if (allowedEventTypes == null || allowedEventTypes.isEmpty()) {
            return true;
        }
        return eventType != null && allowedEventTypes.contains(eventType.trim());
    }
}
