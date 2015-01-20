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
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Label;

/**
 * UniVocity CSV data format
 */
@Label("dataformat,transformation")
@XmlRootElement(name = "univocity-csv")
@XmlAccessorType(XmlAccessType.FIELD)
public class UniVocityCsvDataFormat extends UniVocityAbstractDataFormat {
    @XmlAttribute
    protected Boolean quoteAllFields;
    @XmlAttribute
    protected String quote;
    @XmlAttribute
    protected String quoteEscape;
    @XmlAttribute
    protected String delimiter;

    public UniVocityCsvDataFormat() {
        super("univocity-csv");
    }

    public Boolean getQuoteAllFields() {
        return quoteAllFields;
    }

    /**
     * Whether or not all values must be quoted when writing them.
     * <p/>
     * The default value is false
     */
    public void setQuoteAllFields(Boolean quoteAllFields) {
        this.quoteAllFields = quoteAllFields;
    }

    public String getQuote() {
        return quote;
    }

    /**
     * The quote symbol.
     * <p/>
     * The default value is "
     */
    public void setQuote(String quote) {
        this.quote = quote;
    }

    public String getQuoteEscape() {
        return quoteEscape;
    }

    /**
     * The quote escape symbol
     * <p/>
     * The default value is "
     */
    public void setQuoteEscape(String quoteEscape) {
        this.quoteEscape = quoteEscape;
    }

    public String getDelimiter() {
        return delimiter;
    }

    /**
     * The delimiter of values
     * <p/>
     * The default value is ,
     */
    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        super.configureDataFormat(dataFormat, camelContext);

        if (quoteAllFields != null) {
            setProperty(camelContext, dataFormat, "quoteAllFields", quoteAllFields);
        }
        if (quote != null) {
            setProperty(camelContext, dataFormat, "quote", singleCharOf("quote", quote));
        }
        if (quoteEscape != null) {
            setProperty(camelContext, dataFormat, "quoteEscape", singleCharOf("quoteEscape", quoteEscape));
        }
        if (delimiter != null) {
            setProperty(camelContext, dataFormat, "delimiter", singleCharOf("delimiter", delimiter));
        }
    }
}
