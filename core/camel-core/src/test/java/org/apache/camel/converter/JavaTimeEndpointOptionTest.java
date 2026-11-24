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
package org.apache.camel.converter;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.TypeConversionException;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Endpoint options of type OffsetDateTime, ZonedDateTime and Instant set in an endpoint uri.
 */
public class JavaTimeEndpointOptionTest extends ContextTestSupport {

    private static final OffsetDateTime EXPECTED = OffsetDateTime.of(2026, 11, 18, 23, 0, 0, 0, ZoneOffset.ofHours(1));

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext answer = super.createCamelContext();
        answer.addComponent("mytime", new MyComponent());
        return answer;
    }

    @Test
    public void testPlusInUri() {
        // a plain + in the uri is decoded to a space, which the converters read as a +
        MyEndpoint endpoint = context.getEndpoint("mytime:plus?offsetDateTime=2026-11-18T23:00:00+01:00"
                                                  + "&zonedDateTime=2026-11-18T23:00:00+01:00"
                                                  + "&instant=2026-11-18T23:00:00+01:00",
                MyEndpoint.class);
        assertEquals(EXPECTED, endpoint.getOffsetDateTime());
        assertEquals(EXPECTED.toZonedDateTime(), endpoint.getZonedDateTime());
        assertEquals(EXPECTED.toInstant(), endpoint.getInstant());
    }

    @Test
    public void testEncodedPlusAndRawInUri() {
        MyEndpoint endpoint = context.getEndpoint("mytime:encoded?offsetDateTime=2026-11-18T23:00:00%2B01:00"
                                                  + "&zonedDateTime=RAW(2026-11-18T23:00:00+01:00)"
                                                  + "&instant=2026-11-18T22:00:00Z",
                MyEndpoint.class);
        assertEquals(EXPECTED, endpoint.getOffsetDateTime());
        assertEquals(EXPECTED.toZonedDateTime(), endpoint.getZonedDateTime());
        assertEquals(EXPECTED.toInstant(), endpoint.getInstant());
    }

    @Test
    public void testZoneIdInUri() {
        MyEndpoint endpoint = context.getEndpoint(
                "mytime:zone?zonedDateTime=2026-11-18T23:00:00+01:00[Europe/Paris]", MyEndpoint.class);
        assertEquals(ZonedDateTime.of(2026, 11, 18, 23, 0, 0, 0, ZoneId.of("Europe/Paris")), endpoint.getZonedDateTime());
        assertNull(endpoint.getOffsetDateTime());
        assertNull(endpoint.getInstant());
    }

    @Test
    public void testHeaderAs() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader("when", "2026-11-18T22:00:00Z");
        Object value = context.resolveLanguage("simple").createExpression("${headerAs(when, java.time.Instant)}")
                .evaluate(exchange, Object.class);
        assertEquals(EXPECTED.toInstant(), value);
    }

    @Test
    public void testHeaderAsInvalid() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader("when", "tomorrow");
        Expression expression = context.resolveLanguage("simple").createExpression("${headerAs(when, java.time.Instant)}");
        assertThrows(TypeConversionException.class, () -> expression.evaluate(exchange, Object.class));
    }

    public static final class MyEndpoint extends DefaultEndpoint {

        private OffsetDateTime offsetDateTime;
        private ZonedDateTime zonedDateTime;
        private Instant instant;

        private MyEndpoint(String endpointUri, Component component) {
            super(endpointUri, component);
        }

        @Override
        public Producer createProducer() {
            return null;
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            return null;
        }

        public OffsetDateTime getOffsetDateTime() {
            return offsetDateTime;
        }

        public void setOffsetDateTime(OffsetDateTime offsetDateTime) {
            this.offsetDateTime = offsetDateTime;
        }

        public ZonedDateTime getZonedDateTime() {
            return zonedDateTime;
        }

        public void setZonedDateTime(ZonedDateTime zonedDateTime) {
            this.zonedDateTime = zonedDateTime;
        }

        public Instant getInstant() {
            return instant;
        }

        public void setInstant(Instant instant) {
            this.instant = instant;
        }
    }

    public static final class MyComponent extends DefaultComponent {

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
            MyEndpoint answer = new MyEndpoint(uri, this);
            setProperties(answer, parameters);
            return answer;
        }
    }
}
