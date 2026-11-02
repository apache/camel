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
 * Compress and decompress streams using <code>java.util.zip.Deflater</code> and <code>java.util.zip.Inflater</code>.
 */
@Metadata(firstVersion = "2.12.0", label = "dataformat,transformation", title = "Zip Deflater",
          description = "Compress and decompress streams using java.util.zip.Deflater and java.util.zip.Inflater")
@XmlRootElement(name = "zipDeflater")
@XmlAccessorType(XmlAccessType.FIELD)
public class ZipDeflaterDataFormat extends DataFormatDefinition {

    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer", defaultValue = "-1", enums = "-1,0,1,2,3,4,5,6,7,8,9",
              description = "Compression level to use (0-9). -1 is default compression, 0 is no compression, and 9 is the best compression.")
    private String compressionLevel;

    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Long", defaultValue = "1073741824",
              description = "Set the maximum decompressed size of the data (in bytes). The default value if not specified corresponds to 1 gigabyte."
                            + " An IOException is thrown if the decompressed size exceeds this amount."
                            + " Set to -1 to disable setting a maximum decompressed size.")
    private String maxDecompressedSize;

    public ZipDeflaterDataFormat() {
        super("zipDeflater");
    }

    protected ZipDeflaterDataFormat(ZipDeflaterDataFormat source) {
        super(source);
        this.compressionLevel = source.compressionLevel;
        this.maxDecompressedSize = source.maxDecompressedSize;
    }

    private ZipDeflaterDataFormat(Builder builder) {
        this();
        this.compressionLevel = builder.compressionLevel;
        this.maxDecompressedSize = builder.maxDecompressedSize;
    }

    @Override
    public ZipDeflaterDataFormat copyDefinition() {
        return new ZipDeflaterDataFormat(this);
    }

    public String getCompressionLevel() {
        return compressionLevel;
    }

    public void setCompressionLevel(String compressionLevel) {
        this.compressionLevel = compressionLevel;
    }

    public String getMaxDecompressedSize() {
        return maxDecompressedSize;
    }

    public void setMaxDecompressedSize(String maxDecompressedSize) {
        this.maxDecompressedSize = maxDecompressedSize;
    }

    /**
     * {@code Builder} is a specific builder for {@link ZipDeflaterDataFormat}.
     */
    @XmlTransient
    public static class Builder implements DataFormatBuilder<ZipDeflaterDataFormat> {

        private String compressionLevel;
        private String maxDecompressedSize;

        /**
         * To specify a specific compression between 0-9. -1 is default compression, 0 is no compression, and 9 is the
         * best compression.
         */
        public Builder compressionLevel(String compressionLevel) {
            this.compressionLevel = compressionLevel;
            return this;
        }

        /**
         * To specify a specific compression between 0-9. -1 is default compression, 0 is no compression, and 9 is the
         * best compression.
         */
        public Builder compressionLevel(int compressionLevel) {
            this.compressionLevel = Integer.toString(compressionLevel);
            return this;
        }

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
        public ZipDeflaterDataFormat end() {
            return new ZipDeflaterDataFormat(this);
        }
    }
}
