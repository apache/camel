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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.CharWidth;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import org.apache.camel.diagram.RouteDiagramAsciiRenderer;
import org.apache.camel.diagram.RouteDiagramHelper;
import org.apache.camel.diagram.RouteDiagramLayoutEngine;
import org.apache.camel.diagram.TopologyAsciiRenderer;
import org.apache.camel.diagram.TopologyHelper;
import org.apache.camel.diagram.TopologyLayoutEngine;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyEdgeInfo;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyLayoutEdge;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyLayoutNode;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyLayoutResult;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyNodeInfo;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.*;

class DiagramSupport {

    private boolean showDiagram;
    private boolean topologyMode;
    private boolean showDescription;
    private List<RouteDiagramAsciiRenderer.CounterPos> counterPositions = Collections.emptyList();
    private Set<Integer> routeTitleRows = Collections.emptySet();
    private List<String> lines = Collections.emptyList();
    private int scrollY;
    private int scrollX;
    private final ScrollbarState vScrollState = new ScrollbarState();
    private final ScrollbarState hScrollState = new ScrollbarState();
    private final AtomicBoolean loading = new AtomicBoolean(false);
    private List<TopologyAsciiRenderer.NodeBox> nodeBoxes = Collections.emptyList();
    private List<TopologyLayoutNode> topologyNodes = Collections.emptyList();
    private List<TopologyLayoutEdge> topologyEdges = Collections.emptyList();
    private int selectedNodeIndex = -1;
    private String pendingSelectionRouteId;
    private int lastVisibleHeight;
    private int lastVisibleWidth;

    // Native widget rendering data
    private TopologyLayoutResult topologyLayout;
    private int topologyNodeWidth;
    private java.util.Map<String, RouteDiagramLayoutEngine.LayoutRoute> routeLayouts = Collections.emptyMap();

    List<String> getLines() {
        return lines;
    }

    boolean isShowDiagram() {
        return showDiagram;
    }

    boolean isTopologyMode() {
        return topologyMode;
    }

    void setTopologyMode(boolean topologyMode) {
        this.topologyMode = topologyMode;
    }

    boolean isShowDescription() {
        return showDescription;
    }

    void setShowDescription(boolean showDescription) {
        this.showDescription = showDescription;
    }

    List<TopologyAsciiRenderer.NodeBox> getNodeBoxes() {
        return nodeBoxes;
    }

    int getSelectedNodeIndex() {
        return selectedNodeIndex;
    }

    void setSelectedNodeIndex(int idx) {
        this.selectedNodeIndex = idx;
    }

    void setPendingSelectionRouteId(String routeId) {
        this.pendingSelectionRouteId = routeId;
    }

    String getSelectedRouteId() {
        if (selectedNodeIndex >= 0 && selectedNodeIndex < nodeBoxes.size()) {
            return nodeBoxes.get(selectedNodeIndex).routeId();
        }
        return null;
    }

    TopologyLayoutNode getSelectedTopologyNode() {
        String routeId = getSelectedRouteId();
        if (routeId == null) {
            return null;
        }
        for (TopologyLayoutNode node : topologyNodes) {
            if (routeId.equals(node.routeId)) {
                return node;
            }
        }
        return null;
    }

    String getConnectedRouteId(String externalNodeId) {
        for (TopologyLayoutEdge edge : topologyEdges) {
            if (externalNodeId.equals(edge.from.routeId)) {
                return edge.to.routeId;
            }
            if (externalNodeId.equals(edge.to.routeId)) {
                return edge.from.routeId;
            }
        }
        return null;
    }

    boolean hasDiagramData() {
        if (topologyLayout != null || !routeLayouts.isEmpty()) {
            return true;
        }
        return !lines.isEmpty();
    }

    boolean hasNativeLayout() {
        return topologyLayout != null;
    }

    TopologyLayoutResult getTopologyLayout() {
        return topologyLayout;
    }

    int getTopologyNodeWidth() {
        return topologyNodeWidth;
    }

    RouteDiagramLayoutEngine.LayoutRoute getRouteLayout(String routeId) {
        return routeLayouts.get(routeId);
    }

    int getScrollX() {
        return scrollX;
    }

    int getScrollY() {
        return scrollY;
    }

    ScrollbarState getVScrollState() {
        return vScrollState;
    }

    ScrollbarState getHScrollState() {
        return hScrollState;
    }

    boolean handleScrollKeys(KeyEvent ke) {
        if (!showDiagram) {
            return false;
        }
        if (ke.isUp()) {
            scrollY = Math.max(0, scrollY - 1);
            return true;
        }
        if (ke.isDown()) {
            scrollY++;
            return true;
        }
        if (ke.isPageUp()) {
            scrollY = Math.max(0, scrollY - 20);
            return true;
        }
        if (ke.isPageDown()) {
            scrollY += 20;
            return true;
        }
        if (ke.isLeft()) {
            scrollX = Math.max(0, scrollX - 1);
            return true;
        }
        if (ke.isRight()) {
            scrollX++;
            return true;
        }
        if (ke.isHome()) {
            scrollY = 0;
            scrollX = 0;
            return true;
        }
        if (ke.isEnd()) {
            scrollY = Integer.MAX_VALUE;
            return true;
        }
        return false;
    }

    void resetScroll() {
        scrollY = 0;
        scrollX = 0;
    }

    void toggleDiagram(Runnable loadTrigger) {
        if (showDiagram) {
            close();
        } else {
            loadTrigger.run();
        }
    }

    boolean handleEscape() {
        if (showDiagram) {
            close();
            return true;
        }
        return false;
    }

    void close() {
        showDiagram = false;
    }

    void reset() {
        close();
        lines = Collections.emptyList();
        nodeBoxes = Collections.emptyList();
        topologyNodes = Collections.emptyList();
        topologyEdges = Collections.emptyList();
        topologyLayout = null;
        topologyNodeWidth = 0;
        routeLayouts = Collections.emptyMap();
        selectedNodeIndex = -1;
        eipNodeBoxes = Collections.emptyList();
        selectedEipNodeIndex = -1;
        scrollY = 0;
        scrollX = 0;
    }

    void renderFooterHints(List<Span> spans) {
        hint(spans, "Esc", "close");
        hint(spans, "↑↓←→", "scroll");
        hint(spans, "PgUp/PgDn", "page");
        hint(spans, "Home/End", "top/end");
    }

    // ---- Node selection ----

