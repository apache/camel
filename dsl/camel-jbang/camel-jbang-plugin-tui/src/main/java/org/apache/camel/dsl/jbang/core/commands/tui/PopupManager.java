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

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.CharWidth;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import dev.tamboui.widgets.Clear;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.list.ListItem;
import dev.tamboui.widgets.list.ListState;
import dev.tamboui.widgets.list.ListWidget;
import dev.tamboui.widgets.list.ScrollMode;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarState;

/**
 * Manages the switch-integration, more-tabs, and kill-confirm popups. Extracted from {@link CamelMonitor} to reduce
 * class size.
 */
class PopupManager {

    /**
     * Callback interface for actions the popup triggers in CamelMonitor.
     */
    interface PopupCallbacks {
        void selectMoreTab(int index);

        void resetIntegrationTabState();

        void refreshLogData();

        void stopSelectedProcess(boolean forceKill);
    }

    private final MonitorContext ctx;
    private final Supplier<List<IntegrationInfo>> nonVanishingIntegrationsSupplier;
    private final Supplier<List<TabRegistry.MoreTab>> moreTabsSupplier;
    private final PopupCallbacks callbacks;
    private final FilesBrowser filesBrowser;

    // "Switch integration" popup state
    private boolean showSwitchPopup;
    private final ListState switchPopupState = new ListState();

    // "More" dropdown state
    private boolean showMorePopup;
    private final ListState morePopupState = new ListState();
    private final ScrollbarState moreScrollbarState = new ScrollbarState();
    private int lastMoreSelection;
    private Line[] currentTabLabels;

    // Kill confirm
    private boolean showKillConfirm;

    // Last rendered popup rects for mouse hit-testing
    private Rect lastMorePopupRect;
    private Rect lastSwitchPopupRect;

    PopupManager(MonitorContext ctx, Supplier<List<IntegrationInfo>> nonVanishingIntegrationsSupplier,
                 Supplier<List<TabRegistry.MoreTab>> moreTabsSupplier,
                 FilesBrowser filesBrowser, PopupCallbacks callbacks) {
        this.ctx = ctx;
        this.nonVanishingIntegrationsSupplier = nonVanishingIntegrationsSupplier;
        this.moreTabsSupplier = moreTabsSupplier;
        this.filesBrowser = filesBrowser;
        this.callbacks = callbacks;
    }

    private List<TabRegistry.MoreTab> buildMoreVisualList() {
        List<TabRegistry.MoreTab> tabs = moreTabsSupplier.get();
        List<TabRegistry.MoreTab> visual = new ArrayList<>();
        String currentGroup = null;
        for (TabRegistry.MoreTab tab : tabs) {
            String group = tab.group();
            if (group != null && !group.equals(currentGroup)) {
                if (currentGroup != null) {
                    visual.add(null);
                }
                currentGroup = group;
            }
            visual.add(tab);
        }
        return visual;
    }

    private int moreVisualCount() {
        return buildMoreVisualList().size();
    }

    private boolean isMoreDividerIndex(int visualIndex) {
        List<TabRegistry.MoreTab> visual = buildMoreVisualList();
        return visualIndex >= 0 && visualIndex < visual.size() && visual.get(visualIndex) == null;
    }

    private int visualToMoreIndex(int visualIndex) {
        List<TabRegistry.MoreTab> visual = buildMoreVisualList();
        if (visualIndex < 0 || visualIndex >= visual.size() || visual.get(visualIndex) == null) {
            return -1;
        }
        int moreIndex = 0;
        for (int i = 0; i < visualIndex; i++) {
            if (visual.get(i) != null) {
                moreIndex++;
            }
        }
        return moreIndex;
    }

    private int moreToVisualIndex(int moreIndex) {
        List<TabRegistry.MoreTab> visual = buildMoreVisualList();
        int count = 0;
        for (int i = 0; i < visual.size(); i++) {
            if (visual.get(i) != null) {
                if (count == moreIndex) {
                    return i;
                }
                count++;
            }
        }
        return 0;
    }

    // ---- State queries ----

    boolean isAnyPopupVisible() {
        return showSwitchPopup || showMorePopup || showKillConfirm || filesBrowser.isVisible();
    }

