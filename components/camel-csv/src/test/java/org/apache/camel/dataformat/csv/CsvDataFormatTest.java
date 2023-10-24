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
    void shouldUseDefaultFormat() {
        CsvDataFormat dataFormat = new CsvDataFormat();

        // Properly initialized
        assertSame(CSVFormat.DEFAULT, dataFormat.getFormat());

        // Properly used
        assertEquals(CSVFormat.DEFAULT, dataFormat.getActiveFormat());
    }

    @Test
    void shouldUseFormatFromConstructor() {
        CsvDataFormat dataFormat = new CsvDataFormat(CSVFormat.EXCEL);

        // Properly initialized
        assertSame(CSVFormat.EXCEL, dataFormat.getFormat());

        // Properly used
        assertEquals(CSVFormat.EXCEL, dataFormat.getActiveFormat());
    }

    @Test
    void shouldUseSpecifiedFormat() {
        CsvDataFormat dataFormat = new CsvDataFormat()
                .setFormat(CSVFormat.MYSQL);

        // Properly saved
        assertSame(CSVFormat.MYSQL, dataFormat.getFormat());

        // Properly used
        assertEquals(CSVFormat.MYSQL, dataFormat.getActiveFormat());
    }

    @Test
    void shouldFallbackToDefaultFormat() {
        CsvDataFormat dataFormat = new CsvDataFormat(CSVFormat.EXCEL)
                .setFormat(null);

        // Properly saved
        assertSame(CSVFormat.DEFAULT, dataFormat.getFormat());

        // Properly used
        assertEquals(CSVFormat.DEFAULT, dataFormat.getActiveFormat());
    }

    @Test
    void shouldDefineFormatByName() {
        CsvDataFormat dataFormat = new CsvDataFormat()
                .setFormatName("EXCEL");

        // Properly saved
        assertSame(CSVFormat.EXCEL, dataFormat.getFormat());

        // Properly used
        assertEquals(CSVFormat.EXCEL, dataFormat.getActiveFormat());
    }

    @Test
    void shouldDisableCommentMarker() {
        CsvDataFormat dataFormat = new CsvDataFormat()
                .setCommentMarkerDisabled(true)
                .setCommentMarker('c');

        // Properly saved
        assertSame(CSVFormat.DEFAULT, dataFormat.getFormat());
        assertTrue(dataFormat.isCommentMarkerDisabled());
        assertEquals(Character.valueOf('c'), dataFormat.getCommentMarker());

        // Properly used
        assertNull(dataFormat.getActiveFormat().getCommentMarker());
    }

    @Test
    void shouldOverrideCommentMarker() {
        CsvDataFormat dataFormat = new CsvDataFormat()
                .setCommentMarker('c');

        // Properly saved
        assertSame(CSVFormat.DEFAULT, dataFormat.getFormat());
        assertEquals(Character.valueOf('c'), dataFormat.getCommentMarker());

        // Properly used
        assertEquals(Character.valueOf('c'), dataFormat.getActiveFormat().getCommentMarker());
    }

    @Test
    void shouldOverrideDelimiter() {
        CsvDataFormat dataFormat = new CsvDataFormat()
                .setDelimiter('d');

        // Properly saved
        assertSame(CSVFormat.DEFAULT, dataFormat.getFormat());
        assertEquals(Character.valueOf('d'), dataFormat.getDelimiter());

        // Properly used
        assertEquals('d', dataFormat.getActiveFormat().getDelimiter());
    }

    @Test
    void shouldDisableEscape() {
        CsvDataFormat dataFormat = new CsvDataFormat()
                .setEscapeDisabled(true)
                .setEscape('e');

        // Properly saved
        assertSame(CSVFormat.DEFAULT, dataFormat.getFormat());
        assertTrue(dataFormat.isEscapeDisabled());
        assertEquals(Character.valueOf('e'), dataFormat.getEscape());

        // Properly used
        assertNull(dataFormat.getActiveFormat().getEscapeCharacter());
    }

    @Test
    void shouldOverrideEscape() {
        CsvDataFormat dataFormat = new CsvDataFormat()
                .setEscape('e');

        // Properly saved
        assertSame(CSVFormat.DEFAULT, dataFormat.getFormat());
        assertEquals(Character.valueOf('e'), dataFormat.getEscape());

        // Properly used
        assertEquals(Character.valueOf('e'), dataFormat.getActiveFormat().getEscapeCharacter());
    }

    @Test
    void shouldDisableHeader() {
        CsvDataFormat dataFormat = new CsvDataFormat()
                .setHeaderDisabled(true)
                .setHeader(new String[] { "a", "b", "c" });

        // Properly saved
        assertSame(CSVFormat.DEFAULT, dataFormat.getFormat());
        assertTrue(dataFormat.isHeaderDisabled());
        assertEquals("a,b,c", dataFormat.getHeader());

        // Properly used
        assertNull(dataFormat.getActiveFormat().getHeader());
    }

    @Test
    void shouldOverrideHeader() {
        CsvDataFormat dataFormat = new CsvDataFormat()
                .setHeader("a,b,c");

        // Properly saved
        assertSame(CSVFormat.DEFAULT, dataFormat.getFormat());
        assertEquals("a,b,c", dataFormat.getHeader());

        // Properly used
        assertArrayEquals(new String[] { "a", "b", "c" }, dataFormat.getActiveFormat().getHeader());
    }

    @Test
    void shouldAllowMissingColumnNames() {
        CsvDataFormat dataFormat = new CsvDataFormat()
                .setAllowMissingColumnNames(true);

        // Properly saved
        assertSame(CSVFormat.DEFAULT, dataFormat.getFormat());
        assertEquals(Boolean.TRUE, dataFormat.getAllowMissingColumnNames());

        // Properly used
        assertTrue(dataFormat.getActiveFormat().getAllowMissingColumnNames());
    }

    @Test
    void shouldNotAllowMissingColumnNames() {
        CsvDataFormat dataFormat = new CsvDataFormat()
                .setAllowMissingColumnNames(false);

        // Properly saved
        assertSame(CSVFormat.DEFAULT, dataFormat.getFormat());
        assertEquals(Boolean.FALSE, dataFormat.getAllowMissingColumnNames());

        // Properly used
        assertFalse(dataFormat.getActiveFormat().getAllowMissingColumnNames());
    }

    @Test
    void shouldIgnoreEmptyLines() {
        CsvDataFormat dataFormat = new CsvDataFormat()
                .setIgnoreEmptyLines(true);

        // Properly saved
        assertSame(CSVFormat.DEFAULT, dataFormat.getFormat());
        assertEquals(Boolean.TRUE, dataFormat.getIgnoreEmptyLines());

        // Properly used
        assertTrue(dataFormat.getActiveFormat().getIgnoreEmptyLines());
    }

    @Test
    void shouldNotIgnoreEmptyLines() {
        CsvDataFormat dataFormat = new CsvDataFormat()
                .setIgnoreEmptyLines(false);

        // Properly saved
        assertSame(CSVFormat.DEFAULT, dataFormat.getFormat());
        assertEquals(Boolean.FALSE, dataFormat.getIgnoreEmptyLines());

        // Properly used
        assertFalse(dataFormat.getActiveFormat().getIgnoreEmptyLines());
    }

    @Test
    void shouldIgnoreSurroundingSpaces() {
        CsvDataFormat dataFormat = new CsvDataFormat()
                .setIgnoreSurroundingSpaces(true);

        // Properly saved
        assertSame(CSVFormat.DEFAULT, dataFormat.getFormat());
        assertEquals(Boolean.TRUE, dataFormat.getIgnoreSurroundingSpaces());

        // Properly used
        assertTrue(dataFormat.getActiveFormat().getIgnoreSurroundingSpaces());
    }

    @Test
    void shouldNotIgnoreSurroundingSpaces() {
        CsvDataFormat dataFormat = new CsvDataFormat()
                .setIgnoreSurroundingSpaces(false);

        // Properly saved
        assertSame(CSVFormat.DEFAULT, dataFormat.getFormat());
        assertEquals(Boolean.FALSE, dataFormat.getIgnoreSurroundingSpaces());

        // Properly used
        assertFalse(dataFormat.getActiveFormat().getIgnoreSurroundingSpaces());
    }

    @Test
    void shouldDisableNullString() {
        CsvDataFormat dataFormat = new CsvDataFormat()
                .setNullStringDisabled(true)
                .setNullString("****");

        // Properly saved
        assertSame(CSVFormat.DEFAULT, dataFormat.getFormat());
        assertTrue(dataFormat.isNullStringDisabled());
        assertEquals("****", dataFormat.getNullString());

        // Properly used
        assertNull(dataFormat.getActiveFormat().getNullString());
    }

    @Test
    void shouldOverrideNullString() {
        CsvDataFormat dataFormat = new CsvDataFormat()
                .setNullString("****");

        // Properly saved
        assertSame(CSVFormat.DEFAULT, dataFormat.getFormat());
        assertEquals("****", dataFormat.getNullString());

        // Properly used
        assertEquals("****", dataFormat.getActiveFormat().getNullString());
    }

    @Test
    void shouldDisableQuote() {
        CsvDataFormat dataFormat = new CsvDataFormat()
                .setQuoteDisabled(true)
                .setQuote('q');

        // Properly saved
        assertSame(CSVFormat.DEFAULT, dataFormat.getFormat());
        assertTrue(dataFormat.isQuoteDisabled());
        assertEquals(Character.valueOf('q'), dataFormat.getQuote());

        // Properly used
        assertNull(dataFormat.getActiveFormat().getQuoteCharacter());
    }

    @Test
    void shouldOverrideQuote() {
        CsvDataFormat dataFormat = new CsvDataFormat()
                .setQuote('q');

        // Properly saved
        assertSame(CSVFormat.DEFAULT, dataFormat.getFormat());
        assertEquals(Character.valueOf('q'), dataFormat.getQuote());

        // Properly used
        assertEquals(Character.valueOf('q'), dataFormat.getActiveFormat().getQuoteCharacter());
    }

    @Test
    void shouldOverrideQuoteMode() {
        CsvDataFormat dataFormat = new CsvDataFormat()
                .setQuoteMode(QuoteMode.ALL);

        // Properly saved
        assertSame(CSVFormat.DEFAULT, dataFormat.getFormat());
        assertEquals(QuoteMode.ALL, dataFormat.getQuoteMode());

        // Properly used
        assertEquals(QuoteMode.ALL, dataFormat.getActiveFormat().getQuoteMode());
    }

    @Test
    void shouldDisableRecordSeparator() {
        CsvDataFormat dataFormat = new CsvDataFormat()
                .setRecordSeparatorDisabled(true)
                .setRecordSeparator("separator");

        // Properly saved
        assertSame(CSVFormat.DEFAULT, dataFormat.getFormat());
        assertTrue(dataFormat.isRecordSeparatorDisabled());
        assertEquals("separator", dataFormat.getRecordSeparator());

        // Properly used
        assertNull(dataFormat.getActiveFormat().getRecordSeparator());
    }

    @Test
    void shouldOverrideRecordSeparator() {
        CsvDataFormat dataFormat = new CsvDataFormat()
                .setRecordSeparator("separator");

        // Properly saved
        assertSame(CSVFormat.DEFAULT, dataFormat.getFormat());
        assertEquals("separator", dataFormat.getRecordSeparator());

        // Properly used
        assertEquals("separator", dataFormat.getActiveFormat().getRecordSeparator());
    }

    @Test
    void shouldSkipHeaderRecord() {
        CsvDataFormat dataFormat = new CsvDataFormat()
                .setSkipHeaderRecord(true);

        // Properly saved
        assertSame(CSVFormat.DEFAULT, dataFormat.getFormat());
        assertEquals(Boolean.TRUE, dataFormat.getSkipHeaderRecord());

        // Properly used
        assertTrue(dataFormat.getActiveFormat().getSkipHeaderRecord());
    }

    @Test
    void shouldNotSkipHeaderRecord() {
        CsvDataFormat dataFormat = new CsvDataFormat()
                .setSkipHeaderRecord(false);

        // Properly saved
        assertSame(CSVFormat.DEFAULT, dataFormat.getFormat());
        assertEquals(Boolean.FALSE, dataFormat.getSkipHeaderRecord());

        // Properly used
        assertFalse(dataFormat.getActiveFormat().getSkipHeaderRecord());
    }

    @Test
    void shouldHandleLazyLoad() {
        CsvDataFormat dataFormat = new CsvDataFormat()
                .setLazyLoad(true);

        // Properly saved
        assertSame(CSVFormat.DEFAULT, dataFormat.getFormat());
        assertTrue(dataFormat.isLazyLoad());

        // Properly used (it doesn't modify the format)
        assertEquals(CSVFormat.DEFAULT, dataFormat.getActiveFormat());
    }

    @Test
    void shouldHandleUseMaps() {
        CsvDataFormat dataFormat = new CsvDataFormat()
                .setUseMaps(true);

        // Properly saved
        assertSame(CSVFormat.DEFAULT, dataFormat.getFormat());
        assertTrue(dataFormat.isUseMaps());

        // Properly used (it doesn't modify the format)
        assertEquals(CSVFormat.DEFAULT, dataFormat.getActiveFormat());
    }

    @Test
    void shouldHandleRecordConverter() {
        CsvRecordConverter<String> converter = new CsvRecordConverter<String>() {
            @Override
            public String convertRecord(CSVRecord record) {
                return record.toString();
            }
        };

        CsvDataFormat dataFormat = new CsvDataFormat()
                .setRecordConverter(converter);

        // Properly saved
        assertSame(CSVFormat.DEFAULT, dataFormat.getFormat());
        assertSame(converter, dataFormat.getRecordConverter());

        // Properly used (it doesn't modify the format)
        assertEquals(CSVFormat.DEFAULT, dataFormat.getActiveFormat());
    }

    @Test
    void testTrim() {
        // Set to TRUE
        CsvDataFormat dataFormat = new CsvDataFormat().setTrim(true);
        // Properly saved
        assertSame(CSVFormat.DEFAULT, dataFormat.getFormat());
        assertEquals(Boolean.TRUE, dataFormat.getTrim());
        // Properly used
        assertTrue(dataFormat.getActiveFormat().getTrim());

        // NOT set
        dataFormat = new CsvDataFormat();
        // Properly saved
        assertSame(CSVFormat.DEFAULT, dataFormat.getFormat());
        assertNull(dataFormat.getTrim());
        // Properly used
        assertFalse(dataFormat.getActiveFormat().getTrim());

        // Set to false
        dataFormat = new CsvDataFormat().setTrim(false);
        // Properly saved
        assertSame(CSVFormat.DEFAULT, dataFormat.getFormat());
        assertEquals(Boolean.FALSE, dataFormat.getTrim());
        // Properly used
        assertFalse(dataFormat.getActiveFormat().getTrim());

    }

    @Test
    void testIgnoreHeaderCase() {
        // Set to TRUE
        CsvDataFormat dataFormat = new CsvDataFormat().setIgnoreHeaderCase(true);
        // Properly saved
        assertSame(CSVFormat.DEFAULT, dataFormat.getFormat());
        assertEquals(Boolean.TRUE, dataFormat.getIgnoreHeaderCase());
        // Properly used
        assertTrue(dataFormat.getActiveFormat().getIgnoreHeaderCase());

        // NOT set
        dataFormat = new CsvDataFormat();
        // Properly saved
        assertSame(CSVFormat.DEFAULT, dataFormat.getFormat());
        assertNull(dataFormat.getIgnoreHeaderCase());
        // Properly used
        assertFalse(dataFormat.getActiveFormat().getIgnoreHeaderCase());

        // Set to false
        dataFormat = new CsvDataFormat().setIgnoreHeaderCase(false);
        // Properly saved
        assertSame(CSVFormat.DEFAULT, dataFormat.getFormat());
        assertEquals(Boolean.FALSE, dataFormat.getIgnoreHeaderCase());
        // Properly used
        assertFalse(dataFormat.getActiveFormat().getIgnoreHeaderCase());
    }

    @Test
    void testTrailingDelimiter() {
        // Set to TRUE
        CsvDataFormat dataFormat = new CsvDataFormat().setTrailingDelimiter(true);
        // Properly saved
        assertSame(CSVFormat.DEFAULT, dataFormat.getFormat());
        assertEquals(Boolean.TRUE, dataFormat.getTrailingDelimiter());
        // Properly used
        assertTrue(dataFormat.getActiveFormat().getTrailingDelimiter());

        // NOT set
        dataFormat = new CsvDataFormat();
        // Properly saved
        assertSame(CSVFormat.DEFAULT, dataFormat.getFormat());
        assertNull(dataFormat.getTrailingDelimiter());
        // Properly used
        assertFalse(dataFormat.getActiveFormat().getTrailingDelimiter());

        // Set to false
        dataFormat = new CsvDataFormat().setTrailingDelimiter(false);
        // Properly saved
        assertSame(CSVFormat.DEFAULT, dataFormat.getFormat());
        assertEquals(Boolean.FALSE, dataFormat.getTrailingDelimiter());
        // Properly used
        assertFalse(dataFormat.getActiveFormat().getTrailingDelimiter());
    }

}
