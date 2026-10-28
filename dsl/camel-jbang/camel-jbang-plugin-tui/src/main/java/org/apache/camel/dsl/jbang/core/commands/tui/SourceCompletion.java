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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.widgets.input.TextAreaState;

import static org.apache.camel.dsl.jbang.core.commands.tui.YamlSourceContext.*;

/**
 * The Tab completion of the source editor ({@link SourceViewer}): which completion fits where the cursor is (a simple
 * expression, an endpoint uri, the XML or Java DSL, a YAML route, a properties file), the popup it opens at the cursor,
 * and the insertion of the item chosen there. SourceTab gives it the completion providers of the file it opens.
 */
class SourceCompletion {

    /** What the completion needs of the editor it completes in. */
    interface Editor {
        boolean isEditMode();

        /** Whether the file is a YAML route file, completed by its structure. */
        boolean isCamelYamlFile();

        /** Before an edit: the undo history and the modified flag. */
        void recordEditChange();

        /** The text was changed without an undo step. */
        void markDirty();
    }

    private record UriCompletion(int row, int endCol, String prefix, String suffix) {
    }

    private record SimpleCompletion(int row, int endCol, SimpleCompletionContext context) {
    }

    private record SemanticCompletion(CompletableFuture<List<AutocompletePopup.CompletionItem>> future,
            String text, int row, int col, String value) {
    }

    private record XmlCompletion(int row, int endCol, XmlCompletionContext context) {
    }

    private record JavaCompletion(int row, int endCol, JavaChainContext context) {
    }

    private final TextAreaState editState;
    private final YamlSourceContext yaml;
    private final Editor editor;
    private AutocompletePopup.AutocompleteProvider autocompleteProvider;
    private AutocompletePopup.ValueProvider autocompleteValueProvider;
    private Predicate<String> listItemNodeChecker;
    private AutocompletePopup autocompletePopup;
    /** java or xml: Tab completes the endpoint uris of a Java or XML route file; null for neither. */
    private String uriCompletion;
    /** The completion being chosen in a uri: its row, the column its prefix ends at, the prefix and suffix. */
    private UriCompletion pendingUriCompletion;
    private BiFunction<SimpleCompletionContext, List<String>, List<AutocompletePopup.CompletionItem>> simpleCompletion;
    private SimpleCompletion pendingSimpleCompletion;
    private BiFunction<XmlCompletionContext, List<String>, List<AutocompletePopup.CompletionItem>> xmlCompletion;
    private XmlCompletion pendingXmlCompletion;
    private Function<SemanticCompletionContext, CompletableFuture<List<AutocompletePopup.CompletionItem>>> semanticCompletion;
    private SemanticCompletion pendingSemanticCompletion;
    private Function<JavaChainContext, List<AutocompletePopup.CompletionItem>> javaCompletion;
    private JavaCompletion pendingJavaCompletion;

    SourceCompletion(TextAreaState editState, YamlSourceContext yaml, Editor editor) {
        this.editState = editState;
        this.yaml = yaml;
        this.editor = editor;
    }

    void setAutocompleteProvider(AutocompletePopup.AutocompleteProvider provider) {
        this.autocompleteProvider = provider;
    }

    void setAutocompleteValueProvider(AutocompletePopup.ValueProvider provider) {
        this.autocompleteValueProvider = provider;
    }

    void setListItemNodeChecker(Predicate<String> checker) {
        this.listItemNodeChecker = checker;
    }

    /**
     * Tab completion in the endpoint uris of a Java or XML route file (CAMEL-25208): in the string given to from, to,
     * toD... of Java, or the uri attribute of those elements in XML, the component names, the endpoint options and
     * their values, from the completion providers the YAML uris use.
     *
     * @param dsl java or xml; null for a file that is neither
     */
    void setUriCompletion(String dsl) {
        this.uriCompletion = dsl;
    }

    /**
     * Tab completion in the simple expressions of a YAML, Java or XML route file (CAMEL-25219): the functions after ${,
     * the header names after ${header., the operators after a function. Given the context at the cursor and the lines
     * of the file; null for a file without routes.
     */
    void setSimpleCompletion(
            BiFunction<SimpleCompletionContext, List<String>, List<AutocompletePopup.CompletionItem>> provider) {
        this.simpleCompletion = provider;
    }

