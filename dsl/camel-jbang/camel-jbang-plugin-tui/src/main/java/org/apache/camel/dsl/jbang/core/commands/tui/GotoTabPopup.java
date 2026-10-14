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
import java.util.function.Supplier;

import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;

/**
 * Go to Tab (Shift+F2): the tabs listed for the selected integration, by name or by what they show, with their digit or
 * More menu shortcut.
 */
class GotoTabPopup extends FilterListPopup<TabRegistry.TabEntry> {

    private static final int NAME_COL_WIDTH = 22;

    private List<TabRegistry.TabEntry> allEntries;
    private Runnable callback;
    private TabRegistry.TabEntry pendingEntry;
    private Supplier<IntegrationInfo> integrationSupplier;
    private Supplier<List<TabRegistry.MoreTab>> moreTabsSupplier;

    void setTabEntries(List<TabRegistry.TabEntry> entries, Runnable callback) {
        this.allEntries = entries;
        this.callback = callback;
    }

    /** Lists only the More tabs active for the selected integration, as the More menu does. */
    void setActiveTabFilter(
            Supplier<IntegrationInfo> integrationSupplier, Supplier<List<TabRegistry.MoreTab>> moreTabsSupplier) {
        this.integrationSupplier = integrationSupplier;
        this.moreTabsSupplier = moreTabsSupplier;
    }

    void open() {
        show();
    }

    /** The tab chosen, for the callback to go to; cleared by the call. */
    TabRegistry.TabEntry consumePendingEntry() {
        TabRegistry.TabEntry e = pendingEntry;
        pendingEntry = null;
        return e;
    }

    SelectionContext getSelectionContext() {
        if (!isVisible() || shown().isEmpty()) {
            return null;
        }
        List<String> items = shown().stream().map(TabRegistry.TabEntry::name).toList();
        Integer sel = selectedIndex();
        return new SelectionContext("popup", items, sel != null ? sel : -1, items.size(), "Go to Tab");
    }

    /** The tabs to list: all of them, less the More tabs not active for the selected integration. */
    @Override
    protected List<TabRegistry.TabEntry> entries() {
        if (allEntries == null || integrationSupplier == null || moreTabsSupplier == null) {
            return allEntries;
        }
        IntegrationInfo info = integrationSupplier.get();
        List<TabRegistry.MoreTab> moreTabs = moreTabsSupplier.get();
        List<TabRegistry.TabEntry> active = new ArrayList<>();
        for (TabRegistry.TabEntry entry : allEntries) {
            if (entry.moreIndex() >= 0 && entry.moreIndex() < moreTabs.size()) {
                TabRegistry.MoreTab mt = moreTabs.get(entry.moreIndex());
                if (!TabRegistry.isMoreTabActive(mt, info)) {
                    continue;
                }
            }
            active.add(entry);
        }
        return active;
    }

    @Override
    protected String name() {
        return "Go to Tab";
    }

    @Override
    protected String matchText(TabRegistry.TabEntry entry) {
        return entry.name();
    }

    @Override
    protected boolean matches(TabRegistry.TabEntry entry, String typed) {
        boolean descMatch = typed.length() >= 2 && entry.description() != null
                && entry.description().toLowerCase().contains(typed);
        return matchesText(entry.name(), typed) || descMatch;
    }

    @Override
    protected void chosen(TabRegistry.TabEntry entry) {
        if (callback != null) {
            pendingEntry = entry;
            callback.run();
        }
    }

    @Override
    protected int maxWidth() {
        return 110;
    }

    @Override
    protected int pageStep() {
        return 10;
    }

    @Override
    protected List<Line> rows(List<TabRegistry.TabEntry> entries, int width) {
        int descColWidth = width - NAME_COL_WIDTH - 8;
        Style matchStyle = Theme.label().bold();
        Style dimStyle = Style.EMPTY.dim();
        List<Line> rows = new ArrayList<>();
        for (TabRegistry.TabEntry entry : entries) {
            List<Span> spans = new ArrayList<>();
            String sc = entry.shortcut();
            spans.add(Span.raw(" "));
            spans.add(Span.styled(sc, Theme.mnemonic()));
            if (sc.length() < 2) {
                spans.add(Span.raw(" "));
            }
            spans.add(Span.raw(" " + entry.icon() + " "));
            String name = entry.name();
            if (name.length() > NAME_COL_WIDTH) {
                name = name.substring(0, NAME_COL_WIDTH);
            } else {
                name = String.format("%-" + NAME_COL_WIDTH + "s", name);
            }
            spans.addAll(highlighted(name, Style.EMPTY, matchStyle));
            String desc = entry.description();
            if (desc != null) {
                if (desc.length() > descColWidth) {
                    desc = desc.substring(0, Math.max(0, descColWidth - 1)) + "…";
                }
                spans.add(Span.styled(" ", dimStyle));
                spans.addAll(descriptionSpans(desc, dimStyle, matchStyle));
            }
            rows.add(Line.from(spans));
        }
        return rows;
    }

    /** The description, with the typed text marked where it appears in it (from two characters). */
    private List<Span> descriptionSpans(String desc, Style dimStyle, Style matchStyle) {
        String typed = filter.filter();
        int idx = typed.length() >= 2 ? desc.toLowerCase().indexOf(typed) : -1;
        if (idx < 0) {
            return List.of(Span.styled(desc, dimStyle));
        }
        List<Span> spans = new ArrayList<>();
        if (idx > 0) {
            spans.add(Span.styled(desc.substring(0, idx), dimStyle));
        }
        spans.add(Span.styled(desc.substring(idx, idx + typed.length()), matchStyle));
        if (idx + typed.length() < desc.length()) {
            spans.add(Span.styled(desc.substring(idx + typed.length()), dimStyle));
        }
        return spans;
    }
}
