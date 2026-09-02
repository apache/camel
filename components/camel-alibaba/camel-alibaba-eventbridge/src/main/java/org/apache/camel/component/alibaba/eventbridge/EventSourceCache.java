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
     * Holds exact and prefix type matching rules for a specific event source (or wildcard).
     */
    public record SourceFilter(Set<String> exactTypes, Set<String> prefixTypes) {
        public SourceFilter {
            exactTypes = exactTypes == null ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(exactTypes));
            prefixTypes
                    = prefixTypes == null ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(prefixTypes));
        }

        public boolean matchesType(String eventType) {
            if (eventType == null) {
                return false;
            }
            String trimmed = eventType.trim();
            if (exactTypes.isEmpty() && prefixTypes.isEmpty()) {
                return true;
            }
            if (exactTypes.contains("*") || exactTypes.contains(trimmed)) {
                return true;
            }
            for (String prefix : prefixTypes) {
                if (trimmed.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Java 16 record holding cached bus metadata including mapped event sources and their permitted event types.
     */
    public record BusMetadata(
            boolean exists,
            Set<String> exactSources,
            Set<String> prefixSources,
            Map<String, SourceFilter> sourceToTypesMap) {

        public BusMetadata {
            exactSources
                    = exactSources == null ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(exactSources));
            prefixSources = prefixSources == null
                    ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(prefixSources));
            if (sourceToTypesMap == null) {
                sourceToTypesMap = Collections.emptyMap();
            } else {
                Map<String, SourceFilter> unmodifiable = new HashMap<>();
                for (Map.Entry<String, SourceFilter> entry : sourceToTypesMap.entrySet()) {
                    unmodifiable.put(entry.getKey(), entry.getValue());
                }
                sourceToTypesMap = Collections.unmodifiableMap(unmodifiable);
            }
        }

        public boolean isKnownSource(String source) {
            if (source == null || !exists) {
                return false;
            }
            if (exactSources.isEmpty() && prefixSources.isEmpty() && sourceToTypesMap.isEmpty()) {
                return false;
            }
            String trimmed = source.trim();
            if (exactSources.contains("*") || exactSources.contains(trimmed) || sourceToTypesMap.containsKey("*")
                    || sourceToTypesMap.containsKey(trimmed)) {
                return true;
            }
            for (String prefix : prefixSources) {
                if (trimmed.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        }

        public boolean isKnownType(String source, String eventType) {
            if (eventType == null || !exists) {
                return false;
            }
            if (sourceToTypesMap.isEmpty()) {
                return false;
            }

            String trimmedSource = source != null ? source.trim() : null;
            String trimmedType = eventType.trim();

            SourceFilter filter = sourceToTypesMap.get(trimmedSource);
            if (filter != null && filter.matchesType(trimmedType)) {
                return true;
            }

            for (Map.Entry<String, SourceFilter> entry : sourceToTypesMap.entrySet()) {
                String srcKey = entry.getKey();
                if (trimmedSource != null && !srcKey.equals("*") && trimmedSource.startsWith(srcKey)) {
                    if (entry.getValue().matchesType(trimmedType)) {
                        return true;
                    }
                }
            }

            for (String prefix : prefixSources) {
                if (trimmedSource != null && trimmedSource.startsWith(prefix)) {
                    SourceFilter prefixFilter = sourceToTypesMap.get(prefix);
                    if (prefixFilter != null && prefixFilter.matchesType(trimmedType)) {
                        return true;
                    }
                }
            }

            SourceFilter wildcardFilter = sourceToTypesMap.get("*");
            if (wildcardFilter != null && wildcardFilter.matchesType(trimmedType)) {
                return true;
            }

            return false;
        }

        public String knownSourcesDescription() {
            Set<String> all = new HashSet<>(exactSources);
            for (String prefix : prefixSources) {
                all.add(prefix + "*");
            }
            return all.toString();
        }

        public String knownTypesDescription(String source) {
            SourceFilter filter = sourceToTypesMap.get(source);
            if (filter == null) {
                filter = sourceToTypesMap.get("*");
            }
            if (filter != null) {
                Set<String> all = new HashSet<>(filter.exactTypes());
                for (String p : filter.prefixTypes()) {
                    all.add(p + "*");
                }
                return all.toString();
            }
            return "[]";
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
     * @param  eventBusName       the target event bus name
     * @param  allowedBus         the configured whitelist rules for this bus, or {@code null}
     * @param  validateSource     whether to validate event source existence against Alibaba Cloud
     * @param  validateType       whether to validate event types against Alibaba Cloud rule filter patterns
     * @param  effectiveTtlMillis the effective TTL in milliseconds for caching verified metadata
     * @param  client             the EventBridge client instance; if {@code null}, validation is bypassed
     * @return                    the verified {@link BusMetadata}
     */
    BusMetadata validateAndUpdateCache(
            String eventBusName, AllowedEventBus allowedBus,
            boolean validateSource, boolean validateType, long effectiveTtlMillis, EventBridgeClient client) {
        if (client == null || ObjectHelper.isEmpty(eventBusName)) {
            return new BusMetadata(true, Collections.emptySet(), Collections.emptySet(), Collections.emptyMap());
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

        BusMetadata metadata = fetchCloudRules(eventBusName, client);

        if (validateSource && allowedBus != null && !allowedBus.allowedSources().isEmpty()) {
            for (String source : allowedBus.allowedSources().keySet()) {
                if (!metadata.isKnownSource(source)) {
                    throw new IllegalArgumentException(
                            String.format(
                                    "Event source '%s' is not registered in Alibaba Cloud rules for event bus '%s'. Known sources: %s",
                                    source, eventBusName, metadata.knownSourcesDescription()));
                }
            }
        }

        if (validateType && allowedBus != null && !allowedBus.allowedSources().isEmpty()) {
            for (Map.Entry<String, AllowedEventSource> sourceEntry : allowedBus.allowedSources().entrySet()) {
                String source = sourceEntry.getKey();
                Set<String> allowedTypes = sourceEntry.getValue().allowedEventTypes();
                if (allowedTypes != null && !allowedTypes.isEmpty()) {
                    for (String type : allowedTypes) {
                        if (!metadata.isKnownType(source, type)) {
                            throw new IllegalArgumentException(
                                    String.format(
                                            "Event type '%s' is not registered in Alibaba Cloud rules for source '%s' on bus '%s'. Allowed in Cloud: %s",
                                            type, source, eventBusName, metadata.knownTypesDescription(source)));
                        }
                    }
                }
            }
        }

        if (effectiveTtlMillis > 0) {
            cache.put(eventBusName, new CacheEntry<>(metadata, now + effectiveTtlMillis));
        }
        return metadata;
    }

    BusMetadata validateAndUpdateCache(
            String eventBusName, AllowedEventBus allowedBus,
            boolean validateSource, boolean validateType, EventBridgeClient client) {
        return validateAndUpdateCache(eventBusName, allowedBus, validateSource, validateType, this.ttlMillis, client);
    }

    /**
     * Checks if the event bus is known to exist.
     */
    boolean isKnownEventBus(String eventBusName, long effectiveTtlMillis, EventBridgeClient client) {
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

    boolean isKnownEventBus(String eventBusName, EventBridgeClient client) {
        return isKnownEventBus(eventBusName, this.ttlMillis, client);
    }

    /**
     * Checks if the event source is registered for the given bus.
     */
    boolean isKnownEventSource(String eventBusName, String eventSource, long effectiveTtlMillis, EventBridgeClient client) {
        if (client == null || ObjectHelper.isEmpty(eventBusName) || ObjectHelper.isEmpty(eventSource)) {
            return true;
        }
        BusMetadata metadata = validateAndUpdateCache(eventBusName, null, false, false, effectiveTtlMillis, client);
        return metadata.isKnownSource(eventSource);
    }

    boolean isKnownEventSource(String eventBusName, String eventSource, EventBridgeClient client) {
        return isKnownEventSource(eventBusName, eventSource, this.ttlMillis, client);
    }

    /**
     * Checks if the event type is valid for the given event source on the bus.
     */
    boolean isKnownEventType(
            String eventBusName, String eventSource, String eventType, long effectiveTtlMillis, EventBridgeClient client) {
        if (client == null || ObjectHelper.isEmpty(eventBusName) || ObjectHelper.isEmpty(eventType)) {
            return true;
        }
        BusMetadata metadata = validateAndUpdateCache(eventBusName, null, false, false, effectiveTtlMillis, client);
        return metadata.isKnownType(eventSource, eventType);
    }

    boolean isKnownEventType(String eventBusName, String eventSource, String eventType, EventBridgeClient client) {
        return isKnownEventType(eventBusName, eventSource, eventType, this.ttlMillis, client);
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
                List<EventBusEntry> buses = response != null ? response.getEventBuses() : null;
                if (buses != null) {
                    for (EventBusEntry bus : buses) {
                        if (eventBusName.equals(bus.getEventBusName())) {
                            return true;
                        }
                    }
                }
                nextToken = response != null ? response.getNextToken() : null;
            } while (nextToken != null && !nextToken.isEmpty());

        } catch (Exception e) {
            LOG.error("Failed to verify event bus '{}' existence via Alibaba Cloud EventBridge API: {}",
                    eventBusName, e.getMessage(), e);
            throw new IllegalArgumentException(
                    String.format("Failed to verify event bus '%s' existence via Alibaba Cloud EventBridge API: %s",
                            eventBusName, e.getMessage()),
                    e);
        }
        return false;
    }

    /**
     * Fetches all rules on the bus and extracts source -> event types mappings from rule filter patterns.
     */
    BusMetadata fetchCloudRules(String eventBusName, EventBridgeClient client) {
        Set<String> allExactSources = new HashSet<>();
        Set<String> allPrefixSources = new HashSet<>();
        Map<String, SourceFilterBuilder> sourceToTypes = new HashMap<>();
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
                        parseFilterPattern(rule.getFilterPattern(), allExactSources, allPrefixSources, sourceToTypes);
                    }
                }
                nextToken = response != null ? response.getNextToken() : null;
            } while (nextToken != null && !nextToken.isEmpty());

        } catch (Exception e) {
            LOG.error("Failed to query rules for event bus '{}' via Alibaba Cloud EventBridge API: {}",
                    eventBusName, e.getMessage(), e);
            throw new IllegalArgumentException(
                    String.format("Failed to query rules for event bus '%s' via Alibaba Cloud EventBridge API: %s",
                            eventBusName, e.getMessage()),
                    e);
        }

        Map<String, SourceFilter> built = new HashMap<>();
        for (Map.Entry<String, SourceFilterBuilder> entry : sourceToTypes.entrySet()) {
            built.put(entry.getKey(), entry.getValue().build());
        }
        return new BusMetadata(true, allExactSources, allPrefixSources, built);
    }

    /**
     * Parses an Alibaba Cloud EventBridge rule filter pattern JSON string to extract source-to-types mappings.
     */
    static void parseFilterPattern(
            String filterPatternJson,
            Set<String> allExactSources,
            Set<String> allPrefixSources,
            Map<String, SourceFilterBuilder> sourceToTypes) {
        if (ObjectHelper.isEmpty(filterPatternJson)) {
            return;
        }
        try {
            JsonElement root = GSON.fromJson(filterPatternJson, JsonElement.class);
            if (!root.isJsonObject()) {
                return;
            }
            JsonObject obj = root.getAsJsonObject();

            FilterValues sources = new FilterValues();
            sources.add(obj.get("source"));

            FilterValues types = new FilterValues();
            types.add(obj.get("type"));

            allExactSources.addAll(sources.exactValues);
            allPrefixSources.addAll(sources.prefixValues);

            if (sources.isEmpty()) {
                allExactSources.add("*");
                SourceFilterBuilder filterBuilder = sourceToTypes.computeIfAbsent("*", s -> new SourceFilterBuilder());
                filterBuilder.exactTypes.addAll(types.exactValues);
                filterBuilder.prefixTypes.addAll(types.prefixValues);
            } else {
                for (String src : sources.exactValues) {
                    SourceFilterBuilder filterBuilder = sourceToTypes.computeIfAbsent(src, s -> new SourceFilterBuilder());
                    filterBuilder.exactTypes.addAll(types.exactValues);
                    filterBuilder.prefixTypes.addAll(types.prefixValues);
                }
                for (String prefixSrc : sources.prefixValues) {
                    SourceFilterBuilder filterBuilder
                            = sourceToTypes.computeIfAbsent(prefixSrc, s -> new SourceFilterBuilder());
                    filterBuilder.exactTypes.addAll(types.exactValues);
                    filterBuilder.prefixTypes.addAll(types.prefixValues);
                }
            }
        } catch (Exception e) {
            LOG.debug("Could not parse rule filterPattern JSON '{}': {}", filterPatternJson, e.getMessage());
        }
    }

    static class FilterValues {
        final Set<String> exactValues = new HashSet<>();
        final Set<String> prefixValues = new HashSet<>();

        void add(JsonElement element) {
            if (element == null || element.isJsonNull()) {
                return;
            }
            if (element.isJsonPrimitive()) {
                exactValues.add(element.getAsString());
            } else if (element.isJsonArray()) {
                for (JsonElement item : element.getAsJsonArray()) {
                    if (item.isJsonPrimitive()) {
                        exactValues.add(item.getAsString());
                    } else if (item.isJsonObject()) {
                        JsonObject obj = item.getAsJsonObject();
                        if (obj.has("prefix") && obj.get("prefix").isJsonPrimitive()) {
                            prefixValues.add(obj.get("prefix").getAsString());
                        }
                    }
                }
            } else if (element.isJsonObject()) {
                JsonObject obj = element.getAsJsonObject();
                if (obj.has("prefix") && obj.get("prefix").isJsonPrimitive()) {
                    prefixValues.add(obj.get("prefix").getAsString());
                }
            }
        }

        boolean isEmpty() {
            return exactValues.isEmpty() && prefixValues.isEmpty();
        }
    }

    static class SourceFilterBuilder {
        final Set<String> exactTypes = new HashSet<>();
        final Set<String> prefixTypes = new HashSet<>();

        SourceFilter build() {
            return new SourceFilter(exactTypes, prefixTypes);
        }
    }

    Set<String> cachedBusNames() {
        return Collections.unmodifiableSet(cache.keySet());
    }

    BusMetadata getCachedMetadata(String eventBusName) {
        CacheEntry<BusMetadata> entry = cache.get(eventBusName);
        return entry != null ? entry.value() : null;
    }

    CacheEntry<BusMetadata> getCachedEntry(String eventBusName) {
        return cache.get(eventBusName);
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