    /**
     * Tab completion of the XML DSL (CAMEL-25240): the elements that go inside the parent after &lt; or on an empty
     * line, the attributes in a start tag, their values. Given the context at the cursor and the lines of the file;
     * null for a file that is no XML route.
     */
    void setXmlCompletion(
            BiFunction<XmlCompletionContext, List<String>, List<AutocompletePopup.CompletionItem>> provider) {
        this.xmlCompletion = provider;
    }

    void setSemanticCompletion(
            Function<SemanticCompletionContext, CompletableFuture<List<AutocompletePopup.CompletionItem>>> provider) {
        this.semanticCompletion = provider;
        pendingSemanticCompletion = null;
    }

    /**
     * Tab completion of the Java DSL route chain (CAMEL-25241): after a dot, the options of the EIP the chain is on and
     * the EIPs. Null for a file that is no Java route.
     */
    void setJavaCompletion(Function<JavaChainContext, List<AutocompletePopup.CompletionItem>> provider) {
        this.javaCompletion = provider;
    }

    /** Whether Tab completes in this file: there is a completion of its keys or options. */
    boolean isEnabled() {
        return autocompleteProvider != null;
    }

    /** java or xml for a Java or XML route file, the DSL its refactorings work in too; null for neither. */
    String routeDsl() {
        return uriCompletion;
    }

    /** Whether the children of the YAML key are list items (steps, ...), which Enter starts with a dash. */
    boolean isListItemNode(String key) {
        return listItemNodeChecker != null && listItemNodeChecker.test(key);
    }

    /** Closes the popup; true when it was open. */
    boolean closePopup() {
        if (autocompletePopup == null) {
            return false;
        }
        autocompletePopup = null;
        return true;
    }

    /** Drops the completion of a value still being looked up, as the text or the cursor changed. */
    void dropSemanticCompletion() {
        pendingSemanticCompletion = null;
    }

    /** Forgets the completion of the languages of the file shown, which the next file sets again. */
    void clearLanguageProviders() {
        uriCompletion = null;
        simpleCompletion = null;
        xmlCompletion = null;
        semanticCompletion = null;
        pendingSemanticCompletion = null;
        javaCompletion = null;
    }

    void reset() {
        autocompleteProvider = null;
        autocompleteValueProvider = null;
        autocompletePopup = null;
        clearLanguageProviders();
    }

    /** The keys of the popup while it is open: true then, as it takes them all. */
    boolean handleKeyEvent(KeyEvent ke) {
        if (autocompletePopup == null) {
            return false;
        }
        boolean wasValueMode = autocompletePopup.isValueMode();
        boolean wasListItem = autocompletePopup.isListItemInsertion();
        AutocompletePopup.Result result = autocompletePopup.handleKeyEvent(ke);
        if (result == AutocompletePopup.Result.CLOSED) {
            AutocompletePopup.CompletionItem item = autocompletePopup.consumeSelectedItem();
            autocompletePopup = null;
            if (item != null) {
                insertCompletion(item, wasValueMode, wasListItem);
            }
        } else if (result == AutocompletePopup.Result.CURSOR_RIGHT) {
            editState.moveCursorRight();
        } else if (result == AutocompletePopup.Result.CURSOR_LEFT) {
            editState.moveCursorLeft();
        }
        return true;
    }

    /** The mouse in the popup while it is open: true then, as it takes it. */
    boolean handleMouseEvent(MouseEvent me) {
        if (autocompletePopup == null) {
            return false;
        }
        boolean wasValueMode = autocompletePopup.isValueMode();
        boolean wasListItem = autocompletePopup.isListItemInsertion();
        AutocompletePopup.Result result = autocompletePopup.handleMouseEvent(me);
        if (result == AutocompletePopup.Result.CLOSED) {
            AutocompletePopup.CompletionItem item = autocompletePopup.consumeSelectedItem();
            autocompletePopup = null;
            if (item != null) {
                insertCompletion(item, wasValueMode, wasListItem);
            }
        }
        return true;
    }

    /** Draws the popup, when it is open, at the cursor in the editor area. */
    void render(Frame frame, Rect editorArea) {
        if (autocompletePopup != null) {
            int cursorRow = editState.cursorRow() - editState.scrollRow();
            int cursorCol = editState.cursorCol() - editState.scrollCol();
            autocompletePopup.render(frame, editorArea, cursorRow, cursorCol);
        }
    }

