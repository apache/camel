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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
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
import dev.tamboui.widgets.input.TextInput;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.list.ListItem;
import dev.tamboui.widgets.list.ListState;
import dev.tamboui.widgets.list.ListWidget;
import dev.tamboui.widgets.list.ScrollMode;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.common.LauncherHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

class InfraBrowserPopup {

    record InfraServiceEntry(String alias, String description, List<String> implementations, boolean running) {
    }

    private boolean showBrowser;
    private boolean showPortDialog;
    private final ListState browserState = new ListState();
    private Rect popupRect;
    private int[] itemHeights;
    private List<InfraServiceEntry> catalog;
    private InfraServiceEntry selectedService;
    private int implIndex;
    private int portDialogRow; // 0 = impl, 1 = port
    private TextInputState portState;

    private final Supplier<List<InfraInfo>> infraServices;
    private final LaunchManager launchManager;
    private Runnable burstCallback;
    private BiConsumer<String, Boolean> notificationCallback;

    InfraBrowserPopup(Supplier<List<InfraInfo>> infraServices, LaunchManager launchManager) {
        this.infraServices = infraServices;
        this.launchManager = launchManager;
    }

    void setBurstCallback(Runnable burstCallback) {
        this.burstCallback = burstCallback;
    }

    void setNotificationCallback(BiConsumer<String, Boolean> callback) {
        this.notificationCallback = callback;
    }

    boolean isVisible() {
        return showBrowser || showPortDialog;
    }

    boolean isBrowserVisible() {
        return showBrowser;
    }

    void clearCatalog() {
        catalog = null;
    }

    void open() {
        if (!LaunchManager.isContainerRuntimeAvailable()) {
            notify("Docker or Podman is not running (use F2 → Run Doctor to check)", true);
            return;
        }
        if (catalog == null) {
            catalog = loadCatalog();
        }
        if (catalog.isEmpty()) {
            notify("No infra services found", true);
            return;
        }
        showBrowser = true;
        browserState.select(0);
        if (!catalog.isEmpty() && catalog.get(0).running()) {
            navigate(1);
        }
    }

    void close() {
        showBrowser = false;
        showPortDialog = false;
    }

    boolean handleKeyEvent(KeyEvent ke) {
        if (showPortDialog) {
            return handlePortDialogKey(ke);
        }
        if (showBrowser) {
            return handleBrowserKey(ke);
        }
        return false;
    }

    boolean handleMouseEvent(MouseEvent me) {
        if (!showBrowser) {
            return true;
        }
        if (me.kind() == MouseEventKind.SCROLL_UP) {
            navigate(-1);
            return true;
        }
        if (me.kind() == MouseEventKind.SCROLL_DOWN) {
            navigate(1);
            return true;
        }
        if (me.isClick()) {
            if (popupRect != null && popupRect.contains(me.x(), me.y())) {
                int idx = itemAtMouseY(me.y());
                if (idx >= 0 && catalog != null && idx < catalog.size() && !catalog.get(idx).running()) {
                    browserState.select(idx);
                }
                return true;
            }
            close();
            return true;
        }
        return true;
    }

    private int itemAtMouseY(int mouseY) {
        if (popupRect == null || itemHeights == null) {
            return -1;
        }
        int firstRow = popupRect.top() + 1;
        int relY = mouseY - firstRow;
        if (relY < 0) {
            return -1;
        }
        int offset = browserState.offset();
        int rowAcc = 0;
        for (int i = offset; i < itemHeights.length; i++) {
            rowAcc += itemHeights[i];
            if (relY < rowAcc) {
                return i;
            }
        }
        return -1;
    }

    void render(Frame frame, Rect area) {
        if (showBrowser) {
            renderBrowser(frame, area);
        }
        if (showPortDialog) {
            renderPortDialog(frame, area);
        }
    }

    void renderFooter(List<Span> spans) {
        if (showPortDialog) {
            boolean hasMultiImpl = selectedService != null && selectedService.implementations().size() > 1;
            if (hasMultiImpl) {
                TuiHelper.hint(spans, TuiIcons.HINT_SCROLL, "navigate");
                if (portDialogRow == 0) {
                    TuiHelper.hint(spans, "Space", "cycle");
                }
            }
            TuiHelper.hint(spans, "Enter", "run");
            TuiHelper.hintLast(spans, "Esc", "back");
        } else if (showBrowser) {
            TuiHelper.hint(spans, "↑↓", "navigate");
            TuiHelper.hint(spans, "Enter", "select");
            TuiHelper.hintLast(spans, "Esc", "back");
        }
    }

