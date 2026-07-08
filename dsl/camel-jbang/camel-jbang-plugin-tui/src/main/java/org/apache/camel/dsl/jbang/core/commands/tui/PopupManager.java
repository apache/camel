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
import java.util.function.Supplier;

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
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
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.list.ListItem;
import dev.tamboui.widgets.list.ListState;
import dev.tamboui.widgets.list.ListWidget;
import dev.tamboui.widgets.list.ScrollMode;
import dev.tamboui.widgets.paragraph.Paragraph;

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

    private int moreTabCount() {
        return moreTabsSupplier.get().size();
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

    void closeMorePopup() {
        showMorePopup = false;
    }

    void showKillConfirm() {
        showKillConfirm = true;
    }

    void selectMorePopupEntry(int index) {
        morePopupState.select(index);
        lastMoreSelection = index;
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
        if (ke.isUp()) {
            morePopupState.selectPrevious();
            return true;
        }
        if (ke.isDown()) {
            morePopupState.selectNext(moreTabCount());
            return true;
        }
        if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
            for (int i = 0; i < 5; i++) {
                morePopupState.selectPrevious();
            }
            return true;
        }
        if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
            for (int i = 0; i < 5; i++) {
                morePopupState.selectNext(moreTabCount());
            }
            return true;
        }
        if (ke.isHome() || ke.isKey(KeyCode.HOME)) {
            morePopupState.selectFirst();
            return true;
        }
        if (ke.isEnd() || ke.isKey(KeyCode.END)) {
            morePopupState.selectLast(moreTabCount());
            return true;
        }
        int shortcutSel = morePopupShortcut(ke);
        if (shortcutSel >= 0) {
            morePopupState.select(shortcutSel);
        }
        if (ke.isConfirm() || shortcutSel >= 0) {
            showMorePopup = false;
            Integer sel = shortcutSel >= 0 ? shortcutSel : morePopupState.selected();
            if (sel != null) {
                callbacks.selectMoreTab(sel);
            }
            return true;
        }
        return true;
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

        // Click outside the popup closes it
        if (me.isClick() && !inside) {
            showMorePopup = false;
            return true;
        }
        if (!inside) {
            return true; // consume but ignore drags/scrolls outside
        }
        // Inside the popup: items start at y+1 (after border top row) and each is 1 row
        int itemIndex = me.y() - lastMorePopupRect.y() - 1; // -1 for top border
        if (itemIndex < 0 || itemIndex >= moreTabCount()) {
            return true; // click on border area
        }
        if (me.kind() == MouseEventKind.SCROLL_UP) {
            morePopupState.selectPrevious();
            return true;
        }
        if (me.kind() == MouseEventKind.SCROLL_DOWN) {
            morePopupState.selectNext(moreTabCount());
            return true;
        }
        if (me.isClick()) {
            showMorePopup = false;
            callbacks.selectMoreTab(itemIndex);
            return true;
        }
        // Hover/move: highlight the item under the cursor
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
        int popupW = 28;
        int popupH = 21;
        // Position just below the More tab label
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
        Rect popup = new Rect(x, y, Math.min(popupW, area.width() - (x - area.left())), Math.min(popupH, area.height()));
        lastMorePopupRect = popup;

        frame.renderWidget(Clear.INSTANCE, popup);

        Style keyStyle = Theme.label().bold();
        ListItem[] items = morePopupItems(keyStyle);
        ListWidget list = ListWidget.builder()
                .items(items)
                .highlightStyle(Theme.selectionBg())
                .highlightSymbol("")
                .scrollMode(ScrollMode.NONE)
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(Title.from(Line.from(Span.styled(
                                " " + TuiIcons.TAB_MORE + " More Tabs ",
                                Theme.label().bold()))))
                        .build())
                .build();
        frame.renderStatefulWidget(list, popup, morePopupState);
    }

    private ListItem[] morePopupItems(Style keyStyle) {
        List<TabRegistry.MoreTab> tabs = moreTabsSupplier.get();
        ListItem[] items = new ListItem[tabs.size()];
        for (int i = 0; i < tabs.size(); i++) {
            TabRegistry.MoreTab tab = tabs.get(i);
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
                items[i] = ListItem.from(Line.from(Span.styled(label, Style.EMPTY.fg(Color.CYAN))));
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
                        .title(Title.from(
                                Line.from(Span.styled(" Switch Integration ", Theme.label().bold()))))
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