    /** Apply completed metadata on the UI thread only while the original completion position is unchanged. */
    void refreshSemanticCompletion() {
        SemanticCompletion pending = pendingSemanticCompletion;
        if (pending == null || !pending.future().isDone()) {
            return;
        }
        pendingSemanticCompletion = null;
        if (!editor.isEditMode() || pending.row() != editState.cursorRow() || pending.col() != editState.cursorCol()
                || !pending.text().equals(editState.text())) {
            return;
        }
        List<AutocompletePopup.CompletionItem> items = pending.future().getNow(List.of());
        if (!items.isEmpty()) {
            autocompletePopup = new AutocompletePopup(items, "", pending.value(), true);
        }
    }

    void openAutocomplete() {
        pendingUriCompletion = null;
        pendingSimpleCompletion = null;
        pendingXmlCompletion = null;
        pendingJavaCompletion = null;
        if (openSimpleAutocomplete()) {
            return;
        }
        if (editor.isCamelYamlFile()) {
            openYamlAutocomplete();
        } else if (uriCompletion != null) {
            if (!openUriAutocomplete()) {
                if ("xml".equals(uriCompletion)) {
                    openXmlAutocomplete();
                } else if ("java".equals(uriCompletion)) {
                    openJavaAutocomplete();
                }
            }
        } else {
            openPropertiesAutocomplete();
        }
    }

    /**
     * The completion of the endpoint uri the cursor is in, in a Java or XML route: the component before the colon, an
     * option after ? or &, its value after =. Read from the line alone, so it works while the file does not parse.
     */
    private boolean openUriAutocomplete() {
        int row = editState.cursorRow();
        int col = editState.cursorCol();
        String line = editState.getLine(row);
        EndpointUriContext c = "xml".equals(uriCompletion)
                ? EndpointUriContext.inXml(line, col) : EndpointUriContext.inJava(line, col);
        if (c == null || !c.isEndpoint() || autocompleteProvider == null) {
            return false;
        }
        String before = c.before();
        int colon = before.indexOf(':');
        List<AutocompletePopup.CompletionItem> items;
        String prefix;
        String suffix;
        if (colon < 0) {
            items = autocompleteProvider.provide("yaml-uri:" + c.role());
            prefix = before;
            suffix = ":";
        } else {
            String scheme = before.substring(0, colon);
            int q = before.indexOf('?');
            if (q < 0) {
                // the path of the uri: what goes there is the component's own
                return true;
            }
            int sep = Math.max(before.lastIndexOf('?'), before.lastIndexOf('&'));
            String segment = before.substring(sep + 1);
            int eq = segment.indexOf('=');
            if (eq >= 0) {
                if (autocompleteValueProvider == null) {
                    return true;
                }
                items = autocompleteValueProvider.provide("yaml:" + scheme + ":" + segment.substring(0, eq));
                prefix = segment.substring(eq + 1);
                suffix = "";
            } else {
                List<String> given = new ArrayList<>();
                for (String pair : (sep > q ? before.substring(q + 1, sep) : "").split("&")) {
                    if (!pair.isEmpty()) {
                        given.add(pair.contains("=") ? pair.substring(0, pair.indexOf('=')) : pair);
                    }
                }
                items = autocompleteProvider.provide("yaml:" + scheme + ":" + c.role() + ":" + String.join(",", given)
                                                     + "|" + before.substring(0, sep));
                prefix = segment;
                suffix = "=";
            }
        }
        if (items != null && !items.isEmpty()) {
            autocompletePopup = new AutocompletePopup(items, prefix, prefix, true);
            if (colon < 0) {
                autocompletePopup.setTitlePrefix("Components");
            }
            pendingUriCompletion = new UriCompletion(row, col, prefix, suffix);
        }
        return true;
    }

    /**
     * The completion of the simple expression the cursor is in, read from the line alone like the uris: true when the
     * cursor is in one, so no other completion is tried, also when nothing matches there.
     */
    private boolean openSimpleAutocomplete() {
        if (simpleCompletion == null) {
            return false;
        }
        int row = editState.cursorRow();
        int col = editState.cursorCol();
        List<String> all = SourceEditorNavigation.lines(editState);
        SimpleCompletionContext c = SimpleCompletionContext.at(all, row, col);
        if (c == null) {
            return false;
        }
        List<AutocompletePopup.CompletionItem> items = simpleCompletion.apply(c, all);
        if (items != null && !items.isEmpty()) {
            // not in value mode, which puts a popup with a long description over the line being edited
            autocompletePopup = new AutocompletePopup(items, c.prefix(), c.prefix(), false);
            autocompletePopup.setFullKeys(true);
            autocompletePopup.setTitlePrefix(switch (c.kind()) {
                case FUNCTION -> "Simple functions";
                case HEADER -> "Headers";
                case PROPERTY -> "Exchange properties";
                case VARIABLE -> "Variables";
                case OPERATOR -> "Operators";
                case DATE_COMMAND -> "Date commands";
                case DATE_PATTERN -> "Date patterns";
                case TIME_ZONE -> "Time zones";
                case BEAN -> "Beans";
                case PROPERTY_KEY -> "Properties";
            });
            pendingSimpleCompletion = new SimpleCompletion(row, col, c);
        }
        return true;
    }

