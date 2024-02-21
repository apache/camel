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
package org.apache.camel.dataformat.parquet.avro;

import java.util.List;

import org.apache.avro.generic.GenericRecord;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultUuidGenerator;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.parquet.ParquetReadOptions;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ParquetAvroDataFormatWithoutUnmarshalTypeTest extends CamelTestSupport {

    @Test
    public void testMarshalAndUnmarshalMapWithoutUnmarshalType() throws Exception {
        List<Pojo> in = List.of(
                new Pojo(1, "airport"),
                new Pojo(2, "penguin"),
                new Pojo(3, "verb"));
        MockEndpoint unmarshalMock = getMockEndpoint("mock:unmarshalled");
        unmarshalMock.expectedMessageCount(1);

        MockEndpoint marshalMock = getMockEndpoint("mock:marshalled");
        marshalMock.expectedMessageCount(1);

        template.sendBody("direct:in", in);
        unmarshalMock.assertIsSatisfied();
        marshalMock.assertIsSatisfied();

        List<GenericRecord> records = unmarshalMock.getExchanges().get(0).getMessage().getBody(List.class);
        assertEquals(in.size(), records.size());
        for (int i = 0; i < records.size(); i++) {
            GenericRecord record = GenericRecord.class.cast(records.get(i));
            assertEquals(in.get(i).getId(), record.get("id"));
            assertEquals(in.get(i).getData(), record.get("data").toString());
        }

        byte[] marshalled = marshalMock.getExchanges().get(0).getIn().getBody(byte[].class);
        ParquetInputStream inputStream = new ParquetInputStream(new DefaultUuidGenerator().generateUuid(), marshalled);
        try (ParquetFileReader reader = new ParquetFileReader(inputStream, ParquetReadOptions.builder().build())) {
            assertEquals(in.size(), reader.getRecordCount());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                // First we get a Parquet data from POJO using reflection as preparation
                ParquetAvroDataFormat format = new ParquetAvroDataFormat();
                format.setUnmarshalType(Pojo.class);
                from("direct:in").marshal(format).to("direct:marshalled");

                // Then we ensure that data can be unmarshalled and marshalled again with Avro's GenericRecord
                ParquetAvroDataFormat formatWithoutUnmarshalType = new ParquetAvroDataFormat();
                from("direct:marshalled")
                        .unmarshal(formatWithoutUnmarshalType).to("mock:unmarshalled")
                        .marshal(formatWithoutUnmarshalType).to("mock:marshalled");
            }
        };
    }
}
