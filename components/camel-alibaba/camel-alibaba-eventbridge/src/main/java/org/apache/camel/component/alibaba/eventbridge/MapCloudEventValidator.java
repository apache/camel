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
package org.apache.camel.component.alibaba.eventbridge;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Map;

import com.aliyun.eventbridge.EventBridgeClient;
import com.aliyun.eventbridge.models.CloudEvent;
import com.aliyun.eventbridge.util.EventBuilder;
import com.google.gson.Gson;
import org.apache.camel.component.alibaba.eventbridge.constants.AlibabaEventBridgeConstants;
import org.apache.camel.component.alibaba.eventbridge.models.AllowedEventBus;
import org.apache.camel.component.alibaba.eventbridge.models.AllowedEventSource;
import org.apache.camel.component.alibaba.eventbridge.models.ClientConfigurations;
import org.apache.camel.util.ObjectHelper;

/**
 * Validates and constructs Alibaba Cloud {@link CloudEvent} instances from user-supplied payloads (Map, String, or
 * CloudEvent).
 * <p>
 * Supports multi-bus, source-scoped event validation with a two-phase validated cache workflow:
 * <ul>
 * <li><b>Cloud Validation with Cache</b> ({@code validateEventSource=true} / {@code validateEventType=true}): verifies
 * bus existence, source registration, and source-scoped event type matching against Alibaba Cloud rules, and caches the
 * verified metadata in {@link EventSourceCache}.</li>
 * <li><b>Whitelist Validation</b> ({@code allowedEventBuses}): verifies that the event bus, source, and event type
 * strictly conform to the configured whitelist.</li>
 * <li><b>CloudEvents 1.0 spec validation</b> ({@code validateEventSpec=true}): enforces RFC 3339 / ISO-8601 timestamps,
 * non-blank IDs, and 1.0 spec compliance.</li>
 * </ul>
 */
final class MapCloudEventValidator {

    private static final Gson GSON = new Gson();
    private static final String DEFAULT_SPECVERSION = "1.0";

    private final EventSourceCache eventSourceCache;

    public MapCloudEventValidator(EventSourceCache eventSourceCache) {
        this.eventSourceCache = eventSourceCache;
    }

    public CloudEvent validateAndBuild(Map<?, ?> map, ClientConfigurations configuration, EventBridgeClient client) {
        if (map == null || map.isEmpty()) {
            throw new IllegalArgumentException("Event map cannot be null or empty");
        }

        String eventBusName
                = resolveString(getMapValue(map, AlibabaEventBridgeConstants.EVENT_BUS_NAME, null),
                        configuration.eventBusName());
        if (ObjectHelper.isEmpty(eventBusName)) {
            throw new IllegalArgumentException("Event bus name is required");
        }

        String source = resolveString(getMapValue(map, AlibabaEventBridgeConstants.EVENT_SOURCE, "source"),
                configuration.eventSource());
        if (ObjectHelper.isEmpty(source)) {
            throw new IllegalArgumentException("Event 'source' cannot be empty");
        }
        URI sourceUri;
        try {
            sourceUri = URI.create(source);
        } catch (Exception e) {
            throw new IllegalArgumentException("Event 'source' is not a valid URI: " + source, e);
        }

        String type = resolveString(getMapValue(map, AlibabaEventBridgeConstants.EVENT_TYPE, "type"),
                configuration.eventType());
        if (ObjectHelper.isEmpty(type)) {
            throw new IllegalArgumentException("Event 'type' cannot be empty");
        }

        if (configuration.validateEventSpec()) {
            validateSpecConstraints(map);
        }

        validateBusSourceAndType(eventBusName, source, type, configuration, client);

        EventBuilder builder = EventBuilder.builder()
                .withSource(sourceUri)
                .withType(type)
                .withAliyunEventBus(eventBusName);

        String id = resolveString(getMapValue(map, "id", null), null);
        if (ObjectHelper.isNotEmpty(id)) {
            builder.withId(id);
        }

        String subject = resolveString(getMapValue(map, AlibabaEventBridgeConstants.EVENT_SUBJECT, "subject"),
                configuration.eventSubject());
        if (ObjectHelper.isNotEmpty(subject)) {
            builder.withSubject(subject);
        }

        Date time = parseTime(getMapValue(map, "time", null));
        if (time != null) {
            builder.withTime(time);
        }

        String data = serializeData(getMapValue(map, AlibabaEventBridgeConstants.EVENT_DATA, "data"));
        if (data != null) {
            builder.withJsonStringData(data);
        }

        CloudEvent event = builder.build();

        String dataContentType = resolveString(getMapValue(map, "datacontenttype", null), null);
        if (ObjectHelper.isNotEmpty(dataContentType)) {
            event.setDatacontenttype(dataContentType);
        }

        String dataSchema = resolveString(getMapValue(map, "dataschema", null), null);
        if (ObjectHelper.isNotEmpty(dataSchema)) {
            try {
                URI.create(dataSchema);
                event.setDataschema(dataSchema);
            } catch (Exception e) {
                throw new IllegalArgumentException("Event 'dataschema' is not a valid URI: " + dataSchema, e);
            }
        }

        return event;
    }