    /**
     * Replaces the prefix the simple completion was opened on with the chosen item, and what closes it (the } of a
     * function or name, the space after an operator) unless that is there already. A chosen header. or
     * exchangeProperty. opens the names right away.
     */
    private void insertSimpleCompletion(AutocompletePopup.CompletionItem item) {
        SimpleCompletion sc = pendingSimpleCompletion;
        pendingSimpleCompletion = null;
        if (editState.cursorRow() != sc.row()) {
            return;
        }
        SimpleCompletionContext c = sc.context();
        SourceEditorNavigation.moveToColumn(editState, sc.endCol());
        SourceEditorNavigation.deleteBackTo(editState, editState.cursorCol() - c.prefix().length());
        // Tab in the middle of a word replaces all of it
        String line = editState.getLine(sc.row());
        int from = editState.cursorCol();
        int end = from;
        while (end < line.length() && isSimpleWordChar(line.charAt(end), c.kind())) {
            end++;
        }
        SourceEditorNavigation.deleteForwardTo(editState, end);
        String text = item.insertText();
        String closing = c.closing();
        if (c.kind() == SimpleCompletionContext.Kind.FUNCTION && text.endsWith("}")) {
            text = text.substring(0, text.length() - 1);
            closing = "}";
        }
        boolean hasClosing = !closing.isEmpty() && line.startsWith(closing, end);
        editState.insert(text + (hasClosing ? "" : closing));
        boolean stem = c.kind() == SimpleCompletionContext.Kind.FUNCTION
                && (text.endsWith(".") || text.endsWith("[") || text.endsWith(":") || text.endsWith("("));
        if (stem || c.kind() == SimpleCompletionContext.Kind.TIME_ZONE) {
            // header. opens the header names, date: the date commands, bean: the beans, a time zone the patterns;
            // only the simple completion, nothing else when the function has no values to offer
            openSimpleAutocomplete();
        }
    }

    /** A character of the function, name or operator being completed. */
    private static boolean isSimpleWordChar(char c, SimpleCompletionContext.Kind kind) {
        return switch (kind) {
            case OPERATOR -> !Character.isWhitespace(c) && c != '$' && c != '\'' && c != '"';
            case FUNCTION, HEADER, PROPERTY, VARIABLE ->
                Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.' || c == ':';
            // the argument only: the .method after a bean, the :pattern after a date command stay
            case PROPERTY_KEY -> Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.';
            case DATE_PATTERN -> c != '}' && c != ')' && c != '"' && c != '\'';
            default -> Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '/';
        };
    }

    /**
     * The completion of the XML DSL at the cursor, read from the text above it like the uris: the elements after &lt;
     * or on an empty line, the attributes in a start tag, the values in an attribute.
     */
    private void openXmlAutocomplete() {
        if (xmlCompletion == null) {
            return;
        }
        int row = editState.cursorRow();
        int col = editState.cursorCol();
        List<String> all = SourceEditorNavigation.lines(editState);
        XmlCompletionContext c = XmlCompletionContext.at(all, row, col);
        if (c == null) {
            return;
        }
        List<AutocompletePopup.CompletionItem> items = xmlCompletion.apply(c, all);
        if (items != null && !items.isEmpty()) {
            autocompletePopup = new AutocompletePopup(items, c.prefix(), c.prefix(), false);
            autocompletePopup.setFullKeys(true);
            autocompletePopup.setTitlePrefix(switch (c.kind()) {
                case ELEMENT -> c.parent() != null ? "Inside " + c.parent() : "Elements";
                case ATTRIBUTE -> c.element() + " attributes";
                case VALUE -> c.attribute();
            });
            pendingXmlCompletion = new XmlCompletion(row, col, c);
        }
    }

