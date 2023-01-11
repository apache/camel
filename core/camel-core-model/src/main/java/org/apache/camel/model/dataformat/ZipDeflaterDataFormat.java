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
@Metadata(firstVersion = "2.12.0", label = "dataformat,transformation", title = "Zip Deflater")
@XmlRootElement(name = "zipDeflater")
@XmlAccessorType(XmlAccessType.FIELD)
public class ZipDeflaterDataFormat extends DataFormatDefinition {

    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer", defaultValue = "-1", enums = "-1,0,1,2,3,4,5,6,7,8,9")
    private String compressionLevel;

    public ZipDeflaterDataFormat() {
        super("zipDeflater");
    }

    private ZipDeflaterDataFormat(Builder builder) {
        this();
        this.compressionLevel = builder.compressionLevel;
    }

    public String getCompressionLevel() {
        return compressionLevel;
    }

    /**
     * To specify a specific compression between 0-9. -1 is default compression, 0 is no compression, and 9 is the best
     * compression.
     */
    public void setCompressionLevel(String compressionLevel) {
        this.compressionLevel = compressionLevel;
    }

    /**
     * {@code Builder} is a specific builder for {@link ZipDeflaterDataFormat}.
     */
    @XmlTransient
    public static class Builder implements DataFormatBuilder<ZipDeflaterDataFormat> {

        private String compressionLevel;

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

        @Override
        public ZipDeflaterDataFormat end() {
            return new ZipDeflaterDataFormat(this);
        }
    }
}
