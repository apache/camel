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

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AvroDateMarshalAndUnmarshalTest extends CamelTestSupport {

    private Schema schema;

    @Override
    public void doPreSetup() throws Exception {
        schema = getSchema();
    }

    @Test
    public void testDateMarshalAndUnmarshal() throws InterruptedException {
        marshalAndUnmarshalDate("direct:in", "direct:back");
    }

    @Test
    public void testDateWithNullValues() throws InterruptedException {
        GenericRecord input = new GenericData.Record(schema);
        input.put("date", null);
        input.put("timestamp", null);

        MockEndpoint mock = getMockEndpoint("mock:reverse");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(GenericRecord.class);

        Object marshalled = template.requestBody("direct:in", input);
        template.sendBody("direct:back", marshalled);
        mock.assertIsSatisfied();

        GenericRecord output = mock.getReceivedExchanges().get(0).getIn().getBody(GenericRecord.class);
        assertEquals(input.get("date"), output.get("date"));
        assertEquals(input.get("timestamp"), output.get("timestamp"));
    }

    private void marshalAndUnmarshalDate(String inURI, String outURI) throws InterruptedException {
        GenericRecord input = new GenericData.Record(schema);

        // Avro date logical type - use LocalDate directly
        LocalDate date = LocalDate.of(2025, 10, 28);
        input.put("date", date);

        // Avro timestamp-millis logical type - use Instant directly
        Instant timestamp = Instant.ofEpochMilli(1730000000000L); // Fixed timestamp for consistent testing
        input.put("timestamp", timestamp);

        MockEndpoint mock = getMockEndpoint("mock:reverse");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(GenericRecord.class);

        Object marshalled = template.requestBody(inURI, input);
        template.sendBody(outURI, marshalled);
        mock.assertIsSatisfied();

        GenericRecord output = mock.getReceivedExchanges().get(0).getIn().getBody(GenericRecord.class);
        // The logical type conversions ensure date and timestamp are converted back to LocalDate and Instant
        assertEquals(date, output.get("date"));
        assertEquals(timestamp, output.get("timestamp"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                AvroDataFormat format = new AvroDataFormat(schema);

                from("direct:in").marshal(format);
                from("direct:back").unmarshal(format).to("mock:reverse");
            }
        };
    }

    private Schema getSchema() throws IOException {
        String schemaLocation = getClass().getResource("date.avsc").getFile();
        File schemaFile = new File(schemaLocation);
        assertTrue(schemaFile.exists());
        return new Schema.Parser().parse(schemaFile);
    }
}
