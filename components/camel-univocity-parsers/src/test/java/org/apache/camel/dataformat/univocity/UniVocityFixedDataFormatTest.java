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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This class tests the options of {@link UniVocityFixedDataFormat}.
 */
public final class UniVocityFixedDataFormatTest {
    @Test
    public void shouldConfigureNullValue() {
        UniVocityFixedDataFormat dataFormat = new UniVocityFixedDataFormat()
                .setFieldLengths(new int[] { 1, 2, 3 })
                .setNullValue("N/A");

        assertEquals("N/A", dataFormat.getNullValue());
        assertEquals("N/A", dataFormat.createAndConfigureWriterSettings().getNullValue());
        assertEquals("N/A", dataFormat.createAndConfigureParserSettings().getNullValue());
    }

    @Test
    public void shouldConfigureSkipEmptyLines() {
        UniVocityFixedDataFormat dataFormat = new UniVocityFixedDataFormat()
                .setFieldLengths(new int[] { 1, 2, 3 })
                .setSkipEmptyLines(true);

        assertTrue(dataFormat.getSkipEmptyLines());
        assertTrue(dataFormat.createAndConfigureWriterSettings().getSkipEmptyLines());
        assertTrue(dataFormat.createAndConfigureParserSettings().getSkipEmptyLines());
    }

    @Test
    public void shouldConfigureIgnoreTrailingWhitespaces() {
        UniVocityFixedDataFormat dataFormat = new UniVocityFixedDataFormat()
                .setFieldLengths(new int[] { 1, 2, 3 })
                .setIgnoreTrailingWhitespaces(true);

        assertTrue(dataFormat.getIgnoreTrailingWhitespaces());
        assertTrue(dataFormat.createAndConfigureWriterSettings().getIgnoreTrailingWhitespaces());
        assertTrue(dataFormat.createAndConfigureParserSettings().getIgnoreTrailingWhitespaces());
    }

    @Test
    public void shouldConfigureIgnoreLeadingWhitespaces() {
        UniVocityFixedDataFormat dataFormat = new UniVocityFixedDataFormat()
                .setFieldLengths(new int[] { 1, 2, 3 })
                .setIgnoreLeadingWhitespaces(true);

        assertTrue(dataFormat.getIgnoreLeadingWhitespaces());
        assertTrue(dataFormat.createAndConfigureWriterSettings().getIgnoreLeadingWhitespaces());
        assertTrue(dataFormat.createAndConfigureParserSettings().getIgnoreLeadingWhitespaces());
    }

    @Test
    public void shouldConfigureHeadersDisabled() {
        UniVocityFixedDataFormat dataFormat = new UniVocityFixedDataFormat()
                .setFieldLengths(new int[] { 1, 2, 3 })
                .setHeadersDisabled(true);

        assertTrue(dataFormat.isHeadersDisabled());
        assertNull(dataFormat.createAndConfigureWriterSettings().getHeaders());
        assertNull(dataFormat.createAndConfigureParserSettings().getHeaders());
    }

    @Test
    public void shouldConfigureHeaders() {
        UniVocityFixedDataFormat dataFormat = new UniVocityFixedDataFormat()
                .setFieldLengths(new int[] { 1, 2, 3 })
                .setHeaders(new String[] { "A", "B", "C" });

        assertArrayEquals(new String[] { "A", "B", "C" }, dataFormat.getHeaders());
        assertArrayEquals(new String[] { "A", "B", "C" }, dataFormat.createAndConfigureWriterSettings().getHeaders());
        assertArrayEquals(new String[] { "A", "B", "C" }, dataFormat.createAndConfigureParserSettings().getHeaders());
    }

    @Test
    public void shouldConfigureHeaderExtractionEnabled() {
        UniVocityFixedDataFormat dataFormat = new UniVocityFixedDataFormat()
                .setFieldLengths(new int[] { 1, 2, 3 })
                .setHeaderExtractionEnabled(true);

        assertTrue(dataFormat.getHeaderExtractionEnabled());
        assertTrue(dataFormat.createAndConfigureParserSettings().isHeaderExtractionEnabled());
    }

    @Test
    public void shouldConfigureNumberOfRecordsToRead() {
        UniVocityFixedDataFormat dataFormat = new UniVocityFixedDataFormat()
                .setFieldLengths(new int[] { 1, 2, 3 })
                .setNumberOfRecordsToRead(42);

        assertEquals(Integer.valueOf(42), dataFormat.getNumberOfRecordsToRead());
        assertEquals(42, dataFormat.createAndConfigureParserSettings().getNumberOfRecordsToRead());
    }

    @Test
    public void shouldConfigureEmptyValue() {
        UniVocityFixedDataFormat dataFormat = new UniVocityFixedDataFormat()
                .setFieldLengths(new int[] { 1, 2, 3 })
                .setEmptyValue("empty");

        assertEquals("empty", dataFormat.getEmptyValue());
        assertEquals("empty", dataFormat.createAndConfigureWriterSettings().getEmptyValue());
    }

    @Test
    public void shouldConfigureLineSeparator() {
        UniVocityFixedDataFormat dataFormat = new UniVocityFixedDataFormat()
                .setFieldLengths(new int[] { 1, 2, 3 })
                .setLineSeparator("ls");

        assertEquals("ls", dataFormat.getLineSeparator());
        assertEquals("ls", dataFormat.createAndConfigureWriterSettings().getFormat().getLineSeparatorString());
        assertEquals("ls", dataFormat.createAndConfigureParserSettings().getFormat().getLineSeparatorString());
    }