    /**
     * Replaces the prefix the XML completion was opened on with the chosen element, attribute or value, and puts the
     * cursor where the snippet says (in the first required attribute, inside the new element, in the quotes of the new
     * attribute, whose values open right away).
     */
    private void insertXmlCompletion(AutocompletePopup.CompletionItem item) {
        XmlCompletion xc = pendingXmlCompletion;
        pendingXmlCompletion = null;
        if (editState.cursorRow() != xc.row()) {
            return;
        }
        XmlCompletionContext c = xc.context();
        SourceEditorNavigation.moveToColumn(editState, xc.endCol());
        SourceEditorNavigation.deleteBackTo(editState, editState.cursorCol() - c.prefix().length());
        // Tab in the middle of a name replaces all of it
        String line = editState.getLine(xc.row());
        int from = editState.cursorCol();
        int end = from;
        boolean value = c.kind() == XmlCompletionContext.Kind.VALUE;
        while (end < line.length() && (value
                ? line.charAt(end) != '"' && line.charAt(end) != '\''
                : Character.isLetterOrDigit(line.charAt(end)) || line.charAt(end) == '-')) {
            end++;
        }
        SourceEditorNavigation.deleteForwardTo(editState, end);
        String text = item.insertText();
        int caret = text.indexOf(XmlCompletions.CARET);
        if (caret >= 0) {
            text = text.substring(0, caret) + text.substring(caret + 1);
        }
        editState.insert(text);
        if (caret >= 0) {
            SourceEditorNavigation.moveToColumn(editState, editState.cursorCol() - (text.length() - caret));
        }
        if (c.kind() == XmlCompletionContext.Kind.ATTRIBUTE) {
            openAutocomplete();
        }
    }

    /** The completion of the Java DSL route chain after a dot, read from the text above the cursor like the uris. */
    private void openJavaAutocomplete() {
        if (javaCompletion == null) {
            return;
        }
        int row = editState.cursorRow();
        int col = editState.cursorCol();
        List<String> all = SourceEditorNavigation.lines(editState);
        JavaChainContext c = JavaChainContext.at(all, row, col);
        if (c == null) {
            return;
        }
        List<AutocompletePopup.CompletionItem> items = javaCompletion.apply(c);
        if (items != null && !items.isEmpty()) {
            autocompletePopup = new AutocompletePopup(items, c.prefix(), c.prefix(), false);
            autocompletePopup.setFullKeys(true);
            autocompletePopup.setTitlePrefix("Java DSL");
            pendingJavaCompletion = new JavaCompletion(row, col, c);
        }
    }

    /**
     * Replaces the name typed after the dot with the chosen method, with its parentheses (the cursor inside them when
     * it takes arguments) unless they follow already.
     */
    private void insertJavaCompletion(AutocompletePopup.CompletionItem item) {
        JavaCompletion jc = pendingJavaCompletion;
        pendingJavaCompletion = null;
        if (editState.cursorRow() != jc.row()) {
            return;
        }
        SourceEditorNavigation.moveToColumn(editState, jc.endCol());
        SourceEditorNavigation.deleteBackTo(editState, editState.cursorCol() - jc.context().prefix().length());
        String line = editState.getLine(jc.row());
        int from = editState.cursorCol();
        int end = from;
        while (end < line.length() && Character.isJavaIdentifierPart(line.charAt(end))) {
            end++;
        }
        SourceEditorNavigation.deleteForwardTo(editState, end);
        String text = item.insertText();
        int caret = text.indexOf(XmlCompletions.CARET);
        if (caret >= 0) {
            text = text.substring(0, caret) + text.substring(caret + 1);
        }
        if (end < line.length() && line.charAt(end) == '(') {
            // the arguments are there already: only the name changes
            text = item.key();
            caret = -1;
        }
        editState.insert(text);
        if (caret >= 0) {
            SourceEditorNavigation.moveToColumn(editState, editState.cursorCol() - (text.length() - caret));
        }
    }

    /** Replaces the prefix the completion was opened on with the chosen item and what follows it (: or =). */
    private void insertUriCompletion(AutocompletePopup.CompletionItem item) {
        UriCompletion jc = pendingUriCompletion;
        pendingUriCompletion = null;
        if (editState.cursorRow() != jc.row()) {
            return;
        }
        // the arrows move the cursor within the prefix while the popup is open: back to its end
        SourceEditorNavigation.moveToColumn(editState, jc.endCol());
        SourceEditorNavigation.deleteBackTo(editState, editState.cursorCol() - jc.prefix().length());
        // Tab in the middle of a word replaces all of it: the rest of the name after the cursor goes too
        String line = editState.getLine(jc.row());
        int from = editState.cursorCol();
        int end = from;
        while (end < line.length() && isUriWordChar(line.charAt(end), jc.suffix())) {
            end++;
        }
        SourceEditorNavigation.deleteForwardTo(editState, end);
        // the : or = the name is followed by is not doubled when it is there already
        boolean hasSuffix = !jc.suffix().isEmpty() && line.startsWith(jc.suffix(), end);
        editState.insert(item.key() + (hasSuffix ? "" : jc.suffix()));
    }

