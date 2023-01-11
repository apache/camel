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

import org.apache.camel.spi.Metadata;

/**
 * Marshal and unmarshal Java objects from and to CSV (Comma Separated Values) using UniVocity Parsers.
 */
@Metadata(firstVersion = "2.15.0", label = "dataformat,transformation,csv", title = "uniVocity CSV")
@XmlRootElement(name = "univocityCsv")
@XmlAccessorType(XmlAccessType.FIELD)
public class UniVocityCsvDataFormat extends UniVocityAbstractDataFormat {

    @XmlAttribute
    @Metadata(defaultValue = ",")
    private String delimiter;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String quoteAllFields;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "\"")
    private String quote;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "\"")
    private String quoteEscape;

    public UniVocityCsvDataFormat() {
        super("univocityCsv");
    }

    private UniVocityCsvDataFormat(Builder builder) {
        super("univocityCsv", builder);
        this.delimiter = builder.delimiter;
        this.quoteAllFields = builder.quoteAllFields;
        this.quote = builder.quote;
        this.quoteEscape = builder.quoteEscape;
    }

    public String getQuoteAllFields() {
        return quoteAllFields;
    }

    /**
     * Whether or not all values must be quoted when writing them.
     */
    public void setQuoteAllFields(String quoteAllFields) {
        this.quoteAllFields = quoteAllFields;
    }

    public String getQuote() {
        return quote;
    }

    /**
     * The quote symbol.
     */
    public void setQuote(String quote) {
        this.quote = quote;
    }

    public String getQuoteEscape() {
        return quoteEscape;
    }

    /**
     * The quote escape symbol
     */
    public void setQuoteEscape(String quoteEscape) {
        this.quoteEscape = quoteEscape;
    }

    public String getDelimiter() {
        return delimiter;
    }

    /**
     * The delimiter of values
     */
    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    /**
     * {@code Builder} is a specific builder for {@link UniVocityCsvDataFormat}.
     */
    @XmlTransient
    public static class Builder extends AbstractBuilder<Builder, UniVocityCsvDataFormat> {

        private String delimiter;
        private String quoteAllFields;
        private String quote;
        private String quoteEscape;

        /**
         * Whether or not all values must be quoted when writing them.
         */
        public Builder quoteAllFields(String quoteAllFields) {
            this.quoteAllFields = quoteAllFields;
            return this;
        }

        /**
         * Whether or not all values must be quoted when writing them.
         */
        public Builder quoteAllFields(boolean quoteAllFields) {
            this.quoteAllFields = Boolean.toString(quoteAllFields);
            return this;
        }

        /**
         * The quote symbol.
         */
        public Builder quote(String quote) {
            this.quote = quote;
            return this;
        }

        /**
         * The quote escape symbol
         */
        public Builder quoteEscape(String quoteEscape) {
            this.quoteEscape = quoteEscape;
            return this;
        }

        /**
         * The delimiter of values
         */
        public Builder delimiter(String delimiter) {
            this.delimiter = delimiter;
            return this;
        }

        @Override
        public UniVocityCsvDataFormat end() {
            return new UniVocityCsvDataFormat(this);
        }
    }
}
