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
 * Marshal and unmarshal Java lists and maps to/from flat files (such as CSV, delimited, or fixed length formats) using
 * <a href="https://github.com/appendium/flatpack">Flatpack</a> library.
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
    @Metadata(defaultValue = ",")
    private String delimiter;
    @XmlAttribute
    @Metadata(defaultValue = "true", javaType = "java.lang.Boolean")
    private String ignoreFirstRecord;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String allowShortLines;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String ignoreExtraColumns;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String textQualifier;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String parserFactoryRef;

    public FlatpackDataFormat() {
        super("flatpack");
    }

    private FlatpackDataFormat(Builder builder) {
        this();
        this.definition = builder.definition;
        this.fixed = builder.fixed;
        this.delimiter = builder.delimiter;
        this.ignoreFirstRecord = builder.ignoreFirstRecord;
        this.allowShortLines = builder.allowShortLines;
        this.ignoreExtraColumns = builder.ignoreExtraColumns;
        this.textQualifier = builder.textQualifier;
        this.parserFactoryRef = builder.parserFactoryRef;
    }

    public String getDefinition() {
        return definition;
    }

    /**
     * The flatpack pzmap configuration file. Can be omitted in simpler situations, but its preferred to use the pzmap.
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
     * Whether the first line is ignored for delimited files (for the column headers).
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
     * Allows for lines to be shorter than expected and ignores the extra characters
     */
    public void setAllowShortLines(String allowShortLines) {
        this.allowShortLines = allowShortLines;
    }

    public String getIgnoreExtraColumns() {
        return ignoreExtraColumns;
    }

    /**
     * Allows for lines to be longer than expected and ignores the extra characters.
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

    /**
     * {@code Builder} is a specific builder for {@link FlatpackDataFormat}.
     */
    @XmlTransient
    public static class Builder implements DataFormatBuilder<FlatpackDataFormat> {

        private String definition;
        private String fixed;
        private String delimiter;
        private String ignoreFirstRecord;
        private String allowShortLines;
        private String ignoreExtraColumns;
        private String textQualifier;
        private String parserFactoryRef;

        /**
         * The flatpack pzmap configuration file. Can be omitted in simpler situations, but its preferred to use the
         * pzmap.
         */
        public Builder definition(String definition) {
            this.definition = definition;
            return this;
        }

        /**
         * Delimited or fixed. Is by default false = delimited
         */
        public Builder fixed(String fixed) {
            this.fixed = fixed;
            return this;
        }

        /**
         * Delimited or fixed. Is by default false = delimited
         */
        public Builder fixed(boolean fixed) {
            this.fixed = Boolean.toString(fixed);
            return this;
        }

        /**
         * Whether the first line is ignored for delimited files (for the column headers).
         * <p/>
         * Is by default true.
         */
        public Builder ignoreFirstRecord(String ignoreFirstRecord) {
            this.ignoreFirstRecord = ignoreFirstRecord;
            return this;
        }

        /**
         * Whether the first line is ignored for delimited files (for the column headers).
         * <p/>
         * Is by default true.
         */
        public Builder ignoreFirstRecord(boolean ignoreFirstRecord) {
            this.ignoreFirstRecord = Boolean.toString(ignoreFirstRecord);
            return this;
        }

        /**
         * If the text is qualified with a character.
         * <p/>
         * Uses quote character by default.
         */
        public Builder textQualifier(String textQualifier) {
            this.textQualifier = textQualifier;
            return this;
        }

        /**
         * The delimiter char (could be ; , or similar)
         */
        public Builder delimiter(String delimiter) {
            this.delimiter = delimiter;
            return this;
        }

        /**
         * Allows for lines to be shorter than expected and ignores the extra characters
         */
        public Builder allowShortLines(String allowShortLines) {
            this.allowShortLines = allowShortLines;
            return this;
        }

        /**
         * Allows for lines to be shorter than expected and ignores the extra characters
         */
        public Builder allowShortLines(boolean allowShortLines) {
            this.allowShortLines = Boolean.toString(allowShortLines);
            return this;
        }

        /**
         * Allows for lines to be longer than expected and ignores the extra characters.
         */
        public Builder ignoreExtraColumns(String ignoreExtraColumns) {
            this.ignoreExtraColumns = ignoreExtraColumns;
            return this;
        }

        /**
         * Allows for lines to be longer than expected and ignores the extra characters.
         */
        public Builder ignoreExtraColumns(boolean ignoreExtraColumns) {
            this.ignoreExtraColumns = Boolean.toString(ignoreExtraColumns);
            return this;
        }

        /**
         * References to a custom parser factory to lookup in the registry
         */
        public Builder parserFactoryRef(String parserFactoryRef) {
            this.parserFactoryRef = parserFactoryRef;
            return this;
        }

        @Override
        public FlatpackDataFormat end() {
            return new FlatpackDataFormat(this);
        }
    }
}