    @Test
    public void shouldConfigureNormalizedLineSeparator() {
        UniVocityFixedDataFormat dataFormat = new UniVocityFixedDataFormat()
                .setFieldLengths(new int[] { 1, 2, 3 })
                .setNormalizedLineSeparator('n');

        assertEquals(Character.valueOf('n'), dataFormat.getNormalizedLineSeparator());
        assertEquals('n', dataFormat.createAndConfigureWriterSettings().getFormat().getNormalizedNewline());
        assertEquals('n', dataFormat.createAndConfigureParserSettings().getFormat().getNormalizedNewline());
    }

    @Test
    public void shouldConfigureComment() {
        UniVocityFixedDataFormat dataFormat = new UniVocityFixedDataFormat()
                .setFieldLengths(new int[] { 1, 2, 3 })
                .setComment('c');

        assertEquals(Character.valueOf('c'), dataFormat.getComment());
        assertEquals('c', dataFormat.createAndConfigureWriterSettings().getFormat().getComment());
        assertEquals('c', dataFormat.createAndConfigureParserSettings().getFormat().getComment());
    }

    @Test
    public void shouldConfigureLazyLoad() {
        UniVocityFixedDataFormat dataFormat = new UniVocityFixedDataFormat()
                .setFieldLengths(new int[] { 1, 2, 3 })
                .setLazyLoad(true);

        assertTrue(dataFormat.isLazyLoad());
    }

    @Test
    public void shouldConfigureAsMap() {
        UniVocityFixedDataFormat dataFormat = new UniVocityFixedDataFormat()
                .setFieldLengths(new int[] { 1, 2, 3 })
                .setAsMap(true);

        assertTrue(dataFormat.isAsMap());
    }

    @Test
    public void shouldConfigurePadding() {
        UniVocityFixedDataFormat dataFormat = new UniVocityFixedDataFormat()
                .setFieldLengths(new int[] { 1, 2, 3 })
                .setPadding('p');

        assertEquals(Character.valueOf('p'), dataFormat.getPadding());
        assertEquals('p', dataFormat.createAndConfigureWriterSettings().getFormat().getPadding());
        assertEquals('p', dataFormat.createAndConfigureParserSettings().getFormat().getPadding());
    }

    @Test
    public void shouldConfigureSkipTrailingCharsUntilNewline() {
        UniVocityFixedDataFormat dataFormat = new UniVocityFixedDataFormat()
                .setFieldLengths(new int[] { 1, 2, 3 })
                .setSkipTrailingCharsUntilNewline(true);

        assertTrue(dataFormat.getSkipTrailingCharsUntilNewline());
        assertTrue(dataFormat.createAndConfigureParserSettings().getSkipTrailingCharsUntilNewline());
    }

    @Test
    public void shouldConfigureRecordEndsOnNewline() {
        UniVocityFixedDataFormat dataFormat = new UniVocityFixedDataFormat()
                .setFieldLengths(new int[] { 1, 2, 3 })
                .setRecordEndsOnNewline(true);

        assertTrue(dataFormat.getRecordEndsOnNewline());
        assertTrue(dataFormat.createAndConfigureParserSettings().getRecordEndsOnNewline());
    }

    @Test
    public void shouldConfigureFieldLengthWithLengthsOnly() {
        UniVocityFixedDataFormat dataFormat = new UniVocityFixedDataFormat()
                .setFieldLengths(new int[] { 1, 2, 3 });

        assertArrayEquals(new int[] { 1, 2, 3 }, dataFormat.getFieldLengths());

        dataFormat.createAndConfigureWriterSettings();
    }

    @Test
    public void shouldConfigureFieldLengthWithHeadersAndLengths() {
        UniVocityFixedDataFormat dataFormat = new UniVocityFixedDataFormat()
                .setFieldLengths(new int[] { 1, 2, 3 })
                .setHeaders(new String[] { "A", "B", "C" });

        assertArrayEquals(new int[] { 1, 2, 3 }, dataFormat.getFieldLengths());
        assertArrayEquals(new String[] { "A", "B", "C" }, dataFormat.getHeaders());

        dataFormat.createAndConfigureWriterSettings();
    }

    @Test
    public void shouldNotAllowNoFieldLengths() {
        UniVocityFixedDataFormat dataFormat = new UniVocityFixedDataFormat();
        assertThrows(IllegalArgumentException.class, () -> dataFormat.createAndConfigureWriterSettings());
    }

    @Test
    public void shouldNotAllowHeadersAndLengthsOfDifferentSize() {
        UniVocityFixedDataFormat dataFormat = new UniVocityFixedDataFormat()
                .setFieldLengths(new int[] { 1, 2, 3, 4 })
                .setHeaders(new String[] { "A", "B", "C" });

        assertArrayEquals(new int[] { 1, 2, 3, 4 }, dataFormat.getFieldLengths());
        assertArrayEquals(new String[] { "A", "B", "C" }, dataFormat.getHeaders());

        assertThrows(IllegalArgumentException.class, () -> dataFormat.createAndConfigureWriterSettings());
    }

    @Test
    public void shouldNotAllowHeadersWithSameName() {
        UniVocityFixedDataFormat dataFormat = new UniVocityFixedDataFormat()
                .setFieldLengths(new int[] { 1, 2, 3 })
                .setHeaders(new String[] { "A", "B", "A" });

        assertArrayEquals(new int[] { 1, 2, 3 }, dataFormat.getFieldLengths());
        assertArrayEquals(new String[] { "A", "B", "A" }, dataFormat.getHeaders());

        assertThrows(IllegalArgumentException.class, () -> dataFormat.createAndConfigureWriterSettings());
    }
}
