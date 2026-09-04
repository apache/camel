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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.aliyun.eventbridge.EventBridgeClient;
import com.aliyun.eventbridge.models.CloudEvent;
import com.aliyun.eventbridge.models.Config;
import com.aliyun.eventbridge.models.PutEventsResponse;
import com.aliyun.eventbridge.models.PutEventsResponseEntry;
import com.aliyun.eventbridge.util.EventBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.camel.Exchange;
import org.apache.camel.component.alibaba.common.OpenApiClientSupport;
import org.apache.camel.component.alibaba.eventbridge.constants.AlibabaEventBridgeConstants;
import org.apache.camel.component.alibaba.eventbridge.constants.AlibabaEventBridgeProperties;
import org.apache.camel.component.alibaba.eventbridge.models.AllowedEventBus;
import org.apache.camel.component.alibaba.eventbridge.models.AllowedEventSource;
import org.apache.camel.component.alibaba.eventbridge.models.ClientConfigurations;
import org.apache.camel.util.ObjectHelper;

public final class AlibabaEventBridgeUtils {

    private static final Gson GSON = new Gson();
    private static final Pattern BUS_BLOCK_PATTERN = Pattern.compile("([^|\\[\\]]+)\\[([^\\]]*)\\]");

    private AlibabaEventBridgeUtils() {
    }

    public static EventBridgeClient createClient(AlibabaEventBridgeEndpoint endpoint) {
        if (ObjectHelper.isEmpty(endpoint.getRegion()) && ObjectHelper.isEmpty(endpoint.getEndpoint())) {
            throw new IllegalArgumentException("Region or endpoint is required");
        }

        Config config = new Config();
        config.setAccessKeyId(OpenApiClientSupport.resolveAccessKey(endpoint.getAccessKey(), endpoint.getServiceKeys()));
        config.setAccessKeySecret(OpenApiClientSupport.resolveSecretKey(endpoint.getSecretKey(), endpoint.getServiceKeys()));
        if (ObjectHelper.isNotEmpty(endpoint.getRegion())) {
            config.setRegionId(endpoint.getRegion());
        }
        if (ObjectHelper.isNotEmpty(endpoint.getEndpoint())) {
            config.setEndpoint(endpoint.getEndpoint());
        }
        return new EventBridgeClient(config);
    }

    public static ClientConfigurations createClientConfigurations(AlibabaEventBridgeEndpoint endpoint, Exchange exchange) {
        String defaultBusName = OpenApiClientSupport.resolveString(
                exchange, AlibabaEventBridgeProperties.EVENT_BUS_NAME, endpoint.getEventBusName());

        return new ClientConfigurations(
                OpenApiClientSupport.resolveString(exchange, AlibabaEventBridgeProperties.OPERATION, endpoint.getOperation()),
                defaultBusName,
                OpenApiClientSupport.resolveString(exchange, AlibabaEventBridgeProperties.EVENT_SOURCE,
                        endpoint.getEventSource()),
                OpenApiClientSupport.resolveString(exchange, AlibabaEventBridgeProperties.EVENT_TYPE, endpoint.getEventType()),
                OpenApiClientSupport.resolveString(exchange, AlibabaEventBridgeProperties.EVENT_SUBJECT,
                        endpoint.getEventSubject()),
                OpenApiClientSupport.resolveBoolean(exchange, AlibabaEventBridgeProperties.VALIDATE_EVENT_SOURCE,
                        endpoint.isValidateEventSource()),
                OpenApiClientSupport.resolveBoolean(exchange, AlibabaEventBridgeProperties.VALIDATE_EVENT_TYPE,
                        endpoint.isValidateEventType()),
                OpenApiClientSupport.resolveBoolean(exchange, AlibabaEventBridgeProperties.VALIDATE_EVENT_SPEC,
                        endpoint.isValidateEventSpec()),
                resolveAllowedEventBuses(exchange, AlibabaEventBridgeProperties.ALLOWED_EVENT_SOURCES,
                        endpoint.getAllowedEventSources(), defaultBusName),
                OpenApiClientSupport.resolveLong(exchange, AlibabaEventBridgeProperties.EVENT_SOURCE_CACHE_TTL,
                        endpoint.getEventSourceCacheTtl()));
    }

    public static List<CloudEvent> resolveCloudEvents(Exchange exchange, ClientConfigurations configuration) {
        return resolveCloudEvents(exchange, configuration, null, null);
    }

