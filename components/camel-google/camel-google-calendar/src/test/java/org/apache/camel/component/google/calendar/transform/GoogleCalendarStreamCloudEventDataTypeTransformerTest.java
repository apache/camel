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
package org.apache.camel.component.google.calendar.transform;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.api.services.calendar.model.Event;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.cloudevents.CloudEvent;
import org.apache.camel.component.google.calendar.stream.GoogleCalendarStreamConstants;
import org.apache.camel.component.google.calendar.stream.GoogleCalendarStreamEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.TransformerKey;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleCalendarStreamCloudEventDataTypeTransformerTest {

    private final DefaultCamelContext camelContext = new DefaultCamelContext();

    private final GoogleCalendarStreamCloudEventDataTypeTransformer transformer
            = new GoogleCalendarStreamCloudEventDataTypeTransformer();

    @Test
    void shouldMapToCloudEvent() {
        Exchange exchange = new DefaultExchange(camelContext);
        Event event = new Event();
        event.setEventType("default");
        exchange.getMessage().setHeader(GoogleCalendarStreamConstants.CALENDAR_ID, "primary");
        exchange.getMessage().setHeader(GoogleCalendarStreamConstants.EVENT_ID, "event1");
        exchange.getMessage().setBody(event);

        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        assertEquals("org.apache.camel.event.google.calendar.stream.consume",
                exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_TYPE));
        assertEquals("google.calendar.stream.primary", exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE));
        assertEquals("default", exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_SUBJECT));
    }

    @Test
    void theSourceIsTheCalendarOfTheConsumer() {
        // CAMEL-27101: the source was built from the event id, so every event had a source of its own
        GoogleCalendarStreamEndpoint endpoint = camelContext.getEndpoint(
                "google-calendar-stream://events?calendarId=team@example.com&clientId=id&clientSecret=secret",
                GoogleCalendarStreamEndpoint.class);
        for (String id : List.of("event1", "event2")) {
            Exchange exchange = endpoint.createExchange(ExchangePattern.InOnly, new Event().setId(id).setEventType("default"));
            assertEquals("team@example.com", exchange.getMessage().getHeader(GoogleCalendarStreamConstants.CALENDAR_ID));
            assertEquals(id, exchange.getMessage().getHeader(GoogleCalendarStreamConstants.EVENT_ID));

            transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

            assertEquals("google.calendar.stream.team@example.com",
                    exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE));
            assertEquals(exchange.getExchangeId(), exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_ID));
        }

        // the calendar of the consumer is primary by default
        endpoint = camelContext.getEndpoint("google-calendar-stream://other?clientId=id&clientSecret=secret",
                GoogleCalendarStreamEndpoint.class);
        Exchange exchange = endpoint.createExchange(ExchangePattern.InOnly, new Event().setId("event3"));
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);
        assertEquals("google.calendar.stream.primary", exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE));
    }

    @Test
    void theSourceIsNotTheEventId() {
        // a message without the calendar id of the consumer gets no source
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setHeader(GoogleCalendarStreamConstants.EVENT_ID, "event1");
        exchange.getMessage().setBody(new Event());
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        assertNull(exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE));
    }

    @Test
    void shouldLookupDataTypeTransformer() {
        Transformer transformer = camelContext.getTransformerRegistry()
                .resolveTransformer(new TransformerKey("google-calendar-stream:application-cloudevents"));
        assertNotNull(transformer);
        assertEquals(GoogleCalendarStreamCloudEventDataTypeTransformer.class, transformer.getClass());
    }

    @Test
    void theBodyTypesSayTheBody() {
        // CAMEL-25928: the Event transformerIn says, which transformerOut says is left as it was
        BodyTypes bodyTypes = GoogleCalendarStreamCloudEventDataTypeTransformer.class.getAnnotation(BodyTypes.class);
        assertEquals(Carrier.POJO, bodyTypes.transformerIn().value()[0]);
        assertEquals(Event.class.getCanonicalName(), bodyTypes.transformerIn().javaType());
        assertEquals(Carrier.UNCHANGED, bodyTypes.transformerOut().value()[0]);

        Exchange exchange = new DefaultExchange(camelContext);
        Event event = new Event();
        exchange.getMessage().setBody(event);
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);
        assertSame(event, exchange.getMessage().getBody());
        assertEquals("org.apache.camel.event.google.calendar.stream.consume",
                exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_TYPE));

        // another body is no Event to read the subject from
        Exchange other = new DefaultExchange(camelContext);
        other.getMessage().setBody("Test");
        assertThrows(CamelExecutionException.class,
                () -> transformer.transform(other.getMessage(), DataType.ANY, DataType.ANY));
    }

    @Test
    void anotherBodyFailsWithAClearMessage() {
        // CAMEL-26300: a body that is no Event failed with a NullPointerException
        for (Object body : Arrays.asList("secret text", Map.of("eventType", "secret value"), null)) {
            Exchange exchange = new DefaultExchange(camelContext);
            exchange.getMessage().setHeader(GoogleCalendarStreamConstants.EVENT_ID, "event1");
            exchange.getMessage().setBody(body);

            CamelExecutionException e = assertThrows(CamelExecutionException.class,
                    () -> transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY), String.valueOf(body));

            String type = body != null ? body.getClass().getName() : "null";
            assertTrue(e.getMessage().startsWith("The body must be a Google Calendar Event, such as the body of the"
                                                 + " google-calendar-stream consumer, to read the CloudEvent subject from,"
                                                 + " but was: " + type),
                    e.getMessage());
            assertFalse(e.getMessage().contains("secret"), e.getMessage());
            assertSame(exchange, e.getExchange());
            // no CloudEvent header is set on a message that fails
            assertNull(exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_TYPE));
            assertNull(exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE));
        }
    }
}
