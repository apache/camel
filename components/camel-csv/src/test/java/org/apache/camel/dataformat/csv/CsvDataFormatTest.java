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
package org.apache.camel.dataformat.csv;

import java.io.IOException;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.QuoteMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This class tests the creation of the proper {@link org.apache.commons.csv.CSVFormat} based on the properties of
 * {@link org.apache.camel.dataformat.csv.CsvDataFormat}. It doesn't test the marshalling and unmarshalling based on the
 * CSV format.
 */
public class CsvDataFormatTest {
    @Test
    void shouldUseDefaultFormat() throws IOException {
        try (CsvDataFormat defDataFormat = new CsvDataFormat(); CsvDataFormat dataFormat = defDataFormat) {
            dataFormat.start();
            // Properly initialized
            assertSame(CSVFormat.DEFAULT, dataFormat.getCsvFormat());
            // Properly used
            assertEquals(CSVFormat.DEFAULT, dataFormat.getActiveFormat());
        }
    }

    @Test
    void shouldUseFormatFromConstructor() throws IOException {
        try (CsvDataFormat dataFormat = new CsvDataFormat(CSVFormat.EXCEL)) {
            dataFormat.start();
            // Properly initialized
            assertSame(CSVFormat.EXCEL, dataFormat.getCsvFormat());
            // Properly used
            assertEquals(CSVFormat.EXCEL, dataFormat.getActiveFormat());
        }
    }

    @Test
    void shouldUseSpecifiedFormat() throws IOException {
        try (CsvDataFormat dataFormat = new CsvDataFormat(CSVFormat.MYSQL)) {
            dataFormat.start();
            // Properly saved
            assertSame(CSVFormat.MYSQL, dataFormat.getCsvFormat());
            // Properly used
            assertEquals(CSVFormat.MYSQL, dataFormat.getActiveFormat());
        }
    }

    @Test
    void shouldDefineFormatByName() throws IOException {
        try (CsvDataFormat dataFormat = new CsvDataFormat(CSVFormat.EXCEL)) {
            dataFormat.start();
            // Properly saved
            assertSame(CSVFormat.EXCEL, dataFormat.getCsvFormat());
            // Properly used
            assertEquals(CSVFormat.EXCEL, dataFormat.getActiveFormat());
        }
    }

    @Test
    void shouldDisableCommentMarker() throws IOException {
        try (CsvDataFormat defDataFormat = new CsvDataFormat(); CsvDataFormat dataFormat = defDataFormat
                .setCommentMarkerDisabled(true)
                .setCommentMarker('c')) {
            dataFormat.start();
            // Properly saved
            assertSame(CSVFormat.DEFAULT, dataFormat.getCsvFormat());
            assertTrue(dataFormat.isCommentMarkerDisabled());
            assertEquals(Character.valueOf('c'), dataFormat.getCommentMarker());
            // Properly used
            assertNull(dataFormat.getActiveFormat().getCommentMarker());
        }
    }

    @Test
    void shouldOverrideCommentMarker() throws IOException {
        try (CsvDataFormat defDataFormat = new CsvDataFormat(); CsvDataFormat dataFormat = defDataFormat
                .setCommentMarker('c')) {
            dataFormat.start();

            // Properly saved
            assertSame(CSVFormat.DEFAULT, dataFormat.getCsvFormat());
            assertEquals(Character.valueOf('c'), dataFormat.getCommentMarker());

            // Properly used
            assertEquals(Character.valueOf('c'), dataFormat.getActiveFormat().getCommentMarker());
        }
    }

    @Test
    void shouldOverrideDelimiter() throws IOException {
        try (CsvDataFormat defDataFormat = new CsvDataFormat(); CsvDataFormat dataFormat = defDataFormat
                .setDelimiter('d')) {
            dataFormat.start();

            // Properly saved
            assertSame(CSVFormat.DEFAULT, dataFormat.getCsvFormat());
            assertEquals(Character.valueOf('d'), dataFormat.getDelimiter());

            // Properly used
            assertEquals(String.valueOf('d'), dataFormat.getActiveFormat().getDelimiterString());
        }
    }

