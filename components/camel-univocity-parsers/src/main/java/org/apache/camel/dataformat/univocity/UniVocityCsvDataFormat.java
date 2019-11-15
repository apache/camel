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
package org.apache.camel.dataformat.univocity;

import java.io.Writer;

import com.univocity.parsers.csv.CsvFormat;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;
import org.apache.camel.spi.annotations.Dataformat;

/**
 * This class is the data format that uses the CSV uniVocity parser.
 */
@Dataformat("univocity-csv")
public class UniVocityCsvDataFormat extends AbstractUniVocityDataFormat<CsvFormat, CsvWriterSettings, CsvWriter, CsvParserSettings, CsvParser, UniVocityCsvDataFormat> {
    protected Boolean quoteAllFields;
    protected Character quote;
    protected Character quoteEscape;
    protected Character delimiter;

    /**
     * Gets whether or not all fields must be quoted.
     * If {@code null} then the default settings value is used.
     *
     * @return whether or not all fields must be quoted
     * @see com.univocity.parsers.csv.CsvWriterSettings#getQuoteAllFields()
     */
    public Boolean getQuoteAllFields() {
        return quoteAllFields;
    }

    /**
     * Gets whether or not all fields must be quoted.
     * If {@code null} then the default settings value is used.
     *
     * @param quoteAllFields whether or not all fields must be quoted
     * @return current data format instance, fluent API
     * @see com.univocity.parsers.csv.CsvWriterSettings#setQuoteAllFields(boolean)
     */
    public UniVocityCsvDataFormat setQuoteAllFields(Boolean quoteAllFields) {
        this.quoteAllFields = quoteAllFields;
        return this;
    }

    /**
     * Gets the quote symbol.
     * If {@code null} then the default format value is used.
     *
     * @return the quote symbol
     * @see com.univocity.parsers.csv.CsvFormat#getQuote()
     */
    public Character getQuote() {
        return quote;
    }

    /**
     * Sets the quote symbol.
     * If {@code null} then the default format value is used.
     *
     * @param quote the quote symbol
     * @return current data format instance, fluent API
     * @see com.univocity.parsers.csv.CsvFormat#setQuote(char)
     */
    public UniVocityCsvDataFormat setQuote(Character quote) {
        this.quote = quote;
        return this;
    }

    /**
     * Gets the quote escape symbol.
     * If {@code null} then the default format value is used.
     *
     * @return the quote escape symbol
     * @see com.univocity.parsers.csv.CsvFormat#getQuoteEscape()
     */
    public Character getQuoteEscape() {
        return quoteEscape;
    }

    /**
     * Sets the quote escape symbol.
     * If {@code null} then the default format value is used.
     *
     * @param quoteEscape the quote escape symbol
     * @return current data format instance, fluent API
     * @see com.univocity.parsers.csv.CsvFormat#setQuoteEscape(char)
     */
    public UniVocityCsvDataFormat setQuoteEscape(Character quoteEscape) {
        this.quoteEscape = quoteEscape;
        return this;
    }

    /**
     * Gets the delimiter symbol.
     * If {@code null} then the default format value is used.
     *
     * @return the delimiter symbol
     * @see com.univocity.parsers.csv.CsvFormat#getDelimiter()
     */
    public Character getDelimiter() {
        return delimiter;
    }

    /**
     * Sets the delimiter symbol.
     * If {@code null} then the default format value is used.
     *
     * @param delimiter the delimiter symbol
     * @return current data format instance, fluent API
     * @see com.univocity.parsers.csv.CsvFormat#setDelimiter(char)
     */
    public UniVocityCsvDataFormat setDelimiter(Character delimiter) {
        this.delimiter = delimiter;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected CsvWriterSettings createWriterSettings() {
        return new CsvWriterSettings();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void configureWriterSettings(CsvWriterSettings settings) {
        super.configureWriterSettings(settings);

        if (quoteAllFields != null) {
            settings.setQuoteAllFields(quoteAllFields);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected CsvWriter createWriter(Writer writer, CsvWriterSettings settings) {
        return new CsvWriter(writer, settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected CsvParserSettings createParserSettings() {
        return new CsvParserSettings();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void configureParserSettings(CsvParserSettings settings) {
        super.configureParserSettings(settings);

        if (emptyValue != null) {
            settings.setEmptyValue(emptyValue);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected CsvParser createParser(CsvParserSettings settings) {
        return new CsvParser(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void configureFormat(CsvFormat format) {
        super.configureFormat(format);

        if (quote != null) {
            format.setQuote(quote);
        }
        if (quoteEscape != null) {
            format.setQuoteEscape(quoteEscape);
        }
        if (delimiter != null) {
            format.setDelimiter(delimiter);
        }
    }

    @Override
    public String getDataFormatName() {
        return "univocity-csv";
    }
}