    /** A character of the name or value being completed: up to the : of a component, the = of an option, the & or ". */
    private static boolean isUriWordChar(char c, String suffix) {
        if (suffix.isEmpty()) {
            return c != '&' && c != '"' && c != '\'' && c != '\\';
        }
        return Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.';
    }

    private void openPropertiesAutocomplete() {
        String lineText = editState.getLine(editState.cursorRow());
        int col = editState.cursorCol();
        String textBeforeCursor = col <= lineText.length() ? lineText.substring(0, col) : lineText;

        int eq = textBeforeCursor.indexOf('=');
        if (eq >= 0 && autocompleteValueProvider != null) {
            // cursor is after '=' — try value completion
            String key = textBeforeCursor.substring(0, eq).trim();
            String valuePrefix = textBeforeCursor.substring(eq + 1).trim();
            List<AutocompletePopup.CompletionItem> values = autocompleteValueProvider.provide(key);
            if (values != null && !values.isEmpty()) {
                autocompletePopup = new AutocompletePopup(values, valuePrefix, valuePrefix, true);
            }
            return;
        }

        // key completion
        String prefix = textBeforeCursor.trim();

        // load all options for the group (up to last dot) so the full list is available
        int lastDot = prefix.lastIndexOf('.');
        String groupPrefix = lastDot >= 0 ? prefix.substring(0, lastDot + 1) : prefix;

        // extract full key text for left/right cursor navigation
        String fullKey = lineText;
        int eqFull = fullKey.indexOf('=');
        if (eqFull >= 0) {
            fullKey = fullKey.substring(0, eqFull);
        }
        fullKey = fullKey.trim();

        List<AutocompletePopup.CompletionItem> items = autocompleteProvider.provide(groupPrefix);
        if (items != null && !items.isEmpty()) {
            autocompletePopup = new AutocompletePopup(items, prefix, fullKey);
        }
    }