    @Test
    void shouldDisableEscape() throws IOException {
        try (CsvDataFormat defDataFormat = new CsvDataFormat(); CsvDataFormat dataFormat = defDataFormat
                .setEscapeDisabled(true)
                .setEscape('e')) {
            dataFormat.start();

            // Properly saved
            assertSame(CSVFormat.DEFAULT, dataFormat.getCsvFormat());
            assertTrue(dataFormat.isEscapeDisabled());
            assertEquals(Character.valueOf('e'), dataFormat.getEscape());

            // Properly used
            assertNull(dataFormat.getActiveFormat().getEscapeCharacter());
        }
    }

    @Test
    void shouldOverrideEscape() throws IOException {
        try (CsvDataFormat defDataFormat = new CsvDataFormat(); CsvDataFormat dataFormat = defDataFormat
                .setEscape('e')) {
            dataFormat.start();

            // Properly saved
            assertSame(CSVFormat.DEFAULT, dataFormat.getCsvFormat());
            assertEquals(Character.valueOf('e'), dataFormat.getEscape());

            // Properly used
            assertEquals(Character.valueOf('e'), dataFormat.getActiveFormat().getEscapeCharacter());
        }
    }

    @Test
    void shouldDisableHeader() throws IOException {
        try (CsvDataFormat defDataFormat = new CsvDataFormat(); CsvDataFormat dataFormat = defDataFormat
                .setHeaderDisabled(true)
                .setHeader(new String[] { "a", "b", "c" })) {
            dataFormat.start();

            // Properly saved
            assertSame(CSVFormat.DEFAULT, dataFormat.getCsvFormat());
            assertTrue(dataFormat.isHeaderDisabled());
            assertEquals("a,b,c", dataFormat.getHeader());

            // Properly used
            assertNull(dataFormat.getActiveFormat().getHeader());
        }
    }

    @Test
    void shouldOverrideHeader() throws IOException {
        try (CsvDataFormat defDataFormat = new CsvDataFormat(); CsvDataFormat dataFormat = defDataFormat
                .setHeader("a,b,c")) {
            dataFormat.start();

            // Properly saved
            assertSame(CSVFormat.DEFAULT, dataFormat.getCsvFormat());
            assertEquals("a,b,c", dataFormat.getHeader());

            // Properly used
            assertArrayEquals(new String[] { "a", "b", "c" }, dataFormat.getActiveFormat().getHeader());
        }
    }

    @Test
    void shouldAllowMissingColumnNames() throws IOException {
        try (CsvDataFormat defDataFormat = new CsvDataFormat(); CsvDataFormat dataFormat = defDataFormat
                .setAllowMissingColumnNames(true)) {
            dataFormat.start();

            // Properly saved
            assertSame(CSVFormat.DEFAULT, dataFormat.getCsvFormat());
            assertEquals(Boolean.TRUE, dataFormat.getAllowMissingColumnNames());

            // Properly used
            assertTrue(dataFormat.getActiveFormat().getAllowMissingColumnNames());
        }
    }

    @Test
    void shouldNotAllowMissingColumnNames() throws IOException {
        try (CsvDataFormat defDataFormat = new CsvDataFormat(); CsvDataFormat dataFormat = defDataFormat
                .setAllowMissingColumnNames(false)) {
            dataFormat.start();

            // Properly saved
            assertSame(CSVFormat.DEFAULT, dataFormat.getCsvFormat());
            assertEquals(Boolean.FALSE, dataFormat.getAllowMissingColumnNames());

            // Properly used
            assertFalse(dataFormat.getActiveFormat().getAllowMissingColumnNames());
        }
    }

    @Test
    void shouldIgnoreEmptyLines() throws IOException {
        try (CsvDataFormat defDataFormat = new CsvDataFormat(); CsvDataFormat dataFormat = defDataFormat
                .setIgnoreEmptyLines(true)) {
            dataFormat.start();

            // Properly saved
            assertSame(CSVFormat.DEFAULT, dataFormat.getCsvFormat());
            assertEquals(Boolean.TRUE, dataFormat.getIgnoreEmptyLines());

            // Properly used
            assertTrue(dataFormat.getActiveFormat().getIgnoreEmptyLines());
        }
    }

    @Test
    void shouldNotIgnoreEmptyLines() throws IOException {
        try (CsvDataFormat defDataFormat = new CsvDataFormat(); CsvDataFormat dataFormat = defDataFormat
                .setIgnoreEmptyLines(false)) {
            dataFormat.start();

            // Properly saved
            assertSame(CSVFormat.DEFAULT, dataFormat.getCsvFormat());
            assertEquals(Boolean.FALSE, dataFormat.getIgnoreEmptyLines());

            // Properly used
            assertFalse(dataFormat.getActiveFormat().getIgnoreEmptyLines());
        }
    }

