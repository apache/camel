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
 * The uniVocity CSV data format is used for working with CSV (Comma Separated
 * Values) flat payloads.
 */
@Metadata(firstVersion = "2.15.0", label = "dataformat,transformation,csv", title = "uniVocity CSV")
@XmlRootElement(name = "univocity-csv")
@XmlAccessorType(XmlAccessType.FIELD)
public class UniVocityCsvDataFormat extends UniVocityAbstractDataFormat {
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String quoteAllFields;
    @XmlAttribute
    @Metadata(defaultValue = "\"")
    private String quote;
    @XmlAttribute
    @Metadata(defaultValue = "\"")
    private String quoteEscape;
    @XmlAttribute
    @Metadata(defaultValue = ",")
    private String delimiter;

    public UniVocityCsvDataFormat() {
        super("univocity-csv");
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

}
