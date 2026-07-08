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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.List;

import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FuzzyFilterTest {

    // ---- fuzzyMatch tests ----

    @Test
    void fuzzyMatchExactMatch() {
        int[] positions = FuzzyFilter.fuzzyMatch("hello", "hello");
        assertNotNull(positions);
        assertArrayEquals(new int[] { 0, 1, 2, 3, 4 }, positions);
    }

    @Test
    void fuzzyMatchScatteredMatch() {
        // Pattern "hlo" should match positions of 'h', 'l', 'o' in "hello"
        int[] positions = FuzzyFilter.fuzzyMatch("hello", "hlo");
        assertNotNull(positions);
        assertArrayEquals(new int[] { 0, 2, 4 }, positions);
    }

    @Test
    void fuzzyMatchNoMatch() {
        int[] positions = FuzzyFilter.fuzzyMatch("hello", "xyz");
        assertNull(positions);
    }

    @Test
    void fuzzyMatchEmptyPattern() {
        int[] positions = FuzzyFilter.fuzzyMatch("hello", "");
        assertNotNull(positions);
        assertEquals(0, positions.length);
    }

    @Test
    void fuzzyMatchNullPattern() {
        int[] positions = FuzzyFilter.fuzzyMatch("hello", null);
        assertNotNull(positions);
        assertEquals(0, positions.length);
    }

    @Test
    void fuzzyMatchCaseInsensitive() {
        int[] positions = FuzzyFilter.fuzzyMatch("Hello World", "hw");
        assertNotNull(positions);
        assertEquals(2, positions.length);
        assertEquals(0, positions[0]); // 'H' matches 'h'
        assertEquals(6, positions[1]); // 'W' matches 'w'
    }

    // ---- highlightLine tests ----

    @Test
    void highlightLineMatchAtStart() {
        Style normal = Style.EMPTY;
        Style match = Style.EMPTY.fg(Color.RED);

        Line line = FuzzyFilter.highlightLine("ABC", new int[] { 0 }, normal, match);
        List<Span> spans = line.spans();
        assertEquals(2, spans.size());
        assertEquals("A", spans.get(0).content());
        assertEquals(match, spans.get(0).style());
        assertEquals("BC", spans.get(1).content());
        assertEquals(normal, spans.get(1).style());
    }

    @Test
    void highlightLineMatchAtEnd() {
        Style normal = Style.EMPTY;
        Style match = Style.EMPTY.fg(Color.RED);

        Line line = FuzzyFilter.highlightLine("ABC", new int[] { 2 }, normal, match);
        List<Span> spans = line.spans();
        assertEquals(2, spans.size());
        assertEquals("AB", spans.get(0).content());
        assertEquals("C", spans.get(1).content());
        assertEquals(match, spans.get(1).style());
    }

    @Test
    void highlightLineConsecutiveMatches() {
        Style normal = Style.EMPTY;
        Style match = Style.EMPTY.fg(Color.RED);

        Line line = FuzzyFilter.highlightLine("ABC", new int[] { 0, 1, 2 }, normal, match);
        List<Span> spans = line.spans();
        // Each matched char is its own span
        assertEquals(3, spans.size());
        for (Span span : spans) {
            assertEquals(match, span.style());
        }
    }

    @Test
    void highlightLineNullPositions() {
        Style normal = Style.EMPTY;
        Style match = Style.EMPTY.fg(Color.RED);

        Line line = FuzzyFilter.highlightLine("hello", null, normal, match);
        List<Span> spans = line.spans();
        assertEquals(1, spans.size());
        assertEquals("hello", spans.get(0).content());
        assertEquals(normal, spans.get(0).style());
    }

    @Test
    void highlightLineEmptyPositions() {
        Style normal = Style.EMPTY;
        Style match = Style.EMPTY.fg(Color.RED);

        Line line = FuzzyFilter.highlightLine("hello", new int[0], normal, match);
        List<Span> spans = line.spans();
        assertEquals(1, spans.size());
        assertEquals("hello", spans.get(0).content());
    }

    // ---- appendChar / deleteChar / clearFilter state management ----

    @Test
    void appendCharDeleteCharClearFilterRoundTrip() {
        FuzzyFilter filter = new FuzzyFilter();
        assertFalse(filter.hasFilter());
        assertEquals("", filter.filter());

        filter.appendChar('A');
        filter.appendChar('B');
        assertTrue(filter.hasFilter());
        assertEquals("ab", filter.filter()); // appendChar lowercases

        filter.deleteChar();
        assertEquals("a", filter.filter());

        filter.clearFilter();
        assertFalse(filter.hasFilter());
        assertEquals("", filter.filter());
    }

    @Test
    void deleteCharOnEmptyFilterIsNoop() {
        FuzzyFilter filter = new FuzzyFilter();
        filter.deleteChar(); // should not throw
        assertEquals("", filter.filter());
    }

    @Test
    void matchDelegatesToFuzzyMatch() {
        FuzzyFilter filter = new FuzzyFilter();
        filter.appendChar('h');
        filter.appendChar('l');
        int[] positions = filter.match("hello");
        assertNotNull(positions);
        assertArrayEquals(new int[] { 0, 2 }, positions);
    }
}
