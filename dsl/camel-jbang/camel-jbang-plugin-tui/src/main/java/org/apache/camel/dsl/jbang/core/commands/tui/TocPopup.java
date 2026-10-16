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
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;

/** The table of contents of the documentation viewer: the headings of the page, to jump to one. */
class TocPopup extends FilterListPopup<TocPopup.TocEntry> {

    record TocEntry(int level, String title, int charOffset) {
    }

    private List<TocEntry> allEntries;
    private TocEntry pendingEntry;

    void open(List<TocEntry> entries) {
        this.allEntries = entries;
        this.pendingEntry = null;
        show();
    }

    /** The heading chosen with Enter or a click, or null; cleared by the call. */
    TocEntry consumePendingEntry() {
        TocEntry e = pendingEntry;
        pendingEntry = null;
        return e;
    }

    @Override
    protected List<TocEntry> entries() {
        return allEntries;
    }

    @Override
    protected String name() {
        return "Table of Contents";
    }

    @Override
    protected String matchText(TocEntry entry) {
        return entry.title();
    }

    @Override
    protected boolean matches(TocEntry entry, String typed) {
        return matchesText(matchText(entry), typed);
    }

    @Override
    protected void chosen(TocEntry entry) {
        pendingEntry = entry;
    }

    @Override
    protected int overflowHeight(int available) {
        // a long page has many headings: the popup takes all the height there is
        return available;
    }

    @Override
    protected List<Line> rows(List<TocEntry> entries, int width) {
        int titleColWidth = width - 8;
        Style matchStyle = Theme.label().bold();
        List<Line> rows = new ArrayList<>();
        for (TocEntry entry : entries) {
            List<Span> spans = new ArrayList<>();
            String indent = entry.level() >= 3 ? "    " : "  ";
            String prefix = entry.level() >= 3 ? "└ " : "";
            String title = prefix + entry.title();
            if (title.length() > titleColWidth) {
                title = title.substring(0, titleColWidth - 1) + "…";
            }
            spans.add(Span.raw(indent));
            spans.addAll(highlighted(title, Style.EMPTY, matchStyle));
            rows.add(Line.from(spans));
        }
        return rows;
    }

    static List<TocEntry> extractHeadings(String markdown) {
        List<TocEntry> entries = new ArrayList<>();
        if (markdown == null) {
            return entries;
        }
        int offset = 0;
        for (String line : markdown.split("\n", -1)) {
            if (line.startsWith("### ")) {
                entries.add(new TocEntry(3, line.substring(4).trim(), offset));
            } else if (line.startsWith("## ")) {
                entries.add(new TocEntry(2, line.substring(3).trim(), offset));
            }
            offset += line.length() + 1;
        }
        return entries;
    }
}
