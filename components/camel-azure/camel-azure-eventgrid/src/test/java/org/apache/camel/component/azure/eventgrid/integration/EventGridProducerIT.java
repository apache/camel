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
package org.apache.camel.component.azure.eventgrid.integration;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.azure.eventgrid.EventGridConstants;
import org.apache.camel.test.infra.azure.eventgrid.clients.AzureEventGridManagementClient;
import org.apache.camel.test.infra.azure.eventgrid.services.AzureEventGridService;
import org.apache.camel.test.infra.azure.eventgrid.services.AzureEventGridServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Publishes events to a topic of Floci AZ, which delivers them to a webhook subscriber of the test.
 */
@DisabledIfSystemProperty(named = "azure.instance.type", matches = "remote",
                          disabledReason = "A remote topic cannot deliver its events to the subscriber of the test")
class EventGridProducerIT extends CamelTestSupport {
    private static final String TRUST_STORE = "javax.net.ssl.trustStore";
    private static final String TRUST_STORE_PASSWORD = "javax.net.ssl.trustStorePassword";
    private static final String TRUST_STORE_TYPE = "javax.net.ssl.trustStoreType";
    private static final Map<String, String> SAVED_PROPERTIES = new HashMap<>();

    @Order(1)
    @RegisterExtension
    static EventGridSubscriber subscriber = new EventGridSubscriber();

    @Order(2)
    @RegisterExtension
    static AzureEventGridService service = AzureEventGridServiceFactory.createService();

    @BeforeAll
    static void subscribe() {
        // the Event Grid SDK sends the access key only over HTTPS: trust the certificate authority of Floci AZ
        setProperty(TRUST_STORE, service.trustStore());
        setProperty(TRUST_STORE_PASSWORD, service.trustStorePassword());
        setProperty(TRUST_STORE_TYPE, "PKCS12");

        new AzureEventGridManagementClient(service.resourceManagerEndpoint())
                .createWebhookSubscription(service.topicId(), "camel-it", subscriber.endpointUrl());
    }

    @AfterAll
    static void restoreProperties() {
        SAVED_PROPERTIES.forEach((name, value) -> {
            if (value != null) {
                System.setProperty(name, value);
            } else {
                System.clearProperty(name);
            }
        });
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:publish")
                        .to("azure-eventgrid:" + service.topicEndpoint() + "?accessKey=RAW(" + service.topicAccessKey() + ")");
            }
        };
    }

    @Test
    void eventIsDelivered() {
        String subject = "/orders/" + UUID.randomUUID();
        OffsetDateTime time = OffsetDateTime.parse("2026-11-05T10:15:30Z");

        Exchange result = template.send("direct:publish", exchange -> {
            exchange.getIn().setBody(Map.of("item", "Camel", "quantity", 3));
            exchange.getIn().setHeader(EventGridConstants.EVENT_TYPE, "Camel.Order.Created");
            exchange.getIn().setHeader(EventGridConstants.SUBJECT, subject);
            exchange.getIn().setHeader(EventGridConstants.ID, "order-1");
            exchange.getIn().setHeader(EventGridConstants.EVENT_TIME, time);
        });

        assertNull(result.getException());
        Map<String, Object> event = receivedEvents(subject, 1).get(0);
        assertEquals("order-1", event.get("id"));
        assertEquals("Camel.Order.Created", event.get("type"));
        assertEquals("1.0", event.get("specversion"));
        assertTrue(time.isEqual(OffsetDateTime.parse((String) event.get("time"))));
        assertEquals(Map.of("item", "Camel", "quantity", 3), event.get("data"));
    }

    @Test
    void listIsDeliveredAsOneEventPerElement() {
        String subject = "/batches/" + UUID.randomUUID();
        String text = "he said \"hi\"\n\tC:\\camel Grüße";

        Exchange result = template.send("direct:publish", exchange -> {
            exchange.getIn().setBody(List.of(Map.of("name", "Camel"), text, 42));
            exchange.getIn().setHeader(EventGridConstants.SUBJECT, subject);
        });

        assertNull(result.getException());
        // Event Grid delivers the events of a batch one by one, in no given order
        List<Map<String, Object>> events = receivedEvents(subject, 3);
        assertEquals(Set.of(Map.of("name", "Camel"), text, 42),
                events.stream().map(event -> event.get("data")).collect(Collectors.toSet()));
        assertEquals(3, events.stream().map(event -> event.get("id")).distinct().count());
        assertEquals(Set.of("Camel.Event"), events.stream().map(event -> event.get("type")).collect(Collectors.toSet()));
    }

    private static List<Map<String, Object>> receivedEvents(String subject, int count) {
        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(count, subscriber.events(subject).size()));
        return subscriber.events(subject);
    }

    private static void setProperty(String name, String value) {
        SAVED_PROPERTIES.put(name, System.getProperty(name));
        System.setProperty(name, value);
    }
}
