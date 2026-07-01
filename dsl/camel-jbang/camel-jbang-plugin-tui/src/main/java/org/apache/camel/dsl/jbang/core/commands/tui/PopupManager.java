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
import dev.tamboui.tui.event.KeyEvent;
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

    PopupManager(MonitorContext ctx, Supplier<List<IntegrationInfo>> nonVanishingIntegrationsSupplier,
                 FilesBrowser filesBrowser, PopupCallbacks callbacks) {
        this.ctx = ctx;
        this.nonVanishingIntegrationsSupplier = nonVanishingIntegrationsSupplier;
        this.filesBrowser = filesBrowser;
        this.callbacks = callbacks;
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
            morePopupState.selectNext(17);
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

    // ---- Rendering ----

    void renderMorePopup(Frame frame, Rect area) {
        int popupW = 22;
        int popupH = 19;
        // Position just below the "0 More▾" tab label
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

        frame.renderWidget(Clear.INSTANCE, popup);

        Style keyStyle = Style.EMPTY.fg(Color.YELLOW).bold();
        ListItem[] items = {
                ListItem.from(Line.from(Span.raw("  "), Span.styled("B", keyStyle), Span.raw("eans"))),
                ListItem.from(Line.from(Span.raw("  Bro"), Span.styled("w", keyStyle), Span.raw("se"))),
                ListItem.from(Line.from(Span.raw("  "), Span.styled("C", keyStyle), Span.raw("ircuit Breaker"))),
                ListItem.from(Line.from(Span.raw("  Cl"), Span.styled("a", keyStyle), Span.raw("sspath"))),
                ListItem.from(Line.from(Span.raw("  Confi"), Span.styled("g", keyStyle), Span.raw("uration"))),
                ListItem.from(Line.from(Span.raw("  Co"), Span.styled("n", keyStyle), Span.raw("sumers"))),
                ListItem.from(Line.from(Span.raw("  "), Span.styled("D", keyStyle), Span.raw("ataSource"))),
                ListItem.from(Line.from(Span.raw("  "), Span.styled("H", keyStyle), Span.raw("eap Histogram"))),
                ListItem.from(Line.from(Span.raw("  "), Span.styled("I", keyStyle), Span.raw("nflight"))),
                ListItem.from(Line.from(Span.raw("  "), Span.styled("M", keyStyle), Span.raw("emory"))),
                ListItem.from(Line.from(Span.raw("  M"), Span.styled("e", keyStyle), Span.raw("trics"))),
                ListItem.from(Line.from(Span.raw("  S"), Span.styled("Q", keyStyle), Span.raw("L Query"))),
                ListItem.from(Line.from(Span.raw("  SQL T"), Span.styled("r", keyStyle), Span.raw("ace"))),
                ListItem.from(Line.from(Span.raw("  "), Span.styled("O", keyStyle), Span.raw("Tel Spans"))),
                ListItem.from(Line.from(Span.raw("  "), Span.styled("P", keyStyle), Span.raw("rocess"))),
                ListItem.from(Line.from(Span.raw("  "), Span.styled("S", keyStyle), Span.raw("tartup"))),
                ListItem.from(Line.from(Span.raw("  "), Span.styled("T", keyStyle), Span.raw("hreads"))),
        };
        ListWidget list = ListWidget.builder()
                .items(items)
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSymbol("")
                .scrollMode(ScrollMode.NONE)
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(Title.from(Line.from(Span.styled(" More Tabs ", Style.EMPTY.fg(Color.YELLOW).bold()))))
                        .build())
                .build();
        frame.renderStatefulWidget(list, popup, morePopupState);
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

        frame.renderWidget(Clear.INSTANCE, popup);

        ListItem[] items = new ListItem[integrations.size()];
        for (int i = 0; i < integrations.size(); i++) {
            IntegrationInfo info = integrations.get(i);
            String name = info.name != null ? info.name : "?";
            boolean current = info.pid.equals(ctx.selectedPid);
            String label = String.format("  🐪 %s (pid:%s)%s", name, info.pid, current ? " ●" : "");
            if (current) {
                items[i] = ListItem.from(Line.from(Span.styled(label, Style.EMPTY.fg(Color.CYAN))));
            } else {
                items[i] = ListItem.from(label);
            }
        }

        ListWidget listWidget = ListWidget.builder()
                .items(items)
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSymbol("")
                .scrollMode(ScrollMode.NONE)
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(Title.from(
                                Line.from(Span.styled(" Switch Integration ", Style.EMPTY.fg(Color.YELLOW).bold()))))
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
                .borderStyle(Style.EMPTY.fg(Color.LIGHT_RED))
                .title(" Confirm Kill ")
                .build();
        frame.renderWidget(block, popup);
        Rect inner = block.inner(popup);
        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(
                                Line.from(Span.raw("")),
                                Line.from(Span.styled(msg, Style.EMPTY.fg(Color.LIGHT_RED).bold())),
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

    // ---- Static utilities ----

    static int morePopupShortcut(KeyEvent ke) {
        if (ke.isChar('b')) {
            return 0;
        }
        if (ke.isChar('w')) {
            return 1;
        }
        if (ke.isChar('c')) {
            return 2;
        }
        if (ke.isChar('a')) {
            return 3;
        }
        if (ke.isChar('g')) {
            return 4;
        }
        if (ke.isChar('n')) {
            return 5;
        }
        if (ke.isChar('d')) {
            return 6;
        }
        if (ke.isChar('h')) {
            return 7;
        }
        if (ke.isChar('i')) {
            return 8;
        }
        if (ke.isChar('m')) {
            return 9;
        }
        if (ke.isChar('e')) {
            return 10;
        }
        if (ke.isChar('q')) {
            return 11;
        }
        if (ke.isChar('r')) {
            return 12;
        }
        if (ke.isChar('o')) {
            return 13;
        }
        if (ke.isChar('p')) {
            return 14;
        }
        if (ke.isChar('s')) {
            return 15;
        }
        if (ke.isChar('t')) {
            return 16;
        }
        return -1;
    }

    List<IntegrationInfo> getNonVanishingIntegrations() {
        return nonVanishingIntegrationsSupplier.get();
    }
}
