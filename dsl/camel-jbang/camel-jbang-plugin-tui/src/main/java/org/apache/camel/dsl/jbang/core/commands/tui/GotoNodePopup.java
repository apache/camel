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

import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import org.apache.camel.dsl.jbang.core.commands.tui.diagram.DiagramColors;
import org.apache.camel.dsl.jbang.core.commands.tui.diagram.RouteDiagramWidget;

/** Go to Node (g) of the route diagram: the nodes of the route shown, by their EIP and label. */
class GotoNodePopup extends FilterListPopup<GotoNodePopup.NodeEntry> {

    private List<NodeEntry> allEntries;
    private int selectedIndex = -1;

    record NodeEntry(int index, String type, String label, String nodeId) {
    }

    void open(List<RouteDiagramWidget.EipNodeBox> eipNodeBoxes, int currentNodeIndex) {
        allEntries = new ArrayList<>();
        for (int i = 0; i < eipNodeBoxes.size(); i++) {
            var box = eipNodeBoxes.get(i);
            var ln = box.layoutNode();
            String type = ln != null ? ln.type : box.type();
            String label = "";
            if (ln != null && ln.wrappedLines != null && !ln.wrappedLines.isEmpty()) {
                label = String.join(" ", ln.wrappedLines);
            }
            String nodeId = box.nodeId() != null ? box.nodeId() : (ln != null ? ln.id : "");
            allEntries.add(new NodeEntry(i, type != null ? type : "", label, nodeId != null ? nodeId : ""));
        }
        show();
        if (currentNodeIndex >= 0 && currentNodeIndex < allEntries.size()) {
            select(currentNodeIndex);
        }
    }

    /** The node chosen with Enter, or -1; cleared by the call. */
    int consumeSelectedIndex() {
        int idx = selectedIndex;
        selectedIndex = -1;
        return idx;
    }

    @Override
    protected List<NodeEntry> entries() {
        return allEntries;
    }

    @Override
    protected String name() {
        return "Go to Node";
    }

    @Override
    protected String matchText(NodeEntry entry) {
        return entry.type() + " " + entry.label() + " " + entry.nodeId();
    }

    @Override
    protected boolean matches(NodeEntry entry, String typed) {
        return matchesText(matchText(entry), typed);
    }

    @Override
    protected void chosen(NodeEntry entry) {
        selectedIndex = entry.index();
    }

    @Override
    protected List<Line> rows(List<NodeEntry> entries, int width) {
        // the EIP tags take the width of the longest, so the labels line up
        int maxTypeW = 0;
        for (NodeEntry entry : entries) {
            maxTypeW = Math.max(maxTypeW, entry.type().length());
        }
        int maxLabelW = width - maxTypeW - 6;

        Style matchStyle = Theme.label().bold();
        List<Line> rows = new ArrayList<>();
        for (NodeEntry entry : entries) {
            List<Span> spans = new ArrayList<>();
            spans.add(Span.raw(" "));
            String typeTag = entry.type();
            String typePad = " ".repeat(Math.max(0, maxTypeW - typeTag.length()));
            Color eipColor = DiagramColors.getEipColor(typeTag);
            spans.add(Span.styled("[" + typeTag + "]" + typePad, Style.EMPTY.fg(eipColor).bold()));
            spans.add(Span.raw(" "));

            String searchable = entry.label().isBlank() ? entry.nodeId() : entry.label();
            if (searchable.length() > maxLabelW && maxLabelW > 3) {
                searchable = searchable.substring(0, maxLabelW - 1) + "…";
            }
            spans.addAll(highlighted(searchable, Style.EMPTY, matchStyle));
            rows.add(Line.from(spans));
        }
        return rows;
    }
}