    public static List<CloudEvent> resolveCloudEvents(
            Exchange exchange, ClientConfigurations configuration,
            EventSourceCache eventSourceCache, EventBridgeClient client) {
        Object body = exchange.getMessage().getBody();
        List<CloudEvent> events = new ArrayList<>();
        MapCloudEventValidator mapValidator = new MapCloudEventValidator(eventSourceCache);

        if (body instanceof List<?> listBody) {
            for (Object item : listBody) {
                events.add(toCloudEvent(item, configuration, mapValidator, client));
            }
            return events;
        }

        events.add(toCloudEvent(body, configuration, mapValidator, client));
        return events;
    }

    private static CloudEvent toCloudEvent(
            Object body, ClientConfigurations configuration,
            MapCloudEventValidator mapValidator, EventBridgeClient client) {
        if (body instanceof CloudEvent cloudEvent) {
            mapValidator.validateCloudEvent(cloudEvent, configuration, client);
            return cloudEvent;
        }

        if (body instanceof Map<?, ?> mapBody) {
            return mapValidator.validateAndBuild(mapBody, configuration, client);
        }

        if (body instanceof String stringBody) {
            if (ObjectHelper.isEmpty(configuration.eventSource())
                    || ObjectHelper.isEmpty(configuration.eventType())
                    || ObjectHelper.isEmpty(configuration.eventBusName())) {
                throw new IllegalArgumentException("Event source, type and event bus name are required when body is a string");
            }

            mapValidator.validateBusSourceAndType(
                    configuration.eventBusName(), configuration.eventSource(), configuration.eventType(),
                    configuration, client);

            EventBuilder builder = EventBuilder.builder()
                    .withSource(URI.create(configuration.eventSource()))
                    .withType(configuration.eventType())
                    .withAliyunEventBus(configuration.eventBusName())
                    .withJsonStringData(stringBody);

            if (ObjectHelper.isNotEmpty(configuration.eventSubject())) {
                builder.withSubject(configuration.eventSubject());
            }
            return builder.build();
        }

        throw new IllegalArgumentException("Exchange body must be a CloudEvent, Map or JSON string");
    }

    /**
     * Resolves and parses allowed event buses, sources, and source-scoped event types from Header, Property, or
     * Endpoint option.
     */
    public static Map<String, AllowedEventBus> resolveAllowedEventBuses(
            Exchange exchange, String name, String endpointValue, String defaultBusName) {
        Object raw = exchange.getIn().getHeader(name);
        if (raw == null) {
            raw = exchange.getProperty(name);
        }
        if (raw == null) {
            raw = endpointValue;
        }

        if (raw == null) {
            return Collections.emptyMap();
        }

        if (raw instanceof Map<?, ?> map) {
            return parseAllowedBusesFromMap(map, defaultBusName);
        }

        if (raw instanceof Collection<?> coll) {
            return parseAllowedBusesFromCollection(coll, defaultBusName);
        }

        if (raw instanceof String str) {
            return parseAllowedBusesFromString(str, defaultBusName);
        }

        return Collections.emptyMap();
    }

