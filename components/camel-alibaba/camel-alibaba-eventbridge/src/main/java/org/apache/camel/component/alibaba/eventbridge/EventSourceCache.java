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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.aliyun.eventbridge.EventBridgeClient;
import com.aliyun.eventbridge.models.EventBusEntry;
import com.aliyun.eventbridge.models.EventRuleDTO;
import com.aliyun.eventbridge.models.ListEventBusesRequest;
import com.aliyun.eventbridge.models.ListEventBusesResponse;
import com.aliyun.eventbridge.models.ListRulesRequest;
import com.aliyun.eventbridge.models.ListRulesResponse;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.camel.component.alibaba.eventbridge.models.AllowedEventBus;
import org.apache.camel.component.alibaba.eventbridge.models.AllowedEventSource;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TTL-based in-memory cache for verified Alibaba Cloud EventBridge bus, event source, and source-scoped event type
 * definitions.
 * <p>
 * Implements a two-phase validated cache workflow:
 * <ol>
 * <li>Fetch and validate configured event sources and event types against Alibaba Cloud API ({@code listEventBuses} and
 * {@code listRules}).</li>
 * <li><b>Only after validation passes</b>, populate the cache with the verified {@link BusMetadata} for fast runtime
 * comparison.</li>
 * </ol>
 */
final class EventSourceCache {

    private static final Logger LOG = LoggerFactory.getLogger(EventSourceCache.class);
    private static final Gson GSON = new Gson();
    private static final int PAGE_LIMIT = 100;

    /**
     * Java 16 record holding cached bus metadata including mapped event sources and their permitted event types.
     */
    public record BusMetadata(boolean exists, Map<String, Set<String>> sourceToTypesMap) {
        public BusMetadata {
            if (sourceToTypesMap == null) {
                sourceToTypesMap = Collections.emptyMap();
            } else {
                Map<String, Set<String>> unmodifiable = new HashMap<>();
                for (Map.Entry<String, Set<String>> entry : sourceToTypesMap.entrySet()) {
                    unmodifiable.put(entry.getKey(), Collections.unmodifiableSet(new HashSet<>(entry.getValue())));
                }
                sourceToTypesMap = Collections.unmodifiableMap(unmodifiable);
            }
        }

        public boolean isKnownSource(String source) {
            if (source == null || sourceToTypesMap.isEmpty()) {
                return true;
            }
            return sourceToTypesMap.containsKey(source.trim()) || sourceToTypesMap.containsKey("*");
        }

        public boolean isKnownType(String source, String eventType) {
            if (eventType == null || sourceToTypesMap.isEmpty()) {
                return true;
            }
            Set<String> types = sourceToTypesMap.get(source != null ? source.trim() : null);
            if (types == null || types.isEmpty()) {
                types = sourceToTypesMap.get("*");
            }
            if (types == null || types.isEmpty()) {
                return true;
            }
            return types.contains(eventType.trim());
        }
    }

    /**
     * Java 16 record representing a TTL-aware cache entry.
     */
    public record CacheEntry<T>(T value, long expiryTime) {
        public boolean isExpired(long now) {
            return now >= expiryTime;
        }
    }

    private final Map<String, CacheEntry<BusMetadata>> cache = new ConcurrentHashMap<>();
    private final long ttlMillis;

