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
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.widgets.Clear;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.input.TextInput;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.camel.dsl.jbang.core.common.LauncherHelper;

class FolderInputPopup {

    private static final String PROP_LAST_FOLDER = "camel.tui.lastFolder";

    private boolean showInput;
    private TextInputState inputState;
    private final List<String> folderHistory = new ArrayList<>();
    private int historyIndex = -1;
    private String selectedFolder;
    private String detectedPomPath;
    private final FolderBrowser folderBrowser = new FolderBrowser();

    private final LaunchManager launchManager;
    private Runnable burstCallback;
    private BiConsumer<String, Boolean> notificationCallback;
    private Runnable onFolderConfirmed;
    private Runnable infraCatalogClearer;

    FolderInputPopup(LaunchManager launchManager) {
        this.launchManager = launchManager;
    }

    void setBurstCallback(Runnable burstCallback) {
        this.burstCallback = burstCallback;
    }

    void setNotificationCallback(BiConsumer<String, Boolean> callback) {
        this.notificationCallback = callback;
    }

    void setOnFolderConfirmed(Runnable callback) {
        this.onFolderConfirmed = callback;
    }

    void setInfraCatalogClearer(Runnable clearer) {
        this.infraCatalogClearer = clearer;
    }

    boolean isVisible() {
        return showInput || folderBrowser.isVisible();
    }

    boolean isInputVisible() {
        return showInput;
    }

    boolean isBrowserVisible() {
        return folderBrowser.isVisible();
    }

    String getSelectedFolder() {
        return selectedFolder;
    }

    String getDetectedPomPath() {
        return detectedPomPath;
    }

    void clearSelection() {
        selectedFolder = null;
        detectedPomPath = null;
    }

    void showInput() {
        showInput = true;
    }

    void open() {
        showInput = true;
        String defaultFolder = TuiSettings.load().getDefaultFolder();
        String initialFolder;
        if (defaultFolder != null) {
            initialFolder = defaultFolder;
        } else {
            String lastFolder = loadLastFolder();
            initialFolder = lastFolder != null ? lastFolder : System.getProperty("user.dir");
        }
        inputState = new TextInputState(initialFolder);
        historyIndex = -1;
        folderBrowser.setOnSelect(path -> {
            inputState = new TextInputState(path);
            showInput = true;
        });
    }

    void close() {
        showInput = false;
        folderBrowser.close();
    }

    boolean handleMouseEvent(MouseEvent me) {
        if (folderBrowser.isVisible()) {
            return folderBrowser.handleMouseEvent(me);
        }
        return false;
    }

    boolean handleKeyEvent(KeyEvent ke) {
        if (folderBrowser.isVisible()) {
            folderBrowser.handleKeyEvent(ke);
            if (!folderBrowser.isVisible() && !showInput) {
                showInput = true;
            }
            return true;
        }
        if (showInput) {
            if (ke.isCancel()) {
                showInput = false;
                return true;
            }
            if (ke.isConfirm()) {
                confirmInput();
                return true;
            }
            if (ke.isKey(KeyCode.TAB)) {
                String current = inputState.text().trim();
                showInput = false;
                folderBrowser.open(current);
                return true;
            }
            if (ke.isUp()) {
                navigateHistory(-1);
                return true;
            }
            if (ke.isDown()) {
                navigateHistory(1);
                return true;
            }
            handleTextInput(ke);
            return true;
        }
        return false;
    }

    void render(Frame frame, Rect area) {
        if (folderBrowser.isVisible()) {
            folderBrowser.render(frame, area);
        }
        if (showInput) {
            renderInput(frame, area);
        }
    }

    void renderFooter(List<Span> spans) {
        if (folderBrowser.isVisible()) {
            folderBrowser.renderFooter(spans);
        } else if (showInput) {
            if (!folderHistory.isEmpty()) {
                TuiHelper.hint(spans, "↑↓", "history");
            }
            TuiHelper.hint(spans, "Tab", "browse");
            TuiHelper.hint(spans, "Enter", "run...");
            TuiHelper.hintLast(spans, "Esc", "back");
        }
    }

    void launchFolder(String displayName, List<String> extraArgs, boolean jaegerExport) {
        if (selectedFolder == null) {
            return;
        }
        String folder = selectedFolder;
        String pomPath = detectedPomPath;
        selectedFolder = null;
        detectedPomPath = null;

        if (jaegerExport && !launchManager.isJaegerRunning()) {
            if (!LaunchManager.isContainerRuntimeAvailable()) {
                notify("Docker/Podman required for Jaeger. Run Doctor for details", true);
                return;
            }
            startMissingInfraAndDeferFolder(folder, pomPath, displayName, extraArgs);
            return;
        }

        doLaunchFolder(folder, pomPath, displayName, extraArgs);
    }

    // ---- Private ----