    private void openYamlAutocomplete() {
        int row = editState.cursorRow();
        if (yaml.findScopeLineRow(row) < 0) {
            return;
        }
        String lineText = editState.getLine(row);
        String trimmed = lineText.trim();
        if (trimmed.startsWith("- ")) {
            trimmed = trimmed.substring(2).trim();
        } else if (trimmed.equals("-")) {
            trimmed = "";
        }

        // try component name completion on uri: lines first
        YamlUriContext uriCtx = yaml.findUriContext(row);
        if (uriCtx != null && autocompleteProvider != null) {
            String role = uriCtx.consumer() ? "consumer" : "producer";
            String context = "yaml-uri:" + role;
            List<AutocompletePopup.CompletionItem> items = autocompleteProvider.provide(context);
            if (items != null && !items.isEmpty()) {
                autocompletePopup = new AutocompletePopup(items, uriCtx.prefix(), uriCtx.prefix(), true);
                autocompletePopup.setTitlePrefix("Components");
            }
            return;
        }

        YamlEndpointContext ctx = yaml.findEnclosingComponent(row);
        if (ctx != null) {
            int colonIdx = trimmed.indexOf(':');
            if (colonIdx > 0) {
                String optionName = trimmed.substring(0, colonIdx).trim();
                String valueText = trimmed.substring(colonIdx + 1).trim();
                if (valueText.startsWith("\"") || valueText.startsWith("'")) {
                    valueText = valueText.substring(1);
                }
                if (valueText.endsWith("\"") || valueText.endsWith("'")) {
                    valueText = valueText.substring(0, valueText.length() - 1);
                }
                if (autocompleteValueProvider != null) {
                    String context = "yaml:" + ctx.component() + ":" + optionName;
                    List<AutocompletePopup.CompletionItem> values = autocompleteValueProvider.provide(context);
                    if (values != null && !values.isEmpty()) {
                        autocompletePopup = new AutocompletePopup(values, "", valueText, true);
                    }
                }
            } else {
                // auto-insert parameters: block if cursor is below uri: without one
                if (ctx.needsParameters() && lineText.isBlank()) {
                    // parameters: must be a sibling of uri:, so use the cursor's real column
                    // (matching uri:'s indent via Enter-key auto-indent) rather than
                    // deriveInsertionIndent's EIP-scope heuristic, which resolves the scope to
                    // the uri: line itself here and then adds a level, nesting parameters: one
                    // level too deep and breaking findEnclosingComponent's uri-sibling lookup
                    int indent = yaml.effectiveBlankIndent(row);
                    String indentStr = " ".repeat(indent);
                    editState.moveCursorToLineStart();
                    editState.insert(indentStr + "parameters:");
                    editState.insert('\n');
                    editState.insert(indentStr + "  ");
                    editor.markDirty();
                    trimmed = "";
                }

                String filter = trimmed;
                String role = ctx.consumer() ? "consumer" : "producer";
                Set<String> existing = yaml.collectExistingParameters(editState.cursorRow());
                String context = "yaml:" + ctx.component() + ":" + role;
                if (!existing.isEmpty()) {
                    context += ":" + String.join(",", existing);
                }
                if (ctx.uri() != null) {
                    context += "|" + ctx.uri();
                }
                List<AutocompletePopup.CompletionItem> items = autocompleteProvider.provide(context);
                if (items != null && !items.isEmpty()) {
                    autocompletePopup = new AutocompletePopup(items, filter, filter);
                    autocompletePopup.setTitlePrefix(optionsTitle(ctx.component(), ctx.uri()));
                }
            }
            return;
        }

        // Tree completion uses the full YAML path so named map entries retain their schema context.
        if (autocompleteProvider != null) {
            String parentKey = yaml.findParentYamlKey(row);
            String parentPath = yaml.findParentYamlPath(row);
            int colonIdx = trimmed.indexOf(':');

            if (colonIdx > 0) {
                // value completion
                String optionName = trimmed.substring(0, colonIdx).trim();
                String valueText = trimmed.substring(colonIdx + 1).trim();
                if (valueText.startsWith("\"") || valueText.startsWith("'")) {
                    valueText = valueText.substring(1);
                }
                if (valueText.endsWith("\"") || valueText.endsWith("'")) {
                    valueText = valueText.substring(0, valueText.length() - 1);
                }
                if (autocompleteValueProvider != null) {
                    String context = "yaml-tree-value:" + parentPath + ":" + optionName;
                    SemanticCompletionContext semantic = semanticCompletion != null
                            ? SemanticCompletionContext.at(SourceEditorNavigation.lines(editState), row) : null;
                    if (semantic != null) {
                        pendingSemanticCompletion = new SemanticCompletion(
                                semanticCompletion.apply(semantic),
                                editState.text(), row, editState.cursorCol(), valueText);
                        refreshSemanticCompletion();
                    } else {
                        List<AutocompletePopup.CompletionItem> values = autocompleteValueProvider.provide(context);
                        if (values != null && !values.isEmpty()) {
                            autocompletePopup = new AutocompletePopup(values, "", valueText, true);
                        }
                    }
                }
            } else {
                // key completion
                String filter = trimmed;
                Set<String> existing = yaml.collectExistingSiblingKeys(row);
                String context = "yaml-tree:" + parentPath;
                if (!existing.isEmpty()) {
                    context += ":" + String.join(",", existing);
                }
                List<AutocompletePopup.CompletionItem> items = autocompleteProvider.provide(context);
                if (items != null && !items.isEmpty()) {
                    autocompletePopup = new AutocompletePopup(items, filter, filter);
                    autocompletePopup.setTitlePrefix(parentKey);
                    if (listItemNodeChecker != null && listItemNodeChecker.test(parentKey)) {
                        autocompletePopup.setListItemInsertion(true);
                    }
                }
            }
        }
    }

    private void insertCompletion(AutocompletePopup.CompletionItem item, boolean valueMode, boolean listItem) {
        editor.recordEditChange();
        if (pendingUriCompletion != null) {
            insertUriCompletion(item);
            return;
        }
        if (pendingSimpleCompletion != null) {
            insertSimpleCompletion(item);
            return;
        }
        if (pendingXmlCompletion != null) {
            insertXmlCompletion(item);
            return;
        }
        if (pendingJavaCompletion != null) {
            insertJavaCompletion(item);
            return;
        }
        String currentLine = editState.getLine(editState.cursorRow());
        if (editor.isCamelYamlFile()) {
            insertYamlCompletion(item, valueMode, currentLine, listItem);
        } else {
            insertPropertiesCompletion(item, valueMode, currentLine);
        }
    }

