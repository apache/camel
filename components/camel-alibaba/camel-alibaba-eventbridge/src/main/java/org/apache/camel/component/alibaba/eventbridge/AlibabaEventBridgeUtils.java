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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.aliyun.eventbridge.EventBridgeClient;
import com.aliyun.eventbridge.models.CloudEvent;
import com.aliyun.eventbridge.models.Config;
import com.aliyun.eventbridge.models.PutEventsResponse;
import com.aliyun.eventbridge.models.PutEventsResponseEntry;
import com.aliyun.eventbridge.util.EventBuilder;
import com.google.gson.Gson;
import org.apache.camel.Exchange;
import org.apache.camel.component.alibaba.common.OpenApiClientSupport;
import org.apache.camel.component.alibaba.eventbridge.constants.AlibabaEventBridgeConstants;
import org.apache.camel.component.alibaba.eventbridge.constants.AlibabaEventBridgeProperties;
import org.apache.camel.component.alibaba.eventbridge.models.ClientConfigurations;
import org.apache.camel.util.ObjectHelper;

public final class AlibabaEventBridgeUtils {

    private static final Gson GSON = new Gson();

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
        ClientConfigurations configuration = new ClientConfigurations();
        configuration.setOperation(
                OpenApiClientSupport.resolveString(exchange, AlibabaEventBridgeProperties.OPERATION, endpoint.getOperation()));
        configuration.setEventBusName(
                OpenApiClientSupport.resolveString(exchange, AlibabaEventBridgeProperties.EVENT_BUS_NAME,
                        endpoint.getEventBusName()));
        configuration.setEventSource(
                OpenApiClientSupport.resolveString(exchange, AlibabaEventBridgeProperties.EVENT_SOURCE,
                        endpoint.getEventSource()));
        configuration.setEventType(
                OpenApiClientSupport.resolveString(exchange, AlibabaEventBridgeProperties.EVENT_TYPE, endpoint.getEventType()));
        configuration.setEventSubject(
                OpenApiClientSupport.resolveString(exchange, AlibabaEventBridgeProperties.EVENT_SUBJECT,
                        endpoint.getEventSubject()));
        return configuration;
    }

    public static List<CloudEvent> resolveCloudEvents(Exchange exchange, ClientConfigurations configuration) {
        Object body = exchange.getMessage().getBody();
        List<CloudEvent> events = new ArrayList<>();

        if (body instanceof List<?> listBody) {
            for (Object item : listBody) {
                events.add(toCloudEvent(item, configuration));
            }
            return events;
        }

        events.add(toCloudEvent(body, configuration));
        return events;
    }

    private static CloudEvent toCloudEvent(Object body, ClientConfigurations configuration) {
        if (body instanceof CloudEvent cloudEvent) {
            return cloudEvent;
        }

        if (body instanceof Map<?, ?> mapBody) {
            String eventBusName
                    = stringValue(mapBody.get(AlibabaEventBridgeConstants.EVENT_BUS_NAME), configuration.getEventBusName());
            String source = stringValue(mapBody.get(AlibabaEventBridgeConstants.EVENT_SOURCE), configuration.getEventSource());
            String type = stringValue(mapBody.get(AlibabaEventBridgeConstants.EVENT_TYPE), configuration.getEventType());
            String subject
                    = stringValue(mapBody.get(AlibabaEventBridgeConstants.EVENT_SUBJECT), configuration.getEventSubject());
            String data = jsonDataValue(mapBody.get(AlibabaEventBridgeConstants.EVENT_DATA));

            if (ObjectHelper.isEmpty(source) || ObjectHelper.isEmpty(type) || ObjectHelper.isEmpty(eventBusName)) {
                throw new IllegalArgumentException("Event source, type and event bus name are required");
            }

            EventBuilder builder = EventBuilder.builder()
                    .withSource(URI.create(source))
                    .withType(type)
                    .withAliyunEventBus(eventBusName);

            if (ObjectHelper.isNotEmpty(subject)) {
                builder.withSubject(subject);
            }
            if (data != null) {
                builder.withJsonStringData(data);
            }
            return builder.build();
        }

        if (body instanceof String stringBody) {
            if (ObjectHelper.isEmpty(configuration.getEventSource())
                    || ObjectHelper.isEmpty(configuration.getEventType())
                    || ObjectHelper.isEmpty(configuration.getEventBusName())) {
                throw new IllegalArgumentException("Event source, type and event bus name are required when body is a string");
            }

            EventBuilder builder = EventBuilder.builder()
                    .withSource(URI.create(configuration.getEventSource()))
                    .withType(configuration.getEventType())
                    .withAliyunEventBus(configuration.getEventBusName())
                    .withJsonStringData(stringBody);

            if (ObjectHelper.isNotEmpty(configuration.getEventSubject())) {
                builder.withSubject(configuration.getEventSubject());
            }
            return builder.build();
        }

        throw new IllegalArgumentException("Exchange body must be a CloudEvent, Map or JSON string");
    }

    private static String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof String stringValue) {
            return stringValue;
        }
        return value.toString();
    }

    private static String jsonDataValue(Object value) {
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
