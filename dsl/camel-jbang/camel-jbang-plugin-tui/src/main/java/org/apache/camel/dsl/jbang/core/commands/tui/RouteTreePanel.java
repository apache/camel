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
import java.util.Locale;
import java.util.Map;

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.widgets.Clear;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.camel.dsl.jbang.core.commands.tui.diagram.DiagramColors;

/**
 * The mini panel of the source editor: the tree of the route the cursor is in (from, then each step, the branches
 * indented), with the step under the cursor marked, so the structure of a large route stays in view while editing it.
 * It is built from the text as it is (the route need not run), with the scanners of Go to Node (Ctrl+G), for the YAML,
 * XML and Java DSLs. Ctrl+T shows or hides it.
 */
final class RouteTreePanel {

    static final int MIN_EDITOR_WIDTH = 90;
    static final int MIN_EDITOR_HEIGHT = 12;

    private int scannedHash;
    private String scannedName;
    private List<YamlRouteNodeScanner.NodeEntry> entries = List.of();

    /** The nodes of the route the cursor is in, and which of them is under the cursor (-1 for none). */
    record RouteTree(String routeId, String fromUri, List<YamlRouteNodeScanner.NodeEntry> nodes, int current) {
    }

    /**
     * Draws the panel at the top right of the editor area, when it is wide enough and the file has a route.
     *
     * @param cursorRow the line of the cursor, 0-based
     */
    void render(Frame frame, Rect area, List<String> lines, String fileName, int cursorRow) {
        if (area.width() < MIN_EDITOR_WIDTH || area.height() < MIN_EDITOR_HEIGHT || fileName == null) {
            return;
        }
        RouteTree tree = treeAt(scan(lines, fileName), cursorRow);
        if (tree == null || tree.nodes().isEmpty()) {
            return;
        }
        int w = Math.min(46, area.width() / 3);
        int maxRows = Math.max(3, area.height() * 2 / 3 - 2);
        // the from line takes one row; the steps the rest
        int rows = Math.min(tree.nodes().size(), maxRows - 1);
        int top = windowTop(tree.current(), tree.nodes().size(), rows);
        List<Line> out = new ArrayList<>();
        // where the route starts
        String from = tree.fromUri() != null ? "from " + tree.fromUri() : "from";
        Style fromStyle = tree.current() < 0
                ? Theme.selectionBg().bold()
                : Style.EMPTY.fg(DiagramColors.getEipColor("from"));
        out.add(Line.from(Span.styled(TuiHelper.truncate((tree.current() < 0 ? "▶" : " ") + from, w - 2), fromStyle)));
        for (int i = top; i < top + rows; i++) {
            out.add(line(tree.nodes(), i, i == tree.current(), w - 2));
        }
        Rect rect = new Rect(area.x() + area.width() - w - 2, area.y() + 1, w, out.size() + 2);
        frame.renderWidget(Clear.INSTANCE, rect);
        String title = tree.routeId() != null ? " Route: " + tree.routeId() + " " : " Route ";
        Block block = Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                .borderStyle(Theme.muted())
                .title(TuiHelper.truncate(title, w - 4))
                .build();
        frame.renderWidget(Paragraph.builder().text(Text.from(out)).block(block).build(), rect);
    }

    /** The first step shown, so the step under the cursor is in the window of the given rows. */
    static int windowTop(int current, int size, int rows) {
        if (current < rows) {
            return 0;
        }
        return Math.max(0, Math.min(current - rows / 2, size - rows));
    }

    /** The route the cursor is in: the last route that starts at or above it, else the first one. */
    static RouteTree treeAt(List<YamlRouteNodeScanner.NodeEntry> entries, int cursorRow) {
        int start = -1;
        for (int i = 0; i < entries.size(); i++) {
            YamlRouteNodeScanner.NodeEntry e = entries.get(i);
            if (e.kind() == YamlRouteNodeScanner.EntryKind.ROUTE && (start < 0 || e.lineIndex() <= cursorRow)) {
                start = i;
            }
        }
        if (start < 0) {
            return null;
        }
        String routeId = entries.get(start).routeId();
        String fromUri = entries.get(start).fromUri();
        List<YamlRouteNodeScanner.NodeEntry> nodes = new ArrayList<>();
        int current = -1;
        for (int i = start + 1; i < entries.size(); i++) {
            YamlRouteNodeScanner.NodeEntry e = entries.get(i);
            if (e.kind() == YamlRouteNodeScanner.EntryKind.ROUTE) {
                break;
            }
            nodes.add(e);
            if (e.lineIndex() <= cursorRow) {
                current = nodes.size() - 1;
            }
        }
        return new RouteTree(routeId, fromUri, nodes, current);
    }

    private List<YamlRouteNodeScanner.NodeEntry> scan(List<String> lines, String fileName) {
        int hash = lines.hashCode();
        if (hash == scannedHash && fileName.equals(scannedName)) {
            return entries;
        }
        String name = fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".yaml") || name.endsWith(".yml")) {
            entries = YamlRouteNodeScanner.scanLines(lines, fileName);
        } else if (name.endsWith(".xml") || name.endsWith(".java")) {
            entries = ModelRouteNodeScanner.scan(fileName, fileName, String.join("\n", lines), Map.of(),
                    ArchitectureView.catalog());
        } else {
            entries = List.of();
        }
        scannedHash = hash;
        scannedName = fileName;
        return entries;
    }

    private static Line line(List<YamlRouteNodeScanner.NodeEntry> nodes, int idx, boolean current, int width) {
        YamlRouteNodeScanner.NodeEntry e = nodes.get(idx);
        String prefix = GotoSourceNodePopup.buildTreePrefix(nodes, idx, e);
        String text = e.type() + (e.label().isBlank() ? "" : " " + e.label());
        String mark = current ? "▶" : " ";
        String all = TuiHelper.truncate(mark + prefix.substring(Math.min(1, prefix.length())) + text, width);
        Style style = current
                ? Theme.selectionBg().bold()
                : Style.EMPTY.fg(DiagramColors.getEipColor(YamlSourceContext.dashToCamelCase(e.type())));
        return Line.from(Span.styled(all, style));
    }
}