    private void insertPropertiesCompletion(AutocompletePopup.CompletionItem item, boolean valueMode, String currentLine) {
        if (valueMode) {
            int eq = currentLine.indexOf('=');
            if (eq >= 0) {
                String keyPart = currentLine.substring(0, eq + 1);
                SourceEditorNavigation.clearLine(editState);
                editState.insert(keyPart + item.key());
            }
        } else {
            SourceEditorNavigation.clearLine(editState);
            boolean isGroup = item.key().endsWith(".");
            String insertText = isGroup ? item.key() : item.key() + "=";
            editState.insert(insertText);
            if (isGroup && autocompleteProvider != null) {
                openAutocomplete();
            }
        }
    }

    void insertYamlCompletion(AutocompletePopup.CompletionItem item, boolean valueMode, String currentLine) {
        // no explicit cursor column given (e.g. direct test calls) — assume the cursor sits at
        // the end of the given line, matching this method's original, column-agnostic behavior
        insertYamlCompletion(item, valueMode, currentLine, false, currentLine.length());
    }

    private void insertYamlCompletion(
            AutocompletePopup.CompletionItem item, boolean valueMode, String currentLine, boolean listItem) {
        insertYamlCompletion(item, valueMode, currentLine, listItem, editState.cursorCol());
    }

    /** Package-private (rather than private) so tests can exercise an explicit cursor column. */
    void insertYamlCompletion(
            AutocompletePopup.CompletionItem item, boolean valueMode, String currentLine, boolean listItem,
            int cursorCol) {
        int indent;
        if (currentLine.isEmpty()) {
            // truly empty line (no auto-inserted whitespace yet) — derive from the enclosing EIP
            indent = yaml.deriveInsertionIndent(editState.cursorRow());
        } else if (currentLine.isBlank()) {
            // whitespace-only line: the cursor's column is the real, intended nesting depth —
            // Shift+Tab (see yaml.moveCursorToPreviousIndentStop()) can dedent it within the existing
            // whitespace without trimming the line, so the line's own length would be stale here
            indent = Math.min(cursorCol, currentLine.length());
        } else {
            indent = countLeadingSpaces(currentLine);
        }
        String indentStr = " ".repeat(indent);

        SourceEditorNavigation.clearLine(editState);

        if (valueMode) {
            String trimmed = currentLine.trim();
            if (trimmed.startsWith("- ")) {
                trimmed = trimmed.substring(2).trim();
            }
            int colonIdx = trimmed.indexOf(':');
            String value = item.key();
            if (value.contains("{{")) {
                value = "\"" + value + "\"";
            }
            if (colonIdx > 0) {
                String keyPart = trimmed.substring(0, colonIdx);
                editState.insert(indentStr + keyPart + ": " + value);
            } else {
                editState.insert(indentStr + value);
            }
            // for component names, add parameters: block if not already present
            if ("component".equals(item.type())) {
                int nextRow = editState.cursorRow() + 1;
                boolean hasParameters = nextRow < editState.lineCount()
                        && editState.getLine(nextRow).trim().startsWith("parameters:");
                if (!hasParameters) {
                    editState.insert('\n');
                    editState.insert(indentStr + "parameters:");
                    editState.insert('\n');
                    editState.insert(indentStr + "  ");
                }
            }
        } else {
            String prefix = listItem ? "- " : "";
            editState.insert(indentStr + prefix + item.key() + ":");
            if ("array".equals(item.type())) {
                editState.insert('\n');
                int childIndent = indent + (listItem ? 4 : 2);
                editState.insert(" ".repeat(childIndent) + "- ");
            } else if ("object".equals(item.type())) {
                editState.insert('\n');
                editState.insert(indentStr + (listItem ? "    " : "  "));
            } else {
                editState.insert(' ');
            }
        }
    }

    /**
     * The title of the options popup of an endpoint: the component, or for a kamelet: endpoint the Kamelet it names,
     * whose properties the list starts with (CAMEL-25411).
     */
    static String optionsTitle(String component, String uri) {
        if ("kamelet".equals(component) && uri != null && uri.startsWith("kamelet:")) {
            String name = uri.substring("kamelet:".length()).split("[?/]", 2)[0].trim();
            if (!name.isEmpty()) {
                return name + " options";
            }
        }
        return component + " options";
    }
}