    boolean isSwitchPopupVisible() {
        return showSwitchPopup;
    }

    boolean isMorePopupVisible() {
        return showMorePopup;
    }

    boolean isKillConfirmVisible() {
        return showKillConfirm;
    }

    int getLastMoreSelection() {
        return lastMoreSelection;
    }

    Line[] getCurrentTabLabels() {
        return currentTabLabels;
    }

    void setCurrentTabLabels(Line[] labels) {
        this.currentTabLabels = labels;
    }

    // ---- Open/close ----

    void openSwitchPopup(String currentPid, List<IntegrationInfo> integrations) {
        if (integrations.size() > 1) {
            showSwitchPopup = true;
            for (int i = 0; i < integrations.size(); i++) {
                if (integrations.get(i).pid.equals(currentPid)) {
                    switchPopupState.select(i);
                    break;
                }
            }
        }
    }

    void openMorePopup() {
        showMorePopup = !showMorePopup;
        if (showMorePopup) {
            morePopupState.select(lastMoreSelection);
        }
    }

    void dismissAll() {
        showMorePopup = false;
        showSwitchPopup = false;
        showKillConfirm = false;
        filesBrowser.reset();
    }

    void closeMorePopup() {
        showMorePopup = false;
    }

    void showKillConfirm() {
        showKillConfirm = true;
    }

    void selectMorePopupEntry(int moreIndex) {
        int visualIndex = moreToVisualIndex(moreIndex);
        morePopupState.select(visualIndex);
        lastMoreSelection = visualIndex;
    }

    // ---- Key handling ----

    boolean handleKeyEvent(KeyEvent ke, int selectedTab, int tabLog) {
        if (filesBrowser.isVisible()) {
            return filesBrowser.handleKeyEvent(ke);
        }
        if (showMorePopup) {
            return handleMorePopupKeys(ke);
        }
        if (showSwitchPopup) {
            return handleSwitchPopupKeys(ke, selectedTab, tabLog);
        }
        if (showKillConfirm) {
            return handleKillConfirmKeys(ke);
        }
        return false;
    }

