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
@Dataformat("univocityFixed")
public class UniVocityFixedDataFormat
        extends AbstractUniVocityDataFormat<
                FixedWidthFormat,
                FixedWidthWriterSettings,
                FixedWidthWriter,
                FixedWidthParserSettings,
                FixedWidthParser,
                UniVocityFixedDataFormat> {

    private String fieldLengths;
    private Boolean skipTrailingCharsUntilNewline;
    private Boolean recordEndsOnNewline;
    private Character padding;

    public int[] fieldLengthsAsArray() {
        if (fieldLengths == null) {
            return null;
        }
        String[] arr = fieldLengths.split(",");
        int[] answer = new int[arr.length];
        for (int i = 0; i < arr.length; i++) {
            answer[i] = Integer.parseInt(arr[i]);
        }
        return answer;
    }

    public String getFieldLengths() {
        return fieldLengths;
    }

    public void setFieldLengths(String fieldLengths) {
        this.fieldLengths = fieldLengths;
    }

    public Boolean getSkipTrailingCharsUntilNewline() {
        return skipTrailingCharsUntilNewline;
    }

    public void setSkipTrailingCharsUntilNewline(Boolean skipTrailingCharsUntilNewline) {
        this.skipTrailingCharsUntilNewline = skipTrailingCharsUntilNewline;
    }

    public Boolean getRecordEndsOnNewline() {
        return recordEndsOnNewline;
    }

    public void setRecordEndsOnNewline(Boolean recordEndsOnNewline) {
        this.recordEndsOnNewline = recordEndsOnNewline;
    }

    public Character getPadding() {
        return padding;
    }

    public void setPadding(Character padding) {
        this.padding = padding;
    }

    @Override
    protected FixedWidthWriterSettings createWriterSettings() {
        return new FixedWidthWriterSettings(createFixedWidthFields());
    }

    @Override
    protected FixedWidthWriter createWriter(Writer writer, FixedWidthWriterSettings settings) {
        return new FixedWidthWriter(writer, settings);
    }

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

    @Override
    protected FixedWidthParser createParser(FixedWidthParserSettings settings) {
        return new FixedWidthParser(settings);
    }

    @Override
    protected void configureFormat(FixedWidthFormat format) {
        super.configureFormat(format);

        if (padding != null) {
            format.setPadding(padding);
        }
    }

    /**
     * Creates the {@link com.univocity.parsers.fixed.FixedWidthFields} instance based on the headers and field lengths.
     *
     * @return new {@code FixedWidthFields} based on the header and field lengths.
     */
    private FixedWidthFields createFixedWidthFields() {
        // Ensure that the field lengths have been defined.
        if (fieldLengths == null) {
            throw new IllegalArgumentException(
                    "The fieldLengths must have been defined in order to use the fixed-width format.");
        }

        // If there's no header then we only use their length
        if (headers == null || headers.isBlank()) {
            return new FixedWidthFields(fieldLengthsAsArray());
        }

        String[] arr1 = headersAsArray();
        int[] arr2 = fieldLengthsAsArray();

        // Use both headers and field lengths (same size and no duplicate headers)
        if (arr1.length != arr2.length) {
            throw new IllegalArgumentException(
                    "The headers and fieldLengths must have the same number of element in order to use the fixed-width format.");
        }
        LinkedHashMap<String, Integer> fields = new LinkedHashMap<>();
        for (int i = 0; i < arr1.length; i++) {
            fields.put(arr1[i], arr2[i]);
        }
        if (fields.size() != arr1.length) {
            throw new IllegalArgumentException(
                    "The headers cannot have duplicates in order to use the fixed-width format.");
        }
        return new FixedWidthFields(fields);
    }

    @Override
    public String getDataFormatName() {
        return "univocityFixed";
    }
}
