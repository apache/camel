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

import java.util.Collection;
import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultUuidGenerator;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.parquet.ParquetReadOptions;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.junit.jupiter.api.Test;

import static org.apache.parquet.hadoop.metadata.CompressionCodecName.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ParquetAvroMarshalCompressionCodecTest extends CamelTestSupport {

    Collection<Pojo> in = List.of(
            new Pojo(1, "airport"),
            new Pojo(2, "penguin"),
            new Pojo(3, "verb"));

    DefaultUuidGenerator uuidGenerator = new DefaultUuidGenerator();

    ParquetReadOptions readOptions = ParquetReadOptions.builder().build();

    @Test
    public void testMarshalCompressionCodec() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:default");
        mock.expectedMessageCount(1);

        template.requestBody("direct:default", in);
        mock.assertIsSatisfied();

        byte[] marshalled = mock.getExchanges().get(0).getIn().getBody(byte[].class);
        ParquetInputStream inputStream = new ParquetInputStream(uuidGenerator.generateUuid(), marshalled);
        try (ParquetFileReader reader = new ParquetFileReader(inputStream, readOptions)) {
            CompressionCodecName codecName = reader.getRowGroups().get(0).getColumns().get(0).getCodec();
            assertEquals(GZIP, codecName);
        }
    }

    @Test
    public void testMarshalCompressionCodecGzip() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:gzip");
        mock.expectedMessageCount(1);

        template.requestBody("direct:gzip", in);
        mock.assertIsSatisfied();

        byte[] marshalled = mock.getExchanges().get(0).getIn().getBody(byte[].class);
        ParquetInputStream inputStream = new ParquetInputStream(uuidGenerator.generateUuid(), marshalled);
        try (ParquetFileReader reader = new ParquetFileReader(inputStream, readOptions)) {
            CompressionCodecName codecName = reader.getRowGroups().get(0).getColumns().get(0).getCodec();
            assertEquals(GZIP, codecName);
        }
    }

    @Test
    public void testMarshalCompressionCodecSnappy() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:snappy");
        mock.expectedMessageCount(1);

        template.requestBody("direct:snappy", in);
        mock.assertIsSatisfied();

        byte[] marshalled = mock.getExchanges().get(0).getIn().getBody(byte[].class);
        ParquetInputStream inputStream = new ParquetInputStream(uuidGenerator.generateUuid(), marshalled);
        try (ParquetFileReader reader = new ParquetFileReader(inputStream, readOptions)) {
            CompressionCodecName codecName = reader.getRowGroups().get(0).getColumns().get(0).getCodec();
            assertEquals(SNAPPY, codecName);
        }
    }

    @Test
    public void testMarshalCompressionCodecUncompressed() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:uncompressed");
        mock.expectedMessageCount(1);

        template.requestBody("direct:uncompressed", in);
        mock.assertIsSatisfied();

        byte[] marshalled = mock.getExchanges().get(0).getIn().getBody(byte[].class);
        ParquetInputStream inputStream = new ParquetInputStream(uuidGenerator.generateUuid(), marshalled);
        try (ParquetFileReader reader = new ParquetFileReader(inputStream, readOptions)) {
            CompressionCodecName codecName = reader.getRowGroups().get(0).getColumns().get(0).getCodec();
            assertEquals(UNCOMPRESSED, codecName);
        }
    }

    @Test
    public void testMarshalCompressionCodecZstd() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:zstd");
        mock.expectedMessageCount(1);

        template.requestBody("direct:zstd", in);
        mock.assertIsSatisfied();

        byte[] marshalled = mock.getExchanges().get(0).getIn().getBody(byte[].class);
        ParquetInputStream inputStream = new ParquetInputStream(uuidGenerator.generateUuid(), marshalled);
        try (ParquetFileReader reader = new ParquetFileReader(inputStream, readOptions)) {
            CompressionCodecName codecName = reader.getRowGroups().get(0).getColumns().get(0).getCodec();
            assertEquals(ZSTD, codecName);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() {
                ParquetAvroDataFormat defaultFormat = new ParquetAvroDataFormat();
                defaultFormat.setUnmarshalType(Pojo.class);
                from("direct:default").marshal(defaultFormat).to("mock:default");

                ParquetAvroDataFormat gzipFormat = new ParquetAvroDataFormat();
                gzipFormat.setUnmarshalType(Pojo.class);
                gzipFormat.setCompressionCodecName(GZIP.name());
                from("direct:gzip").marshal(gzipFormat).to("mock:gzip");

                ParquetAvroDataFormat snappyFormat = new ParquetAvroDataFormat();
                snappyFormat.setUnmarshalType(Pojo.class);
                snappyFormat.setCompressionCodecName(SNAPPY.name());
                from("direct:snappy").marshal(snappyFormat).to("mock:snappy");

                ParquetAvroDataFormat uncompressedFormat = new ParquetAvroDataFormat();
                uncompressedFormat.setUnmarshalType(Pojo.class);
                uncompressedFormat.setCompressionCodecName(UNCOMPRESSED.name());
                from("direct:uncompressed").marshal(uncompressedFormat).to("mock:uncompressed");

                ParquetAvroDataFormat zstdFormat = new ParquetAvroDataFormat();
                zstdFormat.setUnmarshalType(Pojo.class);
                zstdFormat.setCompressionCodecName(ZSTD.name());
                from("direct:zstd").marshal(zstdFormat).to("mock:zstd");
            }
        };
    }
}
