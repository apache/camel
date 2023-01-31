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

import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.builder.DataFormatBuilder;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Compress and decompress messages using <code>java.util.zip.GZIP*Stream</code>.
 */
@Metadata(firstVersion = "2.0.0", label = "dataformat,transformation", title = "GZip Deflater")
@XmlRootElement(name = "gzipDeflater")
public class GzipDeflaterDataFormat extends DataFormatDefinition {

    public GzipDeflaterDataFormat() {
        super("gzipDeflater");
    }

    /**
     * {@code Builder} is a specific builder for {@link GzipDeflaterDataFormat}.
     */
    @XmlTransient
    public static class Builder implements DataFormatBuilder<GzipDeflaterDataFormat> {
        @Override
        public GzipDeflaterDataFormat end() {
            return new GzipDeflaterDataFormat();
        }
    }
}
