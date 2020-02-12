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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.Metadata;

/**
 * The LZF data format is a message compression and de-compression format (uses
 * the LZF deflate algorithm).
 */
@Metadata(firstVersion = "2.17.0", label = "dataformat,transformation", title = "LZF Deflate Compression")
@XmlRootElement(name = "lzf")
@XmlAccessorType(XmlAccessType.FIELD)
public class LZFDataFormat extends DataFormatDefinition {
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String usingParallelCompression;

    public LZFDataFormat() {
        super("lzf");
    }

    public String getUsingParallelCompression() {
        return usingParallelCompression;
    }

    /**
     * Enable encoding (compress) using multiple processing cores.
     */
    public void setUsingParallelCompression(String usingParallelCompression) {
        this.usingParallelCompression = usingParallelCompression;
    }
}
