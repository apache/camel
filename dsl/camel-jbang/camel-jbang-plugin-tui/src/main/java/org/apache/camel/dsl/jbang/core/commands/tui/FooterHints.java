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

import dev.tamboui.text.Span;

/**
 * Fits the key hints of the footer into its width. When they do not fit, whole hints are dropped: first the F-keys
 * every screen has (F10, F2, then F1), then the screen's own hints from the tail, then its own F-keys (such as Shift+F8
 * fix with AI, F12 file actions). What belongs to the screen goes last; its first hint (what Esc does) stays.
 */
final class FooterHints {

    private FooterHints() {
    }

    /**
     * Where the F-key hints go in a screen's footer: after its first hint, its most important one. While text is typed
     * the footer is the field with its own keys (Enter, Esc): the F-keys go after those, as after the first two spans
     * they would part the field from its keys, or cut it in two when the caret is inside it. The field's last key has
     * no gap after it, so one is added.
     */
    static int fKeyPosition(List<Span> spans, boolean typing) {
        if (!typing) {
            return Math.min(2, spans.size());
        }
        if (!spans.isEmpty() && !spans.get(spans.size() - 1).content().endsWith("  ")) {
            spans.add(Span.raw("  "));
        }
        return spans.size();
    }

    /**
     * @param  spans       the footer: the screen's hints, with the F-key hints inserted at {@code fKeyStart}; the hints
     *                     dropped are removed from it
     * @param  fKeyStart   where the F-key hints start; the size of the spans when there are none
     * @param  globalSpans the spans of the F-keys every screen has (F1, F2, F10), first among the F-keys
     * @param  screenSpans the spans of the screen's own F-keys, after the global ones
     * @param  width       the width of the spans
     * @param  available   the width of the footer
     * @return             the width of the spans after dropping
     */
    static int fit(List<Span> spans, int fKeyStart, int globalSpans, int screenSpans, int width, int available) {
        while (globalSpans > 0 && width > available) {
            width -= removePair(spans, fKeyStart + globalSpans - 2);
            globalSpans -= 2;
        }
        int fKeyEnd = fKeyStart + globalSpans + screenSpans;
        while (spans.size() > fKeyEnd && width > available) {
            width -= removeLastHint(spans, fKeyEnd);
        }
        while (screenSpans > 0 && width > available) {
            width -= removePair(spans, fKeyStart + globalSpans + screenSpans - 2);
            screenSpans -= 2;
        }
        // a footer without F-keys (a popup's) keeps two hints, as Esc is often the second
        while (spans.size() > 4 && width > available) {
            width -= removeLastHint(spans, 2);
        }
        return width;
    }

    /** Removes the hint whose key span is at the index, and its label. */
    private static int removePair(List<Span> spans, int keyIndex) {
        Span label = spans.remove(keyIndex + 1);
        Span key = spans.remove(keyIndex);
        return key.width() + label.width();
    }

    /**
     * Removes the last hint, from its key to the end: a hint is a key and its label, or more spans after the key (the
     * show BHPV toggles of the Errors tab), so it goes whole.
     */
    private static int removeLastHint(List<Span> spans, int floor) {
        int start = spans.size() - 1;
        while (start > floor && !Theme.hintKey().equals(spans.get(start).style())) {
            start--;
        }
        int width = 0;
        while (spans.size() > start) {
            width += spans.remove(spans.size() - 1).width();
        }
        return width;
    }
}
