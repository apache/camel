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
import java.util.LinkedHashMap;

import com.univocity.parsers.fixed.FixedWidthFields;
import com.univocity.parsers.fixed.FixedWidthFormat;
import com.univocity.parsers.fixed.FixedWidthParser;
import com.univocity.parsers.fixed.FixedWidthParserSettings;
import com.univocity.parsers.fixed.FixedWidthWriter;
import com.univocity.parsers.fixed.FixedWidthWriterSettings;
import org.apache.camel.spi.annotations.Dataformat;

/**
 * This class is the data format that uses the fixed-width uniVocity parser.
 */
@Dataformat("univocity-fixed")
public class UniVocityFixedWidthDataFormat extends AbstractUniVocityDataFormat<FixedWidthFormat, FixedWidthWriterSettings,
        FixedWidthWriter, FixedWidthParserSettings, FixedWidthParser, UniVocityFixedWidthDataFormat> {
    protected int[] fieldLengths;
    protected Boolean skipTrailingCharsUntilNewline;
    protected Boolean recordEndsOnNewline;
    protected Character padding;

    /**
     * Gets the field lengths.
     * It's used to construct uniVocity {@link com.univocity.parsers.fixed.FixedWidthFields} instance.
     *
     * @return the field lengths
     */
    public int[] getFieldLengths() {
        return fieldLengths;
    }

    /**
     * Sets the field lengths
     * It's used to construct uniVocity {@link com.univocity.parsers.fixed.FixedWidthFields} instance.
     *
     * @param fieldLengths the field length
     * @return current data format instance, fluent API
     */
    public UniVocityFixedWidthDataFormat setFieldLengths(int[] fieldLengths) {
        this.fieldLengths = fieldLengths;
        return this;
    }

    /**
     * Gets whether or not trailing characters until new line must be ignored.
     *
     * @return whether or not trailing characters until new line must be ignored
     * @see com.univocity.parsers.fixed.FixedWidthParserSettings#getSkipTrailingCharsUntilNewline()
     */
    public Boolean getSkipTrailingCharsUntilNewline() {
        return skipTrailingCharsUntilNewline;
    }

    /**
     * Sets whether or not trailing characters until new line must be ignored.
     *
     * @param skipTrailingCharsUntilNewline whether or not trailing characters until new line must be ignored
     * @return current data format instance, fluent API
     * @see com.univocity.parsers.fixed.FixedWidthParserSettings#setSkipTrailingCharsUntilNewline(boolean)
     */
    public UniVocityFixedWidthDataFormat setSkipTrailingCharsUntilNewline(Boolean skipTrailingCharsUntilNewline) {
        this.skipTrailingCharsUntilNewline = skipTrailingCharsUntilNewline;
        return this;
    }

    /**
     * Gets whether or not the record ends on new line.
     *
     * @return whether or not the record ends on new line
     * @see com.univocity.parsers.fixed.FixedWidthParserSettings#getRecordEndsOnNewline()
     */
    public Boolean getRecordEndsOnNewline() {
        return recordEndsOnNewline;
    }

    /**
     * Sets whether or not the record ends on new line
     *
     * @param recordEndsOnNewline whether or not the record ends on new line
     * @return current data format instance, fluent API
     * @see com.univocity.parsers.fixed.FixedWidthParserSettings#setRecordEndsOnNewline(boolean)
     */
    public UniVocityFixedWidthDataFormat setRecordEndsOnNewline(Boolean recordEndsOnNewline) {
        this.recordEndsOnNewline = recordEndsOnNewline;
        return this;
    }

    /**
     * Gets the padding symbol.
     * If {@code null} then the default format value is used.
     *
     * @return the padding symbol
     * @see com.univocity.parsers.fixed.FixedWidthFormat#getPadding()
     */
    public Character getPadding() {
        return padding;
    }

    /**
     * Sets the padding symbol.
     * If {@code null} then the default format value is used.
     *
     * @param padding the padding symbol
     * @return current data format instance, fluent API
     * @see com.univocity.parsers.fixed.FixedWidthFormat#setPadding(char)
     */
    public UniVocityFixedWidthDataFormat setPadding(Character padding) {
        this.padding = padding;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected FixedWidthWriterSettings createWriterSettings() {
        return new FixedWidthWriterSettings(createFixedWidthFields());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected FixedWidthWriter createWriter(Writer writer, FixedWidthWriterSettings settings) {
        return new FixedWidthWriter(writer, settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected FixedWidthParserSettings createParserSettings() {
        return new FixedWidthParserSettings(createFixedWidthFields());
    }

    @Override
    protected void configureParserSettings(FixedWidthParserSettings settings) {
        super.configureParserSettings(settings);

        if (skipTrailingCharsUntilNewline != null) {
            settings.setSkipTrailingCharsUntilNewline(skipTrailingCharsUntilNewline);
        }
        if (recordEndsOnNewline != null) {
            settings.setRecordEndsOnNewline(true);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected FixedWidthParser createParser(FixedWidthParserSettings settings) {
        return new FixedWidthParser(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void configureFormat(FixedWidthFormat format) {
        super.configureFormat(format);

        if (padding != null) {
            format.setPadding(padding);
        }
    }

    /**
     * Creates the {@link com.univocity.parsers.fixed.FixedWidthFields} instance based on the headers and field
     * lengths.
     *
     * @return new {@code FixedWidthFields} based on the header and field lengths.
     */
    private FixedWidthFields createFixedWidthFields() {
        // Ensure that the field lengths have been defined.
        if (fieldLengths == null) {
            throw new IllegalArgumentException("The fieldLengths must have been defined in order to use the fixed-width format.");
        }

        // If there's no header then we only use their length
        if (headers == null) {
            return new FixedWidthFields(fieldLengths);
        }

        // Use both headers and field lengths (same size and no duplicate headers)
        if (fieldLengths.length != headers.length) {
            throw new IllegalArgumentException("The headers and fieldLengths must have the same number of element in order to use the fixed-width format.");
        }
        LinkedHashMap<String, Integer> fields = new LinkedHashMap<>();
        for (int i = 0; i < headers.length; i++) {
            fields.put(headers[i], fieldLengths[i]);
        }
        if (fields.size() != headers.length) {
            throw new IllegalArgumentException("The headers cannot have duplicates in order to use the fixed-width format.");
        }
        return new FixedWidthFields(fields);
    }

    @Override
    public String getDataFormatName() {
        return "univocity-fixed";
    }
}
