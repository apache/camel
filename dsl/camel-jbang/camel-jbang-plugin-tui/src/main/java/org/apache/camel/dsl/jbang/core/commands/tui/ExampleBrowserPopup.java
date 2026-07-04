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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.Clear;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.list.ListItem;
import dev.tamboui.widgets.list.ListState;
import dev.tamboui.widgets.list.ListWidget;
import dev.tamboui.widgets.list.ScrollMode;
import org.apache.camel.dsl.jbang.core.common.ExampleHelper;
import org.apache.camel.dsl.jbang.core.common.LauncherHelper;
import org.apache.camel.util.json.JsonObject;

class ExampleBrowserPopup {

    private boolean visible;
    private final ListState listState = new ListState();
    private List<JsonObject> catalog;
    private JsonObject selectedExample;

    private final DocViewerPopup docViewerPopup;
    private final LaunchManager launchManager;
    private Runnable burstCallback;
    private BiConsumer<String, Boolean> notificationCallback;
    private Runnable onNameInputRequest;

    ExampleBrowserPopup(DocViewerPopup docViewerPopup, LaunchManager launchManager) {
        this.docViewerPopup = docViewerPopup;
        this.launchManager = launchManager;
    }

    void setBurstCallback(Runnable burstCallback) {
        this.burstCallback = burstCallback;
    }

    void setNotificationCallback(BiConsumer<String, Boolean> callback) {
        this.notificationCallback = callback;
    }

    void setOnNameInputRequest(Runnable callback) {
        this.onNameInputRequest = callback;
    }

    boolean isVisible() {
        return visible;
    }

    JsonObject getSelectedExample() {
        return selectedExample;
    }

    void open() {
        if (catalog == null) {
            catalog = loadAndSortExamples();
        }
        if (catalog.isEmpty()) {
            notify("No examples found", true);
            return;
        }
        visible = true;
        listState.select(1);
    }

    void close() {
        visible = false;
    }

    boolean handleKeyEvent(KeyEvent ke) {
        if (ke.isCancel()) {
            close();
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
        if (ke.isChar('r')) {
            launchSelected();
            return true;
        }
        if (ke.isChar('d')) {
            loadDocFromExample();
            return true;
        }
        if (ke.isConfirm()) {
            openNameInput();
            return true;
        }
        return true;
    }

    void render(Frame frame, Rect area) {
        if (catalog == null || catalog.isEmpty()) {
            return;
        }
        int popupW = Math.min(100, area.width() - 4);
        int popupH = Math.min(catalog.size() + 10, Math.min(22, area.height() - 4));
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + 2;
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height() - 2));

        frame.renderWidget(Clear.INSTANCE, popup);

