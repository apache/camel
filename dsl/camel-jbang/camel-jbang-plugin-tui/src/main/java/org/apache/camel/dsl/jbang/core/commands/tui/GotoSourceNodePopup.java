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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import org.apache.camel.dsl.jbang.core.commands.tui.diagram.DiagramColors;

/**
 * Go to node or line of the source editor: the routes of the file and their processors as a tree; a number typed goes
 * to that line instead. A typed text keeps a route when the route or one of its processors matches.
 */
class GotoSourceNodePopup extends FilterListPopup<YamlRouteNodeScanner.NodeEntry> {

    private List<YamlRouteNodeScanner.NodeEntry> allEntries;
    private YamlRouteNodeScanner.NodeEntry selectedEntry;
    private int gotoLineNumber = -1;
    private int totalLineCount;

    void open(List<YamlRouteNodeScanner.NodeEntry> entries, int lineCount) {
        allEntries = entries != null ? new ArrayList<>(entries) : List.of();
        totalLineCount = lineCount;
        gotoLineNumber = -1;
        show();
    }

    @Override
    void close() {
        super.close();
        gotoLineNumber = -1;
    }

    /** The node chosen with Enter, or null; cleared by the call. */
    YamlRouteNodeScanner.NodeEntry consumeSelection() {
        YamlRouteNodeScanner.NodeEntry entry = selectedEntry;
        selectedEntry = null;
        return entry;
    }

    /** The line number typed and confirmed with Enter, or -1; cleared by the call. */
    int consumeGotoLineNumber() {
        int line = gotoLineNumber;
        gotoLineNumber = -1;
        return line;
    }

    @Override
    protected List<YamlRouteNodeScanner.NodeEntry> entries() {
        return allEntries;
    }

    @Override
    protected String name() {
        return "Go to Node";
    }

    @Override
    protected void chosen(YamlRouteNodeScanner.NodeEntry entry) {
        selectedEntry = entry;
    }

    @Override
    protected boolean confirmTyped(String typed) {
        if (!isLineNumber(typed)) {
            return false;
        }
        int num = Integer.parseInt(typed.trim());
        close();
        gotoLineNumber = Math.max(1, Math.min(num, totalLineCount));
        return true;
    }

    @Override
    protected int maxWidth() {
        return 90;
    }

    @Override
    protected Line prompt(String typed) {
        Line prompt = super.prompt(typed);
        if (isLineNumber(typed)) {
            List<Span> spans = new ArrayList<>(prompt.spans());
            spans.add(Span.styled("  Go to line " + typed.trim(), Style.EMPTY.dim()));
            return Line.from(spans);
        }
        return prompt;
    }

    @Override
    protected String title(int shownCount, int total) {
        if (filter.hasFilter() && isLineNumber(filter.filter())) {
            return " Go to Line ";
        }
        if (total == 0) {
            return " Go to Line (type a line number) ";
        }
        return super.title(shownCount, total);
    }

    /** A route stays when it or one of its processors matches; a processor when it or its route matches. */
    @Override
    protected List<YamlRouteNodeScanner.NodeEntry> filter(List<YamlRouteNodeScanner.NodeEntry> entries, String typed) {
        Map<String, Boolean> routeMatched = new LinkedHashMap<>();
        for (YamlRouteNodeScanner.NodeEntry entry : entries) {
            if (entry.kind() == YamlRouteNodeScanner.EntryKind.ROUTE) {
                routeMatched.put(routeKey(entry), matches(entry, typed));
            }
        }
        List<YamlRouteNodeScanner.NodeEntry> result = new ArrayList<>();
        for (YamlRouteNodeScanner.NodeEntry entry : entries) {
            String key = routeKey(entry);
            if (entry.kind() == YamlRouteNodeScanner.EntryKind.ROUTE) {
                if (Boolean.TRUE.equals(routeMatched.get(key)) || hasMatchingProcessor(entry, typed)) {
                    result.add(entry);
                }
            } else if (Boolean.TRUE.equals(routeMatched.get(key)) || matches(entry, typed)) {
                result.add(entry);
            }
        }
        return result;
    }

    @Override
    protected String matchText(YamlRouteNodeScanner.NodeEntry entry) {
        return entry.kind() == YamlRouteNodeScanner.EntryKind.ROUTE
                ? entry.routeId() + " " + entry.fromUri()
                : entry.type() + " " + entry.label() + " " + entry.routeId();
    }

    @Override
    protected boolean matches(YamlRouteNodeScanner.NodeEntry entry, String typed) {
        return matchesText(matchText(entry), typed);
    }

