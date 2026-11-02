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
package org.apache.camel.component.azure.eventgrid;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.azure.core.models.CloudEvent;
import com.azure.json.JsonOptions;
import com.azure.json.JsonProviders;
import com.azure.json.JsonReader;
import com.azure.json.JsonWriter;
import com.azure.messaging.eventgrid.EventGridPublisherClient;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * The events the producer publishes through a mocked publisher client (CAMEL-26299), checked as the JSON the client
 * sends to Event Grid.
 */
class EventGridProducerTest extends CamelTestSupport {

    private static final String URI
            = "azure-eventgrid:https://mytopic.eastus-1.eventgrid.azure.net/api/events?publisherClient=#publisherClient";
    private static final byte[] BINARY = { 0x7b, (byte) 0xff, 0x00, (byte) 0xfe, 0x7d };
    // Event Grid reads strict JSON: no NaN or Infinity
    private static final JsonOptions STRICT_JSON = new JsonOptions().setNonNumericNumbersSupported(false);

    @SuppressWarnings("unchecked")
    private final EventGridPublisherClient<CloudEvent> publisherClient = mock(EventGridPublisherClient.class);

    @TempDir
    Path tempDir;

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("publisherClient", publisherClient);
    }

    @Test
    void mapIsSentAsJson() throws Exception {
        Exchange result = template.send(URI, e -> e.getIn().setBody(Map.of("name", "Camel", "size", 3)));

        assertNull(result.getException());
        Map<String, Object> event = publishedEvent();
        assertEquals(Map.of("name", "Camel", "size", 3), event.get("data"));
        assertEquals("/camel", event.get("source"));
        assertEquals("Camel.Event", event.get("type"));
        assertEquals("/camel/event", event.get("subject"));
    }

    @Test
    void pojoIsSentAsJson() throws Exception {
        Exchange result = template.send(URI, e -> e.getIn().setBody(new Order("Camel", 3)));

        assertNull(result.getException());
        assertEquals(Map.of("item", "Camel", "quantity", 3), publishedEvent().get("data"));
    }

    @Test
    void numberIsSentAsJsonNumber() throws Exception {
        Exchange result = template.send(URI, e -> e.getIn().setBody(42));

        assertNull(result.getException());
        assertEquals(42, publishedEvent().get("data"));
    }

    @Test
    void jsonObjectTextIsSentAsJson() throws Exception {
        Exchange result = template.send(URI, e -> e.getIn().setBody("{\"name\":\"Camel\",\"note\":\"a \\\"b\\\"\"}"));

        assertNull(result.getException());
        assertEquals(Map.of("name", "Camel", "note", "a \"b\""), publishedEvent().get("data"));
    }

    @Test
    void jsonArrayTextIsSentAsJson() throws Exception {
        Exchange result = template.send(URI, e -> e.getIn().setBody(" [1, {\"name\":\"Camel\"}] "));

        assertNull(result.getException());
        assertEquals(List.of(1, Map.of("name", "Camel")), publishedEvent().get("data"));
    }

    @Test
    void textIsSentAsJsonString() throws Exception {
        Exchange result = template.send(URI, e -> e.getIn().setBody("Hello Camel"));

        assertNull(result.getException());
        assertEquals("Hello Camel", publishedEvent().get("data"));
    }

    @Test
    void textWithQuotesAndLineBreaksIsKept() throws Exception {
        String text = "he said \"hi\"\n\tC:\\camel Grüße";

        Exchange result = template.send(URI, e -> e.getIn().setBody(text));

        assertNull(result.getException());
        assertEquals(text, publishedEvent().get("data"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "{\"v\": NaN}", "[1, -Infinity]" })
    void textWithNonNumericNumbersIsSentAsJsonString(String text) throws Exception {
        // NaN and Infinity are not JSON: such text must not be written as JSON data
        Exchange result = template.send(URI, e -> e.getIn().setBody(text));

        assertNull(result.getException());
        assertEquals(text, publishedEvent().get("data"));
    }

    @Test
    void textThatIsMoreThanJsonIsSentAsJsonString() throws Exception {
        Exchange result = template.send(URI, e -> e.getIn().setBody("{\"name\":\"Camel\"} and more"));

        assertNull(result.getException());
        assertEquals("{\"name\":\"Camel\"} and more", publishedEvent().get("data"));
    }

    @Test
    void bytesAreSentAsBinaryData() throws Exception {
        Exchange result = template.send(URI, e -> e.getIn().setBody(BINARY));

        assertNull(result.getException());
        assertBinaryData(publishedEvent());
    }

    @Test
    void streamIsSentAsBinaryData() throws Exception {
        Exchange result = template.send(URI, e -> e.getIn().setBody(new ByteArrayInputStream(BINARY)));

        assertNull(result.getException());
        assertBinaryData(publishedEvent());
    }

    @Test
    void fileIsSentAsBinaryData() throws Exception {
        Path file = Files.write(tempDir.resolve("event.bin"), BINARY);

        Exchange result = template.send(URI, e -> e.getIn().setBody(file.toFile()));

        assertNull(result.getException());
        assertBinaryData(publishedEvent());
    }

    @Test
    void fileOfFileConsumerIsSentAsBinaryData() throws Exception {
        Path inbox = Files.createDirectory(tempDir.resolve("inbox"));
        Files.write(inbox.resolve("event.bin"), BINARY);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("file:" + inbox + "?noop=true&initialDelay=0").to(URI);
            }
        });

        assertBinaryData(publishedEvent());
    }

    @Test
    void listIsSentAsOneEventPerElement() throws Exception {
        Exchange result = template.send(URI, e -> e.getIn().setBody(List.of(Map.of("name", "Camel"), "Hello")));

        assertNull(result.getException());
        List<Map<String, Object>> events = publishedEvents();
        assertEquals(2, events.size());
        assertEquals(Map.of("name", "Camel"), events.get(0).get("data"));
        assertEquals("Hello", events.get(1).get("data"));
        assertNotEquals(events.get(0).get("id"), events.get(1).get("id"));
    }

    @Test
    void headersSetTheAttributesOfTheEvent() throws Exception {
        OffsetDateTime time = OffsetDateTime.parse("2026-11-02T10:15:30Z");

        Exchange result = template.send(URI, e -> {
            e.getIn().setBody(Map.of("name", "Camel"));
            e.getIn().setHeader(EventGridConstants.EVENT_TYPE, "Camel.Order.Created");
            e.getIn().setHeader(EventGridConstants.SUBJECT, "/orders/1");
            e.getIn().setHeader(EventGridConstants.ID, "order-1");
            e.getIn().setHeader(EventGridConstants.EVENT_TIME, time);
        });

        assertNull(result.getException());
        Map<String, Object> event = publishedEvent();
        assertEquals("Camel.Order.Created", event.get("type"));
        assertEquals("/orders/1", event.get("subject"));
        assertEquals("order-1", event.get("id"));
        assertTrue(time.isEqual(OffsetDateTime.parse((String) event.get("time"))));
    }

    @Test
    void noBodyFails() {
        Exchange result = template.send(URI, e -> e.getIn().setBody(null));

        assertInstanceOf(InvalidPayloadException.class, result.getException());
        verify(publisherClient, never()).sendEvents(any());
    }

    private static void assertBinaryData(Map<String, Object> event) {
        assertFalse(event.containsKey("data"), "binary data must be in data_base64");
        assertArrayEquals(BINARY, Base64.getDecoder().decode((String) event.get("data_base64")));
    }

    private Map<String, Object> publishedEvent() throws IOException {
        List<Map<String, Object>> events = publishedEvents();
        assertEquals(1, events.size());
        return events.get(0);
    }

    /**
     * The events of the one call of the publisher client, as the JSON it sends to Event Grid: reading them back fails
     * when that is not valid JSON.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> publishedEvents() throws IOException {
        ArgumentCaptor<Iterable<CloudEvent>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(publisherClient, timeout(10000)).sendEvents(captor.capture());

        List<Map<String, Object>> events = new ArrayList<>();
        for (CloudEvent event : captor.getValue()) {
            StringWriter json = new StringWriter();
            try (JsonWriter writer = JsonProviders.createWriter(json)) {
                event.toJson(writer);
            }
            try (JsonReader reader = JsonProviders.createReader(json.toString(), STRICT_JSON)) {
                events.add((Map<String, Object>) reader.readUntyped());
            }
        }
        return events;
    }

    public static class Order {
        private final String item;
        private final int quantity;

        Order(String item, int quantity) {
            this.item = item;
            this.quantity = quantity;
        }

        public String getItem() {
            return item;
        }

        public int getQuantity() {
            return quantity;
        }
    }
}
