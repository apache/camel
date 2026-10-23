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
 * Compress and decompress messages using <code>java.util.zip.GZIP*Stream</code>.
 */
@Metadata(firstVersion = "2.0.0", label = "dataformat,transformation", title = "GZip Deflater",
          description = "Compress and decompress messages using java.util.zip.GZIP*Stream")
@XmlRootElement(name = "gzipDeflater")
@XmlAccessorType(XmlAccessType.FIELD)
public class GzipDeflaterDataFormat extends DataFormatDefinition {

    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Long", defaultValue = "1073741824",
              description = "Set the maximum decompressed size of the data (in bytes). The default value if not specified corresponds to 1 gigabyte."
                            + " An IOException is thrown if the decompressed size exceeds this amount."
                            + " Set to -1 to disable setting a maximum decompressed size.")
    private String maxDecompressedSize;

    public GzipDeflaterDataFormat() {
        super("gzipDeflater");
    }

    protected GzipDeflaterDataFormat(GzipDeflaterDataFormat source) {
        super(source);
        this.maxDecompressedSize = source.maxDecompressedSize;
    }

    private GzipDeflaterDataFormat(Builder builder) {
        this();
        this.maxDecompressedSize = builder.maxDecompressedSize;
    }

    @Override
    public GzipDeflaterDataFormat copyDefinition() {
        return new GzipDeflaterDataFormat(this);
    }

    public String getMaxDecompressedSize() {
        return maxDecompressedSize;
    }

    public void setMaxDecompressedSize(String maxDecompressedSize) {
        this.maxDecompressedSize = maxDecompressedSize;
    }

    /**
     * {@code Builder} is a specific builder for {@link GzipDeflaterDataFormat}.
     */
    @XmlTransient
    public static class Builder implements DataFormatBuilder<GzipDeflaterDataFormat> {

        private String maxDecompressedSize;

        /**
         * Set the maximum decompressed size of the data (in bytes). The default value if not specified corresponds to 1
         * gigabyte. An IOException will be thrown if the decompressed size exceeds this amount. Set to -1 to disable
         * setting a maximum decompressed size.
         *
         * @param maxDecompressedSize the maximum decompressed size of the data (in bytes)
         */
        public Builder maxDecompressedSize(String maxDecompressedSize) {
            this.maxDecompressedSize = maxDecompressedSize;
            return this;
        }

        /**
         * Set the maximum decompressed size of the data (in bytes). The default value if not specified corresponds to 1
         * gigabyte. An IOException will be thrown if the decompressed size exceeds this amount. Set to -1 to disable
         * setting a maximum decompressed size.
         *
         * @param maxDecompressedSize the maximum decompressed size of the data (in bytes)
         */
        public Builder maxDecompressedSize(long maxDecompressedSize) {
            this.maxDecompressedSize = Long.toString(maxDecompressedSize);
            return this;
        }

        @Override
        public GzipDeflaterDataFormat end() {
            return new GzipDeflaterDataFormat(this);
        }
    }
}