    SelectionContext getSelectionContext() {
        if (!showBrowser || catalog == null) {
            return null;
        }
        List<String> items = catalog.stream().map(InfraServiceEntry::alias).collect(Collectors.toList());
        Integer sel = browserState.selected();
        return new SelectionContext("list", items, sel != null ? sel : -1, catalog.size(), "Dev/Infra Services");
    }

    // ---- Key handling ----

    private boolean handleBrowserKey(KeyEvent ke) {
        if (ke.isCancel()) {
            showBrowser = false;
            return true;
        }
        if (ke.isUp()) {
            navigate(-1);
            return true;
        }
        if (ke.isDown()) {
            navigate(1);
            return true;
        }
        if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
            navigate(-10);
            return true;
        }
        if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
            navigate(10);
            return true;
        }
        if (ke.isConfirm()) {
            selectService();
            return true;
        }
        if (ke.code() == KeyCode.CHAR) {
            jumpTo(ke.string().charAt(0));
            return true;
        }
        return true;
    }

    private boolean handlePortDialogKey(KeyEvent ke) {
        if (ke.isCancel()) {
            showPortDialog = false;
            showBrowser = true;
            return true;
        }
        if (ke.isConfirm()) {
            launchService();
            return true;
        }
        boolean hasMultiImpl = selectedService != null && selectedService.implementations().size() > 1;
        if (ke.isUp() || ke.isFocusPrevious()) {
            if (hasMultiImpl && portDialogRow > 0) {
                portDialogRow--;
            }
            return true;
        }
        if (ke.isDown() || ke.isFocusNext()) {
            if (hasMultiImpl && portDialogRow < 1) {
                portDialogRow++;
            }
            return true;
        }
        if (portDialogRow == 0 && hasMultiImpl) {
            if (ke.isLeft() || ke.isChar(' ')) {
                implIndex = (implIndex - 1 + selectedService.implementations().size())
                            % selectedService.implementations().size();
                return true;
            }
            if (ke.isRight()) {
                implIndex = (implIndex + 1) % selectedService.implementations().size();
                return true;
            }
            return true;
        }
        handlePortInput(ke);
        return true;
    }

    // ---- Navigation ----

    private void navigate(int direction) {
        if (catalog == null || catalog.isEmpty()) {
            return;
        }
        int total = catalog.size();
        Integer current = browserState.selected();
        int next = (current != null ? current : 0) + direction;
        next = Math.max(0, Math.min(next, total - 1));
        while (next >= 0 && next < total && catalog.get(next).running()) {
            next += direction;
        }
        next = Math.max(0, Math.min(next, total - 1));
        if (!catalog.get(next).running()) {
            browserState.select(next);
        }
    }

    private void selectService() {
        Integer sel = browserState.selected();
        if (sel == null || sel >= catalog.size()) {
            return;
        }
        InfraServiceEntry entry = catalog.get(sel);
        if (entry.running()) {
            return;
        }
        selectedService = entry;
        implIndex = 0;
        portDialogRow = entry.implementations().size() > 1 ? 0 : 1;
        portState = new TextInputState("");
        showBrowser = false;
        showPortDialog = true;
    }

    private void jumpTo(char ch) {
        if (catalog == null) {
            return;
        }
        char lower = Character.toLowerCase(ch);
        for (int i = 0; i < catalog.size(); i++) {
            if (catalog.get(i).alias().toLowerCase().startsWith(String.valueOf(lower))) {
                browserState.select(i);
                browserState.setOffset(Math.max(0, i - 2));
                return;
            }
        }
    }

    // ---- Rendering ----

    private void renderBrowser(Frame frame, Rect area) {
        if (catalog == null || catalog.isEmpty()) {
            return;
        }
        refreshRunningState();
        int popupW = Math.min(100, area.width() - 4);
        int popupH = Math.min(catalog.size() + 2, Math.min(22, area.height() - 4));
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + 2;
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height() - 2));
        this.popupRect = popup;

        frame.renderWidget(Clear.INSTANCE, popup);

        int nameCol = 22;
        List<ListItem> items = new ArrayList<>();
        List<Integer> heights = new ArrayList<>();
        for (InfraServiceEntry entry : catalog) {
            String padded = String.format("%-" + nameCol + "s", TuiHelper.truncate(entry.alias(), nameCol));
            String prefix = TuiIcons.indent(TuiIcons.INFRA) + padded + " ";
            if (entry.running()) {
                items.add(ListItem.from(prefix + "(running)").style(Style.EMPTY.dim()));
                heights.add(1);
            } else {
                String implStr = entry.implementations().isEmpty() ? "" : String.join(", ", entry.implementations());
                String desc = entry.description();
                if (!implStr.isEmpty()) {
                    desc = desc + " [" + implStr + "]";
                }
                int descW = Math.max(10, popupW - prefix.length() - 2);
                if (desc.length() <= descW) {
                    items.add(ListItem.from(prefix + desc));
                    heights.add(1);
                } else {
                    String indent = " ".repeat(prefix.length());
                    List<Line> lines = new ArrayList<>();
                    List<String> wrapped = TuiHelper.wrapWords(desc, descW);
                    lines.add(Line.from(prefix + wrapped.get(0)));
                    for (int w = 1; w < wrapped.size(); w++) {
                        lines.add(Line.from(indent + wrapped.get(w)));
                    }
                    items.add(ListItem.from(Text.from(lines.toArray(Line[]::new))));
                    heights.add(wrapped.size());
                }
            }
        }
        this.itemHeights = heights.stream().mapToInt(Integer::intValue).toArray();

        long available = catalog.stream().filter(e -> !e.running()).count();
        ListWidget list = ListWidget.builder()
                .items(items.toArray(ListItem[]::new))
                .highlightStyle(Theme.selectionBg())
                .highlightSymbol("")
                .scrollMode(ScrollMode.AUTO_SCROLL)
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" Run Dev/Infra Service [" + available + "/" + catalog.size() + "] ")
                        .build())
                .build();
        frame.renderStatefulWidget(list, popup, browserState);
    }

    private void renderPortDialog(Frame frame, Rect area) {
        if (selectedService == null) {
            return;
        }
        boolean hasMultiImpl = selectedService.implementations().size() > 1;
        int popupW = 42;
        int popupH = hasMultiImpl ? 8 : 6;
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + 2;
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height()));

        frame.renderWidget(Clear.INSTANCE, popup);

        Block block = Block.builder()
                .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                .title(" Run " + selectedService.alias() + " ")
                .build();
        frame.renderWidget(block, popup);
        Rect inner = block.inner(popup);

        int labelW = 8;
        int fieldW = inner.width() - labelW;
        int row = inner.top();
        int ix = inner.left();

        if (hasMultiImpl) {
            row++;
            Style implLabelStyle = portDialogRow == 0 ? Style.EMPTY.bold() : Style.EMPTY.dim();
            Rect labelArea = new Rect(ix, row, labelW, 1);
            frame.renderWidget(Paragraph.from(Line.from(Span.styled("Impl:", implLabelStyle))), labelArea);
            Rect implArea = new Rect(ix + labelW, row, fieldW, 1);
            List<Span> implSpans = new ArrayList<>();
            for (int i = 0; i < selectedService.implementations().size(); i++) {
                if (i > 0) {
                    implSpans.add(Span.styled(" ", Style.EMPTY));
                }
                String label = selectedService.implementations().get(i);
                if (i == implIndex) {
                    implSpans.add(Span.styled("[" + label + "]",
                            portDialogRow == 0 ? Style.EMPTY.bold() : Style.EMPTY));
                } else {
                    implSpans.add(Span.styled(" " + label + " ", Style.EMPTY.dim()));
                }
            }
            frame.renderWidget(Paragraph.from(Line.from(implSpans)), implArea);
            row++;
        }

        row++;
        Style portLabelStyle = portDialogRow == 1 ? Style.EMPTY.bold() : Style.EMPTY.dim();
        Rect portLabelArea = new Rect(ix, row, labelW, 1);
        frame.renderWidget(Paragraph.from(Line.from(Span.styled("Port:", portLabelStyle))), portLabelArea);
        Rect portArea = new Rect(ix + labelW, row, fieldW, 1);
        if (portDialogRow == 1) {
            TextInput textInput = TextInput.builder()
                    .cursorStyle(Style.EMPTY.reversed())
                    .placeholder("default")
                    .build();
            frame.renderStatefulWidget(textInput, portArea, portState);
        } else {
            String portText = portState != null ? portState.text() : "";
            frame.renderWidget(Paragraph.from(Line.from(
                    Span.styled(portText.isEmpty() ? "default" : portText,
                            portText.isEmpty() ? Style.EMPTY.dim() : Style.EMPTY))),
                    portArea);
        }
    }

    private void handlePortInput(KeyEvent ke) {
        if (portState == null) {
            return;
        }
        if (ke.isDeleteBackward()) {
            portState.deleteBackward();
        } else if (ke.isDeleteForward()) {
            portState.deleteForward();
        } else if (ke.isLeft()) {
            portState.moveCursorLeft();
        } else if (ke.isRight()) {
            portState.moveCursorRight();
        } else if (ke.isHome()) {
            portState.moveCursorToStart();
        } else if (ke.isEnd()) {
            portState.moveCursorToEnd();
        } else if (ke.code() == KeyCode.CHAR && Character.isDigit(ke.string().charAt(0))) {
            portState.insert(ke.string().charAt(0));
        }
    }

    // ---- Launch ----

    private void launchService() {
        if (selectedService == null) {
            return;
        }
        String alias = selectedService.alias();
        String impl = null;
        if (!selectedService.implementations().isEmpty()) {
            impl = selectedService.implementations().get(implIndex);
        }
        String portStr = portState != null ? portState.text().trim() : "";
        showPortDialog = false;
        try {
            List<String> cmd = new ArrayList<>(LauncherHelper.getCamelCommand());
            cmd.add("infra");
            cmd.add("run");
            cmd.add(alias);
            if (impl != null) {
                cmd.add(impl);
            }
            cmd.add("--background");
            if (!portStr.isEmpty()) {
                cmd.add("--port=" + portStr);
            }
            Path outputFile = Files.createTempFile("camel-infra-", ".log");
            outputFile.toFile().deleteOnExit();
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.redirectOutput(outputFile.toFile());
            Process process = pb.start();
            launchManager.addPendingLaunchNoAutoSelect(alias, process, outputFile);
            if (burstCallback != null) {
                burstCallback.run();
            }
            notify("Starting infra: " + alias, false);
            catalog = null;
        } catch (Exception e) {
            notify("Failed to start infra: " + alias + " - " + e.getMessage(), true);
        }
    }

    // ---- Catalog ----

    private List<InfraServiceEntry> loadCatalog() {
        try {
            CamelCatalog cat = new DefaultCamelCatalog();
            try (InputStream is = cat.loadResource("test-infra", "metadata.json")) {
                if (is == null) {
                    return List.of();
                }
                String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                JsonArray arr = (JsonArray) Jsoner.deserialize(json);
                Map<String, InfraServiceEntry> byAlias = new LinkedHashMap<>();
                for (Object obj : arr) {
                    JsonObject svc = (JsonObject) obj;
                    String desc = (String) svc.getOrDefault("description", "");
                    JsonArray aliases = (JsonArray) svc.get("alias");
                    JsonArray impls = (JsonArray) svc.get("aliasImplementation");
                    if (aliases != null) {
                        for (Object a : aliases) {
                            String alias = a.toString();
                            InfraServiceEntry entry = byAlias.get(alias);
                            if (entry == null) {
                                entry = new InfraServiceEntry(alias, desc, new ArrayList<>(), false);
                                byAlias.put(alias, entry);
                            }
                            if (impls != null) {
                                for (Object impl : impls) {
                                    String implStr = impl.toString();
                                    if (!entry.implementations().contains(implStr)) {
                                        entry.implementations().add(implStr);
                                    }
                                }
                            }
                        }
                    }
                }
                Set<String> runningAliases = infraServices.get().stream()
                        .filter(i -> i.alive)
                        .map(i -> i.alias)
                        .collect(Collectors.toSet());
                List<InfraServiceEntry> result = new ArrayList<>();
                for (InfraServiceEntry entry : byAlias.values()) {
                    boolean running = runningAliases.contains(entry.alias());
                    result.add(new InfraServiceEntry(entry.alias(), entry.description(), entry.implementations(), running));
                }
                result.sort((a, b) -> a.alias().compareToIgnoreCase(b.alias()));
                return result;
            }
        } catch (Exception e) {
            return List.of();
        }
    }

    private void refreshRunningState() {
        if (catalog == null) {
            return;
        }
        Set<String> runningAliases = infraServices.get().stream()
                .filter(i -> i.alive)
                .map(i -> i.alias)
                .collect(Collectors.toSet());
        List<InfraServiceEntry> refreshed = new ArrayList<>();
        for (InfraServiceEntry entry : catalog) {
            boolean running = runningAliases.contains(entry.alias());
            refreshed.add(new InfraServiceEntry(entry.alias(), entry.description(), entry.implementations(), running));
        }
        catalog = refreshed;
    }

    private void notify(String msg, boolean error) {
        if (notificationCallback != null) {
            notificationCallback.accept(msg, error);
        }
    }
}
