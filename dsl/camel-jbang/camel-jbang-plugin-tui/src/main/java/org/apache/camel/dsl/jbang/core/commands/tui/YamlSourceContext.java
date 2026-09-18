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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import dev.tamboui.widgets.input.TextAreaState;

/**
 * Structural analysis of the Camel YAML DSL text being edited in {@link SourceViewer}: which component, EIP or URI the
 * cursor is inside, existing sibling keys and parameters, indentation rules for inserting and pasting lines, and the
 * breadcrumb of enclosing nodes. All lookups read the live {@link TextAreaState} of the editor.
 */
final class YamlSourceContext {

    private final TextAreaState editState;

    static final Set<String> CONSUMER_EIPS
            = Set.of("from", "pollEnrich", "poll-enrich", "poll", "interceptFrom", "intercept-from");
    static final Set<String> PRODUCER_EIPS
            = Set.of("to", "toD", "to-d", "wireTap", "wire-tap", "enrich",
                    "interceptSendToEndpoint", "intercept-send-to-endpoint");

    static final Set<String> STRUCTURAL_KEYS
            = Set.of("steps", "uri", "parameters", "from", "expression", "routeConfiguration",
                    "routeTemplate", "templatedRoute", "rest", "beans");

    static final Set<String> BREADCRUMB_SKIP_KEYS
            = Set.of("steps", "uri", "expression",
                    "routeConfiguration", "routeTemplate", "templatedRoute", "rest", "beans");

    YamlSourceContext(TextAreaState editState) {
        this.editState = editState;
    }

    record YamlEndpointContext(String component, boolean consumer, String uri, boolean needsParameters) {
        YamlEndpointContext(String component, boolean consumer, String uri) {
            this(component, consumer, uri, false);
        }
    }

    record YamlUriContext(boolean consumer, String prefix) {
    }

    record YamlEipContext(String eipName) {
    }