    public void validateCloudEvent(
            CloudEvent cloudEvent, ClientConfigurations configuration, EventBridgeClient client) {
        if (cloudEvent == null) {
            throw new IllegalArgumentException("CloudEvent cannot be null");
        }

        String eventBusName = extractEventBusName(cloudEvent);
        if (ObjectHelper.isEmpty(eventBusName)) {
            eventBusName = configuration.eventBusName();
        }
        if (ObjectHelper.isEmpty(eventBusName)) {
            throw new IllegalArgumentException("Event bus name is required");
        }

        String source = cloudEvent.getSource() != null ? cloudEvent.getSource() : configuration.eventSource();
        if (ObjectHelper.isEmpty(source)) {
            throw new IllegalArgumentException("Event 'source' cannot be empty");
        }

        String type = ObjectHelper.isNotEmpty(cloudEvent.getType())
                ? cloudEvent.getType() : configuration.eventType();
        if (ObjectHelper.isEmpty(type)) {
            throw new IllegalArgumentException("Event 'type' cannot be empty");
        }

        validateBusSourceAndType(eventBusName, source, type, configuration, client);
    }

    private static String extractEventBusName(CloudEvent cloudEvent) {
        if (cloudEvent != null && cloudEvent.getExtensions() != null) {
            Object bus = cloudEvent.getExtensions().get("aliyuneventbusname");
            if (bus == null) {
                bus = cloudEvent.getExtensions().get("aliyunEventBus");
            }
            if (bus == null) {
                bus = cloudEvent.getExtensions().get("eventBusName");
            }
            if (bus != null) {
                return bus.toString();
            }
        }
        return null;
    }

