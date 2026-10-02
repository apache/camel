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

import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A wrapped detail line continues under its value or its indent, not at the left edge.
 */
class HangingWrapTest {

    @Test
    void aHeaderValueContinuesUnderTheValue() {
        Line header = Line.from(Span.styled("   CamelFileAbsolutePath", Theme.muted()),
                Span.raw(" = /private/var/folders/33/4s84/T/camel-example-75917/orders/order-1003.json"));
        List<String> rows = text(TuiHelper.hangingWrap(List.of(header), 60));

        assertThat(rows).hasSizeGreaterThan(1);
        int value = rows.get(0).indexOf("= ") + 2;
        assertThat(rows.get(1)).startsWith(" ".repeat(value)).doesNotStartWith(" ".repeat(value + 1));
        assertThat(String.join("", rows).replace(" ", ""))
                .isEqualTo(text(List.of(header)).get(0).replace(" ", ""));
        assertThat(rows).allMatch(r -> r.length() <= 60);
    }

    @Test
    void anIndentedLineContinuesUnderItsIndent() {
        Line hint = Line.from(Span.raw(" not grouped yet: /overview groups the routes into capabilities"));
        List<String> rows = text(TuiHelper.hangingWrap(List.of(hint), 30));
        assertThat(rows.get(0)).startsWith(" not grouped");
        assertThat(rows.subList(1, rows.size())).allMatch(r -> r.startsWith(" ") && !r.startsWith("  "));
    }

    @Test
    void stylesAreKeptAndShortLinesUntouched() {
        Style red = Theme.error();
        Line line = Line.from(Span.styled("error: ", red), Span.raw("a b c d e f g h i j k l m n o p q r s t u v w"));
        List<Line> wrapped = TuiHelper.hangingWrap(List.of(line), 20);
        assertThat(wrapped.get(0).spans().get(0).style()).isEqualTo(red);

        Line shortLine = Line.from(Span.raw("short"));
        assertThat(TuiHelper.hangingWrap(List.of(shortLine), 20).get(0)).isSameAs(shortLine);
    }

    @Test
    void aWordLongerThanTheRowIsCut() {
        List<String> rows = text(TuiHelper.hangingWrap(List.of(Line.from(Span.raw("x".repeat(50)))), 20));
        assertThat(rows).containsExactly("x".repeat(20), "x".repeat(20), "x".repeat(10));
    }

    private static List<String> text(List<Line> lines) {
        return lines.stream().map(l -> l.spans().stream().map(Span::content).reduce("", String::concat)).toList();
    }
}