    YamlEndpointContext findEnclosingComponent(int fromRow) {
        String cursorLine = editState.getLine(fromRow);
        // a blank line's own leading whitespace can be stale after a Shift+Tab dedent (see
        // effectiveBlankIndent) — use the cursor's real column so a cursor dedented back out of
        // an endpoint's parameters: block isn't mistaken for still being inside it
        int cursorIndent = cursorLine.isBlank() ? effectiveBlankIndent(fromRow) : countLeadingSpaces(cursorLine);

        // list items (- key:) are inside steps, not inside parameters
        if (!cursorLine.isBlank() && cursorLine.trim().startsWith("- ")) {
            return null;
        }

        // blank line positioned as a sibling of a uri: line with no parameters: block yet —
        // offer to create one. Scan siblings at exactly cursorIndent; a shallower line ends it.
        if (cursorLine.isBlank()) {
            for (int i = fromRow - 1; i >= 0; i--) {
                String line = editState.getLine(i);
                if (line.isBlank()) {
                    continue;
                }
                int indent = countLeadingSpaces(line);
                if (indent < cursorIndent) {
                    break;
                }
                if (indent == cursorIndent) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("parameters:")) {
                        // a parameters: block already exists as a sibling here — nothing to
                        // auto-create, and the cursor isn't inside it either (it's a sibling,
                        // not a child); fall through to generic EIP-field completion instead
                        return null;
                    }
                    if (trimmed.startsWith("uri:") || trimmed.startsWith("- uri:")) {
                        return findComponentFromUriSibling(i);
                    }
                }
            }
        }

        int parametersRow = -1;
        int parametersIndent = -1;

        for (int i = fromRow; i >= 0; i--) {
            String line = editState.getLine(i);
            if (line.isBlank()) {
                continue;
            }
            int indent = countLeadingSpaces(line);
            String trimmed = line.trim();

            if (trimmed.startsWith("parameters:") && indent < cursorIndent) {
                parametersRow = i;
                parametersIndent = indent;
                break;
            }
            if (i < fromRow && indent < cursorIndent && !trimmed.startsWith("#")) {
                // stop if we hit a structural boundary (steps:, from:, etc.)
                break;
            }
            // also stop if we hit a list item at a shallower or equal indent — we've left the parameters scope
            if (i < fromRow && indent <= cursorIndent) {
                String key = extractEipName(trimmed);
                if (key != null && ("steps".equals(key) || "from".equals(key)
                        || trimmed.startsWith("- "))) {
                    break;
                }
            }
        }

        if (parametersRow < 0) {
            return null;
        }

        String foundScheme = null;
        String foundUri = null;
        for (int i = parametersRow - 1; i >= 0; i--) {
            String line = editState.getLine(i);
            if (line.isBlank()) {
                continue;
            }
            int indent = countLeadingSpaces(line);
            String trimmed = line.trim();

            if (indent == parametersIndent) {
                if (foundScheme == null && (trimmed.startsWith("uri:") || trimmed.startsWith("- uri:"))) {
                    foundScheme = extractSchemeFromUriLine(trimmed);
                    foundUri = extractUriValue(trimmed);
                }
            }

            if (indent < parametersIndent) {
                String eipName = extractEipName(trimmed);
                if (foundScheme == null) {
                    foundScheme = extractInlineUri(trimmed);
                    foundUri = foundScheme;
                }
                if (foundScheme != null) {
                    boolean consumer = eipName != null && CONSUMER_EIPS.contains(eipName);
                    return new YamlEndpointContext(foundScheme, consumer, foundUri);
                }
                break;
            }
        }

        if (foundScheme != null) {
            return new YamlEndpointContext(foundScheme, false, foundUri);
        }
        return null;
    }

    /**
     * When cursor is below a uri: line (no parameters: block), find the uri: among siblings and build the endpoint
     * context. Walks up to find the parent EIP to determine consumer vs producer.
     */
    YamlEndpointContext findComponentFromUriSibling(int uriOrSiblingRow) {
        int indent = countLeadingSpaces(editState.getLine(uriOrSiblingRow));

        // find the uri: line among siblings at the same indent
        String uriValue = null;
        String scheme = null;
        for (int i = uriOrSiblingRow; i >= 0; i--) {
            String line = editState.getLine(i);
            if (line.isBlank()) {
                continue;
            }
            int li = countLeadingSpaces(line);
            if (li < indent) {
                break;
            }
            if (li == indent && line.trim().startsWith("uri:")) {
                scheme = extractSchemeFromUriLine(line.trim());
                uriValue = extractUriValue(line.trim());
                break;
            }
        }
        if (scheme == null) {
            return null;
        }

        // find the parent EIP to determine consumer vs producer
        boolean consumer = false;
        for (int i = uriOrSiblingRow; i >= 0; i--) {
            String line = editState.getLine(i);
            if (line.isBlank()) {
                continue;
            }
            int li = countLeadingSpaces(line);
            if (li < indent) {
                String eipName = extractEipName(line.trim());
                if (eipName != null) {
                    consumer = CONSUMER_EIPS.contains(eipName);
                }
                break;
            }
        }
        return new YamlEndpointContext(scheme, consumer, uriValue, true);
    }

    java.util.Set<String> collectExistingParameters(int fromRow) {
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        // find the parameters: row by walking up
        int parametersRow = -1;
        int parametersIndent = -1;
        String cursorLine = editState.getLine(fromRow);
        int cursorIndent = countLeadingSpaces(cursorLine);

        // blank lines: derive indent from nearest preceding non-blank line
        if (cursorLine.isBlank()) {
            for (int i = fromRow - 1; i >= 0; i--) {
                String prev = editState.getLine(i);
                if (!prev.isBlank()) {
                    if (prev.trim().startsWith("parameters:")) {
                        parametersRow = i;
                        parametersIndent = countLeadingSpaces(prev);
                    } else {
                        cursorIndent = countLeadingSpaces(prev);
                    }
                    break;
                }
            }
        }

        if (parametersRow < 0) {
            for (int i = fromRow; i >= 0; i--) {
                String line = editState.getLine(i);
                if (line.isBlank()) {
                    continue;
                }
                String trimmed = line.trim();
                int indent = countLeadingSpaces(line);
                if (trimmed.startsWith("parameters:") && indent < cursorIndent) {
                    parametersRow = i;
                    parametersIndent = indent;
                    break;
                }
                if (i < fromRow && indent < cursorIndent && !trimmed.startsWith("#")) {
                    break;
                }
            }
        }
        if (parametersRow < 0) {
            return keys;
        }
        int childIndent = parametersIndent + 2;
        for (int i = parametersRow + 1; i < editState.lineCount(); i++) {
            if (i == fromRow) {
                continue;
            }
            String line = editState.getLine(i);
            if (line.isBlank()) {
                continue;
            }
            int indent = countLeadingSpaces(line);
            if (indent < childIndent) {
                break;
            }
            if (indent == childIndent) {
                String trimmed = line.trim();
                int colonIdx = trimmed.indexOf(':');
                if (colonIdx > 0) {
                    keys.add(trimmed.substring(0, colonIdx).trim());
                }
            }
        }
        return keys;
    }

    YamlUriContext findUriContext(int row) {
        String lineText = editState.getLine(row);
        String trimmed = lineText.trim();
        if (trimmed.startsWith("- ")) {
            trimmed = trimmed.substring(2).trim();
        }

        // Check if cursor is on a "uri:" line (possibly with partial value)
        if (trimmed.startsWith("uri:")) {
            String value = trimmed.substring(4).trim();
            if (value.startsWith("\"") || value.startsWith("'")) {
                value = value.substring(1);
            }
            if (value.endsWith("\"") || value.endsWith("'")) {
                value = value.substring(0, value.length() - 1);
            }
            // if value already contains a colon, scheme is already typed
            if (value.contains(":")) {
                return null;
            }
            // walk up to find the parent EIP
            int indent = countLeadingSpaces(lineText);
            for (int i = row - 1; i >= 0; i--) {
                String prev = editState.getLine(i);
                if (prev.isBlank()) {
                    continue;
                }
                int prevIndent = countLeadingSpaces(prev);
                if (prevIndent < indent) {
                    String eipName = extractEipName(prev.trim());
                    if (eipName != null) {
                        boolean consumer = CONSUMER_EIPS.contains(eipName);
                        return new YamlUriContext(consumer, value);
                    }
                    break;
                }
            }
            return null;
        }

        // Check if cursor is on an inline EIP line: "to: " or "from: kafka" (no colon in value)
        int colonIdx = trimmed.indexOf(':');
        if (colonIdx > 0) {
            String eipName = trimmed.substring(0, colonIdx).trim();
            if (CONSUMER_EIPS.contains(eipName) || PRODUCER_EIPS.contains(eipName)) {
                String value = trimmed.substring(colonIdx + 1).trim();
                if (value.startsWith("\"") || value.startsWith("'")) {
                    value = value.substring(1);
                }
                if (value.endsWith("\"") || value.endsWith("'")) {
                    value = value.substring(0, value.length() - 1);
                }
                if (value.contains(":")) {
                    return null;
                }
                boolean consumer = CONSUMER_EIPS.contains(eipName);
                return new YamlUriContext(consumer, value);
            }
        }
        return null;
    }

    int deriveBlankLineIndent(int fromRow) {
        return deriveIndentFromPredecessor(fromRow);
    }

    int deriveInsertionIndent(int fromRow) {
        // use the scope line (parent EIP) to derive indent for correct nesting
        int scopeRow = findScopeLineRow(fromRow);
        if (scopeRow >= 0) {
            String scopeLine = editState.getLine(scopeRow);
            int scopeIndent = countLeadingSpaces(scopeLine);
            String scopeTrimmed = scopeLine.trim();
            if (scopeTrimmed.startsWith("- ")) {
                return scopeIndent + 4;
            }
            return scopeIndent + 2;
        }
        // on a blank line with whitespace, walk up to find the parent EIP at lower indent
        String cursorLine = editState.getLine(fromRow);
        if (cursorLine.isBlank()) {
            int wsIndent = cursorLine.length();
            if (wsIndent > 0) {
                for (int i = fromRow - 1; i >= 0; i--) {
                    String line = editState.getLine(i);
                    if (line.isBlank()) {
                        continue;
                    }
                    int indent = countLeadingSpaces(line);
                    if (indent < wsIndent) {
                        String t = line.trim();
                        if (t.startsWith("- ")) {
                            t = t.substring(2).trim();
                        }
                        if (t.endsWith(":") && !STRUCTURAL_KEYS.contains(extractEipName(t))) {
                            return indent + (line.trim().startsWith("- ") ? 4 : 2);
                        }
                        wsIndent = indent;
                    }
                }
            }
        }
        return deriveIndentFromPredecessor(fromRow);
    }

    int deriveIndentFromPredecessor(int fromRow) {
        for (int i = fromRow - 1; i >= 0; i--) {
            String prev = editState.getLine(i);
            if (prev.isBlank()) {
                continue;
            }
            int indent = countLeadingSpaces(prev);
            String pt = prev.trim();
            if (pt.endsWith(":")) {
                return indent + (pt.startsWith("- ") ? 4 : 2);
            }
            return indent;
        }
        return 0;
    }

    /**
     * Shift+Tab: move the cursor left, within the current line's leading whitespace, to the nearest enclosing
     * structural indent level — the same indent a completion inserted at this position would have used one level up.
     * Only acts while the cursor sits inside leading whitespace (nothing typed yet on the line); otherwise it is a
     * no-op.
     */
    void moveCursorToPreviousIndentStop() {
        int row = editState.cursorRow();
        String line = editState.getLine(row);
        int col = Math.min(editState.cursorCol(), line.length());
        if (col <= 0 || !line.substring(0, col).isBlank()) {
            return;
        }

        int target = 0;
        for (int i = row - 1; i >= 0; i--) {
            String prev = editState.getLine(i);
            if (prev.isBlank()) {
                continue;
            }
            int indent = countLeadingSpaces(prev);
            if (indent < col) {
                target = indent;
                break;
            }
        }
        SourceEditorNavigation.positionCursor(editState, row, target);
    }

    /**
     * Effective indent for a possibly-blank line. On the live cursor row, the line's own leading whitespace can be
     * stale — Shift+Tab (see {@link #moveCursorToPreviousIndentStop()}) repositions the cursor within existing
     * whitespace without trimming it — so the cursor's column is the source of truth there. For any other row (e.g.
     * tests resolving an arbitrary row without moving the live cursor there), trust the row's own real whitespace when
     * it has any, and only derive from the preceding line when it is truly empty.
     */
    int effectiveBlankIndent(int row) {
        if (row == editState.cursorRow()) {
            return editState.cursorCol();
        }
        int literal = countLeadingSpaces(editState.getLine(row));
        return literal > 0 ? literal : deriveBlankLineIndent(row);
    }

    String findParentYamlKey(int fromRow) {
        String cursorLine = editState.getLine(fromRow);

        // a blank line has no real indentation yet — derive the intended nesting level so a
        // cursor nested under e.g. "expression:" resolves to that key, not to the enclosing EIP
        // (which would otherwise re-offer already-set fields like name/expression)
        int cursorIndent = cursorLine.isBlank() ? effectiveBlankIndent(fromRow) : countLeadingSpaces(cursorLine);

        // walk up to find parent key at lower indent
        for (int i = fromRow; i >= 0; i--) {
            String line = editState.getLine(i);
            if (line.isBlank()) {
                continue;
            }
            int indent = countLeadingSpaces(line);
            if (indent < cursorIndent) {
                String key = extractEipName(line.trim());
                if (key != null) {
                    return dashToCamelCase(key);
                }
                break;
            }
        }
        return "root";
    }

    YamlEipContext findEnclosingEip(int fromRow) {
        String cursorLine = editState.getLine(fromRow);
        int cursorIndent = cursorLine.isBlank() ? effectiveBlankIndent(fromRow) : countLeadingSpaces(cursorLine);

        // if cursor is inside a parameters: block, defer to component completion
        for (int i = fromRow; i >= 0; i--) {
            String line = editState.getLine(i);
            if (line.isBlank()) {
                continue;
            }
            int indent = countLeadingSpaces(line);
            String trimmed = line.trim();
            if (trimmed.startsWith("parameters:") && indent < cursorIndent) {
                return null;
            }
            if (i < fromRow && indent < cursorIndent) {
                break;
            }
        }

        // walk up to find the parent EIP
        boolean skippedStructural = false;
        for (int i = fromRow; i >= 0; i--) {
            String line = editState.getLine(i);
            if (line.isBlank()) {
                continue;
            }
            int indent = countLeadingSpaces(line);
            if (indent < cursorIndent) {
                String eipName = extractEipName(line.trim());
                if (eipName != null && !STRUCTURAL_KEYS.contains(eipName)) {
                    if (skippedStructural) {
                        return null;
                    }
                    String camelName = dashToCamelCase(eipName);
                    return new YamlEipContext(camelName);
                }
                // keep walking up if we hit a structural key
                skippedStructural = true;
                cursorIndent = indent;
            }
        }
        return null;
    }

    java.util.Set<String> collectExistingSiblingKeys(int fromRow) {
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        String cursorLine = editState.getLine(fromRow);
        int cursorIndent = cursorLine.isBlank() ? effectiveBlankIndent(fromRow) : countLeadingSpaces(cursorLine);

        // scan upward for siblings at same indent
        for (int i = fromRow - 1; i >= 0; i--) {
            String line = editState.getLine(i);
            if (line.isBlank()) {
                continue;
            }
            int indent = countLeadingSpaces(line);
            if (indent < cursorIndent) {
                break;
            }
            if (indent == cursorIndent) {
                String trimmed = line.trim();
                int colonIdx = trimmed.indexOf(':');
                if (colonIdx > 0) {
                    keys.add(trimmed.substring(0, colonIdx).trim());
                }
            }
        }
        // scan downward for siblings at same indent
        int lineCount = editState.lineCount();
        for (int i = fromRow + 1; i < lineCount; i++) {
            String line = editState.getLine(i);
            if (line.isBlank()) {
                continue;
            }
            int indent = countLeadingSpaces(line);
            if (indent < cursorIndent) {
                break;
            }
            if (indent == cursorIndent) {
                String trimmed = line.trim();
                int colonIdx = trimmed.indexOf(':');
                if (colonIdx > 0) {
                    keys.add(trimmed.substring(0, colonIdx).trim());
                }
            }
        }
        return keys;
    }

    int findScopeLineRow(int cursorRow) {
        if (cursorRow < 0 || cursorRow >= editState.lineCount()) {
            return -1;
        }
        String cursorLine = editState.getLine(cursorRow);
        String trimmed = cursorLine.trim();
        if (trimmed.startsWith("- ")) {
            trimmed = trimmed.substring(2).trim();
        }

        // bare list item (- ) with no key yet — no scope
        if (cursorLine.trim().startsWith("- ") && trimmed.isEmpty()) {
            return -1;
        }

        // if cursor is on a uri: line, scope is this row
        if (trimmed.startsWith("uri:")) {
            return cursorRow;
        }
        int colonIdx = trimmed.indexOf(':');
        if (colonIdx > 0) {
            String key = trimmed.substring(0, colonIdx).trim();
            // inline producer/consumer EIP (to:, enrich:) — exclude structural keys like from:
            if (!STRUCTURAL_KEYS.contains(key)
                    && (CONSUMER_EIPS.contains(key) || PRODUCER_EIPS.contains(key))) {
                return cursorRow;
            }
            // EIP definition line in a list (e.g., "- split:", "- log:")
            if (!STRUCTURAL_KEYS.contains(key) && cursorLine.trim().startsWith("- ")) {
                return cursorRow;
            }
        }

        int cursorIndent = countLeadingSpaces(cursorLine);

        if (cursorLine.isBlank()) {
            cursorIndent = editState.cursorCol();
        }

        // walk up looking for the scope line
        int parametersRow = -1;
        int parametersIndent = -1;
        for (int i = cursorRow; i >= 0; i--) {
            String line = editState.getLine(i);
            if (line.isBlank()) {
                continue;
            }
            int indent = countLeadingSpaces(line);
            String t = line.trim();

            if (t.startsWith("parameters:") && indent < cursorIndent) {
                parametersRow = i;
                parametersIndent = indent;
                break;
            }
            if (i < cursorRow && indent < cursorIndent) {
                String eipName = extractEipName(t);
                if (eipName != null && !STRUCTURAL_KEYS.contains(eipName)) {
                    // for from:/to: blocks, scope to the uri: line if cursor is below it
                    if ("from".equals(eipName) || CONSUMER_EIPS.contains(eipName)
                            || PRODUCER_EIPS.contains(eipName)) {
                        for (int j = i + 1; j < cursorRow; j++) {
                            String jl = editState.getLine(j);
                            if (!jl.isBlank() && jl.trim().startsWith("uri:")) {
                                return j;
                            }
                        }
                    }
                    return i;
                }
                cursorIndent = indent;
            }
        }

        // inside parameters: block — find the uri: line at the same indent
        if (parametersRow >= 0) {
            for (int i = parametersRow - 1; i >= 0; i--) {
                String line = editState.getLine(i);
                if (line.isBlank()) {
                    continue;
                }
                int indent = countLeadingSpaces(line);
                String t = line.trim();
                if (indent == parametersIndent && (t.startsWith("uri:") || t.startsWith("- uri:"))) {
                    return i;
                }
                if (indent < parametersIndent) {
                    // check for inline uri on the EIP line itself
                    String eipName = extractEipName(t);
                    if (eipName != null && (CONSUMER_EIPS.contains(eipName) || PRODUCER_EIPS.contains(eipName))) {
                        return i;
                    }
                    break;
                }
            }
        }
        return -1;
    }

    String buildBreadcrumb(int cursorRow) {
        if (cursorRow < 0 || cursorRow >= editState.lineCount()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        String cursorLine = editState.getLine(cursorRow);
        // a blank line's own leading whitespace can be stale (see effectiveBlankIndent), so use
        // the cursor's real column rather than assuming the deepest nesting implied by the line
        // above — otherwise dedenting with Shift+Tab wouldn't be reflected in the breadcrumb
        int cursorIndent = cursorLine.isBlank() ? effectiveBlankIndent(cursorRow) : countLeadingSpaces(cursorLine);

        int prevIndent = cursorIndent;
        for (int i = cursorRow - 1; i >= 0; i--) {
            String line = editState.getLine(i);
            if (line.isBlank()) {
                continue;
            }
            int indent = countLeadingSpaces(line);
            if (indent < prevIndent) {
                String trimmed = line.trim();
                if (trimmed.startsWith("- ")) {
                    trimmed = trimmed.substring(2).trim();
                }
                // only include structural parent keys (lines ending with ":" with no value)
                if (!trimmed.endsWith(":")) {
                    prevIndent = indent;
                    continue;
                }
                String key = extractEipName(line.trim());
                if (key != null) {
                    if (!BREADCRUMB_SKIP_KEYS.contains(key)
                            && !key.endsWith("Configuration")) {
                        parts.add(key);
                    }
                    if ("route".equals(key)) {
                        break;
                    }
                }
                prevIndent = indent;
            }
        }

        if (parts.isEmpty()) {
            return "";
        }
        Collections.reverse(parts);
        return String.join(" > ", parts);
    }

    String adjustPasteIndent(String text, int cursorRow) {
        String current = editState.getLine(cursorRow);
        int targetIndent;
        if (current == null || current.isBlank()) {
            // on a blank line (including one carrying ENTER auto-indent whitespace, which handlePaste
            // strips before inserting): infer the block indent from the context above the cursor and
            // apply it to every pasted line
            int fallback = current == null ? 0 : countLeadingSpaces(current);
            targetIndent = inferBlankLineIndent(text, cursorRow, fallback);
        } else if (editState.cursorCol() == 0) {
            // inserting before an existing line: match that line's own indent
            targetIndent = countLeadingSpaces(current);
        } else {
            // pasting into the middle of existing content: keep the cursor column
            targetIndent = editState.cursorCol();
        }
        return reindentBlock(text, targetIndent);
    }

    int inferBlankLineIndent(String text, int cursorRow, int fallback) {
        int prevIndent = -1;
        boolean prevIsParentKey = false;
        int listIndent = -1;
        for (int i = cursorRow - 1; i >= 0; i--) {
            String l = editState.getLine(i);
            if (l.isBlank()) {
                continue;
            }
            if (prevIndent < 0) {
                // nearest non-blank line: its indent, and whether it opens a child block
                prevIndent = countLeadingSpaces(l);
                String t = l.trim();
                if (t.startsWith("- ")) {
                    t = t.substring(2).trim();
                }
                prevIsParentKey = t.endsWith(":");
            }
            if (l.trim().startsWith("- ")) {
                // nearest existing list item — the sibling level for a pasted list item
                listIndent = countLeadingSpaces(l);
                break;
            }
        }
        String firstTrimmed = firstNonBlankTrimmed(text);
        boolean pasteIsListItem = firstTrimmed.startsWith("- ") || firstTrimmed.equals("-");
        if (pasteIsListItem && listIndent >= 0) {
            // align a pasted step with the nearest existing sibling step
            return listIndent;
        } else if (prevIndent >= 0) {
            // otherwise follow the previous line, indenting deeper under a parent key
            return prevIndent + (prevIsParentKey ? 2 : 0);
        }
        return fallback;
    }

    static String dashToCamelCase(String text) {
        if (text == null || !text.contains("-")) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length());
        boolean upper = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '-') {
                upper = true;
            } else {
                sb.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return sb.toString();
    }

    static String reindentBlock(String text, int targetIndent) {
        // normalize line endings: some terminals deliver pasted line breaks as \r\n or bare \r,
        // which would otherwise collapse a multi-line paste into a single line
        text = text.replace("\r\n", "\n").replace('\r', '\n');
        text = text.replace("\t", "  ");
        String[] pasteLines = text.split("\n", -1);
        int minIndent = Integer.MAX_VALUE;
        for (String pl : pasteLines) {
            if (!pl.isBlank()) {
                minIndent = Math.min(minIndent, countLeadingSpaces(pl));
            }
        }
        if (minIndent == Integer.MAX_VALUE) {
            minIndent = 0;
        }
        int delta = targetIndent - minIndent;
        if (delta == 0) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pasteLines.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            String pl = pasteLines[i];
            if (pl.isBlank()) {
                sb.append(pl);
            } else if (delta > 0) {
                sb.append(" ".repeat(delta)).append(pl);
            } else {
                int strip = Math.min(-delta, countLeadingSpaces(pl));
                sb.append(pl.substring(strip));
            }
        }
        return sb.toString();
    }

    static String firstNonBlankTrimmed(String text) {
        for (String line : text.split("\r\n|\r|\n", -1)) {
            if (!line.isBlank()) {
                return line.trim();
            }
        }
        return "";
    }

    static int countLeadingSpaces(String line) {
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ' ') {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    static String extractSchemeFromUriLine(String trimmed) {
        String value = extractUriValue(trimmed);
        if (value == null) {
            return null;
        }
        int schemeEnd = value.indexOf(':');
        if (schemeEnd > 0) {
            return value.substring(0, schemeEnd);
        }
        return value;
    }

    static String extractUriValue(String trimmed) {
        int colonIdx = trimmed.indexOf(':');
        if (colonIdx < 0) {
            return null;
        }
        String value = trimmed.substring(colonIdx + 1).trim();
        if (value.startsWith("\"") || value.startsWith("'")) {
            value = value.substring(1);
        }
        if (value.endsWith("\"") || value.endsWith("'")) {
            value = value.substring(0, value.length() - 1);
        }
        if (!value.isEmpty()) {
            return value;
        }
        return null;
    }

    static String extractEipName(String trimmed) {
        String line = trimmed;
        if (line.startsWith("- ")) {
            line = line.substring(2).trim();
        }
        int colonIdx = line.indexOf(':');
        if (colonIdx > 0) {
            return line.substring(0, colonIdx).trim();
        }
        return null;
    }

    static String extractInlineUri(String trimmed) {
        String line = trimmed;
        if (line.startsWith("- ")) {
            line = line.substring(2).trim();
        }
        int colonIdx = line.indexOf(':');
        if (colonIdx <= 0) {
            return null;
        }
        String eipPart = line.substring(0, colonIdx).trim();
        if (!CONSUMER_EIPS.contains(eipPart) && !PRODUCER_EIPS.contains(eipPart)) {
            return null;
        }
        String uriPart = line.substring(colonIdx + 1).trim();
        if (uriPart.isEmpty()) {
            return null;
        }
        if (uriPart.startsWith("\"") || uriPart.startsWith("'")) {
            uriPart = uriPart.substring(1);
        }
        if (uriPart.endsWith("\"") || uriPart.endsWith("'")) {
            uriPart = uriPart.substring(0, uriPart.length() - 1);
        }
        int schemeEnd = uriPart.indexOf(':');
        if (schemeEnd > 0) {
            return uriPart.substring(0, schemeEnd);
        }
        return null;
    }
}