    EventSourceCache(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    /**
     * Validates the given {@code eventBusName} and any configured {@link AllowedEventBus} definitions against Alibaba
     * Cloud. Upon successful validation, the verified metadata is stored in the cache.
     *
     * @param  eventBusName   the target event bus name
     * @param  allowedBus     the configured whitelist rules for this bus, or {@code null}
     * @param  validateSource whether to validate event source existence against Alibaba Cloud
     * @param  validateType   whether to validate event types against Alibaba Cloud rule filter patterns
     * @param  client         the EventBridge client instance; if {@code null}, validation is bypassed
     * @return                the verified {@link BusMetadata}
     */
    BusMetadata validateAndUpdateCache(
            String eventBusName, AllowedEventBus allowedBus,
            boolean validateSource, boolean validateType, EventBridgeClient client) {
        if (client == null || ObjectHelper.isEmpty(eventBusName)) {
            return new BusMetadata(true, Collections.emptyMap());
        }

        long now = System.currentTimeMillis();
        CacheEntry<BusMetadata> entry = cache.get(eventBusName);
        if (entry != null && !entry.isExpired(now)) {
            return entry.value();
        }

        boolean busExists = fetchEventBusExists(eventBusName, client);
        if (!busExists) {
            throw new IllegalArgumentException(
                    String.format("Event bus '%s' does not exist in Alibaba Cloud EventBridge", eventBusName));
        }

        Map<String, Set<String>> cloudSourceToTypes = fetchCloudSourceToTypes(eventBusName, client);

        if (validateSource && allowedBus != null && !allowedBus.allowedSources().isEmpty() && !cloudSourceToTypes.isEmpty()) {
            for (String source : allowedBus.allowedSources().keySet()) {
                if (!cloudSourceToTypes.containsKey(source) && !cloudSourceToTypes.containsKey("*")) {
                    throw new IllegalArgumentException(
                            String.format(
                                    "Event source '%s' is not registered in Alibaba Cloud rules for event bus '%s'. Known sources: %s",
                                    source, eventBusName, cloudSourceToTypes.keySet()));
                }
            }
        }

        if (validateType && allowedBus != null && !allowedBus.allowedSources().isEmpty() && !cloudSourceToTypes.isEmpty()) {
            for (Map.Entry<String, AllowedEventSource> sourceEntry : allowedBus.allowedSources().entrySet()) {
                String source = sourceEntry.getKey();
                Set<String> allowedTypes = sourceEntry.getValue().allowedEventTypes();
                if (allowedTypes != null && !allowedTypes.isEmpty()) {
                    Set<String> cloudTypes = cloudSourceToTypes.get(source);
                    if (cloudTypes == null || cloudTypes.isEmpty()) {
                        cloudTypes = cloudSourceToTypes.get("*");
                    }
                    if (cloudTypes != null && !cloudTypes.isEmpty()) {
                        for (String type : allowedTypes) {
                            if (!cloudTypes.contains(type)) {
                                throw new IllegalArgumentException(
                                        String.format(
                                                "Event type '%s' is not registered in Alibaba Cloud rules for source '%s' on bus '%s'. Allowed in Cloud: %s",
                                                type, source, eventBusName, cloudTypes));
                            }
                        }
                    }
                }
            }
        }

        BusMetadata metadata = new BusMetadata(true, cloudSourceToTypes);
        cache.put(eventBusName, new CacheEntry<>(metadata, now + ttlMillis));
        return metadata;
    }

    /**
     * Checks if the event bus is known to exist.
     */
    boolean isKnownEventBus(String eventBusName, EventBridgeClient client) {
        if (client == null || ObjectHelper.isEmpty(eventBusName)) {
            return true;
        }
        long now = System.currentTimeMillis();
        CacheEntry<BusMetadata> entry = cache.get(eventBusName);
        if (entry != null && !entry.isExpired(now)) {
            return entry.value().exists();
        }
        return fetchEventBusExists(eventBusName, client);
    }

    /**
     * Checks if the event source is registered for the given bus.
     */
    boolean isKnownEventSource(String eventBusName, String eventSource, EventBridgeClient client) {
        if (client == null || ObjectHelper.isEmpty(eventBusName) || ObjectHelper.isEmpty(eventSource)) {
            return true;
        }
        BusMetadata metadata = validateAndUpdateCache(eventBusName, null, false, false, client);
        return metadata.isKnownSource(eventSource);
    }

    /**
     * Checks if the event type is valid for the given event source on the bus.
     */
    boolean isKnownEventType(String eventBusName, String eventSource, String eventType, EventBridgeClient client) {
        if (client == null || ObjectHelper.isEmpty(eventBusName) || ObjectHelper.isEmpty(eventType)) {
            return true;
        }
        BusMetadata metadata = validateAndUpdateCache(eventBusName, null, false, false, client);
        return metadata.isKnownType(eventSource, eventType);
    }

    /**
     * Fetches whether the event bus exists via {@code listEventBuses}.
     */
    boolean fetchEventBusExists(String eventBusName, EventBridgeClient client) {
        try {
            String nextToken = null;
            do {
                ListEventBusesRequest request = new ListEventBusesRequest()
                        .setNamePrefix(eventBusName)
                        .setLimit(PAGE_LIMIT);
                if (nextToken != null) {
                    request.setNextToken(nextToken);
                }

                ListEventBusesResponse response = client.listEventBuses(request);
                List<EventBusEntry> buses = response.getEventBuses();
                if (buses != null) {
                    for (EventBusEntry bus : buses) {
                        if (eventBusName.equals(bus.getEventBusName())) {
                            return true;
                        }
                    }
                }
                nextToken = response.getNextToken();
            } while (nextToken != null && !nextToken.isEmpty());

        } catch (Exception e) {
            LOG.warn("Failed to verify event bus '{}' existence via Alibaba Cloud EventBridge API: {}",
                    eventBusName, e.getMessage());
            return true;
        }
        return false;
    }

    /**
     * Fetches all rules on the bus and extracts source -> event types mappings from rule filter patterns.
     */
    Map<String, Set<String>> fetchCloudSourceToTypes(String eventBusName, EventBridgeClient client) {
        Map<String, Set<String>> sourceToTypes = new HashMap<>();
        try {
            String nextToken = null;
            do {
                ListRulesRequest request = new ListRulesRequest()
                        .setEventBusName(eventBusName)
                        .setLimit(PAGE_LIMIT);
                if (nextToken != null) {
                    request.setNextToken(nextToken);
                }

                ListRulesResponse response = client.listRules(request);
                if (response != null && response.getRules() != null) {
                    for (EventRuleDTO rule : response.getRules()) {
                        parseFilterPattern(rule.getFilterPattern(), sourceToTypes);
                    }
                }
                nextToken = response != null ? response.getNextToken() : null;
            } while (nextToken != null && !nextToken.isEmpty());

        } catch (Exception e) {
            LOG.warn("Failed to query rules for event bus '{}' via Alibaba Cloud EventBridge API: {}",
                    eventBusName, e.getMessage());
        }
        return sourceToTypes;
    }

    /**
     * Parses an Alibaba Cloud EventBridge rule filter pattern JSON string to extract source-to-types mappings.
     */
    static void parseFilterPattern(String filterPatternJson, Map<String, Set<String>> sourceToTypes) {
        if (ObjectHelper.isEmpty(filterPatternJson)) {
            return;
        }
        try {
            JsonElement root = GSON.fromJson(filterPatternJson, JsonElement.class);
            if (!root.isJsonObject()) {
                return;
            }
            JsonObject obj = root.getAsJsonObject();

            Set<String> sources = extractStringValues(obj.get("source"));
            Set<String> types = extractStringValues(obj.get("type"));

            if (sources.isEmpty() && !types.isEmpty()) {
                sourceToTypes.computeIfAbsent("*", s -> new HashSet<>()).addAll(types);
            } else {
                for (String src : sources) {
                    Set<String> existingTypes = sourceToTypes.computeIfAbsent(src, s -> new HashSet<>());
                    if (!types.isEmpty()) {
                        existingTypes.addAll(types);
                    }
                }
            }
        } catch (Exception e) {
            LOG.debug("Could not parse rule filterPattern JSON '{}': {}", filterPatternJson, e.getMessage());
        }
    }

    private static Set<String> extractStringValues(JsonElement element) {
        Set<String> results = new HashSet<>();
        if (element == null || element.isJsonNull()) {
            return results;
        }
        if (element.isJsonPrimitive()) {
            results.add(element.getAsString());
        } else if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            for (JsonElement item : arr) {
                if (item.isJsonPrimitive()) {
                    results.add(item.getAsString());
                } else if (item.isJsonObject()) {
                    JsonObject itemObj = item.getAsJsonObject();
                    if (itemObj.has("prefix")) {
                        results.add(itemObj.get("prefix").getAsString());
                    }
                }
            }
        }
        return results;
    }

    Set<String> cachedBusNames() {
        return Collections.unmodifiableSet(cache.keySet());
    }

    BusMetadata getCachedMetadata(String eventBusName) {
        CacheEntry<BusMetadata> entry = cache.get(eventBusName);
        return entry != null ? entry.value() : null;
    }

    void clear() {
        cache.clear();
    }

    void invalidate(String eventBusName) {
        if (eventBusName != null) {
            cache.remove(eventBusName);
        }
    }
}