    private void confirmInput() {
        String folder = inputState.text().trim();
        if (folder.isEmpty()) {
            return;
        }
        if (folder.startsWith("~")) {
            folder = System.getProperty("user.home") + folder.substring(1);
        }
        Path dirPath = Path.of(folder);
        if (!Files.isDirectory(dirPath)) {
            notify("Directory does not exist: " + folder, true);
            return;
        }
        folderHistory.remove(folder);
        folderHistory.add(0, folder);
        if (folderHistory.size() > 20) {
            folderHistory.remove(folderHistory.size() - 1);
        }
        selectedFolder = folder;
        showInput = false;
        persistLastFolder(folder);

        Path pomFile = dirPath.resolve("pom.xml");
        String runtime = Files.isRegularFile(pomFile) ? TuiHelper.detectPomRuntime(pomFile) : null;
        if (runtime != null) {
            detectedPomPath = pomFile.toString();
        } else {
            detectedPomPath = null;
        }

        if (onFolderConfirmed != null) {
            onFolderConfirmed.run();
        }
    }

    private void navigateHistory(int direction) {
        if (folderHistory.isEmpty()) {
            return;
        }
        if (direction < 0) {
            if (historyIndex < folderHistory.size() - 1) {
                historyIndex++;
            }
        } else {
            if (historyIndex > 0) {
                historyIndex--;
            } else if (historyIndex == 0) {
                historyIndex = -1;
                inputState = new TextInputState("");
                return;
            }
        }
        if (historyIndex >= 0 && historyIndex < folderHistory.size()) {
            inputState = new TextInputState(folderHistory.get(historyIndex));
        }
    }

    private void handleTextInput(KeyEvent ke) {
        if (inputState == null) {
            return;
        }
        if (ke.isDeleteBackward()) {
            inputState.deleteBackward();
        } else if (ke.isDeleteForward()) {
            inputState.deleteForward();
        } else if (ke.isLeft()) {
            inputState.moveCursorLeft();
        } else if (ke.isRight()) {
            inputState.moveCursorRight();
        } else if (ke.isHome()) {
            inputState.moveCursorToStart();
        } else if (ke.isEnd()) {
            inputState.moveCursorToEnd();
        } else if (ke.code() == KeyCode.CHAR) {
            char ch = ke.string().charAt(0);
            if (ch >= 0x20 && ch != 0x7F) {
                inputState.insert(ch);
            }
        }
    }

    private void renderInput(Frame frame, Rect area) {
        int popupW = Math.min(70, area.width() - 4);
        int popupH = 4;
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + Math.max(0, (area.height() - 17) / 4);
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height()));

        frame.renderWidget(Clear.INSTANCE, popup);

        Block block = Block.builder()
                .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                .title(" Run from Folder ")
                .build();
        frame.renderWidget(block, popup);
        Rect inner = block.inner(popup);

        int labelW = 9;
        int fieldW = inner.width() - labelW;
        int row = inner.top();
        int ix = inner.left();

        Rect labelArea = new Rect(ix, row, labelW, 1);
        frame.renderWidget(Paragraph.from(Line.from(Span.styled("Folder:", Style.EMPTY.bold()))), labelArea);
        Rect inputArea = new Rect(ix + labelW, row, fieldW, 1);
        TextInput textInput = TextInput.builder()
                .cursorStyle(Style.EMPTY.reversed())
                .placeholder("/path/to/folder")
                .build();
        frame.renderStatefulWidget(textInput, inputArea, inputState);
    }

    private void doLaunchFolder(String folder, String pomPath, String displayName, List<String> extraArgs) {
        try {
            List<String> cmd = new ArrayList<>(LauncherHelper.getCamelCommand());
            cmd.add("run");
            if (pomPath != null) {
                cmd.add(pomPath);
            } else {
                cmd.add("--source-dir=" + folder);
            }
            cmd.add("--logging-color=true");
            cmd.addAll(extraArgs);
            Path outputFile = Files.createTempFile("camel-folder-", ".log");
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
            notify("Failed to start: " + folder + " - " + e.getMessage(), true);
        }
    }

    private void startMissingInfraAndDeferFolder(
            String folder, String pomPath, String displayName, List<String> extraArgs) {
        if (infraCatalogClearer != null) {
            launchManager.setInfraCatalogClearer(infraCatalogClearer);
        }
        launchManager.startMissingInfraAndDefer(
                List.of("jaeger"), displayName, () -> doLaunchFolder(folder, pomPath, displayName, extraArgs));
    }

    private static String loadLastFolder() {
        try {
            return TuiUserConfig.read(PROP_LAST_FOLDER);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void persistLastFolder(String folder) {
        try {
            TuiUserConfig.write(PROP_LAST_FOLDER, folder);
        } catch (RuntimeException e) {
            // best-effort: a persist failure must not disrupt the TUI
        }
    }

    private void notify(String msg, boolean error) {
        if (notificationCallback != null) {
            notificationCallback.accept(msg, error);
        }
    }
}
