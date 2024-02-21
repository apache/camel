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

import java.io.FileInputStream;
import java.util.List;

import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData.Record;
import org.apache.avro.generic.GenericRecordBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ParquetAvroDataFormatUnmarshalLazyLoadTest extends CamelTestSupport {

    @Test
    public void testUnmarshalLazyLoad() throws Exception {

        MockEndpoint mockResults = getMockEndpoint("mock:result");

        mockResults.expectedMessageCount(3);
        mockResults.message(0).body().isEqualTo(new Pojo(1, "airport"));
        mockResults.message(1).body().isEqualTo(new Pojo(2, "penguin"));
        mockResults.message(2).body().isEqualTo(new Pojo(3, "verb"));

        template.sendBody("direct:start", new FileInputStream("src/test/resources/example1.parquet"));

        mockResults.assertIsSatisfied();
    }

    @Test
    public void testUnmarshalLazyLoadNoUnmarshalType() throws Exception {
        Schema schema = SchemaBuilder
                .record("Pojo")
                .fields()
                .requiredString("data")
                .requiredLong("id")
                .endRecord();

        Record expected1 = new GenericRecordBuilder(schema).set("data", "airport").set("id", 1L).build();
        Record expected2 = new GenericRecordBuilder(schema).set("data", "penguin").set("id", 2L).build();
        Record expected3 = new GenericRecordBuilder(schema).set("data", "verb").set("id", 3L).build();

        MockEndpoint mockResults = getMockEndpoint("mock:resultNoUnmarshalType");

        mockResults.expectedMessageCount(3);

        template.sendBody("direct:startNoUnmarshalType", new FileInputStream("src/test/resources/example1.parquet"));

        mockResults.assertIsSatisfied();

        List<Exchange> exchanges = mockResults.getExchanges();
        assertEquals(0, exchanges.get(0).getMessage().getBody(Record.class).compareTo(expected1));
        assertEquals(0, exchanges.get(1).getMessage().getBody(Record.class).compareTo(expected2));
        assertEquals(0, exchanges.get(2).getMessage().getBody(Record.class).compareTo(expected3));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                ParquetAvroDataFormat format = new ParquetAvroDataFormat()
                        .setLazyLoad(true);
                format.setUnmarshalType(Pojo.class);

                ParquetAvroDataFormat formatNoUnmarshalType = new ParquetAvroDataFormat()
                        .setLazyLoad(true);

                from("direct:start")
                        .unmarshal(format)
                        .split(body())
                        .to("mock:result");

                from("direct:startNoUnmarshalType")
                        .unmarshal(formatNoUnmarshalType)
                        .split(body())
                        .to("mock:resultNoUnmarshalType");
            }
        };
    }
}
