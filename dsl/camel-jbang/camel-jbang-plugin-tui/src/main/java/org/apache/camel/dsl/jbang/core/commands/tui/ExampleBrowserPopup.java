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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

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
import dev.tamboui.widgets.list.ListItem;
import dev.tamboui.widgets.list.ListState;
import dev.tamboui.widgets.list.ListWidget;
import dev.tamboui.widgets.list.ScrollMode;
import org.apache.camel.dsl.jbang.core.common.ExampleHelper;
import org.apache.camel.dsl.jbang.core.common.LauncherHelper;
import org.apache.camel.util.json.JsonObject;

class ExampleBrowserPopup {

    private static final String BACK_MARKER = "__BACK__";

    private boolean visible;
    private final ListState listState = new ListState();
    private Rect popupRect;
    private int[] itemHeights;
    private List<JsonObject> catalog;
    // the catalog grouped by level in ladder order; a pure function of the catalog, computed when it is loaded
    private Map<String, List<JsonObject>> grouped;
    private JsonObject selectedExample;

    // the group (level) the browser is in, or null at the top level where the groups are listed
    private String currentFolder;
    private int savedSelection;
    private List<Object> itemData;

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
            grouped = ExampleHelper.groupByLevel(catalog);
        }
        if (catalog.isEmpty()) {
            notify("No examples found", true);
            return;
        }
        currentFolder = null;
        visible = true;
        listState.select(0);
    }

    void close() {
        visible = false;
    }

    boolean handleKeyEvent(KeyEvent ke) {
        if (ke.isCancel()) {
            if (currentFolder != null) {
                navigateBack();
            } else {
                close();
            }
            return true;
        }
        if (ke.isLeft() || ke.isKey(KeyCode.BACKSPACE)) {
            if (currentFolder != null) {
                navigateBack();
            }
            return true;
        }
        if (ke.isRight() && currentFolder == null) {
            activateSelected(false);
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
            activateSelected(false);
            return true;
        }
        if (ke.isChar('d')) {
            loadDocForSelected();
            return true;
        }
        if (currentFolder == null) {
            // 1 to 9 open the first nine groups, 0 the tenth
            for (char c = '0'; c <= '9'; c++) {
                if (ke.isChar(c)) {
                    int n = c == '0' ? 10 : c - '0';
                    List<String> levels = new ArrayList<>(grouped.keySet());
                    if (n >= 1 && n <= levels.size()) {
                        enterFolder(levels.get(n - 1));
                    }
                    return true;
                }
            }
        }
        if (ke.isConfirm()) {
            activateSelected(true);
            return true;
        }
        return true;
    }

    boolean handleMouseEvent(MouseEvent me) {
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
                if (idx >= 0 && !isSeparatorIndex(idx)) {
                    listState.select(idx);
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
        int offset = listState.offset();
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
        if (catalog == null || catalog.isEmpty()) {
            return;
        }
        // wide: the descriptions are sentences, and a wide terminal should show them on one or two lines
        int popupW = Math.max(80, Math.min(area.width() - 10, 170));
        int visibleItems = Math.max(10, catalog.size() + 10);
        int popupH = Math.min(visibleItems, Math.min(22, area.height() - 4));
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + 2;
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height() - 2));
        this.popupRect = popup;

        frame.renderWidget(Clear.INSTANCE, popup);

        List<ListItem> items = buildListItems(popupW - 4);
        String title = currentFolder != null
                ? " " + ExampleHelper.getGroupTitle(currentFolder) + " (" + folderExampleCount(currentFolder) + ") "
                : " Run an Example (" + catalog.size() + ") ";
        ListWidget list = ListWidget.builder()
                .items(items.toArray(ListItem[]::new))
                .highlightStyle(Theme.selectionBg())
                .highlightSymbol("")
                .scrollMode(ScrollMode.AUTO_SCROLL)
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(title)
                        .build())
                .build();
        frame.renderStatefulWidget(list, popup, listState);
    }

    void renderFooter(List<Span> spans) {
        if (currentFolder != null) {
            TuiHelper.hint(spans, "r", "run");
            TuiHelper.hint(spans, "Enter", "run...");
            TuiHelper.hint(spans, "d", "docs");
            // inside a group Esc, Left and Backspace return to the groups; at the top level Esc closes the browser
            TuiHelper.hintLast(spans, "Esc", "back");
        } else {
            TuiHelper.hint(spans, "Enter/→", "open");
            TuiHelper.hint(spans, "1-9", "jump");
            TuiHelper.hint(spans, "d", "docs");
            TuiHelper.hintLast(spans, "Esc", "close");
        }
    }

    SelectionContext getSelectionContext() {
        if (!visible || itemData == null) {
            return null;
        }
        List<String> items = new ArrayList<>();
        for (Object data : itemData) {
            if (data == null) {
                items.add("──");
            } else if (BACK_MARKER.equals(data)) {
                items.add("..");
            } else if (data instanceof String folder) {
                items.add(TuiIcons.FOLDER + " " + ExampleHelper.getGroupTitle(folder));
            } else if (data instanceof JsonObject ex) {
                items.add(ExampleHelper.getShortName(ex));
            }
        }
        Integer sel = listState.selected();
        return new SelectionContext("list", items, sel != null ? sel : -1, itemData.size(), "Examples");
    }

    void doLaunch(String exampleName, String displayName, List<String> extraArgs) {
        try {
            List<String> cmd = new ArrayList<>(LauncherHelper.getCamelCommand());
            cmd.add("run");
            cmd.add("--example=" + exampleName);
            cmd.add("--logging-color=true");
            cmd.addAll(extraArgs);
            if (exampleName.contains("/") && extraArgs.stream().noneMatch(a -> a.startsWith("--name"))) {
                cmd.add("--name=" + TuiHelper.stripCategory(exampleName));
            }
            Path outputFile = LaunchManager.createSecureTempFile("camel-example-", ".log");
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

    private void activateSelected(boolean withOptions) {
        Integer sel = listState.selected();
        if (sel == null || isSeparatorIndex(sel)) {
            return;
        }
        Object data = itemData.get(sel);
        if (BACK_MARKER.equals(data)) {
            navigateBack();
        } else if (data instanceof String folder) {
            enterFolder(folder);
        } else if (data instanceof JsonObject example) {
            if (withOptions) {
                openNameInput(example);
            } else {
                launchExample(example);
            }
        }
    }

    private void enterFolder(String folder) {
        Integer sel = listState.selected();
        savedSelection = sel != null ? sel : 0;
        currentFolder = folder;
        // the first rows inside a group are ".." and its introduction; select the first example
        listState.select(ExampleHelper.getGroupIntro(folder).isEmpty() ? 1 : 2);
    }

    private void navigateBack() {
        currentFolder = null;
        listState.select(savedSelection);
    }

    private void launchExample(JsonObject example) {
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

    private void openNameInput(JsonObject example) {
        selectedExample = example;
        visible = false;
        if (onNameInputRequest != null) {
            onNameInputRequest.run();
        }
    }

    private void loadDocForSelected() {
        Integer sel = listState.selected();
        if (sel == null || sel >= itemData.size()) {
            return;
        }
        Object data = itemData.get(sel);
        if (data instanceof String group) {
            loadDocForGroup(group);
            return;
        }
        if (!(data instanceof JsonObject example)) {
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

    private void loadDocForGroup(String group) {
        // the group's README in the examples repository: its introduction and its examples in reading order
        String url = "https://raw.githubusercontent.com/apache/camel-jbang-examples/main/" + group + "/README.md";
        String content = DocHelper.downloadContent(url);
        if (content != null && !content.isEmpty()) {
            visible = false;
            docViewerPopup.openMarkdown(ExampleHelper.getGroupTitle(group), content, () -> visible = true);
        } else {
            notify("No documentation available for: " + ExampleHelper.getGroupTitle(group), true);
        }
    }

    private void navigate(int direction) {
        if (itemData == null || itemData.isEmpty()) {
            return;
        }
        int totalItems = itemData.size();
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

    private boolean isSeparatorIndex(int index) {
        if (itemData == null || index < 0 || index >= itemData.size()) {
            return true;
        }
        return itemData.get(index) == null;
    }

    private List<ListItem> buildListItems(int width) {
        if (currentFolder != null) {
            return buildFolderItems(width);
        }
        return buildTopLevelItems(width);
    }

    private List<ListItem> buildTopLevelItems(int width) {
        List<ListItem> items = new ArrayList<>();
        List<Integer> heights = new ArrayList<>();
        List<Object> data = new ArrayList<>();
        // the ladder: one row per group in reading order, the number is the hotkey
        Map<String, List<JsonObject>> groups = grouped;
        int n = 0;
        for (Map.Entry<String, List<JsonObject>> group : groups.entrySet()) {
            n++;
            String level = group.getKey();
            String key = n <= 9 ? String.valueOf(n) : n == 10 ? "0" : " ";
            String label = String.format(" %s  %s %s (%d)", key, TuiIcons.FOLDER, ExampleHelper.getGroupTitle(level),
                    group.getValue().size());
            String intro = ExampleHelper.getGroupIntro(level);
            // the longest title, "Connect without a service (3)", fits the label column; the intro takes the rest
            int labelCol = 38;
            int introCol = Math.max(0, width - labelCol - 2);
            String padded = String.format("%-" + labelCol + "s", TuiHelper.truncate(label, labelCol));
            Line line = Line.from(Span.raw(padded + " "), Span.styled(TuiHelper.truncate(intro, introCol), Style.EMPTY.dim()));
            items.add(ListItem.from(Text.from(line)));
            heights.add(1);
            data.add(level);
        }
        items.add(ListItem.from(""));
        heights.add(1);
        data.add(null);
        items.add(ListItem.from(" Enter opens a group, a number jumps to it, d shows what the group is about")
                .style(Style.EMPTY.dim()));
        heights.add(1);
        data.add(null);
        this.itemHeights = heights.stream().mapToInt(Integer::intValue).toArray();
        this.itemData = data;
        return items;
    }

    private List<ListItem> buildFolderItems(int width) {
        List<ListItem> items = new ArrayList<>();
        List<Integer> heights = new ArrayList<>();
        List<Object> data = new ArrayList<>();
        // a folder: ".." goes back up, then the group's introduction as a header, then its examples
        items.add(ListItem.from(" " + TuiIcons.FOLDER + " ..").style(Style.EMPTY.dim()));
        heights.add(1);
        data.add(BACK_MARKER);
        String intro = ExampleHelper.getGroupIntro(currentFolder);
        if (!intro.isEmpty()) {
            items.add(ListItem.from(" " + intro).style(Style.EMPTY.dim()));
            heights.add(1);
            data.add(null);
        }
        List<JsonObject> entries = grouped.get(currentFolder);
        if (entries != null) {
            for (JsonObject ex : entries) {
                addExampleItem(items, heights, data, ex, width);
            }
        }
        items.add(ListItem.from(""));
        heights.add(1);
        data.add(null);
        items.add(ListItem.from(" " + TuiIcons.BUNDLED + " = bundled  " + TuiIcons.ONLINE + " = online  "
                                + TuiIcons.DOCKER + " = Docker  " + TuiIcons.INFRA + " = infra services  " + TuiIcons.CITRUS
                                + " = Citrus tests   .. or Esc = back to the groups")
                .style(Style.EMPTY.dim()));
        heights.add(1);
        data.add(null);
        this.itemHeights = heights.stream().mapToInt(Integer::intValue).toArray();
        this.itemData = data;
        return items;
    }

    private void addExampleItem(
            List<ListItem> items, List<Integer> heights, List<Object> data,
            JsonObject ex, int width) {
        String name = ExampleHelper.getShortName(ex);
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
            heights.add(1);
        } else {
            String indent = " ".repeat(prefix.length());
            List<Line> lines = new ArrayList<>();
            List<String> wrapped = TuiHelper.wrapWords(desc, descCol);
            lines.add(Line.from(prefix + wrapped.get(0)));
            for (int w = 1; w < wrapped.size(); w++) {
                lines.add(Line.from(indent + wrapped.get(w)));
            }
            items.add(ListItem.from(Text.from(lines.toArray(Line[]::new))).style(style));
            heights.add(wrapped.size());
        }
        data.add(ex);
    }

    private int folderExampleCount(String folder) {
        List<JsonObject> entries = grouped.get(folder);
        return entries != null ? entries.size() : 0;
    }

    private static List<JsonObject> loadAndSortExamples() {
        return ExampleHelper.loadCatalog();
    }

    private void notify(String msg, boolean error) {
        if (notificationCallback != null) {
            notificationCallback.accept(msg, error);
        }
    }
}
