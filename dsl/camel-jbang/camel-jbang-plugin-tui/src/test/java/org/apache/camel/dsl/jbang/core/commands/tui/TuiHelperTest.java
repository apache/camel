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

import dev.tamboui.style.AnsiColor;
import dev.tamboui.style.Color;
import dev.tamboui.style.Modifier;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TuiHelperTest {

    // ---- stripAnsi tests ----

    @Test
    void stripAnsiCsiColorCodes() {
        // ESC[31m = red foreground, ESC[0m = reset
        String input = "[31mHello[0m";
        assertEquals("Hello", TuiHelper.stripAnsi(input));
    }

    @Test
    void stripAnsiTwoCharFeSequences() {
        // ESC M = Reverse Index (a 2-char Fe sequence)
        String input = "BeforeMAfter";
        assertEquals("BeforeAfter", TuiHelper.stripAnsi(input));
    }

    @Test
    void stripAnsiTabToEightSpaces() {
        String input = "A\tB";
        assertEquals("A        B", TuiHelper.stripAnsi(input));
    }

    @Test
    void stripAnsiCarriageReturnRemoved() {
        String input = "Hello\rWorld";
        assertEquals("HelloWorld", TuiHelper.stripAnsi(input));
    }

    @Test
    void stripAnsiPlainTextUnchanged() {
        String input = "No escape codes here";
        assertEquals("No escape codes here", TuiHelper.stripAnsi(input));
    }

    @Test
    void stripAnsiNullAndEmpty() {
        assertNull(TuiHelper.stripAnsi(null));
        assertEquals("", TuiHelper.stripAnsi(""));
    }

    @Test
    void stripAnsiDropsWindowTitle() {
        // OSC 0 sets the window title, ended by BEL
        assertEquals("text", TuiHelper.stripAnsi("\u001B]0;my title\u0007text"));
    }

    @Test
    void stripAnsiDropsHyperlinkTarget() {
        // OSC 8 hyperlinks, ended by ST (ESC \)
        assertEquals("see docs here",
                TuiHelper.stripAnsi("see \u001B]8;;https://camel.apache.org\u001B\\docs\u001B]8;;\u001B\\ here"));
    }

    @Test
    void stripAnsiDropsCharsetAndStringSequences() {
        // tput sgr0 writes ESC ( B before the reset; DCS strings end with ST
        assertEquals("plain", TuiHelper.stripAnsi("\u001B(B\u001B[mplain"));
        assertEquals("after", TuiHelper.stripAnsi("\u001BPq#0;2;0;0;0\u001B\\after"));
        assertEquals("cut", TuiHelper.stripAnsi("cut\u001B]0;title without end"));
    }

    @Test
    void stripAnsiCsiCutOffByNextEscape() {
        assertEquals("AB", TuiHelper.stripAnsi("A\u001B[31\u001B[0mB"));
    }

    // ---- ansiToLine tests ----

    @Test
    void ansiToLineDropsOscPayload() {
        Line line = TuiHelper.ansiToLine("\u001B]2;title\u0007\u001B[31mError\u001B[0m", 0);
        assertEquals("Error", rawContent(line));
        assertEquals(Color.ansi(AnsiColor.RED), line.spans().get(0).style().fg().orElse(null));
    }

    @Test
    void ansiToLineOscIsNotCountedWhenSkippingColumns() {
        Line line = TuiHelper.ansiToLine("\u001B]0;title\u0007ABCDEF", 2);
        assertEquals("CDEF", rawContent(line));
    }

    @Test
    void ansiToLineReverseAndStrikethrough() {
        Line line = TuiHelper.ansiToLine("\u001B[7mrev\u001B[27;9mstrike\u001B[29mplain", 0);
        List<Span> spans = line.spans();
        assertEquals(3, spans.size());
        assertTrue(spans.get(0).style().effectiveModifiers().contains(Modifier.REVERSED));
        assertFalse(spans.get(1).style().effectiveModifiers().contains(Modifier.REVERSED));
        assertTrue(spans.get(1).style().effectiveModifiers().contains(Modifier.CROSSED_OUT));
        assertFalse(spans.get(2).style().effectiveModifiers().contains(Modifier.CROSSED_OUT));
    }

    @Test
    void ansiToLineColonColors() {
        // the ITU T.416 form: 38:2:<color space>:r:g:b, 38:2:r:g:b and 48:5:n
        Line line = TuiHelper.ansiToLine(
                "\u001B[38:2::255:128:0mA\u001B[38:2:1:2:3mB\u001B[0;48:5:208mC\u001B[4:3mD\u001B[4:0mE", 0);
        List<Span> spans = line.spans();
        assertEquals("ABCDE", rawContent(line));
        assertEquals(Color.rgb(255, 128, 0), spans.get(0).style().fg().orElse(null));
        assertEquals(Color.rgb(1, 2, 3), spans.get(1).style().fg().orElse(null));
        assertFalse(spans.get(2).style().fg().isPresent());
        assertEquals(Color.indexed(208), spans.get(2).style().bg().orElse(null));
        assertTrue(spans.get(3).style().effectiveModifiers().contains(Modifier.UNDERLINED));
        assertFalse(spans.get(4).style().effectiveModifiers().contains(Modifier.UNDERLINED));
    }

    @Test
    void ansiToLineIgnoresPrivateCsiEndingInM() {
        // xterm's modifyOtherKeys setting, ESC [ > 4 ; 2 m, is not a color
        Line line = TuiHelper.ansiToLine("\u001B[>4;2mtext", 0);
        assertEquals("text", rawContent(line));
        assertFalse(line.spans().get(0).style().effectiveModifiers().contains(Modifier.DIM));
    }

    @Test
    void ansiToLineColorCodesProduceStyledSpans() {
        // ESC[31m = ANSI Red foreground
        String raw = "[31mError[0m text";
        Line line = TuiHelper.ansiToLine(raw, 0);
        List<Span> spans = line.spans();
        // Should have at least 2 spans: colored "Error" and unstyled " text"
        assertTrue(spans.size() >= 2);
        assertEquals("Error", spans.get(0).content());
        assertEquals(Color.ansi(AnsiColor.RED), spans.get(0).style().fg().orElse(null));
    }

    @Test
    void ansiToLineBoldItalicUnderline() {
        // ESC[1m = bold, ESC[3m = italic, ESC[4m = underline
        String raw = "[1;3;4mStyled[0m";
        Line line = TuiHelper.ansiToLine(raw, 0);
        List<Span> spans = line.spans();
        assertTrue(spans.size() >= 1);
        Style style = spans.get(0).style();
        assertTrue(style.effectiveModifiers().contains(Modifier.BOLD));
        assertTrue(style.effectiveModifiers().contains(Modifier.ITALIC));
        assertTrue(style.effectiveModifiers().contains(Modifier.UNDERLINED));
    }

    @Test
    void ansiToLine256Color() {
        // ESC[38;5;196m = 256-color index 196 (red)
        String raw = "[38;5;196mIndexed[0m";
        Line line = TuiHelper.ansiToLine(raw, 0);
        List<Span> spans = line.spans();
        assertTrue(spans.size() >= 1);
        assertEquals("Indexed", spans.get(0).content());
        assertTrue(spans.get(0).style().fg().isPresent());
    }

    @Test
    void ansiToLineRgbColor() {
        // ESC[38;2;255;128;0m = RGB color
        String raw = "[38;2;255;128;0mRGB[0m";
        Line line = TuiHelper.ansiToLine(raw, 0);
        List<Span> spans = line.spans();
        assertTrue(spans.size() >= 1);
        assertEquals("RGB", spans.get(0).content());
        assertEquals(Color.rgb(255, 128, 0), spans.get(0).style().fg().orElse(null));
    }

    @Test
    void ansiToLineResetClearsStyle() {
        String raw = "[1mBold[0m Normal";
        Line line = TuiHelper.ansiToLine(raw, 0);
        List<Span> spans = line.spans();
        assertTrue(spans.size() >= 2);
        assertTrue(spans.get(0).style().effectiveModifiers().contains(Modifier.BOLD));
        assertFalse(spans.get(1).style().effectiveModifiers().contains(Modifier.BOLD));
    }

    @Test
    void ansiToLineHSkipColumnsSkipped() {
        // With hSkip=3, the first 3 visible columns should be omitted
        String raw = "ABCDEF";
        Line line = TuiHelper.ansiToLine(raw, 3);
        String content = rawContent(line);
        assertEquals("DEF", content);
    }

    @Test
    void ansiToLineNullReturnsEmpty() {
        Line line = TuiHelper.ansiToLine(null, 0);
        assertEquals("", rawContent(line));
    }

    @Test
    void skipColumnsKeepsSpanStyles() {
        Style red = Style.EMPTY.fg(Color.RED);
        Line line = Line.from(Span.styled("abc", red), Span.raw("def"));

        Line skipped = TuiHelper.skipColumns(line, 2);

        assertEquals("cdef", rawContent(skipped));
        assertEquals(red, skipped.spans().get(0).style());
        assertEquals("", rawContent(TuiHelper.skipColumns(line, 10)));
        assertEquals(line, TuiHelper.skipColumns(line, 0));
    }

    @Test
    void skipColumnsCountsDisplayWidth() {
        // the CJK characters take two columns each
        assertEquals("日本x", rawContent(TuiHelper.skipColumns(Line.from(Span.raw("ab日本x")), 2)));
        assertEquals("本x", rawContent(TuiHelper.skipColumns(Line.from(Span.raw("日本x")), 2)));
    }

    @Test
    void listItemAtRowWalksItemsByTheirHeights() {
        int[] heights = { 1, 2, 3, 1 };
        assertEquals(0, TuiHelper.listItemAtRow(0, 0, heights));
        assertEquals(1, TuiHelper.listItemAtRow(2, 0, heights));
        assertEquals(2, TuiHelper.listItemAtRow(3, 0, heights));
        // scrolled by 4 rows: the list starts with the last two rows of item 2
        assertEquals(2, TuiHelper.listItemAtRow(0, 4, heights));
        assertEquals(2, TuiHelper.listItemAtRow(1, 4, heights));
        assertEquals(3, TuiHelper.listItemAtRow(2, 4, heights));
        assertEquals(-1, TuiHelper.listItemAtRow(3, 4, heights));
        assertEquals(-1, TuiHelper.listItemAtRow(-1, 4, heights));
    }

    @Test
    void tableRowAtLineStartsWithTheFirstRowEndingBelowTheOffset() {
        int[] twoLines = { 2, 2, 2, 2, 2 };
        assertEquals(0, TuiHelper.tableRowAtLine(1, 0, twoLines));
        assertEquals(1, TuiHelper.tableRowAtLine(2, 0, twoLines));
        // scrolled by 4 lines (two rows)
        assertEquals(2, TuiHelper.tableRowAtLine(0, 4, twoLines));
        assertEquals(3, TuiHelper.tableRowAtLine(3, 4, twoLines));
        // an offset inside row 1 (lines 2 and 3): the table draws row 1 in full at the top
        assertEquals(1, TuiHelper.tableRowAtLine(0, 3, twoLines));
        assertEquals(2, TuiHelper.tableRowAtLine(2, 3, twoLines));
        assertEquals(-1, TuiHelper.tableRowAtLine(6, 4, twoLines));
    }

    @Test
    void wrapWordsByDisplayWidth() {
        // each CJK character takes two columns: "日本語 日本語" is 13 columns, not 7
        assertEquals(List.of("日本語", "日本語 a"), TuiHelper.wrapWords("日本語 日本語 a", 8));
        assertEquals(List.of("one two", "three"), TuiHelper.wrapWords("one two three", 8));
    }

    // ---- shortTypeName tests ----

    @Test
    void shortTypeNameJavaLangPrefix() {
        assertEquals("String", TuiHelper.shortTypeName("java.lang.String"));
        assertEquals("Integer", TuiHelper.shortTypeName("java.lang.Integer"));
    }

    @Test
    void shortTypeNameJavaUtilPrefix() {
        assertEquals("HashMap", TuiHelper.shortTypeName("java.util.HashMap"));
        assertEquals("ArrayList", TuiHelper.shortTypeName("java.util.ArrayList"));
    }

    @Test
    void shortTypeNameJavaUtilConcurrentPrefix() {
        assertEquals("ConcurrentHashMap", TuiHelper.shortTypeName("java.util.concurrent.ConcurrentHashMap"));
    }

    @Test
    void shortTypeNameCamelLongType() {
        // This specific type is mapped to a short name
        assertEquals("WrappedInputStream",
                TuiHelper.shortTypeName("org.apache.camel.converter.stream.CachedOutputStream.WrappedInputStream"));
    }

    @Test
    void shortTypeNameCamelSupportPrefix() {
        assertEquals("DefaultExchange", TuiHelper.shortTypeName("org.apache.camel.support.DefaultExchange"));
    }

    @Test
    void shortTypeNameLongCustomType() {
        // Types longer than 34 chars are shortened to the last segment
        String longType = "com.example.very.long.package.name.MyClass";
        assertEquals("MyClass", TuiHelper.shortTypeName(longType));
    }

    @Test
    void shortTypeNameShortNamePassedThrough() {
        assertEquals("int", TuiHelper.shortTypeName("int"));
        assertEquals("byte[]", TuiHelper.shortTypeName("byte[]"));
    }

    @Test
    void shortTypeNameNull() {
        assertEquals("null", TuiHelper.shortTypeName(null));
    }

    @Test
    void shortTypeNameGroupedExchangeList() {
        assertEquals("GroupedExchangeList",
                TuiHelper.shortTypeName(
                        "org.apache.camel.processor.aggregate.AbstractListAggregationStrategy.GroupedExchangeList"));
    }

    @Test
    void shortTypeNameCachedOutputStream() {
        assertEquals("CachedOutputStream",
                TuiHelper.shortTypeName("org.apache.camel.converter.stream.CachedOutputStream"));
    }

    // ---- objToLong tests ----

    @Test
    void objToLongFromNumber() {
        assertEquals(42L, TuiHelper.objToLong(42));
        assertEquals(100L, TuiHelper.objToLong(100L));
    }

    @Test
    void objToLongFromString() {
        assertEquals(99L, TuiHelper.objToLong("99"));
    }

    @Test
    void objToLongFromNull() {
        assertEquals(0L, TuiHelper.objToLong(null));
    }

    @Test
    void objToLongFromInvalidString() {
        assertEquals(0L, TuiHelper.objToLong("not-a-number"));
    }

    private static String rawContent(Line line) {
        StringBuilder sb = new StringBuilder();
        for (Span span : line.spans()) {
            sb.append(span.content());
        }
        return sb.toString();
    }
}