    /**
     * Validates event bus, source, and event type against both Alibaba Cloud EventBridge (via {@link EventSourceCache})
     * and the configured {@link AllowedEventBus} whitelists.
     */
    public void validateBusSourceAndType(
            String eventBusName, String source, String type,
            ClientConfigurations configuration, EventBridgeClient client) {

        AllowedEventBus allowedBus = findAllowedBus(eventBusName, configuration.allowedEventBuses());

        if (eventSourceCache != null && (configuration.validateEventSource() || configuration.validateEventType())) {
            eventSourceCache.validateAndUpdateCache(
                    eventBusName, allowedBus,
                    configuration.validateEventSource(),
                    configuration.validateEventType(),
                    configuration.eventSourceCacheTtl(),
                    client);

            if (!eventSourceCache.isKnownEventBus(eventBusName, configuration.eventSourceCacheTtl(), client)) {
                throw new IllegalArgumentException(
                        String.format("Event bus '%s' does not exist in Alibaba Cloud EventBridge", eventBusName));
            }

            if (configuration.validateEventSource()
                    && !eventSourceCache.isKnownEventSource(eventBusName, source, configuration.eventSourceCacheTtl(),
                            client)) {
                throw new IllegalArgumentException(
                        String.format("Event source '%s' is not registered on event bus '%s' in Alibaba Cloud EventBridge",
                                source, eventBusName));
            }

            if (configuration.validateEventType()
                    && !eventSourceCache.isKnownEventType(eventBusName, source, type, configuration.eventSourceCacheTtl(),
                            client)) {
                throw new IllegalArgumentException(
                        String.format(
                                "Event type '%s' is not valid for event source '%s' on event bus '%s' in Alibaba Cloud EventBridge",
                                type, source, eventBusName));
            }
        }

        if (configuration.allowedEventBuses() != null && !configuration.allowedEventBuses().isEmpty()) {
            if (allowedBus == null) {
                throw new IllegalArgumentException(
                        String.format("Event bus '%s' is not in the allowed event buses list: %s",
                                eventBusName, configuration.allowedEventBuses().keySet()));
            }

            if (!allowedBus.allowsSource(source)) {
                throw new IllegalArgumentException(
                        String.format("Event source '%s' is not in the allowed sources list for bus '%s'. Allowed: %s",
                                source, eventBusName, allowedBus.allowedSources().keySet()));
            }

            if (!allowedBus.allowsSourceAndType(source, type)) {
                AllowedEventSource sourceDef = allowedBus.allowedSources().get(source);
                throw new IllegalArgumentException(
                        String.format("Event type '%s' is not allowed for event source '%s' on bus '%s'. Allowed types: %s",
                                type, source, eventBusName, sourceDef != null ? sourceDef.allowedEventTypes() : "[]"));
            }
        }
    }

    private AllowedEventBus findAllowedBus(String eventBusName, Map<String, AllowedEventBus> allowedBuses) {
        if (allowedBuses == null || allowedBuses.isEmpty()) {
            return null;
        }
        if (eventBusName != null && allowedBuses.containsKey(eventBusName)) {
            return allowedBuses.get(eventBusName);
        }
        return allowedBuses.get("*");
    }

    private Object getMapValue(Map<?, ?> map, String primaryKey, String secondaryKey) {
        if (map == null) {
            return null;
        }
        Object val = map.get(primaryKey);
        if (val != null) {
            return val;
        }
        return secondaryKey != null ? map.get(secondaryKey) : null;
    }

    private void validateSpecConstraints(Map<?, ?> map) {
        Object specversionObj = map.get("specversion");
        if (specversionObj != null) {
            String specversion = specversionObj.toString().trim();
            if (!DEFAULT_SPECVERSION.equals(specversion)) {
                throw new IllegalArgumentException(
                        "Invalid CloudEvent specversion: '" + specversion
                                                   + "'. Only '" + DEFAULT_SPECVERSION + "' is supported.");
            }
        }

        Object idObj = map.get("id");
        if (idObj != null && idObj.toString().trim().isEmpty()) {
            throw new IllegalArgumentException("Event 'id' cannot be blank when provided");
        }
    }

    private String resolveString(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String str = value.toString().trim();
        return str.isEmpty() ? fallback : str;
    }

    private Date parseTime(Object timeObj) {
        if (timeObj == null) {
            return null;
        }
        if (timeObj instanceof Date date) {
            return date;
        }
        if (timeObj instanceof Instant instant) {
            return Date.from(instant);
        }
        if (timeObj instanceof Number number) {
            return new Date(number.longValue());
        }
        if (timeObj instanceof String timeString) {
            try {
                Instant instant = Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(timeString));
                return Date.from(instant);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException(
                        "Event 'time' must be in RFC 3339 / ISO-8601 format: " + timeString, e);
            }
        }
        return null;
    }

    private String serializeData(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String stringValue) {
            return stringValue;
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        try {
            return GSON.toJson(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to serialize event data as JSON", e);
        }
    }
}
