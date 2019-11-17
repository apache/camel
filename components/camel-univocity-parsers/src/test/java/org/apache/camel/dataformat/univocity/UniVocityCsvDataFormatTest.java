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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This class tests the options of {@link org.apache.camel.dataformat.univocity.UniVocityCsvDataFormat}.
 */
public final class UniVocityCsvDataFormatTest {
    @Test
    public void shouldConfigureNullValue() {
        UniVocityCsvDataFormat dataFormat = new UniVocityCsvDataFormat()
                .setNullValue("N/A");

        assertEquals("N/A", dataFormat.getNullValue());
        assertEquals("N/A", dataFormat.createAndConfigureWriterSettings().getNullValue());
        assertEquals("N/A", dataFormat.createAndConfigureParserSettings().getNullValue());
    }

    @Test
    public void shouldConfigureSkipEmptyLines() {
        UniVocityCsvDataFormat dataFormat = new UniVocityCsvDataFormat()
                .setSkipEmptyLines(true);

        assertTrue(dataFormat.getSkipEmptyLines());
        assertTrue(dataFormat.createAndConfigureWriterSettings().getSkipEmptyLines());
        assertTrue(dataFormat.createAndConfigureParserSettings().getSkipEmptyLines());
    }

    @Test
    public void shouldConfigureIgnoreTrailingWhitespaces() {
        UniVocityCsvDataFormat dataFormat = new UniVocityCsvDataFormat()
                .setIgnoreTrailingWhitespaces(true);

        assertTrue(dataFormat.getIgnoreTrailingWhitespaces());
        assertTrue(dataFormat.createAndConfigureWriterSettings().getIgnoreTrailingWhitespaces());
        assertTrue(dataFormat.createAndConfigureParserSettings().getIgnoreTrailingWhitespaces());
    }

    @Test
    public void shouldConfigureIgnoreLeadingWhitespaces() {
        UniVocityCsvDataFormat dataFormat = new UniVocityCsvDataFormat()
                .setIgnoreLeadingWhitespaces(true);

        assertTrue(dataFormat.getIgnoreLeadingWhitespaces());
        assertTrue(dataFormat.createAndConfigureWriterSettings().getIgnoreLeadingWhitespaces());
        assertTrue(dataFormat.createAndConfigureParserSettings().getIgnoreLeadingWhitespaces());
    }

    @Test
    public void shouldConfigureHeadersDisabled() {
        UniVocityCsvDataFormat dataFormat = new UniVocityCsvDataFormat()
                .setHeadersDisabled(true);

        assertTrue(dataFormat.isHeadersDisabled());
        assertNull(dataFormat.createAndConfigureWriterSettings().getHeaders());
        assertNull(dataFormat.createAndConfigureParserSettings().getHeaders());
    }

    @Test
    public void shouldConfigureHeaders() {
        UniVocityCsvDataFormat dataFormat = new UniVocityCsvDataFormat()
                .setHeaders(new String[]{"A", "B", "C"});

        assertArrayEquals(new String[]{"A", "B", "C"}, dataFormat.getHeaders());
        assertArrayEquals(new String[]{"A", "B", "C"}, dataFormat.createAndConfigureWriterSettings().getHeaders());
        assertArrayEquals(new String[]{"A", "B", "C"}, dataFormat.createAndConfigureParserSettings().getHeaders());
    }

    @Test
    public void shouldConfigureHeaderExtractionEnabled() {
        UniVocityCsvDataFormat dataFormat = new UniVocityCsvDataFormat()
                .setHeaderExtractionEnabled(true);

        assertTrue(dataFormat.getHeaderExtractionEnabled());
        assertTrue(dataFormat.createAndConfigureParserSettings().isHeaderExtractionEnabled());
    }

    @Test
    public void shouldConfigureNumberOfRecordsToRead() {
        UniVocityCsvDataFormat dataFormat = new UniVocityCsvDataFormat()
                .setNumberOfRecordsToRead(42);

        assertEquals(Integer.valueOf(42), dataFormat.getNumberOfRecordsToRead());
        assertEquals(42, dataFormat.createAndConfigureParserSettings().getNumberOfRecordsToRead());
    }

