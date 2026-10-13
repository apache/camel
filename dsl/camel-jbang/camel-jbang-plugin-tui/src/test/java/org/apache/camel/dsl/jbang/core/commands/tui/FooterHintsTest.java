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

import java.util.ArrayList;
import java.util.List;

import dev.tamboui.style.Style;
import dev.tamboui.text.Span;
import org.junit.jupiter.api.Test;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hint;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A footer too wide for the screen drops the F-keys every screen has first, and what belongs to the screen last.
 */
class FooterHintsTest {

    /** Esc back, then the F-keys (F1, F2, F10 and the screen's own Shift+F8), then the screen's other hints. */
    private static List<Span> footer() {
        List<Span> spans = new ArrayList<>();
        hint(spans, "Esc", "back");
        hint(spans, "F1", "help");
        hint(spans, "F2", "actions");
        hint(spans, "F10", "run");
        hint(spans, "Shift+F8", "fix with AI");
        hint(spans, "/", "find");
        hint(spans, "h", "highlight");
        return spans;
    }

    @Test
    void theFKeysEveryScreenHasGoFirstLastOneFirst() {
        List<Span> spans = footer();
        int width = width(spans);

        int fitted = FooterHints.fit(spans, 2, 6, 2, width, width - hintWidth(spans, "F10"));
        assertThat(keys(spans)).containsExactly("Esc", "F1", "F2", "Shift+F8", "/", "h");
        assertThat(fitted).isEqualTo(width(spans));

        spans = footer();
        FooterHints.fit(spans, 2, 6, 2, width,
                width - hintWidth(spans, "F10") - hintWidth(spans, "F2") - hintWidth(spans, "F1"));
        assertThat(keys(spans)).containsExactly("Esc", "Shift+F8", "/", "h");
    }

    @Test
    void thenTheScreensHintsFromTheTailAndItsOwnFKeysLast() {
        List<Span> spans = footer();
        int width = width(spans);
        int globals = hintWidth(spans, "F10") + hintWidth(spans, "F2") + hintWidth(spans, "F1");

        FooterHints.fit(spans, 2, 6, 2, width, width - globals - hintWidth(spans, "h"));
        assertThat(keys(spans)).containsExactly("Esc", "Shift+F8", "/");

        spans = footer();
        FooterHints.fit(spans, 2, 6, 2, width, width - globals - hintWidth(spans, "h") - hintWidth(spans, "/"));
        assertThat(keys(spans)).containsExactly("Esc", "Shift+F8");
    }

    @Test
    void theFirstHintOfTheScreenStays() {
        List<Span> spans = footer();

        int fitted = FooterHints.fit(spans, 2, 6, 2, width(spans), 1);

        assertThat(keys(spans)).containsExactly("Esc");
        assertThat(fitted).isEqualTo(width(spans));
    }

    @Test
    void aHintOfSeveralSpansGoesWhole() {
        List<Span> spans = new ArrayList<>();
        hint(spans, "Esc", "back");
        hint(spans, "F2", "actions");
        hint(spans, "F10", "run");
        hint(spans, "s", "sort");
        // as the Errors tab draws it: the key, then the toggles
        spans.add(Span.styled(" show ", Theme.hintKey()));
        spans.add(Span.raw(" "));
        for (String toggle : List.of("B", "h", "p", "v")) {
            spans.add(Span.styled(toggle, Style.EMPTY.dim()));
        }
        spans.add(Span.raw("  "));
        int width = width(spans);

        FooterHints.fit(spans, 2, 4, 0, width,
                width - hintWidth(spans, "F10") - hintWidth(spans, "F2") - 1);

        assertThat(keys(spans)).containsExactly("Esc", "s");
        assertThat(spans).hasSize(4).noneMatch(s -> s.content().equals("B"));
    }

    @Test
    void aFooterThatFitsIsLeftAlone() {
        List<Span> spans = footer();
        int width = width(spans);

        assertThat(FooterHints.fit(spans, 2, 6, 2, width, width)).isEqualTo(width);
        assertThat(keys(spans)).containsExactly("Esc", "F1", "F2", "F10", "Shift+F8", "/", "h");
    }

    @Test
    void aFooterWithoutFKeysKeepsTwoHints() {
        List<Span> spans = new ArrayList<>();
        hint(spans, "Enter", "select");
        hint(spans, "Esc", "close");
        hint(spans, "Tab", "next");

        FooterHints.fit(spans, spans.size(), 0, 0, width(spans), 1);

        assertThat(keys(spans)).containsExactly("Enter", "Esc");
    }

    private static List<String> keys(List<Span> spans) {
        return spans.stream().filter(s -> Theme.hintKey().equals(s.style())).map(s -> s.content().strip()).toList();
    }

    private static int width(List<Span> spans) {
        return spans.stream().mapToInt(Span::width).sum();
    }

    private static int hintWidth(List<Span> spans, String key) {
        for (int i = 0; i + 1 < spans.size(); i++) {
            if (spans.get(i).content().strip().equals(key)) {
                return spans.get(i).width() + spans.get(i + 1).width();
            }
        }
        throw new IllegalArgumentException("no hint for key " + key);
    }
}