        List<ListItem> items = buildListItems(popupW - 4);
        ListWidget list = ListWidget.builder()
                .items(items.toArray(ListItem[]::new))
                .highlightStyle(Theme.selectionBg())
                .highlightSymbol("")
                .scrollMode(ScrollMode.AUTO_SCROLL)
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" Run an Example (" + catalog.size() + ") ")
                        .build())
                .build();
        frame.renderStatefulWidget(list, popup, listState);
    }

    void renderFooter(List<Span> spans) {
        TuiHelper.hint(spans, "↑↓", "navigate");
        TuiHelper.hint(spans, "r", "run");
        TuiHelper.hint(spans, "Enter", "run...");
        TuiHelper.hint(spans, "d", "docs");
        TuiHelper.hintLast(spans, "Esc", "back");
    }

    SelectionContext getSelectionContext() {
        if (!visible || catalog == null) {
            return null;
        }
        List<String> items = new ArrayList<>();
        String currentLevel = null;
        for (JsonObject ex : catalog) {
            String level = ex.getStringOrDefault("level", "beginner");
            if (!level.equals(currentLevel)) {
                currentLevel = level;
                items.add("── " + TuiHelper.capitalize(level) + " ──");
            }
            items.add(ex.getStringOrDefault("name", ""));
        }
        int total = countListItems();
        Integer sel = listState.selected();
        return new SelectionContext("list", items, sel != null ? sel : -1, total, "Examples");
    }

    void doLaunch(String exampleName, String displayName, List<String> extraArgs) {
        try {
            List<String> cmd = new ArrayList<>(LauncherHelper.getCamelCommand());
            cmd.add("run");
            cmd.add("--example=" + exampleName);
            cmd.add("--logging-color=true");
            cmd.addAll(extraArgs);
            Path outputFile = Files.createTempFile("camel-example-", ".log");
            outputFile.toFile().deleteOnExit();
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.redirectOutput(outputFile.toFile());
            Process process = pb.start();
            launchManager.addPendingLaunch(displayName, process, outputFile);
            if (burstCallback != null) {
                burstCallback.run();
            }
            notify("Starting: " + displayName, false);
        } catch (Exception e) {
            notify("Failed to start: " + exampleName + " - " + e.getMessage(), true);
        }
    }

    void startMissingInfraAndDefer(
            List<String> missingInfra, String exampleName, String displayName,
            List<String> extraArgs, Runnable infraCatalogClearer) {
        launchManager.setInfraCatalogClearer(infraCatalogClearer);
        launchManager.startMissingInfraAndDefer(
                missingInfra, displayName, () -> doLaunch(exampleName, displayName, extraArgs));
    }

    // ---- Private ----

    private void launchSelected() {
        Integer sel = listState.selected();
        if (sel == null || isSeparatorIndex(sel)) {
            return;
        }
        JsonObject example = getExampleAtListIndex(sel);
        if (example == null) {
            return;
        }
        String exampleName = example.getStringOrDefault("name", "");
        visible = false;

        List<String> missing = launchManager.findMissingInfraServices(example);
        if (!missing.isEmpty()) {
            if (!LaunchManager.isContainerRuntimeAvailable()) {
                notify("Docker/Podman required for infra services. Run Doctor for details", true);
                return;
            }
            launchManager.startMissingInfraAndDefer(
                    missing, exampleName, () -> doLaunch(exampleName, exampleName, List.of()));
            return;
        }

        doLaunch(exampleName, exampleName, List.of());
    }

    private void openNameInput() {
        Integer sel = listState.selected();
        if (sel == null || isSeparatorIndex(sel)) {
            return;
        }
        JsonObject example = getExampleAtListIndex(sel);
        if (example == null) {
            return;
        }
        selectedExample = example;
        visible = false;
        if (onNameInputRequest != null) {
            onNameInputRequest.run();
        }
    }

    private void loadDocFromExample() {
        Integer sel = listState.selected();
        if (sel == null || isSeparatorIndex(sel)) {
            return;
        }
        JsonObject example = getExampleAtListIndex(sel);
        if (example == null) {
            return;
        }
        String name = example.getStringOrDefault("name", "");
        boolean bundled = ExampleHelper.isBundled(example);
        String content = null;
        boolean isAdoc = false;
        if (bundled) {
            content = DocHelper.loadResourceContent("examples/" + name + "/README.md");
            if (content == null) {
                content = DocHelper.loadResourceContent("examples/" + name + "/README.adoc");
                isAdoc = content != null;
            }
        } else {
            String base = "https://raw.githubusercontent.com/apache/camel-jbang-examples/main/" + name + "/";
            content = DocHelper.downloadContent(base + "README.md");
            if (content == null) {
                content = DocHelper.downloadContent(base + "README.adoc");
                isAdoc = content != null;
            }
        }
        if (content != null && !content.isEmpty()) {
            String markdown = isAdoc ? DocHelper.asciidocToMarkdown(content) : content;
            visible = false;
            docViewerPopup.openMarkdown(name, markdown, () -> visible = true);
        } else {
            notify("No documentation available for: " + name, true);
        }
    }

    private void navigate(int direction) {
        if (catalog == null || catalog.isEmpty()) {
            return;
        }
        int totalItems = countListItems();
        Integer current = listState.selected();
        if (current == null) {
            current = 0;
        }
        int next = current + direction;
        if (next < 0) {
            next = 0;
        }
        if (next >= totalItems) {
            next = totalItems - 1;
        }
        while (isSeparatorIndex(next) && next > 0 && next < totalItems - 1) {
            next += direction;
        }
        if (next < 0) {
            next = 0;
        }
        if (next >= totalItems) {
            next = totalItems - 1;
        }
        if (isSeparatorIndex(next)) {
            return;
        }
        listState.select(next);
    }

    private int countListItems() {
        if (catalog == null) {
            return 0;
        }
        int count = 0;
        String currentLevel = null;
        for (JsonObject ex : catalog) {
            String level = ex.getStringOrDefault("level", "beginner");
            if (!level.equals(currentLevel)) {
                currentLevel = level;
                count++;
            }
            count++;
        }
        return count + 2;
    }

    private boolean isSeparatorIndex(int index) {
        if (catalog == null) {
            return false;
        }
        int pos = 0;
        String currentLevel = null;
        for (JsonObject ex : catalog) {
            String level = ex.getStringOrDefault("level", "beginner");
            if (!level.equals(currentLevel)) {
                currentLevel = level;
                if (pos == index) {
                    return true;
                }
                pos++;
            }
            if (pos == index) {
                return false;
            }
            pos++;
        }
        return true;
    }

    private JsonObject getExampleAtListIndex(int index) {
        if (catalog == null) {
            return null;
        }
        int pos = 0;
        String currentLevel = null;
        for (JsonObject ex : catalog) {
            String level = ex.getStringOrDefault("level", "beginner");
            if (!level.equals(currentLevel)) {
                currentLevel = level;
                pos++;
            }
            if (pos == index) {
                return ex;
            }
            pos++;
        }
        return null;
    }

    private List<ListItem> buildListItems(int width) {
        List<ListItem> items = new ArrayList<>();
        String currentLevel = null;
        for (JsonObject ex : catalog) {
            String level = ex.getStringOrDefault("level", "beginner");
            if (!level.equals(currentLevel)) {
                currentLevel = level;
                String header = "── " + TuiHelper.capitalize(level) + " ──";
                items.add(ListItem.from(header).style(Style.EMPTY.dim()));
            }
            String name = ex.getStringOrDefault("name", "");
            String desc = ex.getStringOrDefault("description", "");
            boolean docker = ExampleHelper.requiresDocker(ex);
            boolean bundled = ExampleHelper.isBundled(ex);
            boolean citrus = ExampleHelper.hasCitrusTests(ex);
            boolean infra = !ExampleHelper.getInfraServices(ex).isEmpty();

            String icons = (bundled ? TuiIcons.BUNDLED : TuiIcons.ONLINE) + (docker ? TuiIcons.DOCKER : "  ")
                           + (infra ? TuiIcons.INFRA : "  ") + (citrus ? TuiIcons.CITRUS : "  ");
            int nameCol = Math.min(30, width / 3);
            String padded = String.format("%-" + nameCol + "s", TuiHelper.truncate(name, nameCol));
            String prefix = " " + icons + " " + padded + " ";
            int descCol = Math.max(10, width - prefix.length());

            Style style = bundled ? Style.EMPTY : Style.EMPTY.dim();
            if (desc.length() <= descCol) {
                items.add(ListItem.from(prefix + desc).style(style));
            } else {
                String indent = " ".repeat(prefix.length());
                List<Line> lines = new ArrayList<>();
                List<String> wrapped = TuiHelper.wrapWords(desc, descCol);
                lines.add(Line.from(prefix + wrapped.get(0)));
                for (int w = 1; w < wrapped.size(); w++) {
                    lines.add(Line.from(indent + wrapped.get(w)));
                }
                items.add(ListItem.from(Text.from(lines.toArray(Line[]::new))).style(style));
            }
        }
        items.add(ListItem.from(""));
        items.add(ListItem.from(" " + TuiIcons.BUNDLED + " = bundled  " + TuiIcons.ONLINE + " = online  "
                                + TuiIcons.DOCKER + " = Docker  " + TuiIcons.INFRA + " = infra services  " + TuiIcons.CITRUS
                                + " = Citrus tests")
                .style(Style.EMPTY.dim()));
        return items;
    }

    private static List<JsonObject> loadAndSortExamples() {
        List<JsonObject> list = ExampleHelper.loadCatalog();
        list.sort((a, b) -> {
            int la = levelOrder(a.getStringOrDefault("level", "beginner"));
            int lb = levelOrder(b.getStringOrDefault("level", "beginner"));
            if (la != lb) {
                return Integer.compare(la, lb);
            }
            return a.getStringOrDefault("name", "").compareTo(b.getStringOrDefault("name", ""));
        });
        return list;
    }

    private static int levelOrder(String level) {
        return switch (level) {
            case "beginner" -> 0;
            case "intermediate" -> 1;
            case "advanced" -> 2;
            default -> 3;
        };
    }

    private void notify(String msg, boolean error) {
        if (notificationCallback != null) {
            notificationCallback.accept(msg, error);
        }
    }
}
