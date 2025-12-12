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
package org.apache.camel.dataformat.avro;

import java.time.Instant;
import java.time.LocalDate;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.avro.example.DateRecord;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class AvroDateRecordMarshalAndUnmarshalTest extends CamelTestSupport {

    @Test
    public void testDateRecordMarshalAndUnmarshal() throws InterruptedException {
        LocalDate date = LocalDate.of(2025, 10, 28);
        Instant timestamp = Instant.parse("2024-01-15T10:30:00Z");

        DateRecord input = new DateRecord(date, timestamp);

        MockEndpoint mock = getMockEndpoint("mock:reverse");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(DateRecord.class);

        Object marshalled = template.requestBody("direct:in", input);
        template.sendBody("direct:back", marshalled);
        mock.assertIsSatisfied();

        DateRecord output = mock.getReceivedExchanges().get(0).getIn().getBody(DateRecord.class);
        assertNotNull(output);
        assertEquals(date, output.getDate());
        assertEquals(timestamp, output.getTimestamp());
    }

    @Test
    public void testDateRecordWithNullValues() throws InterruptedException {
        DateRecord input = new DateRecord(null, null);

        MockEndpoint mock = getMockEndpoint("mock:reverse");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(DateRecord.class);

        Object marshalled = template.requestBody("direct:in", input);
        template.sendBody("direct:back", marshalled);
        mock.assertIsSatisfied();

        DateRecord output = mock.getReceivedExchanges().get(0).getIn().getBody(DateRecord.class);
        assertNotNull(output);
        assertNull(output.getDate());
        assertNull(output.getTimestamp());
    }

    @Test
    public void testDateRecordWithBuilder() throws InterruptedException {
        LocalDate date = LocalDate.of(2024, 1, 15);
        Instant timestamp = Instant.parse("2024-01-15T10:30:00Z");

        DateRecord input = DateRecord.newBuilder()
                .setDate(date)
                .setTimestamp(timestamp)
                .build();

        MockEndpoint mock = getMockEndpoint("mock:reverse");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(DateRecord.class);

        Object marshalled = template.requestBody("direct:in", input);
        template.sendBody("direct:back", marshalled);
        mock.assertIsSatisfied();

        DateRecord output = mock.getReceivedExchanges().get(0).getIn().getBody(DateRecord.class);
        assertNotNull(output);
        assertEquals(date, output.getDate());
        assertEquals(timestamp, output.getTimestamp());
    }

    @Test
    public void testDateRecordWithOnlyDate() throws InterruptedException {
        LocalDate date = LocalDate.of(2025, 12, 25);
        DateRecord input = new DateRecord(date, null);

        MockEndpoint mock = getMockEndpoint("mock:reverse");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(DateRecord.class);

        Object marshalled = template.requestBody("direct:in", input);
        template.sendBody("direct:back", marshalled);
        mock.assertIsSatisfied();

        DateRecord output = mock.getReceivedExchanges().get(0).getIn().getBody(DateRecord.class);
        assertNotNull(output);
        assertEquals(date, output.getDate());
        assertNull(output.getTimestamp());
    }

    @Test
    public void testDateRecordWithOnlyTimestamp() throws InterruptedException {
        Instant timestamp = Instant.parse("2024-01-15T10:30:00Z");
        DateRecord input = new DateRecord(null, timestamp);

        MockEndpoint mock = getMockEndpoint("mock:reverse");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(DateRecord.class);

        Object marshalled = template.requestBody("direct:in", input);
        template.sendBody("direct:back", marshalled);
        mock.assertIsSatisfied();

        DateRecord output = mock.getReceivedExchanges().get(0).getIn().getBody(DateRecord.class);
        assertNotNull(output);
        assertNull(output.getDate());
        assertEquals(timestamp, output.getTimestamp());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                AvroDataFormat format = new AvroDataFormat(DateRecord.SCHEMA$);

                from("direct:in").marshal(format);
                from("direct:back").unmarshal(format).to("mock:reverse");
            }
        };
    }
}