    private static int findTopLeftNode(List<TopologyAsciiRenderer.NodeBox> boxes) {
        int bestIdx = 0;
        for (int i = 1; i < boxes.size(); i++) {
            TopologyAsciiRenderer.NodeBox nb = boxes.get(i);
            TopologyAsciiRenderer.NodeBox best = boxes.get(bestIdx);
            if (nb.startRow() < best.startRow()
                    || (nb.startRow() == best.startRow() && nb.startCol() < best.startCol())) {
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    // ---- Node selection navigation ----

    void selectNodeUp() {
        if (nodeBoxes.isEmpty()) {
            return;
        }
        if (selectedNodeIndex < 0) {
            selectedNodeIndex = 0;
            return;
        }
        TopologyAsciiRenderer.NodeBox current = nodeBoxes.get(selectedNodeIndex);
        int currentMidCol = (current.startCol() + current.endCol()) / 2;
        int bestIdx = -1;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < nodeBoxes.size(); i++) {
            TopologyAsciiRenderer.NodeBox nb = nodeBoxes.get(i);
            if (nb.layer() < current.layer()) {
                int midCol = (nb.startCol() + nb.endCol()) / 2;
                int dist = Math.abs(midCol - currentMidCol);
                if (nb.layer() > (bestIdx >= 0 ? nodeBoxes.get(bestIdx).layer() : -1) || dist < bestDist) {
                    bestIdx = i;
                    bestDist = dist;
                }
            }
        }
        if (bestIdx >= 0) {
            // find the closest layer above, then pick closest column within that layer
            int targetLayer = nodeBoxes.get(bestIdx).layer();
            bestIdx = -1;
            bestDist = Integer.MAX_VALUE;
            for (int i = 0; i < nodeBoxes.size(); i++) {
                TopologyAsciiRenderer.NodeBox nb = nodeBoxes.get(i);
                if (nb.layer() == targetLayer) {
                    int midCol = (nb.startCol() + nb.endCol()) / 2;
                    int dist = Math.abs(midCol - currentMidCol);
                    if (dist < bestDist) {
                        bestIdx = i;
                        bestDist = dist;
                    }
                }
            }
            if (bestIdx >= 0) {
                selectedNodeIndex = bestIdx;
            }
        }
    }

    void selectNodeDown() {
        if (nodeBoxes.isEmpty()) {
            return;
        }
        if (selectedNodeIndex < 0) {
            selectedNodeIndex = 0;
            return;
        }
        TopologyAsciiRenderer.NodeBox current = nodeBoxes.get(selectedNodeIndex);
        int currentMidCol = (current.startCol() + current.endCol()) / 2;
        int bestIdx = -1;
        for (int i = 0; i < nodeBoxes.size(); i++) {
            TopologyAsciiRenderer.NodeBox nb = nodeBoxes.get(i);
            if (nb.layer() > current.layer()) {
                if (bestIdx < 0 || nb.layer() < nodeBoxes.get(bestIdx).layer()) {
                    bestIdx = i;
                }
            }
        }
        if (bestIdx >= 0) {
            int targetLayer = nodeBoxes.get(bestIdx).layer();
            bestIdx = -1;
            int bestDist = Integer.MAX_VALUE;
            for (int i = 0; i < nodeBoxes.size(); i++) {
                TopologyAsciiRenderer.NodeBox nb = nodeBoxes.get(i);
                if (nb.layer() == targetLayer) {
                    int midCol = (nb.startCol() + nb.endCol()) / 2;
                    int dist = Math.abs(midCol - currentMidCol);
                    if (dist < bestDist) {
                        bestIdx = i;
                        bestDist = dist;
                    }
                }
            }
            if (bestIdx >= 0) {
                selectedNodeIndex = bestIdx;
            }
        }
    }

    void selectNodeLeft() {
        if (nodeBoxes.isEmpty()) {
            return;
        }
        if (selectedNodeIndex < 0) {
            selectedNodeIndex = 0;
            return;
        }
        TopologyAsciiRenderer.NodeBox current = nodeBoxes.get(selectedNodeIndex);
        int bestIdx = -1;
        int bestCol = -1;
        for (int i = 0; i < nodeBoxes.size(); i++) {
            TopologyAsciiRenderer.NodeBox nb = nodeBoxes.get(i);
            if (nb.layer() == current.layer() && nb.startCol() < current.startCol()) {
                if (nb.startCol() > bestCol) {
                    bestIdx = i;
                    bestCol = nb.startCol();
                }
            }
        }
        if (bestIdx >= 0) {
            selectedNodeIndex = bestIdx;
        }
    }

    void selectNodeRight() {
        if (nodeBoxes.isEmpty()) {
            return;
        }
        if (selectedNodeIndex < 0) {
            selectedNodeIndex = 0;
            return;
        }
        TopologyAsciiRenderer.NodeBox current = nodeBoxes.get(selectedNodeIndex);
        int bestIdx = -1;
        int bestCol = Integer.MAX_VALUE;
        for (int i = 0; i < nodeBoxes.size(); i++) {
            TopologyAsciiRenderer.NodeBox nb = nodeBoxes.get(i);
            if (nb.layer() == current.layer() && nb.startCol() > current.startCol()) {
                if (nb.startCol() < bestCol) {
                    bestIdx = i;
                    bestCol = nb.startCol();
                }
            }
        }
        if (bestIdx >= 0) {
            selectedNodeIndex = bestIdx;
        }
    }

    void selectFirstNode() {
        if (nodeBoxes.isEmpty()) {
            return;
        }
        int bestIdx = 0;
        for (int i = 1; i < nodeBoxes.size(); i++) {
            var best = nodeBoxes.get(bestIdx);
            var nb = nodeBoxes.get(i);
            if (nb.startRow() < best.startRow()
                    || (nb.startRow() == best.startRow() && nb.startCol() < best.startCol())) {
                bestIdx = i;
            }
        }
        selectedNodeIndex = bestIdx;
    }

    void selectLastNode() {
        if (nodeBoxes.isEmpty()) {
            return;
        }
        int bestIdx = 0;
        for (int i = 1; i < nodeBoxes.size(); i++) {
            var best = nodeBoxes.get(bestIdx);
            var nb = nodeBoxes.get(i);
            if (nb.startRow() > best.startRow()
                    || (nb.startRow() == best.startRow() && nb.startCol() > best.startCol())) {
                bestIdx = i;
            }
        }
        selectedNodeIndex = bestIdx;
    }

    void scrollToSelectedNode() {
        if (selectedNodeIndex < 0 || selectedNodeIndex >= nodeBoxes.size()) {
            return;
        }
        TopologyAsciiRenderer.NodeBox box = nodeBoxes.get(selectedNodeIndex);
        if (lastVisibleHeight > 0) {
            if (box.startRow() < scrollY + 1) {
                scrollY = Math.max(0, box.startRow() - 1);
            }
            if (box.endRow() >= scrollY + lastVisibleHeight - 1) {
                scrollY = box.endRow() - lastVisibleHeight + 2;
            }
        }
        if (lastVisibleWidth > 0) {
            if (box.startCol() < scrollX + 1) {
                scrollX = Math.max(0, box.startCol() - 1);
            }
            if (box.endCol() >= scrollX + lastVisibleWidth - 1) {
                scrollX = box.endCol() - lastVisibleWidth + 2;
            }
        }
    }

    private List<TopologyAsciiRenderer.NodeBox> computeNodeBoxes(
            TopologyLayoutResult result, int nodeW, boolean metrics) {
        int bw = Math.max(16, nodeW / 15);
        List<TopologyAsciiRenderer.NodeBox> boxes = new ArrayList<>();
        for (TopologyLayoutNode node : result.nodes) {
            int col = nodeW == 0 ? 0 : node.x * bw / nodeW;
            int row = node.y / 20;
            boolean ext = "external-in".equals(node.nodeType) || "external-out".equals(node.nodeType);
            int contentLines;
            if (ext) {
                contentLines = 1;
                if (metrics && node.exchangesTotal > 0) {
                    contentLines++;
                }
            } else {
                contentLines = 3; // routeId + from (2 lines reserved)
                if (metrics) {
                    contentLines++;
                }
                contentLines = Math.min(contentLines, 4);
            }
            int height = 2 + contentLines;
            boxes.add(new TopologyAsciiRenderer.NodeBox(
                    node.routeId, row, row + height - 1, col, col + bw - 1, node.layer));
        }
        return boxes;
    }

    void renderNativeDiagram(Frame frame, Rect area, Line title, boolean metrics) {
        Block block = Block.builder()
                .borderType(BorderType.ROUNDED)
                .title(Title.from(title))
                .build();
        frame.renderWidget(block, area);

        Rect inner = block.inner(area);

        var widget = new org.apache.camel.dsl.jbang.core.commands.tui.diagram.TopologyDiagramWidget(
                topologyLayout, topologyNodeWidth, selectedNodeIndex, scrollX, scrollY, metrics, showDescription);

        int totalRows = widget.getTotalRows();
        int totalCols = widget.getTotalCols();
        int visibleLines = Math.max(1, inner.height() - 1);
        int visibleCols = Math.max(1, inner.width() - 1);
        lastVisibleHeight = visibleLines;
        lastVisibleWidth = visibleCols;

        int maxVScroll = Math.max(0, totalRows - visibleLines);
        int maxHScroll = Math.max(0, totalCols - visibleCols);
        scrollY = Math.min(scrollY, maxVScroll);
        scrollX = Math.min(scrollX, maxHScroll);

        // Re-create widget with clamped scroll
        var finalWidget = new org.apache.camel.dsl.jbang.core.commands.tui.diagram.TopologyDiagramWidget(
                topologyLayout, topologyNodeWidth, selectedNodeIndex, scrollX, scrollY, metrics, showDescription);

        List<Rect> vChunks = Layout.vertical()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(inner);

        List<Rect> hChunks = Layout.horizontal()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(vChunks.get(0));

        frame.renderWidget(finalWidget, hChunks.get(0));

        // Update nodeBoxes from widget
        List<TopologyAsciiRenderer.NodeBox> widgetBoxes = new ArrayList<>();
        for (var nb : finalWidget.getNodeBoxes()) {
            widgetBoxes.add(new TopologyAsciiRenderer.NodeBox(
                    nb.routeId(), nb.startRow(), nb.endRow(), nb.startCol(), nb.endCol(), nb.layer()));
        }
        if (!widgetBoxes.isEmpty()) {
            nodeBoxes = widgetBoxes;
        }

        vScrollState.contentLength(totalRows);
        vScrollState.viewportContentLength(visibleLines);
        vScrollState.position(scrollY);
        frame.renderStatefulWidget(
                Scrollbar.builder().build(),
                hChunks.get(1), vScrollState);

        if (totalCols > visibleCols) {
            hScrollState.contentLength(totalCols);
            hScrollState.viewportContentLength(visibleCols);
            hScrollState.position(scrollX);
            frame.renderStatefulWidget(
                    Scrollbar.horizontal(),
                    vChunks.get(1), hScrollState);
        }
    }

    // ---- Route EIP node selection ----

    private List<org.apache.camel.dsl.jbang.core.commands.tui.diagram.RouteDiagramWidget.EipNodeBox> eipNodeBoxes
            = Collections.emptyList();
    private int selectedEipNodeIndex = -1;

    List<org.apache.camel.dsl.jbang.core.commands.tui.diagram.RouteDiagramWidget.EipNodeBox> getEipNodeBoxes() {
        return eipNodeBoxes;
    }

    int getSelectedEipNodeIndex() {
        return selectedEipNodeIndex;
    }

    void setSelectedEipNodeIndex(int idx) {
        this.selectedEipNodeIndex = idx;
    }

    void selectFromNode(String routeId) {
        selectedEipNodeIndex = 0;
    }

    org.apache.camel.dsl.jbang.core.commands.tui.diagram.RouteDiagramWidget.EipNodeBox getSelectedEipNodeBox() {
        if (selectedEipNodeIndex >= 0 && selectedEipNodeIndex < eipNodeBoxes.size()) {
            return eipNodeBoxes.get(selectedEipNodeIndex);
        }
        return null;
    }

    /**
     * Finds the route ID that the selected EIP node links to, by matching the node's endpoint URI against topology
     * edges and route "from" endpoints.
     */
    String findLinkedRouteId(String currentRouteId) {
        var box = getSelectedEipNodeBox();
        if (box == null || box.layoutNode() == null || box.layoutNode().treeNode == null) {
            return null;
        }
        String type = box.type();
        if (type == null) {
            return null;
        }
        // Only "to"-style nodes can link to other routes
        if (!"to".equals(type) && !"toD".equals(type) && !"wireTap".equals(type)
                && !"enrich".equals(type) && !"pollEnrich".equals(type)
                && !"from".equals(type)) {
            return null;
        }
        String baseUri = getBaseUri(box.layoutNode().treeNode.info);
        if (baseUri == null || baseUri.isBlank()) {
            return null;
        }

        // First try topology edges for direct matching
        for (TopologyLayoutEdge edge : topologyEdges) {
            if ("from".equals(type)) {
                // "from" node: find route that sends TO this endpoint
                if (currentRouteId.equals(edge.to.routeId) && !currentRouteId.equals(edge.from.routeId)) {
                    return edge.from.routeId;
                }
            } else {
                // "to" node: find route that consumes FROM this endpoint
                if (currentRouteId.equals(edge.from.routeId) && !currentRouteId.equals(edge.to.routeId)) {
                    String targetFrom = stripQueryParams(edge.to.from);
                    if (baseUri.equals(targetFrom)) {
                        return edge.to.routeId;
                    }
                }
            }
        }

        // Fallback: match uri against route "from" endpoints in routeLayouts
        if (!"from".equals(type)) {
            for (var entry : routeLayouts.entrySet()) {
                if (currentRouteId.equals(entry.getKey())) {
                    continue;
                }
                String fromBaseUri = findFromUri(entry.getValue());
                if (baseUri.equals(fromBaseUri)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    static String getBaseUri(RouteDiagramLayoutEngine.NodeInfo info) {
        String uri = info.uri;
        if (uri == null) {
            uri = extractUriFromCode(info.code);
        }
        return stripQueryParams(uri);
    }

    private static String extractUriFromCode(String code) {
        if (code == null) {
            return null;
        }
        int open = code.indexOf('[');
        int close = code.lastIndexOf(']');
        return (open >= 0 && close > open) ? code.substring(open + 1, close) : code;
    }

    static String stripQueryParams(String uri) {
        if (uri == null) {
            return null;
        }
        int q = uri.indexOf('?');
        return q >= 0 ? uri.substring(0, q) : uri;
    }

    void selectEipNodeUp() {
        if (eipNodeBoxes.isEmpty()) {
            return;
        }
        if (selectedEipNodeIndex < 0) {
            selectedEipNodeIndex = 0;
            return;
        }
        // Move to previous node in list (follow flow upward)
        if (selectedEipNodeIndex > 0) {
            selectedEipNodeIndex--;
        }
    }

    void selectEipNodeDown() {
        if (eipNodeBoxes.isEmpty()) {
            return;
        }
        if (selectedEipNodeIndex < 0) {
            selectedEipNodeIndex = 0;
            return;
        }
        if (selectedEipNodeIndex < eipNodeBoxes.size() - 1) {
            selectedEipNodeIndex++;
        }
    }

    void selectEipNodeLeft() {
        if (eipNodeBoxes.isEmpty() || selectedEipNodeIndex < 0) {
            return;
        }
        var current = eipNodeBoxes.get(selectedEipNodeIndex);
        int bestIdx = -1;
        int bestCol = -1;
        for (int i = 0; i < eipNodeBoxes.size(); i++) {
            var nb = eipNodeBoxes.get(i);
            if (nb.startCol() < current.startCol()
                    && Math.abs(nb.startRow() - current.startRow()) <= 2) {
                if (nb.startCol() > bestCol) {
                    bestIdx = i;
                    bestCol = nb.startCol();
                }
            }
        }
        if (bestIdx >= 0) {
            selectedEipNodeIndex = bestIdx;
        }
    }

    void selectEipNodeRight() {
        if (eipNodeBoxes.isEmpty() || selectedEipNodeIndex < 0) {
            return;
        }
        var current = eipNodeBoxes.get(selectedEipNodeIndex);
        int bestIdx = -1;
        int bestCol = Integer.MAX_VALUE;
        for (int i = 0; i < eipNodeBoxes.size(); i++) {
            var nb = eipNodeBoxes.get(i);
            if (nb.startCol() > current.startCol()
                    && Math.abs(nb.startRow() - current.startRow()) <= 2) {
                if (nb.startCol() < bestCol) {
                    bestIdx = i;
                    bestCol = nb.startCol();
                }
            }
        }
        if (bestIdx >= 0) {
            selectedEipNodeIndex = bestIdx;
        }
    }

    void selectFirstEipNode() {
        if (eipNodeBoxes.isEmpty()) {
            return;
        }
        int bestIdx = 0;
        for (int i = 1; i < eipNodeBoxes.size(); i++) {
            var best = eipNodeBoxes.get(bestIdx);
            var nb = eipNodeBoxes.get(i);
            if (nb.startRow() < best.startRow()
                    || (nb.startRow() == best.startRow() && nb.startCol() < best.startCol())) {
                bestIdx = i;
            }
        }
        selectedEipNodeIndex = bestIdx;
    }

    void selectLastEipNode() {
        if (eipNodeBoxes.isEmpty()) {
            return;
        }
        int bestIdx = 0;
        for (int i = 1; i < eipNodeBoxes.size(); i++) {
            var best = eipNodeBoxes.get(bestIdx);
            var nb = eipNodeBoxes.get(i);
            if (nb.startRow() > best.startRow()
                    || (nb.startRow() == best.startRow() && nb.startCol() > best.startCol())) {
                bestIdx = i;
            }
        }
        selectedEipNodeIndex = bestIdx;
    }

    void scrollToSelectedEipNode() {
        if (selectedEipNodeIndex < 0 || selectedEipNodeIndex >= eipNodeBoxes.size()) {
            return;
        }
        var box = eipNodeBoxes.get(selectedEipNodeIndex);
        if (lastVisibleHeight > 0) {
            if (box.startRow() < scrollY + 1) {
                scrollY = Math.max(0, box.startRow() - 1);
            }
            if (box.endRow() >= scrollY + lastVisibleHeight - 1) {
                scrollY = box.endRow() - lastVisibleHeight + 2;
            }
        }
        if (lastVisibleWidth > 0) {
            if (box.startCol() < scrollX + 1) {
                scrollX = Math.max(0, box.startCol() - 1);
            }
            if (box.endCol() >= scrollX + lastVisibleWidth - 1) {
                scrollX = box.endCol() - lastVisibleWidth + 2;
            }
        }
    }

    /**
     * Computes a mapping from base endpoint URI to target route ID for navigable links from the current route.
     */
    private Map<String, String> computeLinkableEndpoints(String currentRouteId) {
        Map<String, String> endpoints = new HashMap<>();
        String currentFromUri = null;
        var currentLayout = routeLayouts.get(currentRouteId);
        if (currentLayout != null) {
            currentFromUri = findFromUri(currentLayout);
        }

        for (var entry : routeLayouts.entrySet()) {
            if (currentRouteId.equals(entry.getKey())) {
                continue;
            }
            var lr = entry.getValue();
            String fromUri = findFromUri(lr);
            if (fromUri != null) {
                endpoints.put(fromUri, entry.getKey());
            }
            if (currentFromUri != null) {
                for (var node : lr.nodes) {
                    String type = node.type;
                    if (("to".equals(type) || "toD".equals(type) || "wireTap".equals(type))
                            && node.treeNode != null) {
                        String uri = getBaseUri(node.treeNode.info);
                        if (currentFromUri.equals(uri)) {
                            endpoints.put(currentFromUri, entry.getKey());
                        }
                    }
                }
            }
        }
        return endpoints;
    }

    private Map<String, String> computeRouteDescriptions() {
        Map<String, String> descriptions = new HashMap<>();
        for (var entry : routeLayouts.entrySet()) {
            var lr = entry.getValue();
            for (var node : lr.nodes) {
                if ("route".equals(node.type) && node.treeNode != null) {
                    String desc = node.treeNode.info.description;
                    if (desc != null && !desc.isBlank()) {
                        descriptions.put(entry.getKey(), desc);
                    }
                    break;
                }
            }
        }
        return descriptions;
    }

    private static Set<String> parseExternalUris(JsonObject topoJson) {
        Set<String> uris = new HashSet<>();
        JsonArray arr = topoJson.getJsonArray("externalEndpoints");
        if (arr == null) {
            return uris;
        }
        for (int i = 0; i < arr.size(); i++) {
            JsonObject eo = arr.getJsonObject(i);
            String uri = eo.getString("uri");
            if (uri != null) {
                String base = stripQueryParams(uri);
                if (base != null) {
                    uris.add(base);
                }
            }
        }
        return uris;
    }

    private static String findFromUri(RouteDiagramLayoutEngine.LayoutRoute lr) {
        for (var node : lr.nodes) {
            if ("from".equals(node.type) && node.treeNode != null) {
                return getBaseUri(node.treeNode.info);
            }
        }
        return null;
    }

    void renderNativeRouteDiagram(
            Frame frame, Rect area, Line title, boolean metrics,
            String currentRouteId, RouteDiagramLayoutEngine.LayoutRoute routeLayout) {
        Block block = Block.builder()
                .borderType(BorderType.ROUNDED)
                .title(Title.from(title))
                .build();
        frame.renderWidget(block, area);

        Rect inner = block.inner(area);
        int nw = RouteDiagramLayoutEngine.DEFAULT_BOX_WIDTH * RouteDiagramLayoutEngine.SCALE;
        Map<String, String> linkable = computeLinkableEndpoints(currentRouteId);
        Map<String, String> routeDescs = showDescription ? computeRouteDescriptions() : Collections.emptyMap();

        var widget = new org.apache.camel.dsl.jbang.core.commands.tui.diagram.RouteDiagramWidget(
                routeLayout, nw, selectedEipNodeIndex, scrollX, scrollY, metrics, linkable,
                showDescription, routeDescs);

        int totalRows = widget.getTotalRows();
        int totalCols = widget.getTotalCols();
        int visibleLines = Math.max(1, inner.height() - 1);
        int visibleCols = Math.max(1, inner.width() - 1);
        lastVisibleHeight = visibleLines;
        lastVisibleWidth = visibleCols;

        int maxVScroll = Math.max(0, totalRows - visibleLines);
        int maxHScroll = Math.max(0, totalCols - visibleCols);
        scrollY = Math.min(scrollY, maxVScroll);
        scrollX = Math.min(scrollX, maxHScroll);

        var finalWidget = new org.apache.camel.dsl.jbang.core.commands.tui.diagram.RouteDiagramWidget(
                routeLayout, nw, selectedEipNodeIndex, scrollX, scrollY, metrics, linkable,
                showDescription, routeDescs);

        List<Rect> vChunks = Layout.vertical()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(inner);

        List<Rect> hChunks = Layout.horizontal()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(vChunks.get(0));

        frame.renderWidget(finalWidget, hChunks.get(0));

        eipNodeBoxes = new ArrayList<>(finalWidget.getNodeBoxes());
        if (selectedEipNodeIndex < 0 && !eipNodeBoxes.isEmpty()) {
            selectedEipNodeIndex = 0;
        }

        vScrollState.contentLength(totalRows);
        vScrollState.viewportContentLength(visibleLines);
        vScrollState.position(scrollY);
        frame.renderStatefulWidget(
                Scrollbar.builder().build(),
                hChunks.get(1), vScrollState);

        if (totalCols > visibleCols) {
            hScrollState.contentLength(totalCols);
            hScrollState.viewportContentLength(visibleCols);
            hScrollState.position(scrollX);
            frame.renderStatefulWidget(
                    Scrollbar.horizontal(),
                    vChunks.get(1), hScrollState);
        }
    }

    // ---- Rendering (legacy text) ----

    void renderDiagram(Frame frame, Rect area, String title) {
        Block block = Block.builder()
                .borderType(BorderType.ROUNDED)
                .title(title)
                .build();

        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, CharWidth.of(line));
        }

        Rect inner = block.inner(area);
        int visibleLines = Math.max(1, inner.height() - 1);
        int visibleCols = Math.max(1, inner.width() - 1);
        lastVisibleHeight = visibleLines;
        lastVisibleWidth = visibleCols;

        int maxVScroll = Math.max(0, lines.size() - visibleLines);
        int maxHScroll = Math.max(0, maxWidth - visibleCols);
        scrollY = Math.min(scrollY, maxVScroll);
        scrollX = Math.min(scrollX, maxHScroll);

        List<Line> rendered = new ArrayList<>();
        int end = Math.min(scrollY + visibleLines, lines.size());
        for (int i = scrollY; i < end; i++) {
            String line = lines.get(i);
            if (scrollX > 0) {
                line = scrollX < line.length() ? line.substring(scrollX) : "";
            }
            rendered.add(styleDiagramLine(line, i, scrollX));
        }

        frame.renderWidget(block, area);

        List<Rect> vChunks = Layout.vertical()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(inner);

        List<Rect> hChunks = Layout.horizontal()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(vChunks.get(0));

        Paragraph paragraph = Paragraph.builder()
                .text(Text.from(rendered))
                .build();
        frame.renderWidget(paragraph, hChunks.get(0));

        vScrollState.contentLength(lines.size());
        vScrollState.viewportContentLength(visibleLines);
        vScrollState.position(scrollY);
        frame.renderStatefulWidget(
                Scrollbar.builder().build(),
                hChunks.get(1), vScrollState);

        if (maxWidth > visibleCols) {
            hScrollState.contentLength(maxWidth);
            hScrollState.viewportContentLength(visibleCols);
            hScrollState.position(scrollX);
            frame.renderStatefulWidget(
                    Scrollbar.horizontal(),
                    vChunks.get(1), hScrollState);
        }
    }

    // ---- Async loading ----

    boolean beginLoad() {
        return loading.compareAndSet(false, true);
    }

    void endLoad() {
        loading.set(false);
    }

    void setLoadingPlaceholder() {
        lines = List.of("(Loading diagram...)");
        showDiagram = true;
        scrollY = 0;
        scrollX = 0;
    }

    void loadHighlightedDiagramInBackground(
            MonitorContext ctx, String pid,
            String[] messageHistory, RouteDiagramHelper.HighlightStyle hlStyle) {
        JsonObject jo = requestRouteStructure(ctx, pid);
        if (jo == null) {
            applyResult(ctx, List.of("(No response from integration)"));
            return;
        }

        RouteDiagramHelper.HighlightInfo highlightInfo
                = RouteDiagramHelper.parseMessageHistory(messageHistory, hlStyle);
        Set<String> nodeIds = new LinkedHashSet<>(highlightInfo.getNodeIds());

        List<RouteDiagramLayoutEngine.RouteInfo> routes = RouteDiagramHelper.parseRoutes(jo);
        if (routes.isEmpty()) {
            applyResult(ctx, List.of("(No routes in response)"));
            return;
        }

        for (RouteDiagramLayoutEngine.RouteInfo ri : routes) {
            boolean routeHasHighlight = ri.nodes.stream().anyMatch(n -> n.id != null && nodeIds.contains(n.id));
            if (routeHasHighlight) {
                addParentNodes(ri.nodes, nodeIds);
            }
        }

        RouteDiagramHelper.HighlightInfo fullHighlight
                = new RouteDiagramHelper.HighlightInfo(nodeIds, highlightInfo.getRouteOrder(), hlStyle);
        routes = RouteDiagramHelper.filterAndOrderRoutes(routes, fullHighlight);
        if (routes.isEmpty()) {
            applyResult(ctx, List.of("(No routes contain highlighted nodes)"));
            return;
        }

        renderRoutes(ctx, routes, false, nodeIds, hlStyle);
    }

    void loadAllDiagramsInBackground(
            MonitorContext ctx, String pid, boolean metrics, boolean external) {
        // Single IPC call: topology + route structures
        JsonObject topoJson = requestRouteTopology(ctx, pid, external, true);

        TopologyLayoutResult topoResult = null;
        int nodeW = 0;
        List<TopologyLayoutNode> topoNodes = Collections.emptyList();
        List<TopologyLayoutEdge> topoEdges = Collections.emptyList();

        if (topoJson != null) {
            List<TopologyNodeInfo> nodes = TopologyHelper.parseNodes(topoJson);
            List<TopologyEdgeInfo> edges = TopologyHelper.parseEdges(topoJson);
            if (external) {
                TopologyHelper.addExternalEndpoints(nodes, edges, topoJson);
            }
            if (!nodes.isEmpty()) {
                TopologyLayoutEngine engine = new TopologyLayoutEngine();
                topoResult = engine.layout(nodes, edges);
                normalizeTopologyLayoutY(topoResult);
                nodeW = engine.getNodeWidth();
                topoNodes = topoResult.nodes;
                topoEdges = topoResult.edges;
            }
        }

        // Route structure is included in the topology response (routes=true)
        java.util.Map<String, RouteDiagramLayoutEngine.LayoutRoute> routeMap = new java.util.LinkedHashMap<>();
        if (topoJson != null) {
            List<RouteDiagramLayoutEngine.RouteInfo> routes = RouteDiagramHelper.parseRoutes(topoJson);
            if (!routes.isEmpty()) {
                // Enrich route nodes with remote flag from topology external endpoints
                Set<String> externalUris = parseExternalUris(topoJson);
                if (!externalUris.isEmpty()) {
                    for (RouteDiagramLayoutEngine.RouteInfo r : routes) {
                        for (RouteDiagramLayoutEngine.NodeInfo ni : r.nodes) {
                            if (!ni.remote) {
                                String baseUri = getBaseUri(ni);
                                if (baseUri != null && externalUris.contains(baseUri)) {
                                    ni.remote = true;
                                }
                            }
                        }
                    }
                }
                RouteDiagramLayoutEngine.NodeLabelMode labelMode = showDescription
                        ? RouteDiagramLayoutEngine.NodeLabelMode.DESCRIPTION
                        : RouteDiagramLayoutEngine.NodeLabelMode.CODE;
                RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine(
                        RouteDiagramLayoutEngine.DEFAULT_BOX_WIDTH, RouteDiagramLayoutEngine.DEFAULT_FONT_SIZE,
                        labelMode);
                for (RouteDiagramLayoutEngine.RouteInfo r : routes) {
                    RouteDiagramLayoutEngine.LayoutRoute lr = engine.layoutRoute(r, 0);
                    normalizeRouteLayoutY(lr);
                    routeMap.put(r.routeId, lr);
                }
            }
        }

        // Apply results on render thread
        TopologyLayoutResult finalTopoResult = topoResult;
        int finalNodeW = nodeW;
        List<TopologyLayoutNode> finalTopoNodes = topoNodes;
        List<TopologyLayoutEdge> finalTopoEdges = topoEdges;

        if (ctx.runner == null) {
            return;
        }
        ctx.runner.runOnRenderThread(() -> {
            topologyLayout = finalTopoResult;
            topologyNodeWidth = finalNodeW;
            topologyNodes = finalTopoNodes;
            topologyEdges = finalTopoEdges;
            routeLayouts = routeMap;

            // Preserve selection
            String prevRouteId = getSelectedRouteId();
            if (prevRouteId == null && pendingSelectionRouteId != null) {
                prevRouteId = pendingSelectionRouteId;
            }
            pendingSelectionRouteId = null;

            if (finalTopoResult != null) {
                List<TopologyAsciiRenderer.NodeBox> boxes
                        = computeNodeBoxes(finalTopoResult, finalNodeW, metrics);
                nodeBoxes = boxes;

                if (prevRouteId != null && !boxes.isEmpty()) {
                    int newIdx = -1;
                    for (int i = 0; i < boxes.size(); i++) {
                        if (prevRouteId.equals(boxes.get(i).routeId())) {
                            newIdx = i;
                            break;
                        }
                    }
                    selectedNodeIndex = newIdx >= 0 ? newIdx : 0;
                } else if (!boxes.isEmpty() && selectedNodeIndex < 0) {
                    selectedNodeIndex = findTopLeftNode(boxes);
                } else if (boxes.isEmpty()) {
                    selectedNodeIndex = -1;
                }
            }

            showDiagram = true;
        });
    }

    void loadRouteDiagramInBackground(
            MonitorContext ctx, String pid,
            String routeId, boolean metrics) {
        JsonObject jo = requestRouteStructure(ctx, pid);
        if (jo == null) {
            applyResult(ctx, List.of("(No response from integration)"));
            return;
        }

        List<RouteDiagramLayoutEngine.RouteInfo> routes = RouteDiagramHelper.parseRoutes(jo);
        if (routes.isEmpty()) {
            applyResult(ctx, List.of("(No routes in response)"));
            return;
        }

        if (routeId != null) {
            routes.removeIf(r -> !routeId.equals(r.routeId));
        }

        renderRoutes(ctx, routes, metrics, null, null);
    }

    void loadTopologyDiagramInBackground(
            MonitorContext ctx, String pid, boolean metrics, boolean external) {
        JsonObject jo = requestRouteTopology(ctx, pid, external, false);
        if (jo == null) {
            applyResult(ctx, List.of("(No response from integration)"));
            return;
        }

        List<TopologyNodeInfo> nodes = TopologyHelper.parseNodes(jo);
        List<TopologyEdgeInfo> edges = TopologyHelper.parseEdges(jo);
        if (external) {
            TopologyHelper.addExternalEndpoints(nodes, edges, jo);
        }
        if (nodes.isEmpty()) {
            applyResult(ctx, List.of("(No routes in response)"));
            return;
        }

        TopologyLayoutEngine engine = new TopologyLayoutEngine();
        TopologyLayoutResult result = engine.layout(nodes, edges);

        TopologyAsciiRenderer renderer = new TopologyAsciiRenderer(
                engine.getNodeWidth(), true, metrics, showDescription);
        String text = renderer.renderDiagramPlain(result);

        List<String> resultLines = new ArrayList<>();
        List<RouteDiagramAsciiRenderer.CounterPos> positions = new ArrayList<>();
        String[] rawLines = text.split("\n", -1);
        int[] rowMapping = new int[rawLines.length];
        int newRow = 0;
        for (int i = 0; i < rawLines.length; i++) {
            if (!rawLines[i].isEmpty()) {
                rowMapping[i] = newRow++;
                resultLines.add(rawLines[i]);
            } else {
                rowMapping[i] = -1;
            }
        }

        for (TopologyAsciiRenderer.CounterPos cp : renderer.getCounterPositions()) {
            if (cp.row() >= 0 && cp.row() < rowMapping.length && rowMapping[cp.row()] >= 0) {
                RouteDiagramAsciiRenderer.CounterType mapped = switch (cp.type()) {
                    case OK -> RouteDiagramAsciiRenderer.CounterType.OK;
                    case FAIL -> RouteDiagramAsciiRenderer.CounterType.FAIL;
                    case TRIGGER -> RouteDiagramAsciiRenderer.CounterType.HIGHLIGHT_SUCCESS;
                    case EXTERNAL -> RouteDiagramAsciiRenderer.CounterType.EXTERNAL;
                };
                positions.add(new RouteDiagramAsciiRenderer.CounterPos(
                        rowMapping[cp.row()], cp.col(), cp.length(), mapped));
            }
        }

        List<TopologyAsciiRenderer.NodeBox> boxes = new ArrayList<>();
        for (TopologyAsciiRenderer.NodeBox nb : renderer.getNodeBoxes()) {
            int mappedStart = (nb.startRow() >= 0 && nb.startRow() < rowMapping.length)
                    ? rowMapping[nb.startRow()] : -1;
            int mappedEnd = (nb.endRow() >= 0 && nb.endRow() < rowMapping.length)
                    ? rowMapping[nb.endRow()] : -1;
            if (mappedStart >= 0 && mappedEnd >= 0) {
                boxes.add(new TopologyAsciiRenderer.NodeBox(
                        nb.routeId(), mappedStart, mappedEnd, nb.startCol(), nb.endCol(), nb.layer()));
            }
        }

        applyResult(ctx, resultLines, positions, Collections.emptySet(), boxes,
                result.nodes, result.edges);
    }

    private JsonObject requestRouteTopology(MonitorContext ctx, String pid, boolean external, boolean routes) {
        Path outputFile = ctx.getOutputFile(pid);
        PathUtils.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "route-topology");
        root.put("metric", "true");
        // Always request external endpoints so route diagrams can show dashed borders
        root.put("external", "true");
        if (routes) {
            root.put("routes", "true");
        }

        Path actionFile = ctx.getActionFile(pid);
        PathUtils.writeTextSafely(root.toJson(), actionFile);

        JsonObject jo = pollJsonResponse(outputFile, 5000);
        PathUtils.deleteFile(outputFile);
        return jo;
    }

    private void renderRoutes(
            MonitorContext ctx,
            List<RouteDiagramLayoutEngine.RouteInfo> routes, boolean metrics,
            Set<String> highlightNodeIds, RouteDiagramHelper.HighlightStyle hlStyle) {
        RouteDiagramLayoutEngine.NodeLabelMode labelMode = showDescription
                ? RouteDiagramLayoutEngine.NodeLabelMode.DESCRIPTION
                : RouteDiagramLayoutEngine.NodeLabelMode.CODE;
        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine(
                RouteDiagramLayoutEngine.DEFAULT_BOX_WIDTH, RouteDiagramLayoutEngine.DEFAULT_FONT_SIZE,
                labelMode);

        List<String> result = new ArrayList<>();
        List<RouteDiagramAsciiRenderer.CounterPos> positions = new ArrayList<>();
        Set<Integer> titleRows = new HashSet<>();

        int currentY = RouteDiagramLayoutEngine.PADDING;
        for (RouteDiagramLayoutEngine.RouteInfo r : routes) {
            if (!result.isEmpty()) {
                result.add("");
                result.add("");
            }

            int titleRow = result.size();

            RouteDiagramLayoutEngine.LayoutRoute lr = engine.layoutRoute(r, currentY);
            currentY = lr.maxY + RouteDiagramLayoutEngine.V_GAP;

            RouteDiagramAsciiRenderer asciiRenderer = new RouteDiagramAsciiRenderer(
                    RouteDiagramLayoutEngine.DEFAULT_BOX_WIDTH * RouteDiagramLayoutEngine.SCALE, true, metrics);
            String ascii;
            if (highlightNodeIds != null && hlStyle != null) {
                ascii = asciiRenderer.renderDiagram(List.of(lr),
                        lr.maxY + RouteDiagramLayoutEngine.V_GAP, highlightNodeIds, hlStyle);
            } else {
                ascii = asciiRenderer.renderDiagram(List.of(lr),
                        lr.maxY + RouteDiagramLayoutEngine.V_GAP);
            }
            List<RouteDiagramAsciiRenderer.CounterPos> origPositions = asciiRenderer.getCounterPositions();

            String[] rawLines = ascii.split("\n", -1);
            int[] rowMapping = new int[rawLines.length];
            int newRow = result.size();
            for (int i = 0; i < rawLines.length; i++) {
                if (!rawLines[i].isEmpty()) {
                    rowMapping[i] = newRow++;
                    result.add(rawLines[i]);
                } else {
                    rowMapping[i] = -1;
                }
            }
            for (RouteDiagramAsciiRenderer.CounterPos cp : origPositions) {
                if (cp.row() >= 0 && cp.row() < rowMapping.length && rowMapping[cp.row()] >= 0) {
                    positions.add(new RouteDiagramAsciiRenderer.CounterPos(
                            rowMapping[cp.row()], cp.col(), cp.length(), cp.type()));
                }
            }
            titleRows.add(titleRow);
        }

        applyResult(ctx, result, positions, titleRows);
    }

    private JsonObject requestRouteStructure(MonitorContext ctx, String pid) {
        Path outputFile = ctx.getOutputFile(pid);
        PathUtils.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "route-structure");
        root.put("filter", "*");
        root.put("brief", false);
        root.put("metric", true);

        Path actionFile = ctx.getActionFile(pid);
        PathUtils.writeTextSafely(root.toJson(), actionFile);

        JsonObject jo = pollJsonResponse(outputFile, 5000);
        PathUtils.deleteFile(outputFile);
        return jo;
    }

    private void applyResult(MonitorContext ctx, List<String> resultLines) {
        applyResult(ctx, resultLines, Collections.emptyList(), Collections.emptySet(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    private void applyResult(
            MonitorContext ctx, List<String> resultLines,
            List<RouteDiagramAsciiRenderer.CounterPos> positions, Set<Integer> titleRows) {
        applyResult(ctx, resultLines, positions, titleRows,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    private void applyResult(
            MonitorContext ctx, List<String> resultLines,
            List<RouteDiagramAsciiRenderer.CounterPos> positions, Set<Integer> titleRows,
            List<TopologyAsciiRenderer.NodeBox> resultNodeBoxes,
            List<TopologyLayoutNode> resultTopologyNodes,
            List<TopologyLayoutEdge> resultTopologyEdges) {
        if (ctx.runner == null) {
            return;
        }
        ctx.runner.runOnRenderThread(() -> {
            boolean wasShowing = showDiagram;
            lines = resultLines;
            counterPositions = positions;
            routeTitleRows = titleRows;
            topologyNodes = resultTopologyNodes;
            topologyEdges = resultTopologyEdges;

            // Preserve selection across refreshes by matching routeId
            String prevSelectedRouteId = getSelectedRouteId();
            if (prevSelectedRouteId == null && pendingSelectionRouteId != null) {
                prevSelectedRouteId = pendingSelectionRouteId;
            }
            pendingSelectionRouteId = null;
            nodeBoxes = resultNodeBoxes;
            if (prevSelectedRouteId != null && !resultNodeBoxes.isEmpty()) {
                int newIdx = -1;
                for (int i = 0; i < resultNodeBoxes.size(); i++) {
                    if (prevSelectedRouteId.equals(resultNodeBoxes.get(i).routeId())) {
                        newIdx = i;
                        break;
                    }
                }
                selectedNodeIndex = newIdx >= 0 ? newIdx : 0;
            } else if (!resultNodeBoxes.isEmpty() && selectedNodeIndex < 0) {
                selectedNodeIndex = findTopLeftNode(resultNodeBoxes);
            } else if (resultNodeBoxes.isEmpty()) {
                selectedNodeIndex = -1;
            }

            if (!wasShowing) {
                scrollY = 0;
                scrollX = 0;
            }
            showDiagram = true;
        });
    }

    // ---- Diagram styling ----

    private Line styleDiagramLine(String text, int row, int hScrollX) {
        if (routeTitleRows.contains(row)) {
            return Line.from(Span.styled(text, Style.EMPTY.fg(Color.WHITE).bold()));
        }

        // Check if this row is within the selected node
        int selStartCol = -1;
        int selEndCol = -1;
        if (selectedNodeIndex >= 0 && selectedNodeIndex < nodeBoxes.size()) {
            TopologyAsciiRenderer.NodeBox box = nodeBoxes.get(selectedNodeIndex);
            if (row >= box.startRow() && row <= box.endRow()) {
                selStartCol = box.startCol() - hScrollX;
                selEndCol = box.endCol() - hScrollX;
            }
        }

        List<int[]> counterRanges = new ArrayList<>();
        for (RouteDiagramAsciiRenderer.CounterPos cp : counterPositions) {
            if (cp.row() == row) {
                int start = cp.col() - hScrollX;
                int end = start + cp.length();
                if (end > 0 && start < text.length()) {
                    start = Math.max(0, start);
                    end = Math.min(end, text.length());
                    int colorFlag = switch (cp.type()) {
                        case OK, HIGHLIGHT_SUCCESS -> 1; // green
                        case EXTERNAL -> 3; // cyan
                        default -> 2; // red
                    };
                    counterRanges.add(new int[] { start, end, colorFlag });
                }
            }
        }

        List<Span> spans = new ArrayList<>();
        int idx = 0;
        while (idx < text.length()) {
            int open = text.indexOf('[', idx);
            if (open < 0) {
                addStyledSegment(spans, text, idx, text.length(), counterRanges, Color.WHITE);
                break;
            }
            int close = text.indexOf(']', open);
            if (close < 0) {
                addStyledSegment(spans, text, idx, text.length(), counterRanges, Color.WHITE);
                break;
            }
            if (open > idx) {
                addStyledSegment(spans, text, idx, open, counterRanges, Color.GRAY);
            }
            String tag = text.substring(open + 1, close);
            Color tagColor = getDiagramNodeColor(tag);
            spans.add(Span.styled("[" + tag + "]", Style.EMPTY.fg(tagColor).bold()));

            int afterTag = close + 1;
            int nextOpen = text.indexOf('[', afterTag);
            int labelEnd = nextOpen >= 0 ? nextOpen : text.length();
            if (afterTag < labelEnd) {
                addStyledSegment(spans, text, afterTag, labelEnd, counterRanges, Color.WHITE);
            }
            idx = labelEnd;
        }

        // Apply selection highlighting as background overlay
        if (selStartCol >= 0 && selEndCol >= 0) {
            spans = applySelectionHighlight(spans, selStartCol, selEndCol);
        }

        return Line.from(spans);
    }

    private static List<Span> applySelectionHighlight(List<Span> spans, int startCol, int endCol) {
        List<Span> result = new ArrayList<>();
        int pos = 0;
        for (Span span : spans) {
            String content = span.content();
            int spanStart = pos;
            int spanEnd = pos + content.length();
            pos = spanEnd;

            if (spanEnd <= startCol || spanStart >= endCol + 1) {
                result.add(span);
                continue;
            }

            // Split span at selection boundaries
            if (spanStart < startCol) {
                result.add(Span.styled(content.substring(0, startCol - spanStart), span.style()));
            }
            int hlStart = Math.max(0, startCol - spanStart);
            int hlEnd = Math.min(content.length(), endCol + 1 - spanStart);
            if (hlStart < hlEnd) {
                Style hlStyle = span.style().bg(Color.DARK_GRAY);
                result.add(Span.styled(content.substring(hlStart, hlEnd), hlStyle));
            }
            if (spanStart + content.length() > endCol + 1) {
                result.add(Span.styled(content.substring(endCol + 1 - spanStart), span.style()));
            }
        }
        return result;
    }

    private static void addStyledSegment(
            List<Span> spans, String text, int from, int to, List<int[]> counterRanges, Color defaultColor) {
        int pos = from;
        while (pos < to) {
            int[] cr = findNextCounterRange(counterRanges, pos, to);
            if (cr != null) {
                if (pos < cr[0]) {
                    spans.add(Span.styled(text.substring(pos, cr[0]), Style.EMPTY.fg(defaultColor)));
                }
                int counterEnd = Math.min(cr[1], to);
                Color counterColor = cr[2] == 1 ? Color.GREEN : cr[2] == 3 ? Color.CYAN : Color.LIGHT_RED;
                spans.add(Span.styled(text.substring(cr[0], counterEnd), Style.EMPTY.fg(counterColor).bold()));
                pos = counterEnd;
            } else {
                spans.add(Span.styled(text.substring(pos, to), Style.EMPTY.fg(defaultColor)));
                pos = to;
            }
        }
    }

    private static int[] findNextCounterRange(List<int[]> ranges, int pos, int limit) {
        int[] best = null;
        for (int[] range : ranges) {
            if (range[1] > pos && range[0] < limit) {
                int start = Math.max(range[0], pos);
                if (best == null || start < best[0]) {
                    best = new int[] { start, range[1], range[2] };
                }
            }
        }
        return best;
    }

    static Color getDiagramNodeColor(String type) {
        if (type == null) {
            return Color.GRAY;
        }
        return switch (type) {
            case "from" -> Color.GREEN;
            case "to", "toD", "wireTap", "enrich", "pollEnrich" -> Color.CYAN;
            case "choice", "when", "otherwise" -> Color.YELLOW;
            case "marshal", "unmarshal", "transform", "setBody", "setHeader", "setProperty",
                    "convertBodyTo", "removeHeader", "removeHeaders", "removeProperty", "removeProperties" ->
                Color.CYAN;
            case "bean", "process", "log", "script", "delay" -> Color.MAGENTA;
            case "filter", "split", "aggregate", "multicast", "recipientList",
                    "routingSlip", "dynamicRouter", "loadBalance",
                    "circuitBreaker", "saga", "doTry", "doCatch", "doFinally",
                    "onException", "onCompletion", "intercept",
                    "loop", "resequence", "throttle", "kamelet", "pipeline", "threads" ->
                Color.rgb(0x89, 0x57, 0xE5);
            default -> Color.GRAY;
        };
    }

    static void addParentNodes(List<RouteDiagramLayoutEngine.NodeInfo> nodes, Set<String> nodeIds) {
        for (int i = 0; i < nodes.size(); i++) {
            RouteDiagramLayoutEngine.NodeInfo node = nodes.get(i);
            if (node.id == null || nodeIds.contains(node.id)) {
                continue;
            }
            boolean hasHighlightedChild = false;
            for (int j = i + 1; j < nodes.size(); j++) {
                RouteDiagramLayoutEngine.NodeInfo child = nodes.get(j);
                if (child.level <= node.level) {
                    break;
                }
                if (child.id != null && nodeIds.contains(child.id)) {
                    hasHighlightedChild = true;
                    break;
                }
            }
            if (hasHighlightedChild) {
                nodeIds.add(node.id);
            }
        }
    }

    private static void normalizeTopologyLayoutY(TopologyLayoutResult result) {
        if (result.nodes.isEmpty()) {
            return;
        }
        int minY = Integer.MAX_VALUE;
        for (TopologyLayoutNode node : result.nodes) {
            minY = Math.min(minY, node.y);
        }
        if (minY <= 0) {
            return;
        }
        int shift = minY - 40;
        for (TopologyLayoutNode node : result.nodes) {
            node.y -= shift;
        }
    }

    private static void normalizeRouteLayoutY(RouteDiagramLayoutEngine.LayoutRoute lr) {
        int minY = Integer.MAX_VALUE;
        for (RouteDiagramLayoutEngine.LayoutNode ln : lr.nodes) {
            if (!"route".equals(ln.type)) {
                minY = Math.min(minY, ln.y);
            }
        }
        if (minY <= 0 || minY == Integer.MAX_VALUE) {
            return;
        }
        int shift = minY - 40;
        for (RouteDiagramLayoutEngine.LayoutNode ln : lr.nodes) {
            ln.y -= shift;
            if (ln.connectFromMerge) {
                ln.mergeY -= shift;
            }
        }
        lr.labelY -= shift;
        lr.maxY -= shift;
    }
}
