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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * This class tests the options of {@link org.apache.camel.dataformat.univocity.UniVocityTsvDataFormat}.
 */
public final class UniVocityTsvDataFormatTest {
    @Test
    public void shouldConfigureNullValue() {
        UniVocityTsvDataFormat dataFormat = new UniVocityTsvDataFormat();
        dataFormat.setNullValue("N/A");

        assertEquals("N/A", dataFormat.getNullValue());
        assertEquals("N/A", dataFormat.createAndConfigureWriterSettings().getNullValue());
        assertEquals("N/A", dataFormat.createAndConfigureParserSettings().getNullValue());
    }

    @Test
    public void shouldConfigureSkipEmptyLines() {
        UniVocityTsvDataFormat dataFormat = new UniVocityTsvDataFormat();
        dataFormat.setSkipEmptyLines(true);

        assertTrue(dataFormat.getSkipEmptyLines());
        assertTrue(dataFormat.createAndConfigureWriterSettings().getSkipEmptyLines());
        assertTrue(dataFormat.createAndConfigureParserSettings().getSkipEmptyLines());
    }

    @Test
    public void shouldConfigureIgnoreTrailingWhitespaces() {
        UniVocityTsvDataFormat dataFormat = new UniVocityTsvDataFormat();
        dataFormat.setIgnoreTrailingWhitespaces(true);

        assertTrue(dataFormat.getIgnoreTrailingWhitespaces());
        assertTrue(dataFormat.createAndConfigureWriterSettings().getIgnoreTrailingWhitespaces());
        assertTrue(dataFormat.createAndConfigureParserSettings().getIgnoreTrailingWhitespaces());
    }

    @Test
    public void shouldConfigureIgnoreLeadingWhitespaces() {
        UniVocityTsvDataFormat dataFormat = new UniVocityTsvDataFormat();
        dataFormat.setIgnoreLeadingWhitespaces(true);

        assertTrue(dataFormat.getIgnoreLeadingWhitespaces());
        assertTrue(dataFormat.createAndConfigureWriterSettings().getIgnoreLeadingWhitespaces());
        assertTrue(dataFormat.createAndConfigureParserSettings().getIgnoreLeadingWhitespaces());
    }

    @Test
    public void shouldConfigureHeadersDisabled() {
        UniVocityTsvDataFormat dataFormat = new UniVocityTsvDataFormat();
        dataFormat.setHeadersDisabled(true);

        assertTrue(dataFormat.isHeadersDisabled());
        assertNull(dataFormat.createAndConfigureWriterSettings().getHeaders());
        assertNull(dataFormat.createAndConfigureParserSettings().getHeaders());
    }

    @Test
    public void shouldConfigureHeaders() {
        UniVocityTsvDataFormat dataFormat = new UniVocityTsvDataFormat();
        dataFormat.setHeaders("A,B,C");

        assertArrayEquals(
                new String[] {"A", "B", "C"},
                dataFormat.createAndConfigureWriterSettings().getHeaders());
        assertArrayEquals(
                new String[] {"A", "B", "C"},
                dataFormat.createAndConfigureParserSettings().getHeaders());
    }

    @Test
    public void shouldConfigureHeaderExtractionEnabled() {
        UniVocityTsvDataFormat dataFormat = new UniVocityTsvDataFormat();
        dataFormat.setHeaderExtractionEnabled(true);

        assertTrue(dataFormat.getHeaderExtractionEnabled());
        assertTrue(dataFormat.createAndConfigureParserSettings().isHeaderExtractionEnabled());
    }

    @Test
    public void shouldConfigureNumberOfRecordsToRead() {
        UniVocityTsvDataFormat dataFormat = new UniVocityTsvDataFormat();
        dataFormat.setNumberOfRecordsToRead(42);

        assertEquals(Integer.valueOf(42), dataFormat.getNumberOfRecordsToRead());
        assertEquals(42, dataFormat.createAndConfigureParserSettings().getNumberOfRecordsToRead());
    }

    @Test
    public void shouldConfigureEmptyValue() {
        UniVocityTsvDataFormat dataFormat = new UniVocityTsvDataFormat();
        dataFormat.setEmptyValue("empty");

        assertEquals("empty", dataFormat.getEmptyValue());
        assertEquals("empty", dataFormat.createAndConfigureWriterSettings().getEmptyValue());
    }

    @Test
    public void shouldConfigureLineSeparator() {
        UniVocityTsvDataFormat dataFormat = new UniVocityTsvDataFormat();
        dataFormat.setLineSeparator("ls");

        assertEquals("ls", dataFormat.getLineSeparator());
        assertEquals(
                "ls", dataFormat.createAndConfigureWriterSettings().getFormat().getLineSeparatorString());
        assertEquals(
                "ls", dataFormat.createAndConfigureParserSettings().getFormat().getLineSeparatorString());
    }

    @Test
    public void shouldConfigureNormalizedLineSeparator() {
        UniVocityTsvDataFormat dataFormat = new UniVocityTsvDataFormat();
        dataFormat.setNormalizedLineSeparator('n');

        assertEquals(Character.valueOf('n'), dataFormat.getNormalizedLineSeparator());
        assertEquals(
                'n', dataFormat.createAndConfigureWriterSettings().getFormat().getNormalizedNewline());
        assertEquals(
                'n', dataFormat.createAndConfigureParserSettings().getFormat().getNormalizedNewline());
    }

    @Test
    public void shouldConfigureComment() {
        UniVocityTsvDataFormat dataFormat = new UniVocityTsvDataFormat();
        dataFormat.setComment('c');

        assertEquals(Character.valueOf('c'), dataFormat.getComment());
        assertEquals(
                'c', dataFormat.createAndConfigureWriterSettings().getFormat().getComment());
        assertEquals(
                'c', dataFormat.createAndConfigureParserSettings().getFormat().getComment());
    }

    @Test
    public void shouldConfigureLazyLoad() {
        UniVocityTsvDataFormat dataFormat = new UniVocityTsvDataFormat();
        dataFormat.setLazyLoad(true);

        assertTrue(dataFormat.isLazyLoad());
    }

    @Test
    public void shouldConfigureAsMap() {
        UniVocityTsvDataFormat dataFormat = new UniVocityTsvDataFormat();
        dataFormat.setAsMap(true);

        assertTrue(dataFormat.isAsMap());
    }

    @Test
    public void shouldConfigureEscapeChar() {
        UniVocityTsvDataFormat dataFormat = new UniVocityTsvDataFormat();
        dataFormat.setEscapeChar('e');

        assertEquals(Character.valueOf('e'), dataFormat.getEscapeChar());
        assertEquals(
                'e', dataFormat.createAndConfigureWriterSettings().getFormat().getEscapeChar());
        assertEquals(
                'e', dataFormat.createAndConfigureParserSettings().getFormat().getEscapeChar());
    }
}