    @Override
    protected List<Line> rows(List<YamlRouteNodeScanner.NodeEntry> entries, int width) {
        int maxTypeW = 0;
        for (YamlRouteNodeScanner.NodeEntry entry : entries) {
            if (entry.kind() == YamlRouteNodeScanner.EntryKind.PROCESSOR) {
                maxTypeW = Math.max(maxTypeW, entry.type().length());
            }
        }
        int maxLabelW = Math.max(10, width - maxTypeW - 14);

        Style matchStyle = Theme.label().bold();
        Style treeStyle = Style.EMPTY.dim();
        Style routeStyle = Theme.label().bold();
        List<Line> rows = new ArrayList<>();
        for (int idx = 0; idx < entries.size(); idx++) {
            YamlRouteNodeScanner.NodeEntry entry = entries.get(idx);
            List<Span> spans = new ArrayList<>();
            if (entry.kind() == YamlRouteNodeScanner.EntryKind.ROUTE) {
                // route[routeId]  from[uri]
                spans.add(Span.raw(" "));
                spans.addAll(highlighted("route[" + entry.routeId() + "]", routeStyle, matchStyle));
            } else {
                // tree prefix + type[label] matching diagram panel format
                spans.add(Span.styled(buildTreePrefix(entries, idx, entry), treeStyle));
                String typeTag = entry.type();
                Color eipColor = DiagramColors.getEipColor(YamlSourceContext.dashToCamelCase(typeTag));
                String label = entry.label().isBlank() ? "" : entry.label();
                if (label.length() > maxLabelW && maxLabelW > 3) {
                    label = label.substring(0, maxLabelW - 1) + "…";
                }
                spans.addAll(highlighted(typeTag + "[" + label + "]", Style.EMPTY.fg(eipColor), matchStyle));
            }
            rows.add(Line.from(spans));
        }
        return rows;
    }

    static String buildTreePrefix(
            List<YamlRouteNodeScanner.NodeEntry> entries, int idx, YamlRouteNodeScanner.NodeEntry entry) {
        // Normalize indent to sequential depth within the route group
        int depth = normalizeDepth(entries, idx, entry);
        boolean last = isLastSiblingNorm(entries, idx, entry.indent());
        StringBuilder sb = new StringBuilder(" ");
        for (int d = 1; d < depth; d++) {
            sb.append(hasAncestorSiblingNorm(entries, idx, d) ? "│ " : "  ");
        }
        if (depth > 0) {
            sb.append(last ? "└─" : "├─");
        }
        return sb.toString();
    }

    private static int normalizeDepth(
            List<YamlRouteNodeScanner.NodeEntry> entries, int idx,
            YamlRouteNodeScanner.NodeEntry entry) {
        // Walk backwards to find ancestor chain and count distinct indent levels
        int depth = 1;
        int curIndent = entry.indent();
        for (int i = idx - 1; i >= 0; i--) {
            YamlRouteNodeScanner.NodeEntry e = entries.get(i);
            if (e.kind() == YamlRouteNodeScanner.EntryKind.ROUTE) {
                break;
            }
            if (e.indent() < curIndent) {
                depth++;
                curIndent = e.indent();
            }
        }
        return depth;
    }

    private static boolean isLastSiblingNorm(List<YamlRouteNodeScanner.NodeEntry> entries, int idx, int rawIndent) {
        for (int i = idx + 1; i < entries.size(); i++) {
            YamlRouteNodeScanner.NodeEntry e = entries.get(i);
            if (e.kind() == YamlRouteNodeScanner.EntryKind.ROUTE) {
                return true;
            }
            if (e.indent() < rawIndent) {
                return true;
            }
            if (e.indent() == rawIndent) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasAncestorSiblingNorm(
            List<YamlRouteNodeScanner.NodeEntry> entries, int idx, int normDepth) {
        // Find the raw indent that corresponds to normDepth for this entry's route
        int rawIndent = findRawIndentForDepth(entries, idx, normDepth);
        if (rawIndent < 0) {
            return false;
        }
        for (int i = idx + 1; i < entries.size(); i++) {
            YamlRouteNodeScanner.NodeEntry e = entries.get(i);
            if (e.kind() == YamlRouteNodeScanner.EntryKind.ROUTE) {
                return false;
            }
            if (e.indent() < rawIndent) {
                return false;
            }
            if (e.indent() == rawIndent) {
                return true;
            }
        }
        return false;
    }

    private static int findRawIndentForDepth(List<YamlRouteNodeScanner.NodeEntry> entries, int idx, int targetDepth) {
        // Walk backwards collecting ancestor indent levels
        List<Integer> indents = new ArrayList<>();
        int curIndent = entries.get(idx).indent();
        indents.add(curIndent);
        for (int i = idx - 1; i >= 0; i--) {
            YamlRouteNodeScanner.NodeEntry e = entries.get(i);
            if (e.kind() == YamlRouteNodeScanner.EntryKind.ROUTE) {
                break;
            }
            if (e.indent() < curIndent) {
                indents.add(e.indent());
                curIndent = e.indent();
            }
        }
        Collections.reverse(indents);
        if (targetDepth > 0 && targetDepth <= indents.size()) {
            return indents.get(targetDepth - 1);
        }
        return -1;
    }

    private static boolean isLineNumber(String text) {
        String t = text.trim();
        if (t.isEmpty()) {
            return false;
        }
        for (int i = 0; i < t.length(); i++) {
            if (!Character.isDigit(t.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean hasMatchingProcessor(YamlRouteNodeScanner.NodeEntry routeEntry, String typed) {
        String key = routeKey(routeEntry);
        for (YamlRouteNodeScanner.NodeEntry entry : allEntries) {
            if (entry.kind() == YamlRouteNodeScanner.EntryKind.PROCESSOR
                    && routeKey(entry).equals(key)
                    && matches(entry, typed)) {
                return true;
            }
        }
        return false;
    }

    private static String routeKey(YamlRouteNodeScanner.NodeEntry entry) {
        return entry.filePath() + "#" + entry.routeFromLine();
    }
}