    private static Map<String, AllowedEventBus> parseAllowedBusesFromMap(Map<?, ?> map, String defaultBusName) {
        Map<String, AllowedEventBus> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String busOrSource = String.valueOf(entry.getKey()).trim();
            Object value = entry.getValue();

            if (value instanceof AllowedEventBus allowedBus) {
                result.put(allowedBus.eventBusName(), allowedBus);
            } else if (value instanceof Map<?, ?> subMap) {
                Map<String, AllowedEventSource> sources = new HashMap<>();
                for (Map.Entry<?, ?> subEntry : subMap.entrySet()) {
                    String src = String.valueOf(subEntry.getKey()).trim();
                    Set<String> types = toStringSet(subEntry.getValue());
                    sources.put(src, new AllowedEventSource(src, types));
                }
                result.put(busOrSource, new AllowedEventBus(busOrSource, sources));
            } else if (value instanceof Collection<?> || value instanceof String) {
                String busName = ObjectHelper.isNotEmpty(defaultBusName) ? defaultBusName : "*";
                AllowedEventBus bus = result.computeIfAbsent(busName, k -> new AllowedEventBus(busName, new HashMap<>()));
                Map<String, AllowedEventSource> modifiable = new HashMap<>(bus.allowedSources());
                modifiable.put(busOrSource, new AllowedEventSource(busOrSource, toStringSet(value)));
                result.put(busName, new AllowedEventBus(busName, modifiable));
            }
        }
        return result;
    }

    private static Map<String, AllowedEventBus> parseAllowedBusesFromCollection(Collection<?> coll, String defaultBusName) {
        Map<String, AllowedEventBus> result = new HashMap<>();
        String busName = ObjectHelper.isNotEmpty(defaultBusName) ? defaultBusName : "*";
        Map<String, AllowedEventSource> sources = new HashMap<>();

        for (Object item : coll) {
            if (item instanceof AllowedEventBus bus) {
                result.put(bus.eventBusName(), bus);
            } else if (item instanceof AllowedEventSource src) {
                sources.put(src.source(), src);
            } else if (item instanceof String str) {
                Map<String, AllowedEventSource> parsed = parseSourcesBlock(str);
                sources.putAll(parsed);
            }
        }

        if (!sources.isEmpty() && !result.containsKey(busName)) {
            result.put(busName, new AllowedEventBus(busName, sources));
        }
        return result;
    }

    /**
     * Parses allowed event buses from String input supporting:
     * <ul>
     * <li>JSON format: {@code {"bus1":{"src1":["type1","type2"]}}}</li>
     * <li>Multi-bus DSL: {@code bus1[src1 -> t1,t2; src2 -> t3] | bus2[src3 -> t4]}</li>
     * <li>Single-bus DSL shorthand: {@code src1 -> t1,t2; src2 -> t3}</li>
     * </ul>
     */
    public static Map<String, AllowedEventBus> parseAllowedBusesFromString(String input, String defaultBusName) {
        if (ObjectHelper.isEmpty(input)) {
            return Collections.emptyMap();
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return Collections.emptyMap();
        }

        if (trimmed.startsWith("{")) {
            return parseAllowedBusesFromJson(trimmed, defaultBusName);
        }

        Matcher matcher = BUS_BLOCK_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            Map<String, AllowedEventBus> result = new HashMap<>();
            matcher.reset();
            while (matcher.find()) {
                String busName = matcher.group(1).trim();
                String sourcesBlock = matcher.group(2).trim();
                Map<String, AllowedEventSource> sources = parseSourcesBlock(sourcesBlock);
                result.put(busName, new AllowedEventBus(busName, sources));
            }
            return result;
        }

        String busName = ObjectHelper.isNotEmpty(defaultBusName) ? defaultBusName.trim() : "*";
        Map<String, AllowedEventSource> sources = parseSourcesBlock(trimmed);
        Map<String, AllowedEventBus> result = new HashMap<>();
        result.put(busName, new AllowedEventBus(busName, sources));
        return result;
    }

    private static Map<String, AllowedEventBus> parseAllowedBusesFromJson(String json, String defaultBusName) {
        Map<String, AllowedEventBus> result = new HashMap<>();
        try {
            JsonElement root = GSON.fromJson(json, JsonElement.class);
            if (!root.isJsonObject()) {
                return result;
            }
            JsonObject rootObj = root.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : rootObj.entrySet()) {
                String key = entry.getKey().trim();
                JsonElement val = entry.getValue();

                if (val.isJsonObject()) {
                    Map<String, AllowedEventSource> sources = new HashMap<>();
                    JsonObject subObj = val.getAsJsonObject();
                    for (Map.Entry<String, JsonElement> subEntry : subObj.entrySet()) {
                        String src = subEntry.getKey().trim();
                        Set<String> types = extractTypesFromJsonElement(subEntry.getValue());
                        sources.put(src, new AllowedEventSource(src, types));
                    }
                    result.put(key, new AllowedEventBus(key, sources));
                } else {
                    String busName = ObjectHelper.isNotEmpty(defaultBusName) ? defaultBusName : "*";
                    AllowedEventBus bus = result.computeIfAbsent(busName, k -> new AllowedEventBus(busName, new HashMap<>()));
                    Map<String, AllowedEventSource> modifiable = new HashMap<>(bus.allowedSources());
                    Set<String> types = extractTypesFromJsonElement(val);
                    modifiable.put(key, new AllowedEventSource(key, types));
                    result.put(busName, new AllowedEventBus(busName, modifiable));
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON format for allowedEventSources: " + json, e);
        }
        return result;
    }

    private static Set<String> extractTypesFromJsonElement(JsonElement element) {
        Set<String> types = new HashSet<>();
        if (element == null || element.isJsonNull()) {
            return types;
        }
        if (element.isJsonPrimitive()) {
            for (String t : element.getAsString().split(",")) {
                String trimmed = t.trim();
                if (!trimmed.isEmpty()) {
                    types.add(trimmed);
                }
            }
        } else if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            for (JsonElement item : arr) {
                if (item.isJsonPrimitive()) {
                    String trimmed = item.getAsString().trim();
                    if (!trimmed.isEmpty()) {
                        types.add(trimmed);
                    }
                }
            }
        }
        return types;
    }

    /**
     * Parses a block of event source definitions delimited by {@code ;}. Supports mapping operators {@code ->} and
     * {@code =}.
     */
    static Map<String, AllowedEventSource> parseSourcesBlock(String sourcesBlock) {
        Map<String, AllowedEventSource> sources = new HashMap<>();
        if (ObjectHelper.isEmpty(sourcesBlock)) {
            return sources;
        }

        String[] sourceEntries = sourcesBlock.split(";");
        for (String entry : sourceEntries) {
            String trimmedEntry = entry.trim();
            if (trimmedEntry.isEmpty()) {
                continue;
            }

            String source;
            Set<String> types = new HashSet<>();

            if (trimmedEntry.contains("->")) {
                String[] parts = trimmedEntry.split("->", 2);
                source = parts[0].trim();
                parseCommaSeparatedTypes(parts[1], types);
            } else if (trimmedEntry.contains("=")) {
                String[] parts = trimmedEntry.split("=", 2);
                source = parts[0].trim();
                parseCommaSeparatedTypes(parts[1], types);
            } else {
                source = trimmedEntry;
            }

            if (!source.isEmpty()) {
                sources.put(source, new AllowedEventSource(source, types));
            }
        }
        return sources;
    }

    private static void parseCommaSeparatedTypes(String typeStr, Set<String> types) {
        if (typeStr == null) {
            return;
        }
        for (String t : typeStr.split(",")) {
            String trimmedType = t.trim();
            if (!trimmedType.isEmpty()) {
                types.add(trimmedType);
            }
        }
    }

    private static Set<String> toStringSet(Object value) {
        if (value == null) {
            return Collections.emptySet();
        }
        Set<String> set = new HashSet<>();
        if (value instanceof Collection<?> coll) {
            for (Object item : coll) {
                if (item != null) {
                    set.add(String.valueOf(item).trim());
                }
            }
        } else if (value instanceof String str) {
            parseCommaSeparatedTypes(str, set);
        }
        return set;
    }

    public static Map<String, Object> toPutEventsMap(PutEventsResponse response) {
        Map<String, Object> map = new HashMap<>();
        map.put(AlibabaEventBridgeConstants.EVENT_RESPONSE_REQUEST_IDENTIFIER, response.getRequestId());
        map.put(AlibabaEventBridgeConstants.EVENT_RESPONSE_RESOURCE_OWNER_ACCOUNT_IDENTIFIER,
                response.getResourceOwnerAccountId());
        map.put(AlibabaEventBridgeConstants.EVENT_RESPONSE_FAILED_ENTRY_COUNT, response.getFailedEntryCount());

        if (response.getEntryList() != null) {
            List<Map<String, Object>> entries = new ArrayList<>();
            for (PutEventsResponseEntry entry : response.getEntryList()) {
                Map<String, Object> entryMap = new HashMap<>();
                entryMap.put(AlibabaEventBridgeConstants.EVENT_RESPONSE_ID, entry.getEventId());
                entryMap.put(AlibabaEventBridgeConstants.EVENT_RESPONSE_ERROR_CODE, entry.getErrorCode());
                entryMap.put(AlibabaEventBridgeConstants.EVENT_RESPONSE_ERROR_MESSAGE, entry.getErrorMessage());
                entries.add(entryMap);
            }
            map.put(AlibabaEventBridgeConstants.EVENT_RESPONSE_ENTRY_LIST, entries);
        }
        return map;
    }

    public static byte[] toBytes(String value) {
        return value != null ? value.getBytes(StandardCharsets.UTF_8) : new byte[0];
    }
}
