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

    // ---- ansiToLine tests ----

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
