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
@Dataformat("univocityCsv")
public class UniVocityCsvDataFormat
        extends AbstractUniVocityDataFormat<
                CsvFormat, CsvWriterSettings, CsvWriter, CsvParserSettings, CsvParser, UniVocityCsvDataFormat> {

    private Boolean quoteAllFields;
    private Character quote;
    private Character quoteEscape;
    private Character delimiter;

    public Boolean getQuoteAllFields() {
        return quoteAllFields;
    }

    public void setQuoteAllFields(Boolean quoteAllFields) {
        this.quoteAllFields = quoteAllFields;
    }

    public Character getQuote() {
        return quote;
    }

    public void setQuote(Character quote) {
        this.quote = quote;
    }

    public Character getQuoteEscape() {
        return quoteEscape;
    }

    public void setQuoteEscape(Character quoteEscape) {
        this.quoteEscape = quoteEscape;
    }

    public Character getDelimiter() {
        return delimiter;
    }

    public void setDelimiter(Character delimiter) {
        this.delimiter = delimiter;
    }

    @Override
    protected CsvWriterSettings createWriterSettings() {
        return new CsvWriterSettings();
    }

    @Override
    protected void configureWriterSettings(CsvWriterSettings settings) {
        super.configureWriterSettings(settings);

        if (quoteAllFields != null) {
            settings.setQuoteAllFields(quoteAllFields);
        }
    }

    @Override
    protected CsvWriter createWriter(Writer writer, CsvWriterSettings settings) {
        return new CsvWriter(writer, settings);
    }

    @Override
    protected CsvParserSettings createParserSettings() {
        return new CsvParserSettings();
    }

    @Override
    protected void configureParserSettings(CsvParserSettings settings) {
        super.configureParserSettings(settings);

        if (emptyValue != null) {
            settings.setEmptyValue(emptyValue);
        }
    }

    @Override
    protected CsvParser createParser(CsvParserSettings settings) {
        return new CsvParser(settings);
    }

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
        return "univocityCsv";
    }
}
