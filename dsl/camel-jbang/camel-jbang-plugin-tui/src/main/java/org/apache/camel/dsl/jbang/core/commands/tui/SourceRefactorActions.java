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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.input.TextAreaState;

import static org.apache.camel.dsl.jbang.core.commands.tui.SourceRefactorings.*;
import static org.apache.camel.dsl.jbang.core.commands.tui.YamlSourceContext.countLeadingSpaces;

/**
 * The refactorings of the source editor ({@link SourceViewer}), Ctrl+R in edit mode: the ones that apply at the cursor
 * line, the {@link RefactorPopup} offering them, and the chosen one applied to the text and the files. The text
 * transformations are in {@link SourceRefactorings} (YAML) and {@link RouteRefactorings} (Java and XML).
 */
class SourceRefactorActions {

    /** What the refactorings need of the editor they change. */
    interface Editor {
        /** The file being edited; null when it is not a local file. */
        Path editableFile();

        boolean isCamelYamlFile();

        /** java or xml for a Java or XML route file; null for neither. */
        String routeDsl();

        /** Before an edit: the undo history and the modified flag. */
        void recordEditChange();

        /** The text was written to the file: it is not modified any more. */
        void markSaved();

        /** Tells the user how it went: the message of a notification, an error or not. */
        void report(String message, boolean error);

        /** A new file was written next to the edited one. */
        void fileCreated();
    }

    private final TextAreaState editState;
    private final Editor editor;
    private RefactorPopup refactorPopup;

    SourceRefactorActions(TextAreaState editState, Editor editor) {
        this.editState = editState;
        this.editor = editor;
    }

    /** Closes the popup; true when it was open. */
    boolean closePopup() {
        boolean open = refactorPopup != null && refactorPopup.isVisible();
        refactorPopup = null;
        return open;
    }

    /** The keys of the popup while it is open: true then, as it takes them all. */
    boolean handleKeyEvent(KeyEvent ke) {
        if (refactorPopup == null || !refactorPopup.isVisible()) {
            return false;
        }
        refactorPopup.handleKeyEvent(ke);
        RefactorPopup.Request req = refactorPopup.consumeResult();
        if (req != null) {
            applyRefactoring(req);
        }
        return true;
    }

    void render(Frame frame, Rect area) {
        if (refactorPopup != null && refactorPopup.isVisible()) {
            refactorPopup.render(frame, area);
        }
    }

    /** The footer hints of the popup while it is open: true then, as they replace those of the editor. */
    boolean renderFooter(List<Span> spans) {
        if (refactorPopup == null || !refactorPopup.isVisible()) {
            return false;
        }
        refactorPopup.renderFooter(spans);
        return true;
    }

    void openRefactorPopup() {
        int row = editState.cursorRow();
        if (row < 0 || row >= editState.lineCount()) {
            return;
        }
        String rawLine = editState.getLine(row);
        if (!editor.isCamelYamlFile()) {
            // Java and XML routes (CAMEL-25256)
            openRouteRefactorPopup(row, rawLine);
            return;
        }
        List<RefactorPopup.Action> actions = new ArrayList<>();
        // Extract to new file: available on any EIP step block in a YAML route
        if (editor.isCamelYamlFile()) {
            List<String> lines = SourceEditorNavigation.lines(editState);
            YamlBlockEditor.BlockRange block = YamlBlockEditor.findBlock(lines, row, true);
            if (block != null && !block.isEmpty() && isExtractableStep(lines.get(block.startRow()))) {
                actions.add(RefactorPopup.Action.EXTRACT_TO_FILE);
            }
        }
        String currentUri = extractUriFromLine(rawLine);
        if (currentUri != null) {
            actions.add(RefactorPopup.Action.REPLACE_URI);
        }
        if (currentUri == null && extractValueFromLine(rawLine) != null) {
            actions.add(RefactorPopup.Action.EXTRACT_TO_PROPERTY);
        }
        if (actions.isEmpty()) {
            return;
        }
        refactorPopup = new RefactorPopup();
        refactorPopup.open(actions, currentUri);
    }

    /**
     * The refactorings of a Java or XML route: replace the endpoint URI of the line, extract the value at the cursor to
     * a property, and in XML extract the step block of the line to a new route file.
     */
    private void openRouteRefactorPopup(int row, String rawLine) {
        String dsl = editor.routeDsl();
        List<RefactorPopup.Action> actions = new ArrayList<>();
        if ("xml".equals(dsl) && RouteRefactorings.xmlStep(SourceEditorNavigation.lines(editState), row) != null) {
            actions.add(RefactorPopup.Action.EXTRACT_TO_FILE);
        }
        RouteRefactorings.Value uri = RouteRefactorings.uri(dsl, rawLine);
        if (uri != null) {
            actions.add(RefactorPopup.Action.REPLACE_URI);
        }
        if (RouteRefactorings.isExtractable(RouteRefactorings.valueAt(dsl, rawLine, editState.cursorCol()))) {
            actions.add(RefactorPopup.Action.EXTRACT_TO_PROPERTY);
        }
        if (actions.isEmpty()) {
            editor.report("Nothing to refactor here: put the cursor on an endpoint URI, a value"
                          + ("xml".equals(dsl) ? " or the start tag of a step" : ""),
                    false);
            return;
        }
        refactorPopup = new RefactorPopup();
        refactorPopup.open(actions, uri != null ? uri.text() : null);
    }