    @Test
    public void shouldConfigureEmptyValue() {
        UniVocityCsvDataFormat dataFormat = new UniVocityCsvDataFormat()
                .setEmptyValue("empty");

        assertEquals("empty", dataFormat.getEmptyValue());
        assertEquals("empty", dataFormat.createAndConfigureWriterSettings().getEmptyValue());
        assertEquals("empty", dataFormat.createAndConfigureParserSettings().getEmptyValue());
    }

    @Test
    public void shouldConfigureLineSeparator() {
        UniVocityCsvDataFormat dataFormat = new UniVocityCsvDataFormat()
                .setLineSeparator("ls");

        assertEquals("ls", dataFormat.getLineSeparator());
        assertEquals("ls", dataFormat.createAndConfigureWriterSettings().getFormat().getLineSeparatorString());
        assertEquals("ls", dataFormat.createAndConfigureParserSettings().getFormat().getLineSeparatorString());
    }

    @Test
    public void shouldConfigureNormalizedLineSeparator() {
        UniVocityCsvDataFormat dataFormat = new UniVocityCsvDataFormat()
                .setNormalizedLineSeparator('n');

        assertEquals(Character.valueOf('n'), dataFormat.getNormalizedLineSeparator());
        assertEquals('n', dataFormat.createAndConfigureWriterSettings().getFormat().getNormalizedNewline());
        assertEquals('n', dataFormat.createAndConfigureParserSettings().getFormat().getNormalizedNewline());
    }

    @Test
    public void shouldConfigureComment() {
        UniVocityCsvDataFormat dataFormat = new UniVocityCsvDataFormat()
                .setComment('c');

        assertEquals(Character.valueOf('c'), dataFormat.getComment());
        assertEquals('c', dataFormat.createAndConfigureWriterSettings().getFormat().getComment());
        assertEquals('c', dataFormat.createAndConfigureParserSettings().getFormat().getComment());
    }

    @Test
    public void shouldConfigureLazyLoad() {
        UniVocityCsvDataFormat dataFormat = new UniVocityCsvDataFormat()
                .setLazyLoad(true);

        assertTrue(dataFormat.isLazyLoad());
    }

    @Test
    public void shouldConfigureAsMap() {
        UniVocityCsvDataFormat dataFormat = new UniVocityCsvDataFormat()
                .setAsMap(true);

        assertTrue(dataFormat.isAsMap());
    }

    @Test
    public void shouldConfigureQuoteAllFields() {
        UniVocityCsvDataFormat dataFormat = new UniVocityCsvDataFormat()
                .setQuoteAllFields(true);

        assertTrue(dataFormat.getQuoteAllFields());
        assertTrue(dataFormat.createAndConfigureWriterSettings().getQuoteAllFields());
    }

    @Test
    public void shouldConfigureQuote() {
        UniVocityCsvDataFormat dataFormat = new UniVocityCsvDataFormat()
                .setQuote('q');

        assertEquals(Character.valueOf('q'), dataFormat.getQuote());
        assertEquals('q', dataFormat.createAndConfigureWriterSettings().getFormat().getQuote());
        assertEquals('q', dataFormat.createAndConfigureParserSettings().getFormat().getQuote());
    }

    @Test
    public void shouldConfigureQuoteEscape() {
        UniVocityCsvDataFormat dataFormat = new UniVocityCsvDataFormat()
                .setQuoteEscape('e');

        assertEquals(Character.valueOf('e'), dataFormat.getQuoteEscape());
        assertEquals('e', dataFormat.createAndConfigureWriterSettings().getFormat().getQuoteEscape());
        assertEquals('e', dataFormat.createAndConfigureParserSettings().getFormat().getQuoteEscape());
    }

    @Test
    public void shouldConfigureDelimiter() {
        UniVocityCsvDataFormat dataFormat = new UniVocityCsvDataFormat()
                .setDelimiter('d');

        assertEquals(Character.valueOf('d'), dataFormat.getDelimiter());
        assertEquals('d', dataFormat.createAndConfigureWriterSettings().getFormat().getDelimiter());
        assertEquals('d', dataFormat.createAndConfigureParserSettings().getFormat().getDelimiter());
    }
}
