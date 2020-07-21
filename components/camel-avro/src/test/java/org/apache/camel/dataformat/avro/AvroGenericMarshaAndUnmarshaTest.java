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

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AvroGenericMarshaAndUnmarshaTest extends CamelTestSupport {

    private Schema schema;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        schema = getSchema();
        super.setUp();
    }

    @Test
    public void testGenericMarshalAndUnmarshal() throws InterruptedException {
        marshalAndUnmarshalGeneric("direct:in", "direct:back");
    }


    private void marshalAndUnmarshalGeneric(String inURI, String outURI) throws InterruptedException {
        GenericRecord input = new GenericData.Record(schema);
        input.put("name", "ceposta");

        MockEndpoint mock = getMockEndpoint("mock:reverse");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(GenericRecord.class);
        mock.message(0).body().isEqualTo(input);

        Object marshalled = template.requestBody(inURI, input);
        template.sendBody(outURI, marshalled);
        mock.assertIsSatisfied();

        GenericRecord output = mock.getReceivedExchanges().get(0).getIn().getBody(GenericRecord.class);
        assertEquals(input, output);

    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                AvroDataFormat format = new AvroDataFormat(schema);

                from("direct:in").marshal(format);
                from("direct:back").unmarshal(format).to("mock:reverse");
            }
        };
    }

    private Schema getSchema() throws IOException {
        String schemaLocation = getClass().getResource("user.avsc").getFile();
        File schemaFile = new File(schemaLocation);
        assertTrue(schemaFile.exists());
        return new Schema.Parser().parse(schemaFile);
    }
}
