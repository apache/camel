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

import org.apache.avro.AvroRuntimeException;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericContainer;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
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
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.parquet.hadoop.ParquetFileWriter.Mode.OVERWRITE;
import static org.apache.parquet.hadoop.metadata.CompressionCodecName.GZIP;

@Dataformat("parquetAvro")
public class ParquetAvroDataFormat extends ServiceSupport implements DataFormat, DataFormatName {

    private static final Logger LOG = LoggerFactory.getLogger(ParquetAvroDataFormat.class);

    private static final DefaultUuidGenerator DEFAULT_UUID_GENERATOR = new DefaultUuidGenerator();

    private CompressionCodecName compressionCodecName = GZIP;
    private Class<?> unmarshalType;
    private boolean lazyLoad;

    @Override
    public String getDataFormatName() {
        return "parquetAvro";
    }

    @Override
    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        // marshal from the Java object or GenericRecord (graph) to the parquet-avro type
        Configuration conf = new Configuration();

        FileSystem.get(conf).setWriteChecksum(false);

        BufferedOutputStream parquetOutput = new BufferedOutputStream(stream);
        ParquetOutputStream parquetOutputStream = new ParquetOutputStream(
                DEFAULT_UUID_GENERATOR.generateUuid(),
                parquetOutput);

        List<?> list = (List<?>) graph;

        Schema schema = null;
        GenericData model = null;
        if (unmarshalType != null) {
            try {
                schema = ReflectData.AllowNull.get().getSchema(unmarshalType); // generate nullable fields
                model = ReflectData.get();
            } catch (AvroRuntimeException e) {
                LOG.warn("Fallback to use GenericRecord instead of POJO for marshalling", e);
            }
        }
        if (schema == null) {
            schema = GenericContainer.class.cast(list.get(0)).getSchema();
            model = GenericData.get();
        }

        try (ParquetWriter<Object> writer = AvroParquetWriter.builder(parquetOutputStream)
                .withSchema(schema)
                .withDataModel(model)
                .withConf(conf)
                .withCompressionCodec(compressionCodecName)
                .withWriteMode(OVERWRITE)
                .build()) {
            for (Object grapElem : list) {
                writer.write(grapElem);
            }
        }
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        // unmarshal from the input stream of parquet-avro to Java object or GenericRecord (graph)
        Configuration conf = new Configuration();

        ParquetInputStream parquetInputStream = new ParquetInputStream(
                DEFAULT_UUID_GENERATOR.generateUuid(),
                stream.readAllBytes());

        Class<?> type = GenericRecord.class;
        GenericData model = GenericData.get();
        if (unmarshalType != null) {
            type = unmarshalType;
            model = new ReflectData(unmarshalType.getClassLoader());
        }

        ParquetReader.Builder<?> builder = AvroParquetReader.builder(parquetInputStream)
                .withDataModel(model)
                .disableCompatibility() // always use this (since this is a new project)
                .withConf(conf);

        if (lazyLoad) {
            ParquetIterator<?> iterator = new ParquetIterator<>(builder.build());
            exchange.getExchangeExtension()
                    .addOnCompletion(new ParquetUnmarshalOnCompletion(iterator));
            return iterator;
        } else {
            try (ParquetReader<?> reader = builder.build()) {
                List<Object> parquetObjects = new ArrayList<>();
                Object pojo;
                while ((pojo = type.cast(reader.read())) != null) {
                    parquetObjects.add(pojo);
                }
                return parquetObjects;
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        // no-op
    }

    @Override
    protected void doStop() throws Exception {
        // no-op
    }

    public String getCompressionCodecName() {
        return compressionCodecName.name();
    }

    /**
     * Compression codec to use when marshalling. You can find the supported codecs at
     * https://github.com/apache/parquet-format/blob/master/Compression.md#codecs. Note that some codecs may require you
     * to include additional libraries into the classpath.
     */
    public void setCompressionCodecName(String compressionCodecName) {
        this.compressionCodecName = CompressionCodecName.valueOf(compressionCodecName);
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

    /**
     * Indicates whether the unmarshalling should produce an iterator of records or read all the records at once.
     */
    public boolean isLazyLoad() {
        return lazyLoad;
    }

    /**
     * Sets whether the unmarshalling should produce an iterator of records or read all the records at once.
     */
    public ParquetAvroDataFormat setLazyLoad(boolean lazyLoad) {
        this.lazyLoad = lazyLoad;
        return this;
    }

}
