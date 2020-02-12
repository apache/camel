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

import org.apache.camel.spi.Metadata;

/**
 * The uniVocity Fixed Length data format is used for working with fixed length
 * flat payloads.
 */
@Metadata(firstVersion = "2.15.0", label = "dataformat,transformation,csv", title = "uniVocity Fixed Length")
@XmlRootElement(name = "univocity-fixed")
@XmlAccessorType(XmlAccessType.FIELD)
public class UniVocityFixedWidthDataFormat extends UniVocityAbstractDataFormat {
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String skipTrailingCharsUntilNewline;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String recordEndsOnNewline;
    @XmlAttribute
    private String padding;

    public UniVocityFixedWidthDataFormat() {
        super("univocity-fixed");
    }

    public String getSkipTrailingCharsUntilNewline() {
        return skipTrailingCharsUntilNewline;
    }

    /**
     * Whether or not the trailing characters until new line must be ignored.
     * <p/>
     * The default value is false
     */
    public void setSkipTrailingCharsUntilNewline(String skipTrailingCharsUntilNewline) {
        this.skipTrailingCharsUntilNewline = skipTrailingCharsUntilNewline;
    }

    public String getRecordEndsOnNewline() {
        return recordEndsOnNewline;
    }

    /**
     * Whether or not the record ends on new line.
     * <p/>
     * The default value is false
     */
    public void setRecordEndsOnNewline(String recordEndsOnNewline) {
        this.recordEndsOnNewline = recordEndsOnNewline;
    }

    public String getPadding() {
        return padding;
    }

    /**
     * The padding character.
     * <p/>
     * The default value is a space
     */
    public void setPadding(String padding) {
        this.padding = padding;
    }

}