    @Test
    void shouldIgnoreSurroundingSpaces() throws IOException {
        try (CsvDataFormat defDataFormat = new CsvDataFormat(); CsvDataFormat dataFormat = defDataFormat
                .setIgnoreSurroundingSpaces(true)) {
            dataFormat.start();

            // Properly saved
            assertSame(CSVFormat.DEFAULT, dataFormat.getCsvFormat());
            assertEquals(Boolean.TRUE, dataFormat.getIgnoreSurroundingSpaces());

            // Properly used
            assertTrue(dataFormat.getActiveFormat().getIgnoreSurroundingSpaces());
        }
    }

    @Test
    void shouldNotIgnoreSurroundingSpaces() throws IOException {
        try (CsvDataFormat defDataFormat = new CsvDataFormat(); CsvDataFormat dataFormat = defDataFormat
                .setIgnoreSurroundingSpaces(false)) {
            dataFormat.start();

            // Properly saved
            assertSame(CSVFormat.DEFAULT, dataFormat.getCsvFormat());
            assertEquals(Boolean.FALSE, dataFormat.getIgnoreSurroundingSpaces());

            // Properly used
            assertFalse(dataFormat.getActiveFormat().getIgnoreSurroundingSpaces());
        }
    }

    @Test
    void shouldDisableNullString() throws IOException {
        try (CsvDataFormat defDataFormat = new CsvDataFormat(); CsvDataFormat dataFormat = defDataFormat
                .setNullStringDisabled(true)
                .setNullString("****")) {
            dataFormat.start();

            // Properly saved
            assertSame(CSVFormat.DEFAULT, dataFormat.getCsvFormat());
            assertTrue(dataFormat.isNullStringDisabled());
            assertEquals("****", dataFormat.getNullString());

            // Properly used
            assertNull(dataFormat.getActiveFormat().getNullString());
        }
    }

    @Test
    void shouldOverrideNullString() throws IOException {
        try (CsvDataFormat defDataFormat = new CsvDataFormat(); CsvDataFormat dataFormat = defDataFormat
                .setNullString("****")) {
            dataFormat.start();

            // Properly saved
            assertSame(CSVFormat.DEFAULT, dataFormat.getCsvFormat());
            assertEquals("****", dataFormat.getNullString());

            // Properly used
            assertEquals("****", dataFormat.getActiveFormat().getNullString());
        }
    }

    @Test
    void shouldDisableQuote() throws IOException {
        try (CsvDataFormat defDataFormat = new CsvDataFormat(); CsvDataFormat dataFormat = defDataFormat
                .setQuoteDisabled(true)
                .setQuote('q')) {
            dataFormat.start();

            // Properly saved
            assertSame(CSVFormat.DEFAULT, dataFormat.getCsvFormat());
            assertTrue(dataFormat.isQuoteDisabled());
            assertEquals(Character.valueOf('q'), dataFormat.getQuote());

            // Properly used
            assertNull(dataFormat.getActiveFormat().getQuoteCharacter());
        }
    }

    @Test
    void shouldOverrideQuote() throws IOException {
        try (CsvDataFormat defDataFormat = new CsvDataFormat(); CsvDataFormat dataFormat = defDataFormat
                .setQuote('q')) {
            dataFormat.start();

            // Properly saved
            assertSame(CSVFormat.DEFAULT, dataFormat.getCsvFormat());
            assertEquals(Character.valueOf('q'), dataFormat.getQuote());

            // Properly used
            assertEquals(Character.valueOf('q'), dataFormat.getActiveFormat().getQuoteCharacter());
        }
    }

    @Test
    void shouldOverrideQuoteMode() throws IOException {
        try (CsvDataFormat defDataFormat = new CsvDataFormat(); CsvDataFormat dataFormat = defDataFormat
                .setQuoteMode(QuoteMode.ALL)) {
            dataFormat.start();

            // Properly saved
            assertSame(CSVFormat.DEFAULT, dataFormat.getCsvFormat());
            assertEquals(QuoteMode.ALL, dataFormat.getQuoteMode());

            // Properly used
            assertEquals(QuoteMode.ALL, dataFormat.getActiveFormat().getQuoteMode());
        }
    }

