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

import com.univocity.parsers.tsv.TsvFormat;
import com.univocity.parsers.tsv.TsvParser;
import com.univocity.parsers.tsv.TsvParserSettings;
import com.univocity.parsers.tsv.TsvWriter;
import com.univocity.parsers.tsv.TsvWriterSettings;
import org.apache.camel.spi.annotations.Dataformat;

/**
 * This class is the data format that uses the TSV uniVocity parser.
 */
@Dataformat("univocity-tsv")
public class UniVocityTsvDataFormat extends AbstractUniVocityDataFormat<TsvFormat, TsvWriterSettings, TsvWriter, TsvParserSettings, TsvParser, UniVocityTsvDataFormat> {
    protected Character escapeChar;

    /**
     * Gets the escape character symbol.
     * If {@code null} then the default format value is used.
     *
     * @return the escape character symbol
     * @see com.univocity.parsers.tsv.TsvFormat#getEscapeChar()
     */
    public Character getEscapeChar() {
        return escapeChar;
    }

    /**
     * Sets the escape character symbol.
     * If {@code null} then the default settings value is used.
     *
     * @param escapeChar the escape character symbol
     * @return current data format instance, fluent API
     * @see com.univocity.parsers.tsv.TsvFormat#setEscapeChar(char)
     */
    public UniVocityTsvDataFormat setEscapeChar(Character escapeChar) {
        this.escapeChar = escapeChar;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected TsvWriterSettings createWriterSettings() {
        return new TsvWriterSettings();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected TsvWriter createWriter(Writer writer, TsvWriterSettings settings) {
        return new TsvWriter(writer, settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected TsvParserSettings createParserSettings() {
        return new TsvParserSettings();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected TsvParser createParser(TsvParserSettings settings) {
        return new TsvParser(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void configureFormat(TsvFormat format) {
        super.configureFormat(format);

        if (escapeChar != null) {
            format.setEscapeChar(escapeChar);
        }
    }

    @Override
    public String getDataFormatName() {
        return "univocity-tsv";
    }
}
