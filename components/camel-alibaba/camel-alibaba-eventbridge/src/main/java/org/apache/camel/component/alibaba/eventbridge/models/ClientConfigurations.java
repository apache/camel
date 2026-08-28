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
 * Java 16 record holding resolved client configurations for Alibaba Cloud EventBridge.
 */
public record ClientConfigurations(
        String operation,
        String eventBusName,
        String eventSource,
        String eventType,
        String eventSubject,
        boolean validateEventSource,
        boolean validateEventType,
        boolean validateEventSpec,
        Map<String, AllowedEventBus> allowedEventBuses,
        long eventSourceCacheTtl) {

    public ClientConfigurations {
        if (allowedEventBuses == null) {
            allowedEventBuses = Collections.emptyMap();
        } else {
            allowedEventBuses = Collections.unmodifiableMap(allowedEventBuses);
        }
    }

    public ClientConfigurations() {
        this(null, null, null, null, null, false, false, true, Collections.emptyMap(), 300000L);
    }

    public ClientConfigurations(
                                String operation,
                                String eventBusName,
                                String eventSource,
                                String eventType,
                                String eventSubject) {
        this(operation, eventBusName, eventSource, eventType, eventSubject, false, false, true, Collections.emptyMap(),
             300000L);
    }

    public ClientConfigurations(
                                String operation,
                                String eventBusName,
                                String eventSource,
                                String eventType,
                                String eventSubject,
                                boolean validateEventSource,
                                boolean validateEventSpec) {
        this(operation, eventBusName, eventSource, eventType, eventSubject, validateEventSource, false, validateEventSpec,
             Collections.emptyMap(), 300000L);
    }

    public ClientConfigurations(
                                String operation,
                                String eventBusName,
                                String eventSource,
                                String eventType,
                                String eventSubject,
                                boolean validateEventSource,
                                boolean validateEventType,
                                boolean validateEventSpec,
                                Map<String, AllowedEventBus> allowedEventBuses) {
        this(operation, eventBusName, eventSource, eventType, eventSubject, validateEventSource, validateEventType,
             validateEventSpec, allowedEventBuses, 300000L);
    }
}