    @Test
    void shouldDisableRecordSeparator() throws IOException {
        try (CsvDataFormat defDataFormat = new CsvDataFormat(); CsvDataFormat dataFormat = defDataFormat
                .setRecordSeparatorDisabled(true)
                .setRecordSeparator("separator")) {
            dataFormat.start();

            // Properly saved
            assertSame(CSVFormat.DEFAULT, dataFormat.getCsvFormat());
            assertTrue(dataFormat.isRecordSeparatorDisabled());
            assertEquals("separator", dataFormat.getRecordSeparator());

            // Properly used
            assertNull(dataFormat.getActiveFormat().getRecordSeparator());
        }
    }

    @Test
    void shouldOverrideRecordSeparator() throws IOException {
        try (CsvDataFormat defDataFormat = new CsvDataFormat(); CsvDataFormat dataFormat = defDataFormat
                .setRecordSeparator("separator")) {
            dataFormat.start();

            // Properly saved
            assertSame(CSVFormat.DEFAULT, dataFormat.getCsvFormat());
            assertEquals("separator", dataFormat.getRecordSeparator());

            // Properly used
            assertEquals("separator", dataFormat.getActiveFormat().getRecordSeparator());
        }
    }

    @Test
    void shouldSkipHeaderRecord() throws IOException {
        try (CsvDataFormat defDataFormat = new CsvDataFormat(); CsvDataFormat dataFormat = defDataFormat
                .setSkipHeaderRecord(true)) {
            dataFormat.start();

            // Properly saved
            assertSame(CSVFormat.DEFAULT, dataFormat.getCsvFormat());
            assertEquals(Boolean.TRUE, dataFormat.getSkipHeaderRecord());

            // Properly used
            assertTrue(dataFormat.getActiveFormat().getSkipHeaderRecord());
        }
    }

    @Test
    void shouldNotSkipHeaderRecord() throws IOException {
        try (CsvDataFormat defDataFormat = new CsvDataFormat(); CsvDataFormat dataFormat = defDataFormat
                .setSkipHeaderRecord(false)) {
            dataFormat.start();

            // Properly saved
            assertSame(CSVFormat.DEFAULT, dataFormat.getCsvFormat());
            assertEquals(Boolean.FALSE, dataFormat.getSkipHeaderRecord());

            // Properly used
            assertFalse(dataFormat.getActiveFormat().getSkipHeaderRecord());
        }
    }

    @Test
    void shouldHandleLazyLoad() throws IOException {
        try (CsvDataFormat defDataFormat = new CsvDataFormat(); CsvDataFormat dataFormat = defDataFormat
                .setLazyLoad(true)) {
            dataFormat.start();

            // Properly saved
            assertSame(CSVFormat.DEFAULT, dataFormat.getCsvFormat());
            assertTrue(dataFormat.isLazyLoad());

            // Properly used (it doesn't modify the format)
            assertEquals(CSVFormat.DEFAULT, dataFormat.getActiveFormat());
        }
    }

    @Test
    void shouldHandleUseMaps() throws IOException {
        try (CsvDataFormat defDataFormat = new CsvDataFormat(); CsvDataFormat dataFormat = defDataFormat
                .setUseMaps(true)) {
            dataFormat.start();

            // Properly saved
            assertSame(CSVFormat.DEFAULT, dataFormat.getCsvFormat());
            assertTrue(dataFormat.isUseMaps());

            // Properly used (it doesn't modify the format)
            assertEquals(CSVFormat.DEFAULT, dataFormat.getActiveFormat());
        }
    }

    @Test
    void shouldHandleRecordConverter() throws IOException {
        CsvRecordConverter<String> converter = new CsvRecordConverter<String>() {
            @Override
            public String convertRecord(CSVRecord record) {
                return record.toString();
            }
        };

        try (CsvDataFormat defDataFormat = new CsvDataFormat(); CsvDataFormat dataFormat = defDataFormat
                .setRecordConverter(converter)) {
            dataFormat.start();

            // Properly saved
            assertSame(CSVFormat.DEFAULT, dataFormat.getCsvFormat());
            assertSame(converter, dataFormat.getRecordConverter());

            // Properly used (it doesn't modify the format)
            assertEquals(CSVFormat.DEFAULT, dataFormat.getActiveFormat());
        }
    }