    private void applyRefactoring(RefactorPopup.Request req) {
        int row = editState.cursorRow();
        if (row < 0 || row >= editState.lineCount()) {
            return;
        }
        String rawLine = editState.getLine(row);
        if (!editor.isCamelYamlFile()) {
            applyRouteRefactoring(req, row, rawLine);
            return;
        }
        switch (req.action()) {
            case EXTRACT_TO_FILE -> applyExtractToFile(row, req.value());
            case REPLACE_URI -> applyReplaceUri(row, rawLine, req.value());
            case EXTRACT_TO_PROPERTY -> applyExtractToProperty(row, rawLine, req.value());
        }
    }

    private void applyRouteRefactoring(RefactorPopup.Request req, int row, String rawLine) {
        String dsl = editor.routeDsl();
        switch (req.action()) {
            case REPLACE_URI -> {
                RouteRefactorings.Value uri = RouteRefactorings.uri(dsl, rawLine);
                if (uri != null) {
                    setLine(row, RouteRefactorings.replace(dsl, rawLine, uri, req.value()));
                    editor.report("Replaced URI with: " + req.value(), false);
                }
            }
            case EXTRACT_TO_PROPERTY -> {
                RouteRefactorings.Value v = RouteRefactorings.valueAt(dsl, rawLine, editState.cursorCol());
                if (!RouteRefactorings.isExtractable(v) || req.value() == null || req.value().isBlank()) {
                    return;
                }
                String key = req.value().strip();
                setLine(row, RouteRefactorings.replace(dsl, rawLine, v, "{{" + key + "}}"));
                if (editor.editableFile() != null) {
                    Path props = RouteRefactorings.propertiesFile(editor.editableFile());
                    try {
                        Files.createDirectories(props.getParent());
                        Files.writeString(props, key + "=" + RouteRefactorings.propertiesValue(v.text()) + "\n",
                                StandardCharsets.UTF_8,
                                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    } catch (IOException e) {
                        editor.report("Warning: could not write " + props.getFileName() + ": " + e.getMessage(), true);
                        return;
                    }
                }
                editor.report("Extracted to property: " + key, false);
            }
            case EXTRACT_TO_FILE -> applyXmlExtractToFile(row, req.value());
        }
    }

    /** The line replaced in the edited text, the cursor kept on it. */
    private void setLine(int row, String newLine) {
        editor.recordEditChange();
        List<String> lines = SourceEditorNavigation.lines(editState);
        lines.set(row, newLine);
        editState.setText(YamlBlockEditor.fromLines(lines));
        SourceEditorNavigation.positionCursor(editState, row, countLeadingSpaces(newLine));
    }

    /** The XML step block of the row moved to a new route (from direct:name) in name.camel.xml, a to in its place. */
    private void applyXmlExtractToFile(int row, String name) {
        if (editor.editableFile() == null) {
            editor.report("Cannot extract: file is not writable", true);
            return;
        }
        name = sanitizeFileName(name);
        if (name.isEmpty()) {
            editor.report("Cannot extract: invalid file name", true);
            return;
        }
        List<String> lines = SourceEditorNavigation.lines(editState);
        RouteRefactorings.Block block = RouteRefactorings.xmlStep(lines, row);
        if (block == null) {
            return;
        }
        List<String> blockLines = new ArrayList<>(lines.subList(block.start(), block.end() + 1));
        String newFileName = name + ".camel.xml";
        Path newFile = editor.editableFile().getParent().resolve(newFileName);
        String route = RouteRefactorings.xmlRoute(name, blockLines, block.indent());
        String newContent;
        boolean existed = Files.exists(newFile);
        try {
            if (existed) {
                newContent = RouteRefactorings.addXmlRoute(Files.readString(newFile, StandardCharsets.UTF_8), route);
                if (newContent == null) {
                    editor.report(newFileName + " exists and has no </routes> to add the route to", true);
                    return;
                }
            } else {
                newContent = RouteRefactorings.xmlRouteFile(name, blockLines, block.indent());
            }
        } catch (IOException e) {
            editor.report("Failed to read " + newFileName + ": " + e.getMessage(), true);
            return;
        }
        editor.recordEditChange();
        editState.setText(YamlBlockEditor.fromLines(RouteRefactorings.replaceWithTo(lines, block, name)));
        SourceEditorNavigation.positionCursor(editState, block.start(), block.indent());
        // saved at once, so the route index of the jump links sees both files (as the YAML extraction does)
        try {
            Files.writeString(editor.editableFile(), editState.text(), StandardCharsets.UTF_8);
            editor.markSaved();
            Files.writeString(newFile, newContent, StandardCharsets.UTF_8);
        } catch (IOException e) {
            editor.report("Failed to write " + newFileName + ": " + e.getMessage(), true);
            return;
        }
        if (!existed) {
            editor.fileCreated();
        }
        editor.report(existed ? "Added route to " + newFileName : "Extracted to " + newFileName, false);
    }

    private void applyExtractToFile(int cursorRow, String name) {
        if (editor.editableFile() == null) {
            editor.report("Cannot extract: file is not writable", true);
            return;
        }
        name = sanitizeFileName(name);
        if (name.isEmpty()) {
            editor.report("Cannot extract: invalid file name", true);
            return;
        }
        List<String> lines = SourceEditorNavigation.lines(editState);
        YamlBlockEditor.BlockRange block = YamlBlockEditor.findBlock(lines, cursorRow, true);
        if (block == null || block.isEmpty()) {
            return;
        }
        String stepLine = lines.get(block.startRow());
        if (!isExtractableStep(stepLine)) {
            return;
        }
        int stepIndent = YamlBlockEditor.leadingSpaces(stepLine);
        List<String> blockLines = new ArrayList<>(lines.subList(block.startRow(), block.endRow() + 1));
        String newFileContent = buildExtractedRouteYaml(name, blockLines, stepIndent);
        // Use canonical block-form notation:
        //   - to:
        //       uri: direct:<name>
        String indentStr = " ".repeat(stepIndent);
        String toLine = indentStr + "- to:";
        String uriLine = indentStr + "    uri: direct:" + name;
        editor.recordEditChange();
        List<String> newLines = new ArrayList<>(lines);
        newLines.subList(block.startRow(), block.endRow() + 1).clear();
        newLines.add(block.startRow(), uriLine);
        newLines.add(block.startRow(), toLine);
        editState.setText(YamlBlockEditor.fromLines(newLines));
        SourceEditorNavigation.positionCursor(editState, block.startRow(), stepIndent);
        // Auto-save the original file so the route index captures the refactoring change on disk.
        // Without this, the new file's reverse jump link cannot be resolved until a manual save.
        try {
            Files.writeString(editor.editableFile(), editState.text(), StandardCharsets.UTF_8);
            editor.markSaved();
        } catch (IOException ignored) {
            // best effort; extraction still proceeds
        }
        String newFileName = name + ".camel.yaml";
        Path newFile = editor.editableFile().getParent().resolve(newFileName);
        boolean existed = Files.exists(newFile);
        try {
            if (existed) {
                // Append as an additional route (blank line separator before the new block)
                Files.writeString(newFile, "\n" + newFileContent, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            } else {
                Files.writeString(newFile, newFileContent, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            }
        } catch (IOException e) {
            editor.report("Failed to write " + newFileName + ": " + e.getMessage(), true);
            return;
        }
        if (!existed) {
            editor.fileCreated();
        }
        editor.report(existed ? "Added route to " + newFileName : "Extracted to " + newFileName, false);
    }

    private void applyReplaceUri(int row, String rawLine, String newUri) {
        String newLine = replaceUriOnLine(rawLine, newUri);
        editor.recordEditChange();
        List<String> lines = SourceEditorNavigation.lines(editState);
        lines.set(row, newLine);
        removeParametersBlock(lines, row, rawLine);
        editState.setText(YamlBlockEditor.fromLines(lines));
        SourceEditorNavigation.positionCursor(editState, row, countLeadingSpaces(newLine));
        editor.report("Replaced URI with: " + newUri, false);
    }

    private void applyExtractToProperty(int row, String rawLine, String propKey) {
        String value = extractValueFromLine(rawLine);
        if (value == null) {
            return;
        }
        String newLine = replaceValueWithPlaceholder(rawLine, propKey);
        editor.recordEditChange();
        List<String> lines = SourceEditorNavigation.lines(editState);
        lines.set(row, newLine);
        editState.setText(YamlBlockEditor.fromLines(lines));
        SourceEditorNavigation.positionCursor(editState, row, countLeadingSpaces(newLine));
        if (editor.editableFile() != null) {
            try {
                Path propsFile = editor.editableFile().getParent().resolve("application.properties");
                Files.writeString(propsFile, propKey + "=" + value + "\n", StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            } catch (IOException e) {
                editor.report("Warning: could not write application.properties: " + e.getMessage(), true);
                return;
            }
        }
        editor.report("Extracted to property: " + propKey, false);
    }
}