    private boolean handleMorePopupKeys(KeyEvent ke) {
        if (ke.isCancel()) {
            showMorePopup = false;
            return true;
        }
        int total = moreVisualCount();
        if (ke.isUp()) {
            navigateMorePopup(-1, total);
            return true;
        }
        if (ke.isDown()) {
            navigateMorePopup(1, total);
            return true;
        }
        if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
            navigateMorePopupToSection(-1, total);
            return true;
        }
        if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
            navigateMorePopupToSection(1, total);
            return true;
        }
        if (ke.isHome() || ke.isKey(KeyCode.HOME)) {
            morePopupState.selectFirst();
            if (isMoreDividerIndex(0)) {
                navigateMorePopup(1, total);
            }
            return true;
        }
        if (ke.isEnd() || ke.isKey(KeyCode.END)) {
            morePopupState.selectLast(total);
            if (isMoreDividerIndex(total - 1)) {
                navigateMorePopup(-1, total);
            }
            return true;
        }
        int shortcutSel = morePopupShortcut(ke);
        if (shortcutSel >= 0) {
            int visualSel = moreToVisualIndex(shortcutSel);
            morePopupState.select(visualSel);
        }
        if (ke.isConfirm() || shortcutSel >= 0) {
            Integer visualSel = shortcutSel >= 0 ? moreToVisualIndex(shortcutSel) : morePopupState.selected();
            if (visualSel != null && !isMoreDividerIndex(visualSel)) {
                showMorePopup = false;
                int moreIdx = visualToMoreIndex(visualSel);
                if (moreIdx >= 0) {
                    callbacks.selectMoreTab(moreIdx);
                }
            }
            return true;
        }
        return true;
    }

    private void navigateMorePopup(int direction, int total) {
        Integer current = morePopupState.selected();
        int next = (current != null ? current : 0) + direction;
        next = Math.max(0, Math.min(next, total - 1));
        while (isMoreDividerIndex(next) && next > 0 && next < total - 1) {
            next += direction;
        }
        next = Math.max(0, Math.min(next, total - 1));
        if (!isMoreDividerIndex(next)) {
            morePopupState.select(next);
        }
    }

    private void navigateMorePopupToSection(int direction, int total) {
        Integer current = morePopupState.selected();
        int pos = current != null ? current : 0;
        pos += direction;
        while (pos > 0 && pos < total - 1 && !isMoreDividerIndex(pos)) {
            pos += direction;
        }
        if (isMoreDividerIndex(pos)) {
            pos += direction;
        }
        pos = Math.max(0, Math.min(pos, total - 1));
        if (!isMoreDividerIndex(pos)) {
            morePopupState.select(pos);
        }
    }

    private boolean handleSwitchPopupKeys(KeyEvent ke, int selectedTab, int tabLog) {
        if (ke.isCancel()) {
            showSwitchPopup = false;
            return true;
        }
        List<IntegrationInfo> switchList = nonVanishingIntegrationsSupplier.get();
        if (ke.isUp()) {
            switchPopupState.selectPrevious();
            return true;
        }
        if (ke.isDown()) {
            switchPopupState.selectNext(switchList.size());
            return true;
        }
        if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
            for (int i = 0; i < 5; i++) {
                switchPopupState.selectPrevious();
            }
            return true;
        }
        if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
            for (int i = 0; i < 5; i++) {
                switchPopupState.selectNext(switchList.size());
            }
            return true;
        }
        if (ke.isHome() || ke.isKey(KeyCode.HOME)) {
            switchPopupState.selectFirst();
            return true;
        }
        if (ke.isEnd() || ke.isKey(KeyCode.END)) {
            switchPopupState.selectLast(switchList.size());
            return true;
        }
        if (ke.isConfirm()) {
            showSwitchPopup = false;
            Integer sel = switchPopupState.selected();
            if (sel != null && sel >= 0 && sel < switchList.size()) {
                IntegrationInfo chosen = switchList.get(sel);
                ctx.selectedPid = chosen.pid;
                ctx.lastSelectedName = chosen.name;
                callbacks.resetIntegrationTabState();
                if (selectedTab == tabLog) {
                    callbacks.refreshLogData();
                }
            }
            return true;
        }
        return true;
    }

    private boolean handleKillConfirmKeys(KeyEvent ke) {
        if (ke.isConfirm()) {
            showKillConfirm = false;
            callbacks.stopSelectedProcess(true);
        } else {
            showKillConfirm = false;
        }
        return true;
    }

    // ---- Mouse handling ----

    boolean handleMouseEvent(MouseEvent me, int selectedTab, int tabLog) {
        if (showMorePopup) {
            return handleMorePopupMouse(me);
        }
        if (showSwitchPopup) {
            return handleSwitchPopupMouse(me, selectedTab, tabLog);
        }
        return false;
    }

    private boolean handleMorePopupMouse(MouseEvent me) {
        if (lastMorePopupRect == null) {
            return false;
        }
        boolean inside = TuiHelper.contains(lastMorePopupRect, me.x(), me.y());

        if (me.isClick() && !inside) {
            showMorePopup = false;
            return true;
        }
        if (!inside) {
            return true;
        }
        int total = moreVisualCount();
        int itemIndex = me.y() - lastMorePopupRect.y() - 1;
        if (itemIndex < 0 || itemIndex >= total) {
            return true;
        }
        if (me.kind() == MouseEventKind.SCROLL_UP) {
            navigateMorePopup(-1, total);
            return true;
        }
        if (me.kind() == MouseEventKind.SCROLL_DOWN) {
            navigateMorePopup(1, total);
            return true;
        }
        if (isMoreDividerIndex(itemIndex)) {
            return true;
        }
        if (me.isClick()) {
            showMorePopup = false;
            int moreIdx = visualToMoreIndex(itemIndex);
            if (moreIdx >= 0) {
                callbacks.selectMoreTab(moreIdx);
            }
            return true;
        }
        if (me.kind() == MouseEventKind.MOVE || me.kind() == MouseEventKind.DRAG) {
            morePopupState.select(itemIndex);
            return true;
        }
        return true;
    }

    private boolean handleSwitchPopupMouse(MouseEvent me, int selectedTab, int tabLog) {
        if (lastSwitchPopupRect == null) {
            return false;
        }
        List<IntegrationInfo> switchList = nonVanishingIntegrationsSupplier.get();
        boolean inside = TuiHelper.contains(lastSwitchPopupRect, me.x(), me.y());

        if (me.isClick() && !inside) {
            showSwitchPopup = false;
            return true;
        }
        if (!inside) {
            return true;
        }
        int itemIndex = me.y() - lastSwitchPopupRect.y() - 1; // -1 for top border
        if (itemIndex < 0 || itemIndex >= switchList.size()) {
            return true;
        }
        if (me.kind() == MouseEventKind.SCROLL_UP) {
            switchPopupState.selectPrevious();
            return true;
        }
        if (me.kind() == MouseEventKind.SCROLL_DOWN) {
            switchPopupState.selectNext(switchList.size());
            return true;
        }
        if (me.isClick()) {
            showSwitchPopup = false;
            IntegrationInfo chosen = switchList.get(itemIndex);
            ctx.selectedPid = chosen.pid;
            ctx.lastSelectedName = chosen.name;
            callbacks.resetIntegrationTabState();
            if (selectedTab == tabLog) {
                callbacks.refreshLogData();
            }
            return true;
        }
        if (me.kind() == MouseEventKind.MOVE || me.kind() == MouseEventKind.DRAG) {
            switchPopupState.select(itemIndex);
            return true;
        }
        return true;
    }

    // ---- Rendering ----

    void renderMorePopup(Frame frame, Rect area) {
        int popupW = 30;
        int itemCount = moreVisualCount();
        int popupH = Math.min(itemCount + 2, area.height());
        int dividerW = CharWidth.of(" | ");
        int tabBarX = 0;
        Line[] tabLabels = currentTabLabels;
        if (tabLabels != null) {
            for (int i = 0; i < tabLabels.length - 1; i++) {
                tabBarX += tabLabels[i].width();
                tabBarX += dividerW;
            }
        }
        int x = area.left() + tabBarX;
        int y = area.top();
        if (x + popupW > area.right()) {
            x = Math.max(area.left(), area.right() - popupW);
        }
        Rect popup = new Rect(x, y, Math.min(popupW, area.width() - (x - area.left())), popupH);
        lastMorePopupRect = popup;

        frame.renderWidget(Clear.INSTANCE, popup);

        Style keyStyle = Theme.mnemonic();
        ListItem[] items = morePopupItems(keyStyle);
        ListWidget list = ListWidget.builder()
                .items(items)
                .highlightStyle(Theme.selectionBg())
                .highlightSymbol("")
                .scrollMode(ScrollMode.AUTO_SCROLL)
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" " + TuiIcons.TAB_MORE + " More Tabs ")
                        .build())
                .build();
        frame.renderStatefulWidget(list, popup, morePopupState);

        int visibleRows = Math.max(1, popup.height() - 2);
        if (itemCount > visibleRows) {
            Integer sel = morePopupState.selected();
            moreScrollbarState
                    .contentLength(itemCount)
                    .viewportContentLength(visibleRows)
                    .position(sel != null ? sel : 0);
            frame.renderStatefulWidget(Scrollbar.builder().build(), popup, moreScrollbarState);
        }
    }

    private ListItem[] morePopupItems(Style keyStyle) {
        List<TabRegistry.MoreTab> visual = buildMoreVisualList();
        ListItem[] items = new ListItem[visual.size()];
        String currentGroup = null;
        for (int i = 0; i < visual.size(); i++) {
            TabRegistry.MoreTab tab = visual.get(i);
            if (tab == null) {
                String nextGroup = null;
                for (int j = i + 1; j < visual.size(); j++) {
                    if (visual.get(j) != null) {
                        nextGroup = visual.get(j).group();
                        break;
                    }
                }
                String label = nextGroup != null ? nextGroup : "";
                String divider = "  ─── " + label + " " + "─".repeat(Math.max(1, 20 - label.length()));
                items[i] = ListItem.from(divider).style(Style.EMPTY.dim());
            } else {
                String name = tab.displayName();
                String prefix = TuiIcons.indent(tab.icon());
                int keyPos = tab.mnemonicIndex();
                if (keyPos >= 0 && keyPos < name.length()) {
                    items[i] = ListItem.from(Line.from(
                            Span.raw(prefix + name.substring(0, keyPos)),
                            Span.styled(String.valueOf(name.charAt(keyPos)), keyStyle),
                            Span.raw(name.substring(keyPos + 1))));
                } else {
                    items[i] = ListItem.from(prefix + name);
                }
            }
        }
        return items;
    }

    void renderSwitchPopup(Frame frame, Rect area) {
        List<IntegrationInfo> integrations = nonVanishingIntegrationsSupplier.get();
        if (integrations.isEmpty()) {
            showSwitchPopup = false;
            return;
        }

        int maxLabelLen = integrations.stream()
                .mapToInt(i -> {
                    String n = i.name != null ? i.name : "?";
                    return n.length() + i.pid.length() + 14;
                })
                .max().orElse(30);
        int popupW = Math.min(area.width() - 4, Math.max(40, maxLabelLen + 4));
        int popupH = Math.min(area.height() - 4, integrations.size() + 2);

        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + 2;
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height() - 2));
        lastSwitchPopupRect = popup;

        frame.renderWidget(Clear.INSTANCE, popup);

        ListItem[] items = new ListItem[integrations.size()];
        for (int i = 0; i < integrations.size(); i++) {
            IntegrationInfo info = integrations.get(i);
            String name = info.name != null ? info.name : "?";
            boolean current = info.pid.equals(ctx.selectedPid);
            String label = String.format("  %s %s (pid:%s)%s", TuiIcons.CAMEL, name, info.pid,
                    current ? " " + TuiIcons.SELECTED : "");
            if (current) {
                items[i] = ListItem.from(Line.from(Span.styled(label, Style.EMPTY.fg(Theme.accent()))));
            } else {
                items[i] = ListItem.from(label);
            }
        }

        ListWidget listWidget = ListWidget.builder()
                .items(items)
                .highlightStyle(Theme.selectionBg())
                .highlightSymbol("")
                .scrollMode(ScrollMode.NONE)
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" Switch Integration ")
                        .build())
                .build();
        frame.renderStatefulWidget(listWidget, popup, switchPopupState);
    }

    void renderKillConfirm(Frame frame, Rect area) {
        String name = ctx.selectedName();
        String msg = " Kill " + name + " (PID: " + ctx.selectedPid + ")? ";
        int popupW = Math.max(34, msg.length() + 4);
        int popupH = 6;
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + Math.max(0, (area.height() - popupH) / 2);
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height()));

        frame.renderWidget(Clear.INSTANCE, popup);
        Block block = Block.builder()
                .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                .borderStyle(Theme.error())
                .title(" Confirm Kill ")
                .build();
        frame.renderWidget(block, popup);
        Rect inner = block.inner(popup);
        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(
                                Line.from(Span.raw("")),
                                Line.from(Span.styled(msg, Theme.error().bold())),
                                Line.from(Span.raw("")),
                                Line.from(
                                        Span.raw("  "),
                                        Span.styled("Enter", Style.EMPTY.bold()),
                                        Span.raw(" confirm  "),
                                        Span.styled("Esc", Style.EMPTY.bold()),
                                        Span.raw(" cancel"))))
                        .build(),
                inner);
    }

    int morePopupShortcut(KeyEvent ke) {
        List<TabRegistry.MoreTab> tabs = moreTabsSupplier.get();
        for (int i = 0; i < tabs.size(); i++) {
            int idx = tabs.get(i).mnemonicIndex();
            if (idx < 0) {
                continue;
            }
            char letter = tabs.get(i).displayName().charAt(idx);
            // trigger on either case so Shift+letter works too
            if (ke.isChar(Character.toLowerCase(letter)) || ke.isChar(Character.toUpperCase(letter))) {
                return i;
            }
        }
        return -1;
    }

    List<IntegrationInfo> getNonVanishingIntegrations() {
        return nonVanishingIntegrationsSupplier.get();
    }
}