    @Test
    void testTrim() throws IOException {
        // Set to TRUE
        try (CsvDataFormat defDataFormat = new CsvDataFormat(); CsvDataFormat dataFormat = defDataFormat.setTrim(true)) {
            dataFormat.start();
            // Properly saved
            assertSame(CSVFormat.DEFAULT, dataFormat.getCsvFormat());
            assertEquals(Boolean.TRUE, dataFormat.getTrim());
            // Properly used
            assertTrue(dataFormat.getActiveFormat().getTrim());
        }
        // NOT set
        try (CsvDataFormat defDataFormat = new CsvDataFormat(); CsvDataFormat dataFormat = defDataFormat) {
            dataFormat.start();
            // Properly saved
            assertSame(CSVFormat.DEFAULT, dataFormat.getCsvFormat());
            assertNull(dataFormat.getTrim());
            // Properly used
            assertFalse(dataFormat.getActiveFormat().getTrim());
        }
        // Set to false
        try (CsvDataFormat defDataFormat = new CsvDataFormat(); CsvDataFormat dataFormat = defDataFormat.setTrim(false)) {
            dataFormat.start();
            // Properly saved
            assertSame(CSVFormat.DEFAULT, dataFormat.getCsvFormat());
            assertEquals(Boolean.FALSE, dataFormat.getTrim());
            // Properly used
            assertFalse(dataFormat.getActiveFormat().getTrim());
        }

    }

    @Test
    void testIgnoreHeaderCase() throws IOException {
        // Set to TRUE
        try (CsvDataFormat defDataFormat = new CsvDataFormat();
             CsvDataFormat dataFormat = defDataFormat.setIgnoreHeaderCase(true)) {
            dataFormat.start();
            // Properly saved
            assertSame(CSVFormat.DEFAULT, dataFormat.getCsvFormat());
            assertEquals(Boolean.TRUE, dataFormat.getIgnoreHeaderCase());
            // Properly used
            assertTrue(dataFormat.getActiveFormat().getIgnoreHeaderCase());
        }
        // NOT set
        try (CsvDataFormat defDataFormat = new CsvDataFormat(); CsvDataFormat dataFormat = defDataFormat) {
            dataFormat.start();
            // Properly saved
            assertSame(CSVFormat.DEFAULT, dataFormat.getCsvFormat());
            assertNull(dataFormat.getIgnoreHeaderCase());
            // Properly used
            assertFalse(dataFormat.getActiveFormat().getIgnoreHeaderCase());
        }
        // Set to false
        try (CsvDataFormat defDataFormat = new CsvDataFormat();
             CsvDataFormat dataFormat = defDataFormat.setIgnoreHeaderCase(false)) {
            dataFormat.start();
            // Properly saved
            assertSame(CSVFormat.DEFAULT, dataFormat.getCsvFormat());
            assertEquals(Boolean.FALSE, dataFormat.getIgnoreHeaderCase());
            // Properly used
            assertFalse(dataFormat.getActiveFormat().getIgnoreHeaderCase());
        }
    }

    @Test
    void testTrailingDelimiter() throws IOException {
        // Set to TRUE
        try (CsvDataFormat defDataFormat = new CsvDataFormat();
             CsvDataFormat dataFormat = defDataFormat.setTrailingDelimiter(true)) {
            dataFormat.start();
            // Properly saved
            assertSame(CSVFormat.DEFAULT, dataFormat.getCsvFormat());
            assertEquals(Boolean.TRUE, dataFormat.getTrailingDelimiter());
            // Properly used
            assertTrue(dataFormat.getActiveFormat().getTrailingDelimiter());
        }
        // NOT set
        try (CsvDataFormat defDataFormat = new CsvDataFormat(); CsvDataFormat dataFormat = defDataFormat) {
            dataFormat.start();
            // Properly saved
            assertSame(CSVFormat.DEFAULT, dataFormat.getCsvFormat());
            assertNull(dataFormat.getTrailingDelimiter());
            // Properly used
            assertFalse(dataFormat.getActiveFormat().getTrailingDelimiter());
        }
        // Set to false
        try (CsvDataFormat defDataFormat = new CsvDataFormat();
             CsvDataFormat dataFormat = defDataFormat.setTrailingDelimiter(false)) {
            dataFormat.start();
            // Properly saved
            assertSame(CSVFormat.DEFAULT, dataFormat.getCsvFormat());
            assertEquals(Boolean.FALSE, dataFormat.getTrailingDelimiter());
            // Properly used
            assertFalse(dataFormat.getActiveFormat().getTrailingDelimiter());
        }
    }

}
