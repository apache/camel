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

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aliyun.eventbridge.EventBridgeClient;
import com.aliyun.eventbridge.models.CloudEvent;
import com.aliyun.eventbridge.models.EventBusEntry;
import com.aliyun.eventbridge.models.EventRuleDTO;
import com.aliyun.eventbridge.models.ListEventBusesRequest;
import com.aliyun.eventbridge.models.ListEventBusesResponse;
import com.aliyun.eventbridge.models.ListRulesRequest;
import com.aliyun.eventbridge.models.ListRulesResponse;
import org.apache.camel.Exchange;
import org.apache.camel.component.alibaba.eventbridge.models.AllowedEventBus;
import org.apache.camel.component.alibaba.eventbridge.models.AllowedEventSource;
import org.apache.camel.component.alibaba.eventbridge.models.ClientConfigurations;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MapCloudEventValidationTest extends CamelTestSupport {

    private EventBridgeClient eventBridgeClient;
    private EventSourceCache eventSourceCache;
    private MapCloudEventValidator validator;

    @BeforeEach
    void initTest() {
        eventBridgeClient = mock(EventBridgeClient.class);
        eventSourceCache = new EventSourceCache(300000L);
        validator = new MapCloudEventValidator(eventSourceCache);
    }

    @Test
    void testValidateAndBuildSuccessWithFullMap() {
        ClientConfigurations config
                = new ClientConfigurations(null, "default-bus", null, null, null, false, false, true, Map.of(), 300000L);

        Map<String, Object> map = new HashMap<>();
        map.put("eventBusName", "my-bus");
        map.put("source", "acs:oss:cn-hangzhou:12345:my-bucket");
        map.put("type", "oss:ObjectCreated:PutObject");
        map.put("id", "event-id-123");
        map.put("specversion", "1.0");
        map.put("subject", "my-object.jpg");
        map.put("time", "2026-08-23T10:15:30Z");
        map.put("datacontenttype", "application/json");
        map.put("dataschema", "http://example.com/schema.json");
        map.put("data", Map.of("fileSize", 1024, "bucket", "my-bucket"));

        CloudEvent event = validator.validateAndBuild(map, config, eventBridgeClient);

        assertThat(event).isNotNull();
        assertThat(event.getSource().toString()).isEqualTo("acs:oss:cn-hangzhou:12345:my-bucket");
        assertThat(event.getType()).isEqualTo("oss:ObjectCreated:PutObject");
        assertThat(event.getId()).isEqualTo("event-id-123");
        assertThat(event.getSubject()).isEqualTo("my-object.jpg");
        assertThat(event.getSpecversion()).isEqualTo("1.0");
        assertThat(event.getDatacontenttype()).isEqualTo("application/json");
        assertThat(event.getDataschema().toString()).isEqualTo("http://example.com/schema.json");
        assertThat(new String(event.getData(), StandardCharsets.UTF_8)).contains("\"fileSize\":1024");
    }

    @Test
    void testValidateAndBuildWithFallbackConfig() {
        ClientConfigurations config
                = new ClientConfigurations(null, "default-bus", "my.custom.app", "order.created", "order-999");

        Map<String, Object> map = new HashMap<>();
        map.put("data", "{\"orderId\":\"999\"}");

        CloudEvent event = validator.validateAndBuild(map, config, eventBridgeClient);

        assertThat(event).isNotNull();
        assertThat(event.getSource().toString()).isEqualTo("my.custom.app");
        assertThat(event.getType()).isEqualTo("order.created");
        assertThat(event.getSubject()).isEqualTo("order-999");
        assertThat(new String(event.getData(), StandardCharsets.UTF_8)).isEqualTo("{\"orderId\":\"999\"}");
    }

    @Test
    void testValidateFailsWhenBusNameMissing() {
        ClientConfigurations config = new ClientConfigurations();
        Map<String, Object> map = Map.of("source", "my.source", "type", "my.type");

        assertThatThrownBy(() -> validator.validateAndBuild(map, config, eventBridgeClient))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Event bus name is required");
    }

    @Test
    void testValidateFailsWhenSourceMissing() {
        ClientConfigurations config = new ClientConfigurations(null, "test-bus", null, null, null);
        Map<String, Object> map = Map.of("type", "my.type");

        assertThatThrownBy(() -> validator.validateAndBuild(map, config, eventBridgeClient))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Event 'source' cannot be empty");
    }

    @Test
    void testValidateFailsWhenTypeMissing() {
        ClientConfigurations config = new ClientConfigurations(null, "test-bus", "my.source", null, null);
        Map<String, Object> map = Map.of("source", "my.source");

        assertThatThrownBy(() -> validator.validateAndBuild(map, config, eventBridgeClient))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Event 'type' cannot be empty");
    }

    @Test
    void testValidateFailsWhenInvalidSpecversion() {
        ClientConfigurations config = new ClientConfigurations(null, "test-bus", null, null, null, false, true);

        Map<String, Object> map = Map.of(
                "source", "my.source",
                "type", "my.type",
                "specversion", "0.3");

        assertThatThrownBy(() -> validator.validateAndBuild(map, config, eventBridgeClient))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid CloudEvent specversion: '0.3'");
    }

    @Test
    void testSingleBusDslWithColonsInSourceAndTypes() {
        String dsl
                = "acs:oss:cn-hangzhou:12345:my-bucket -> oss:ObjectCreated:PutObject, oss:ObjectCreated:PostObject ; app.orders -> order:created:v1";
        Map<String, AllowedEventBus> buses = AlibabaEventBridgeUtils.parseAllowedBusesFromString(dsl, "order-bus");

        assertThat(buses).containsKey("order-bus");
        AllowedEventBus bus = buses.get("order-bus");
        assertThat(bus.allowedSources()).containsKeys("acs:oss:cn-hangzhou:12345:my-bucket", "app.orders");

        AllowedEventSource ossSource = bus.allowedSources().get("acs:oss:cn-hangzhou:12345:my-bucket");
        assertThat(ossSource.allowedEventTypes()).containsExactlyInAnyOrder(
                "oss:ObjectCreated:PutObject", "oss:ObjectCreated:PostObject");

        ClientConfigurations config = new ClientConfigurations(
                null, "order-bus", null, null, null, false, false, true, buses, 300000L);

        Map<String, Object> validEvent = Map.of(
                "source", "acs:oss:cn-hangzhou:12345:my-bucket",
                "type", "oss:ObjectCreated:PutObject");

        CloudEvent event = validator.validateAndBuild(validEvent, config, eventBridgeClient);
        assertThat(event).isNotNull();

        Map<String, Object> invalidTypeEvent = Map.of(
                "source", "acs:oss:cn-hangzhou:12345:my-bucket",
                "type", "oss:ObjectDeleted:DeleteObject");

        assertThatThrownBy(() -> validator.validateAndBuild(invalidTypeEvent, config, eventBridgeClient))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Event type 'oss:ObjectDeleted:DeleteObject' is not allowed for event source");
    }

    @Test
    void testMultiBusDslWithColonsInSourceAndTypes() {
        String dsl
                = "orders-bus[ acs:oss:cn-hangzhou:12345:orders -> oss:ObjectCreated:PutObject ; app.orders -> order:created:v1 ]"
                  + " | payments-bus[ app.payments -> payment:authorized:v1, payment:captured:v1 ]";

        Map<String, AllowedEventBus> buses = AlibabaEventBridgeUtils.parseAllowedBusesFromString(dsl, null);
        assertThat(buses).containsKeys("orders-bus", "payments-bus");

        ClientConfigurations config = new ClientConfigurations(
                null, "orders-bus", null, null, null, false, false, true, buses, 300000L);

        Map<String, Object> event1 = Map.of(
                "eventBusName", "orders-bus",
                "source", "app.orders",
                "type", "order:created:v1");
        assertThat(validator.validateAndBuild(event1, config, eventBridgeClient)).isNotNull();

        Map<String, Object> event2 = Map.of(
                "eventBusName", "payments-bus",
                "source", "app.payments",
                "type", "payment:captured:v1");
        assertThat(validator.validateAndBuild(event2, config, eventBridgeClient)).isNotNull();

        Map<String, Object> invalidEvent1 = Map.of(
                "eventBusName", "orders-bus",
                "source", "app.payments",
                "type", "payment:captured:v1");
        assertThatThrownBy(() -> validator.validateAndBuild(invalidEvent1, config, eventBridgeClient))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Event source 'app.payments' is not in the allowed sources list for bus 'orders-bus'");

        Map<String, Object> invalidEvent2 = Map.of(
                "eventBusName", "unknown-bus",
                "source", "app.orders",
                "type", "order:created:v1");
        assertThatThrownBy(() -> validator.validateAndBuild(invalidEvent2, config, eventBridgeClient))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Event bus 'unknown-bus' is not in the allowed event buses list");
    }

    @Test
    void testJsonConfigurationParsing() {
        String json = """
                {
                  "orders-bus": {
                    "acs:oss:cn-hangzhou:12345:orders": ["oss:ObjectCreated:PutObject", "oss:ObjectCreated:PostObject"],
                    "app.orders": ["order:created:v1"]
                  },
                  "payments-bus": {
                    "app.payments": ["payment:authorized:v1"]
                  }
                }
                """;

        Map<String, AllowedEventBus> buses = AlibabaEventBridgeUtils.parseAllowedBusesFromString(json, null);
        assertThat(buses).containsKeys("orders-bus", "payments-bus");

        AllowedEventBus ordersBus = buses.get("orders-bus");
        assertThat(ordersBus.allowedSources().get("acs:oss:cn-hangzhou:12345:orders").allowedEventTypes())
                .contains("oss:ObjectCreated:PutObject", "oss:ObjectCreated:PostObject");
    }

    @Test
    void testValidatedCacheWorkflowSuccess() {
        EventBusEntry busEntry = new EventBusEntry();
        busEntry.setEventBusName("cloud-orders-bus");
        ListEventBusesResponse busResponse = new ListEventBusesResponse();
        busResponse.setEventBuses(List.of(busEntry));
        when(eventBridgeClient.listEventBuses(any(ListEventBusesRequest.class))).thenReturn(busResponse);

        EventRuleDTO ruleDTO = new EventRuleDTO();
        ruleDTO.setFilterPattern(
                "{\"source\":[\"acs:oss:cn-hangzhou:12345:orders\"],\"type\":[\"oss:ObjectCreated:PutObject\"]}");
        ListRulesResponse rulesResponse = new ListRulesResponse();
        rulesResponse.setRules(List.of(ruleDTO));
        when(eventBridgeClient.listRules(any(ListRulesRequest.class))).thenReturn(rulesResponse);

        AllowedEventSource allowedSource = new AllowedEventSource(
                "acs:oss:cn-hangzhou:12345:orders", Set.of("oss:ObjectCreated:PutObject"));
        AllowedEventBus allowedBus = new AllowedEventBus("cloud-orders-bus", Map.of(allowedSource.source(), allowedSource));

        ClientConfigurations config = new ClientConfigurations(
                null, "cloud-orders-bus", null, null, null, true, true, true,
                Map.of("cloud-orders-bus", allowedBus), 300000L);

        Map<String, Object> validEvent = Map.of(
                "eventBusName", "cloud-orders-bus",
                "source", "acs:oss:cn-hangzhou:12345:orders",
                "type", "oss:ObjectCreated:PutObject");

        CloudEvent event = validator.validateAndBuild(validEvent, config, eventBridgeClient);
        assertThat(event).isNotNull();

        assertThat(eventSourceCache.cachedBusNames()).contains("cloud-orders-bus");
        EventSourceCache.BusMetadata metadata = eventSourceCache.getCachedMetadata("cloud-orders-bus");
        assertThat(metadata).isNotNull();
        assertThat(metadata.isKnownSource("acs:oss:cn-hangzhou:12345:orders")).isTrue();
        assertThat(metadata.isKnownType("acs:oss:cn-hangzhou:12345:orders", "oss:ObjectCreated:PutObject")).isTrue();
    }

    @Test
    void testValidatedCacheWorkflowFailsWhenTypeNotOnCloud() {
        EventBusEntry busEntry = new EventBusEntry();
        busEntry.setEventBusName("cloud-orders-bus");
        ListEventBusesResponse busResponse = new ListEventBusesResponse();
        busResponse.setEventBuses(List.of(busEntry));
        when(eventBridgeClient.listEventBuses(any(ListEventBusesRequest.class))).thenReturn(busResponse);

        EventRuleDTO ruleDTO = new EventRuleDTO();
        ruleDTO.setFilterPattern("{\"source\":[\"app.orders\"],\"type\":[\"OrderCreated\"]}");
        ListRulesResponse rulesResponse = new ListRulesResponse();
        rulesResponse.setRules(List.of(ruleDTO));
        when(eventBridgeClient.listRules(any(ListRulesRequest.class))).thenReturn(rulesResponse);

        AllowedEventSource allowedSource = new AllowedEventSource("app.orders", Set.of("OrderCreated", "UnregisteredType"));
        AllowedEventBus allowedBus = new AllowedEventBus("cloud-orders-bus", Map.of(allowedSource.source(), allowedSource));

        ClientConfigurations config = new ClientConfigurations(
                null, "cloud-orders-bus", null, null, null, true, true, true,
                Map.of("cloud-orders-bus", allowedBus), 300000L);

        Map<String, Object> event = Map.of(
                "eventBusName", "cloud-orders-bus",
                "source", "app.orders",
                "type", "OrderCreated");

        assertThatThrownBy(() -> validator.validateAndBuild(event, config, eventBridgeClient))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Event type 'UnregisteredType' is not registered in Alibaba Cloud rules");
    }

    @Test
    void testResolveBatchListOfMaps() {
        ClientConfigurations config = new ClientConfigurations(null, "batch-bus", null, null, null);

        Map<String, Object> event1 = Map.of("source", "app.one", "type", "type.one");
        Map<String, Object> event2 = Map.of("source", "app.two", "type", "type.two");

        Exchange exchange = context.getEndpoint("direct:start").createExchange();
        exchange.getMessage().setBody(List.of(event1, event2));
        List<CloudEvent> events = AlibabaEventBridgeUtils.resolveCloudEvents(
                exchange, config, eventSourceCache, eventBridgeClient);

        assertThat(events).hasSize(2);
        assertThat(events.get(0).getSource().toString()).isEqualTo("app.one");
        assertThat(events.get(1).getSource().toString()).isEqualTo("app.two");
    }

    @Test
    void testValidateCloudEventDirectObjectValidation() {
        AllowedEventSource allowedSource = new AllowedEventSource("app.orders", Set.of("OrderCreated"));
        AllowedEventBus allowedBus = new AllowedEventBus("orders-bus", Map.of("app.orders", allowedSource));
        ClientConfigurations config = new ClientConfigurations(
                null, "orders-bus", null, null, null, false, false, true,
                Map.of("orders-bus", allowedBus), 300000L);

        CloudEvent validEvent = com.aliyun.eventbridge.util.EventBuilder.builder()
                .withAliyunEventBus("orders-bus")
                .withSource(java.net.URI.create("app.orders"))
                .withType("OrderCreated")
                .withId("event-123")
                .build();

        validator.validateCloudEvent(validEvent, config, eventBridgeClient);

        CloudEvent invalidEvent = com.aliyun.eventbridge.util.EventBuilder.builder()
                .withAliyunEventBus("orders-bus")
                .withSource(java.net.URI.create("app.unauthorized"))
                .withType("OrderCreated")
                .withId("event-124")
                .build();

        assertThatThrownBy(() -> validator.validateCloudEvent(invalidEvent, config, eventBridgeClient))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Event source 'app.unauthorized' is not in the allowed sources list");
    }

    @Test
    void testCloudApiFailureFailsClosedOnBusCheck() {
        when(eventBridgeClient.listEventBuses(any(ListEventBusesRequest.class)))
                .thenThrow(new RuntimeException("Cloud API connection timeout"));

        ClientConfigurations config = new ClientConfigurations(
                null, "cloud-orders-bus", null, null, null, true, false, true, Map.of(), 300000L);

        Map<String, Object> event = Map.of(
                "eventBusName", "cloud-orders-bus",
                "source", "app.orders",
                "type", "OrderCreated");

        assertThatThrownBy(() -> validator.validateAndBuild(event, config, eventBridgeClient))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "Failed to verify event bus 'cloud-orders-bus' existence via Alibaba Cloud EventBridge API")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void testCloudApiFailureFailsClosedOnRulesCheck() {
        EventBusEntry busEntry = new EventBusEntry();
        busEntry.setEventBusName("cloud-orders-bus");
        ListEventBusesResponse busResponse = new ListEventBusesResponse();
        busResponse.setEventBuses(List.of(busEntry));
        when(eventBridgeClient.listEventBuses(any(ListEventBusesRequest.class))).thenReturn(busResponse);

        when(eventBridgeClient.listRules(any(ListRulesRequest.class)))
                .thenThrow(new RuntimeException("API rate limit exceeded"));

        AllowedEventSource allowedSource = new AllowedEventSource("app.orders", Set.of("OrderCreated"));
        AllowedEventBus allowedBus = new AllowedEventBus("cloud-orders-bus", Map.of("app.orders", allowedSource));

        ClientConfigurations config = new ClientConfigurations(
                null, "cloud-orders-bus", null, null, null, true, true, true,
                Map.of("cloud-orders-bus", allowedBus), 300000L);

        Map<String, Object> event = Map.of(
                "eventBusName", "cloud-orders-bus",
                "source", "app.orders",
                "type", "OrderCreated");

        assertThatThrownBy(() -> validator.validateAndBuild(event, config, eventBridgeClient))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "Failed to query rules for event bus 'cloud-orders-bus' via Alibaba Cloud EventBridge API")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void testPrefixFilterPatternMatching() {
        EventBusEntry busEntry = new EventBusEntry();
        busEntry.setEventBusName("cloud-orders-bus");
        ListEventBusesResponse busResponse = new ListEventBusesResponse();
        busResponse.setEventBuses(List.of(busEntry));
        when(eventBridgeClient.listEventBuses(any(ListEventBusesRequest.class))).thenReturn(busResponse);

        EventRuleDTO ruleDTO = new EventRuleDTO();
        ruleDTO.setFilterPattern(
                "{\"source\":[{\"prefix\":\"acs:oss:\"}],\"type\":[{\"prefix\":\"oss:ObjectCreated:\"}]}");
        ListRulesResponse rulesResponse = new ListRulesResponse();
        rulesResponse.setRules(List.of(ruleDTO));
        when(eventBridgeClient.listRules(any(ListRulesRequest.class))).thenReturn(rulesResponse);

        ClientConfigurations config = new ClientConfigurations(
                null, "cloud-orders-bus", null, null, null, true, true, true, Map.of(), 300000L);

        Map<String, Object> validEvent = Map.of(
                "eventBusName", "cloud-orders-bus",
                "source", "acs:oss:cn-hangzhou:12345:my-bucket",
                "type", "oss:ObjectCreated:PutObject");

        CloudEvent event = validator.validateAndBuild(validEvent, config, eventBridgeClient);
        assertThat(event).isNotNull();

        Map<String, Object> invalidEvent = Map.of(
                "eventBusName", "cloud-orders-bus",
                "source", "acs:oss:cn-hangzhou:12345:my-bucket",
                "type", "oss:ObjectDeleted:DeleteObject");

        assertThatThrownBy(() -> validator.validateAndBuild(invalidEvent, config, eventBridgeClient))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Event type 'oss:ObjectDeleted:DeleteObject' is not valid for event source");
    }

    @Test
    void testValidateEventSpecFalseAllowsNonStandardValues() {
        ClientConfigurations config = new ClientConfigurations(
                null, "test-bus", null, null, null, false, false, false, Map.of(), 300000L);

        Map<String, Object> map = new HashMap<>();
        map.put("source", "my.source");
        map.put("type", "my.type");
        map.put("specversion", "0.3");
        map.put("id", "");

        CloudEvent event = validator.validateAndBuild(map, config, eventBridgeClient);
        assertThat(event).isNotNull();
    }

    @Test
    void testPerMessageCacheTtlHeaderOverride() {
        EventBusEntry busEntry = new EventBusEntry();
        busEntry.setEventBusName("custom-ttl-bus");
        ListEventBusesResponse busResponse = new ListEventBusesResponse();
        busResponse.setEventBuses(List.of(busEntry));
        when(eventBridgeClient.listEventBuses(any(ListEventBusesRequest.class))).thenReturn(busResponse);

        EventRuleDTO ruleDTO = new EventRuleDTO();
        ruleDTO.setFilterPattern("{\"source\":[\"app.orders\"]}");
        ListRulesResponse rulesResponse = new ListRulesResponse();
        rulesResponse.setRules(List.of(ruleDTO));
        when(eventBridgeClient.listRules(any(ListRulesRequest.class))).thenReturn(rulesResponse);

        long customTtl = 50000L;
        long before = System.currentTimeMillis();

        ClientConfigurations config = new ClientConfigurations(
                null, "custom-ttl-bus", null, null, null, true, false, true, Map.of(), customTtl);

        Map<String, Object> event = Map.of(
                "eventBusName", "custom-ttl-bus",
                "source", "app.orders",
                "type", "OrderCreated");

        validator.validateAndBuild(event, config, eventBridgeClient);

        EventSourceCache.CacheEntry<EventSourceCache.BusMetadata> entry = eventSourceCache.getCachedEntry("custom-ttl-bus");
        assertThat(entry).isNotNull();
        assertThat(entry.expiryTime()).isGreaterThanOrEqualTo(before + customTtl);
    }

    @Test
    void testRejectionWhenMapBodyOverridesBusOrSourceOutsideWhitelist() {
        AllowedEventSource allowedSource = new AllowedEventSource("app.orders", Set.of("OrderCreated"));
        AllowedEventBus allowedBus = new AllowedEventBus("orders-bus", Map.of("app.orders", allowedSource));
        ClientConfigurations config = new ClientConfigurations(
                null, "orders-bus", null, null, null, false, false, true,
                Map.of("orders-bus", allowedBus), 300000L);

        Map<String, Object> unauthorizedBusEvent = Map.of(
                "eventBusName", "unauthorized-bus",
                "source", "app.orders",
                "type", "OrderCreated");

        assertThatThrownBy(() -> validator.validateAndBuild(unauthorizedBusEvent, config, eventBridgeClient))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Event bus 'unauthorized-bus' is not in the allowed event buses list");

        Map<String, Object> unauthorizedSourceEvent = Map.of(
                "eventBusName", "orders-bus",
                "source", "unauthorized.source",
                "type", "OrderCreated");

        assertThatThrownBy(() -> validator.validateAndBuild(unauthorizedSourceEvent, config, eventBridgeClient))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Event source 'unauthorized.source' is not in the allowed sources list");
    }

    @Test
    void testSingleBusDslWithHttpUriAndPort() {
        String dsl = "http://example.com:8080/events; https://api.service.com:9443/webhooks -> order:created, order:updated";
        Map<String, AllowedEventBus> buses = AlibabaEventBridgeUtils.parseAllowedBusesFromString(dsl, "default-bus");

        assertThat(buses).containsKey("default-bus");
        AllowedEventBus bus = buses.get("default-bus");
        assertThat(bus.allowedSources()).containsKeys("http://example.com:8080/events",
                "https://api.service.com:9443/webhooks");

        AllowedEventSource httpSource = bus.allowedSources().get("http://example.com:8080/events");
        assertThat(httpSource.source()).isEqualTo("http://example.com:8080/events");
        assertThat(httpSource.allowedEventTypes()).isEmpty();

        AllowedEventSource httpsSource = bus.allowedSources().get("https://api.service.com:9443/webhooks");
        assertThat(httpsSource.source()).isEqualTo("https://api.service.com:9443/webhooks");
        assertThat(httpsSource.allowedEventTypes()).containsExactlyInAnyOrder("order:created", "order:updated");
    }

    @Test
    void testSingleBusDslWithUrnAndEquals() {
        String dsl = "urn:custom:event:source = event:type:v1, event:type:v2; urn:another:source";
        Map<String, AllowedEventBus> buses = AlibabaEventBridgeUtils.parseAllowedBusesFromString(dsl, "custom-bus");

        assertThat(buses).containsKey("custom-bus");
        AllowedEventBus bus = buses.get("custom-bus");
        assertThat(bus.allowedSources()).containsKeys("urn:custom:event:source", "urn:another:source");

        AllowedEventSource urnSource1 = bus.allowedSources().get("urn:custom:event:source");
        assertThat(urnSource1.source()).isEqualTo("urn:custom:event:source");
        assertThat(urnSource1.allowedEventTypes()).containsExactlyInAnyOrder("event:type:v1", "event:type:v2");

        AllowedEventSource urnSource2 = bus.allowedSources().get("urn:another:source");
        assertThat(urnSource2.source()).isEqualTo("urn:another:source");
        assertThat(urnSource2.allowedEventTypes()).isEmpty();
    }
}
