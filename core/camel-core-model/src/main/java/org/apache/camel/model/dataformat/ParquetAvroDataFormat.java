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
package org.apache.camel.model.dataformat;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.builder.DataFormatBuilder;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Parquet Avro serialization and de-serialization.
 */
@Metadata(firstVersion = "4.0.0", label = "dataformat,transformation,file", title = "Parquet File")
@XmlRootElement(name = "parquetAvro")
@XmlAccessorType(XmlAccessType.FIELD)
public class ParquetAvroDataFormat extends DataFormatDefinition {

    @XmlAttribute
    @Metadata(defaultValue = "GZIP", enums = "UNCOMPRESSED,SNAPPY,GZIP,LZO,BROTLI,LZ4,ZSTD,LZ4_RAW")
    private String compressionCodecName;
    @XmlTransient
    private Class<?> unmarshalType;
    @XmlAttribute(name = "unmarshalType")
    private String unmarshalTypeName;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String lazyLoad;

    public ParquetAvroDataFormat() {
        super("parquetAvro");
    }

    public ParquetAvroDataFormat(String unmarshalTypeName) {
        this();
        setUnmarshalTypeName(unmarshalTypeName);
    }

    public ParquetAvroDataFormat(Class<?> unmarshalType) {
        this();
        this.unmarshalType = unmarshalType;
    }

    public ParquetAvroDataFormat(boolean lazyLoad) {
        this();
        setLazyLoad(Boolean.toString(lazyLoad));
    }

    private ParquetAvroDataFormat(Builder builder) {
        this();
        this.compressionCodecName = builder.compressionCodecName;
        this.unmarshalTypeName = builder.unmarshalTypeName;
        this.unmarshalType = builder.unmarshalType;
        this.lazyLoad = builder.lazyLoad;
    }

    /**
     * Compression codec to use when marshalling.
     */
    public void setCompressionCodecName(String compressionCodecName) {
        this.compressionCodecName = compressionCodecName;
    }

    public String getCompressionCodecName() {
        return compressionCodecName;
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
     * Class to use when (un)marshalling. If omitted, parquet files are converted into Avro's GenericRecords for
     * unmarshalling and input objects are assumed as GenericRecords for marshalling.
     */
    public void setUnmarshalTypeName(String unmarshalTypeName) {
        this.unmarshalTypeName = unmarshalTypeName;
    }

    public String getUnmarshalTypeName() {
        return unmarshalTypeName;
    }

    public String getLazyLoad() {
        return lazyLoad;
    }

    /**
     * Whether the unmarshalling should produce an iterator of records or read all the records at once.
     */
    public void setLazyLoad(String lazyLoad) {
        this.lazyLoad = lazyLoad;
    }

    /**
     * {@code Builder} is a specific builder for {@link ParquetAvroDataFormat}.
     */
    @XmlTransient
    public static class Builder implements DataFormatBuilder<ParquetAvroDataFormat> {

        private String compressionCodecName;
        private Class<?> unmarshalType;
        private String unmarshalTypeName;
        private String lazyLoad;

        /**
         * Compression codec to use when marshalling.
         */
        public Builder compressionCodecName(String compressionCodecName) {
            this.compressionCodecName = compressionCodecName;
            return this;
        }

        /**
         * Class to use when unmarshalling.
         */
        public Builder unmarshalTypeName(String unmarshalTypeName) {
            this.unmarshalTypeName = unmarshalTypeName;
            return this;
        }

        /**
         * Class to use when unmarshalling.
         */
        public Builder unmarshalType(Class<?> unmarshalType) {
            this.unmarshalType = unmarshalType;
            return this;
        }

        /**
         * Whether the unmarshalling should produce an iterator of records or read all the records at once.
         */
        public Builder lazyLoad(String lazyLoad) {
            this.lazyLoad = lazyLoad;
            return this;
        }

        /**
         * Whether the unmarshalling should produce an iterator of records or read all the records at once.
         */
        public Builder lazyLoad(boolean lazyLoad) {
            this.lazyLoad = Boolean.toString(lazyLoad);
            return this;
        }

        @Override
        public ParquetAvroDataFormat end() {
            return new ParquetAvroDataFormat(this);
        }
    }

}
