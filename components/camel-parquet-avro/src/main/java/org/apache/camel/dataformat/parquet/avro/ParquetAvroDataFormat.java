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

import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.avro.reflect.ReflectData;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.DefaultUuidGenerator;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.ParquetWriter;

import static org.apache.parquet.hadoop.ParquetFileWriter.Mode.OVERWRITE;
import static org.apache.parquet.hadoop.metadata.CompressionCodecName.GZIP;

@Dataformat("parquetAvro")
public class ParquetAvroDataFormat extends ServiceSupport implements DataFormat, DataFormatName {

    private static final DefaultUuidGenerator DEFAULT_UUID_GENERATOR = new DefaultUuidGenerator();

    private Class<?> unmarshalType;

    public String getDataFormatName() {
        return "parquetAvro";
    }

    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        // marshal from the Java object (graph) to the parquet-avro type
        Configuration conf = new Configuration();

        FileSystem.get(conf).setWriteChecksum(false);

        BufferedOutputStream parquetOutput = new BufferedOutputStream(stream);
        ParquetOutputStream parquetOutputStream = new ParquetOutputStream(
                DEFAULT_UUID_GENERATOR.generateUuid(),
                parquetOutput);

        List<?> list = (List<?>) graph;

        try (ParquetWriter<Object> writer = AvroParquetWriter.builder(parquetOutputStream)
                .withSchema(ReflectData.AllowNull.get().getSchema(unmarshalType)) // generate nullable fields
                .withDataModel(ReflectData.get())
                .withConf(conf)
                .withCompressionCodec(GZIP)
                .withWriteMode(OVERWRITE)
                .build()) {
            for (Object grapElem : list) {
                writer.write(grapElem);
            }
        }
    }

    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        // unmarshal from the input stream of parquet-avro to Java object (graph)
        List<Object> parquetObjects = new ArrayList<>();
        Configuration conf = new Configuration();

        ParquetInputStream parquetInputStream = new ParquetInputStream(
                DEFAULT_UUID_GENERATOR.generateUuid(),
                stream.readAllBytes());

        try (ParquetReader<?> reader = AvroParquetReader.builder(parquetInputStream)
                .withDataModel(new ReflectData(unmarshalType.getClassLoader()))
                .disableCompatibility() // always use this (since this is a new project)
                .withConf(conf)
                .build()) {

            Object pojo;
            while ((pojo = unmarshalType.cast(reader.read())) != null) {
                parquetObjects.add(pojo);
            }
        }

        return parquetObjects;
    }

    @Override
    protected void doStart() throws Exception {
        // no-op
    }

    @Override
    protected void doStop() throws Exception {
        // no-op
    }

    public Class<?> getUnmarshalType() {
        return unmarshalType;
    }

    /**
     * Class to use when unmarshalling.
     */
    public void setUnmarshalType(Class<?> unmarshalType) {
        this.unmarshalType = unmarshalType;
    }

}
