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
 * The Flatpack data format is used for working with flat payloads (such as CSV,
 * delimited, or fixed length formats).
 */
@Metadata(firstVersion = "2.1.0", label = "dataformat,transformation,csv", title = "Flatpack")
@XmlRootElement(name = "flatpack")
@XmlAccessorType(XmlAccessType.FIELD)
public class FlatpackDataFormat extends DataFormatDefinition {
    @XmlAttribute
    private String definition;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String fixed;
    @XmlAttribute
    @Metadata(defaultValue = "true", javaType = "java.lang.Boolean")
    private String ignoreFirstRecord;
    @XmlAttribute
    private String textQualifier;
    @XmlAttribute
    @Metadata(defaultValue = ",")
    private String delimiter;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String allowShortLines;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String ignoreExtraColumns;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String parserFactoryRef;

    public FlatpackDataFormat() {
        super("flatpack");
    }

    public String getDefinition() {
        return definition;
    }

    /**
     * The flatpack pzmap configuration file. Can be omitted in simpler
     * situations, but its preferred to use the pzmap.
     */
    public void setDefinition(String definition) {
        this.definition = definition;
    }

    public String getFixed() {
        return fixed;
    }

    /**
     * Delimited or fixed. Is by default false = delimited
     */
    public void setFixed(String fixed) {
        this.fixed = fixed;
    }

    public String getIgnoreFirstRecord() {
        return ignoreFirstRecord;
    }

    /**
     * Whether the first line is ignored for delimited files (for the column
     * headers).
     * <p/>
     * Is by default true.
     */
    public void setIgnoreFirstRecord(String ignoreFirstRecord) {
        this.ignoreFirstRecord = ignoreFirstRecord;
    }

    public String getTextQualifier() {
        return textQualifier;
    }

    /**
     * If the text is qualified with a character.
     * <p/>
     * Uses quote character by default.
     */
    public void setTextQualifier(String textQualifier) {
        this.textQualifier = textQualifier;
    }

    public String getDelimiter() {
        return delimiter;
    }

    /**
     * The delimiter char (could be ; , or similar)
     */
    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    public String getAllowShortLines() {
        return allowShortLines;
    }

    /**
     * Allows for lines to be shorter than expected and ignores the extra
     * characters
     */
    public void setAllowShortLines(String allowShortLines) {
        this.allowShortLines = allowShortLines;
    }

    public String getIgnoreExtraColumns() {
        return ignoreExtraColumns;
    }

    /**
     * Allows for lines to be longer than expected and ignores the extra
     * characters.
     */
    public void setIgnoreExtraColumns(String ignoreExtraColumns) {
        this.ignoreExtraColumns = ignoreExtraColumns;
    }

    public String getParserFactoryRef() {
        return parserFactoryRef;
    }

    /**
     * References to a custom parser factory to lookup in the registry
     */
    public void setParserFactoryRef(String parserFactoryRef) {
        this.parserFactoryRef = parserFactoryRef;
    }

}
