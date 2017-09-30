/**
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

import org.apache.camel.CamelContext;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Metadata;

/**
 * The Base64 data format is used for base64 encoding and decoding.
 *
 * @version 
 */
@Metadata(firstVersion = "2.11.0", label = "dataformat,transformation", title = "Base64")
@XmlRootElement(name = "base64")
@XmlAccessorType(XmlAccessType.FIELD)
public class Base64DataFormat extends DataFormatDefinition {

    @XmlAttribute @Metadata(defaultValue = "76")
    private Integer lineLength;
    @XmlAttribute
    private String lineSeparator;
    @XmlAttribute
    private Boolean urlSafe;

    public Base64DataFormat() {
        super("base64");
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        if (getLineLength() != null) {
            setProperty(camelContext, dataFormat, "lineLength", getLineLength());
        }
        if (getUrlSafe() != null) {
            setProperty(camelContext, dataFormat, "urlSafe", getUrlSafe());
        }
        if (getLineSeparator() != null) {
            // line separator must be a byte[]
            byte[] bytes = getLineSeparator().getBytes();
            setProperty(camelContext, dataFormat, "lineSeparator", bytes);
        }
    }

    public Integer getLineLength() {
        return lineLength;
    }

    /**
     * To specific a maximum line length for the encoded data.
     * <p/>
     * By default 76 is used.
     */
    public void setLineLength(Integer lineLength) {
        this.lineLength = lineLength;
    }

    public String getLineSeparator() {
        return lineSeparator;
    }

    /**
     * The line separators to use.
     * <p/>
     * Uses new line characters (CRLF) by default.
     */
    public void setLineSeparator(String lineSeparator) {
        this.lineSeparator = lineSeparator;
    }

    public Boolean getUrlSafe() {
        return urlSafe;
    }

    /**
     * Instead of emitting '+' and '/' we emit '-' and '_' respectively.
     * urlSafe is only applied to encode operations. Decoding seamlessly handles both modes.
     * Is by default false.
     */
    public void setUrlSafe(Boolean urlSafe) {
        this.urlSafe = urlSafe;
    }
}
