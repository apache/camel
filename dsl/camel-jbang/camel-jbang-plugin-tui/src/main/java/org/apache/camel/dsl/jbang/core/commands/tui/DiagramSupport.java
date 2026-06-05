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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.Clear;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
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
    private String cachedPid;
    private volatile boolean preloading;

    // History diagram state
    private boolean historyMode;
    private List<String> historyNodeOrder = Collections.emptyList();
    private int historyStepIndex;
    private boolean historyFailed;

    // History drill-down state (topology → route navigation)
    private boolean historyTopologyMode;
    private String historyDrillDownRouteId;
    private final Deque<String> historyNavigationStack = new ArrayDeque<>();
    private Map<String, String> historyNodeRouteMap = Collections.emptyMap();
    private Set<String> historyRouteIds = Collections.emptySet();

    boolean isShowDiagram() {
        return showDiagram;
    }

    void setShowDiagram(boolean show) {
        this.showDiagram = show;
    }

    boolean isLoading() {
        return loading.get();
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
        return topologyLayout != null || !routeLayouts.isEmpty();
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

    void scrollLeft() {
        scrollX = Math.max(0, scrollX - 1);
    }

    void scrollRight() {
        scrollX++;
    }

    void scrollHome() {
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
        invalidateCache();
    }

    void invalidateCache() {
        cachedPid = null;
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
        historyMode = false;
        historyNodeOrder = Collections.emptyList();
        historyStepIndex = 0;
        historyFailed = false;
        historyTopologyMode = false;
        historyDrillDownRouteId = null;
        historyNavigationStack.clear();
        historyNodeRouteMap = Collections.emptyMap();
        historyRouteIds = Collections.emptySet();
    }

    boolean hasCachedData(String pid) {
        return pid != null && pid.equals(cachedPid) && hasDiagramData();
    }

    void showCached() {
        showDiagram = true;
        scrollY = 0;
        scrollX = 0;
    }

    void applyPendingSelection() {
        if (pendingSelectionRouteId != null && !nodeBoxes.isEmpty()) {
            int newIdx = -1;
            for (int i = 0; i < nodeBoxes.size(); i++) {
                if (pendingSelectionRouteId.equals(nodeBoxes.get(i).routeId())) {
                    newIdx = i;
                    break;
                }
            }
            if (newIdx >= 0) {
                selectedNodeIndex = newIdx;
            }
        }
        pendingSelectionRouteId = null;
    }

    void preload(MonitorContext ctx, String pid) {
        if (hasCachedData(pid)) {
            return;
        }
        if (!beginLoad()) {
            return;
        }
        preloading = true;
        if (ctx.runner != null) {
            ctx.runner.scheduler().execute(() -> {
                try {
                    setTopologyMode(true);
                    loadAllDiagramsInBackground(ctx, pid, false, false);
                } finally {
                    endLoad();
                }
            });
        } else {
            endLoad();
        }
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

        renderTreePreview(frame, hChunks.get(0));
    }

    private void renderTreePreview(Frame frame, Rect area) {
        renderTreePreview(frame, area, getSelectedRouteId());
    }

    private void renderTreePreview(Frame frame, Rect area, String routeId) {
        if (routeId == null) {
            return;
        }
        RouteDiagramLayoutEngine.LayoutRoute routeLayout = routeLayouts.get(routeId);
        if (routeLayout == null || routeLayout.nodes.isEmpty()) {
            return;
        }

        int previewW = Math.min(38, area.width() / 3);
        int previewH = Math.min(10, area.height() / 2);
        if (previewW < 10 || previewH < 4 || area.width() < 40 || area.height() < 12) {
            return;
        }

        int previewX = area.x() + area.width() - previewW - 1;
        int previewY = area.y() + area.height() - previewH - 1;
        Rect previewRect = new Rect(previewX, previewY, previewW, previewH);

        frame.renderWidget(Clear.INSTANCE, previewRect);

        Block previewBlock = Block.builder()
                .borderType(BorderType.ROUNDED)
                .title(Title.from(Line.from(Span.styled(" Route ", Style.EMPTY.dim()))))
                .build();
        frame.renderWidget(previewBlock, previewRect);

        Rect innerRect = previewBlock.inner(previewRect);
        RouteDiagramLayoutEngine.TreeNode selectedTreeNode = null;
        var selectedBox = getSelectedEipNodeBox();
        if (selectedBox != null && selectedBox.layoutNode() != null) {
            selectedTreeNode = selectedBox.layoutNode().treeNode;
        }
        List<Line> treeLines = RouteTreePreview.buildTree(
                routeLayout, innerRect.height(), innerRect.width(), selectedTreeNode);
        frame.renderWidget(Paragraph.builder().text(Text.from(treeLines)).build(), innerRect);
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

        renderMinimap(frame, hChunks.get(0), currentRouteId);
        renderTreePreview(frame, hChunks.get(0), currentRouteId);
    }

    private void renderMinimap(Frame frame, Rect area, String currentRouteId) {
        if (topologyLayout == null || topologyLayout.nodes.size() < 2) {
            return;
        }
        int mapW = Math.min(22, area.width() / 3);
        int mapH = Math.min(8, area.height() / 3);
        if (mapW < 6 || mapH < 3 || area.width() < 40 || area.height() < 12) {
            return;
        }

        int mapX = area.x() + area.width() - mapW - 1;
        int mapY = area.y();
        Rect mapRect = new Rect(mapX, mapY, mapW, mapH);

        frame.renderWidget(Clear.INSTANCE, mapRect);

        Block mapBlock = Block.builder()
                .borderType(BorderType.ROUNDED)
                .title(Title.from(Line.from(Span.styled(" Map ", Style.EMPTY.dim()))))
                .build();
        frame.renderWidget(mapBlock, mapRect);

        Rect innerRect = mapBlock.inner(mapRect);
        var minimap = new org.apache.camel.dsl.jbang.core.commands.tui.diagram.TopologyMinimapWidget(
                topologyLayout, currentRouteId);
        frame.renderWidget(minimap, innerRect);
    }

    // ---- Async loading ----

    boolean beginLoad() {
        return loading.compareAndSet(false, true);
    }

    void endLoad() {
        loading.set(false);
    }

    void setLoadingPlaceholder() {
        showDiagram = true;
        scrollY = 0;
        scrollX = 0;
    }

    void loadHighlightedNativeDiagramInBackground(
            MonitorContext ctx, String pid,
            String[] messageHistory, boolean failed, int initialStepIndex) {
        // Single IPC call: topology + route structures
        JsonObject jo = requestRouteTopology(ctx, pid, false, true);
        if (jo == null) {
            close();
            return;
        }

        // Parse topology layout
        TopologyLayoutResult topoResult = null;
        int nodeW = 0;
        List<TopologyLayoutNode> topoNodes = Collections.emptyList();
        List<TopologyLayoutEdge> topoEdges = Collections.emptyList();
        List<TopologyNodeInfo> tNodes = TopologyHelper.parseNodes(jo);
        List<TopologyEdgeInfo> tEdges = TopologyHelper.parseEdges(jo);
        if (!tNodes.isEmpty()) {
            TopologyLayoutEngine topoEngine = new TopologyLayoutEngine();
            topoResult = topoEngine.layout(tNodes, tEdges);
            normalizeTopologyLayoutY(topoResult);
            nodeW = topoEngine.getNodeWidth();
            topoNodes = topoResult.nodes;
            topoEdges = topoResult.edges;
        }

        // Parse and layout all routes
        List<RouteDiagramLayoutEngine.RouteInfo> routes = RouteDiagramHelper.parseRoutes(jo);
        if (routes.isEmpty()) {
            close();
            return;
        }

        Set<String> externalUris = parseExternalUris(jo);
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

        Map<String, RouteDiagramLayoutEngine.LayoutRoute> routeMap = new LinkedHashMap<>();
        for (RouteDiagramLayoutEngine.RouteInfo r : routes) {
            RouteDiagramLayoutEngine.LayoutRoute lr = engine.layoutRoute(r, 0);
            normalizeRouteLayoutY(lr);
            routeMap.put(r.routeId, lr);
        }

        // Parse message history: build nodeId visit order and nodeId→routeId mapping
        RouteDiagramHelper.HighlightStyle hlStyle = failed
                ? RouteDiagramHelper.HighlightStyle.FAIL
                : RouteDiagramHelper.HighlightStyle.SUCCESS;
        RouteDiagramHelper.HighlightInfo highlightInfo
                = RouteDiagramHelper.parseMessageHistory(messageHistory, hlStyle);

        List<String> nodeOrder = new ArrayList<>();
        Map<String, String> nodeRouteMap = new LinkedHashMap<>();
        Set<String> visitedRouteIds = new LinkedHashSet<>();
        Set<String> seenNodes = new LinkedHashSet<>();
        for (String h : messageHistory) {
            int bracket = h.indexOf('[');
            int end = h.indexOf(']');
            if (bracket >= 0 && end > bracket) {
                String routeId = h.substring(0, bracket);
                String nodeId = h.substring(bracket + 1, end);
                if (!nodeId.isEmpty() && seenNodes.add(nodeId)) {
                    nodeOrder.add(nodeId);
                    nodeRouteMap.put(nodeId, routeId);
                }
                if (!routeId.isEmpty()) {
                    visitedRouteIds.add(routeId);
                }
            }
        }

        // Add parent scope nodes to highlight info for routes with highlighted children
        Set<String> allNodeIds = new LinkedHashSet<>(highlightInfo.getNodeIds());
        for (RouteDiagramLayoutEngine.RouteInfo ri : routes) {
            boolean routeHasHighlight = ri.nodes.stream().anyMatch(n -> n.id != null && allNodeIds.contains(n.id));
            if (routeHasHighlight) {
                addParentNodes(ri.nodes, allNodeIds);
            }
        }

        // Map initialStepIndex (index into messageHistory) to nodeOrder index
        int resolvedStep = nodeOrder.size() - 1;
        if (initialStepIndex >= 0 && initialStepIndex < messageHistory.length) {
            String h = messageHistory[initialStepIndex];
            int br = h.indexOf('[');
            int en = h.indexOf(']');
            if (br >= 0 && en > br) {
                String nid = h.substring(br + 1, en);
                int idx = nodeOrder.indexOf(nid);
                if (idx >= 0) {
                    resolvedStep = idx;
                }
            }
        }
        final int clampedStep = resolvedStep;

        // Compute topology nodeBoxes for selection
        boolean isFailed = failed;
        TopologyLayoutResult finalTopoResult = topoResult;
        int finalNodeW = nodeW;
        List<TopologyLayoutNode> finalTopoNodes = topoNodes;
        List<TopologyLayoutEdge> finalTopoEdges = topoEdges;

        if (ctx.runner == null) {
            return;
        }
        ctx.runner.runOnRenderThread(() -> {
            historyMode = true;
            historyNodeOrder = nodeOrder;
            historyStepIndex = clampedStep;
            historyFailed = isFailed;
            historyNodeRouteMap = nodeRouteMap;
            historyRouteIds = visitedRouteIds;
            historyTopologyMode = true;
            historyDrillDownRouteId = null;
            historyNavigationStack.clear();

            // Store topology data
            topologyLayout = finalTopoResult;
            topologyNodeWidth = finalNodeW;
            topologyNodes = finalTopoNodes;
            topologyEdges = finalTopoEdges;
            routeLayouts = routeMap;

            // Initialize topology node selection
            if (finalTopoResult != null) {
                List<TopologyAsciiRenderer.NodeBox> boxes
                        = computeNodeBoxes(finalTopoResult, finalNodeW, false);
                nodeBoxes = boxes;
                // Select the route box matching the current step
                String stepRouteId = null;
                if (clampedStep >= 0 && clampedStep < nodeOrder.size()) {
                    stepRouteId = nodeRouteMap.get(nodeOrder.get(clampedStep));
                }
                int selIdx = -1;
                if (stepRouteId != null) {
                    for (int i = 0; i < boxes.size(); i++) {
                        if (stepRouteId.equals(boxes.get(i).routeId())) {
                            selIdx = i;
                            break;
                        }
                    }
                }
                if (selIdx < 0) {
                    for (int i = 0; i < boxes.size(); i++) {
                        if (visitedRouteIds.contains(boxes.get(i).routeId())) {
                            selIdx = i;
                            break;
                        }
                    }
                }
                selectedNodeIndex = selIdx >= 0 ? selIdx : (boxes.isEmpty() ? -1 : 0);
            }

            scrollY = 0;
            scrollX = 0;
            showDiagram = true;
        });
    }

    boolean isHistoryMode() {
        return historyMode;
    }

    boolean hasHistoryData() {
        return !historyNodeOrder.isEmpty();
    }

    int getHistoryStepIndex() {
        return historyStepIndex;
    }

    int getHistoryStepCount() {
        return historyNodeOrder.size();
    }

    void historyNavigateDown() {
        if (historyStepIndex < historyNodeOrder.size() - 1) {
            historyStepIndex++;
            handleHistoryRouteTransition();
        }
    }

    void historyNavigateUp() {
        if (historyStepIndex > 0) {
            historyStepIndex--;
            handleHistoryRouteTransition();
        }
    }

    private void handleHistoryRouteTransition() {
        if (historyTopologyMode || historyDrillDownRouteId == null) {
            return;
        }
        String nodeId = historyNodeOrder.get(historyStepIndex);
        String nodeRoute = historyNodeRouteMap.get(nodeId);
        if (nodeRoute != null && !nodeRoute.equals(historyDrillDownRouteId)) {
            if (historyNavigationStack.contains(nodeRoute)) {
                while (!historyNavigationStack.isEmpty() && !nodeRoute.equals(historyNavigationStack.peek())) {
                    historyNavigationStack.pop();
                }
                historyNavigationStack.pop();
            } else {
                historyNavigationStack.push(historyDrillDownRouteId);
            }
            historyDrillDownRouteId = nodeRoute;
            scrollY = 0;
            scrollX = 0;
        }
    }

    boolean isHistoryTopologyMode() {
        return historyTopologyMode;
    }

    String getHistoryDrillDownRouteId() {
        return historyDrillDownRouteId;
    }

    Deque<String> getHistoryNavigationStack() {
        return historyNavigationStack;
    }

    void historyEnterDrillDown() {
        String selectedRoute = getSelectedRouteId();
        if (selectedRoute == null && !historyRouteIds.isEmpty()) {
            selectedRoute = historyRouteIds.iterator().next();
        }
        if (selectedRoute == null) {
            return;
        }
        historyTopologyMode = false;
        historyDrillDownRouteId = selectedRoute;
        historyNavigationStack.clear();

        // Set step index to the first node in this route
        for (int i = 0; i < historyNodeOrder.size(); i++) {
            String nRoute = historyNodeRouteMap.get(historyNodeOrder.get(i));
            if (selectedRoute.equals(nRoute)) {
                historyStepIndex = i;
                break;
            }
        }
        scrollY = 0;
        scrollX = 0;
    }

    void historyReturnToTopology() {
        historyTopologyMode = true;
        historyDrillDownRouteId = null;
        historyNavigationStack.clear();
        scrollY = 0;
        scrollX = 0;
        // Restore topology selection to highlight the current route
        if (historyStepIndex >= 0 && historyStepIndex < historyNodeOrder.size()) {
            String nodeId = historyNodeOrder.get(historyStepIndex);
            String nodeRoute = historyNodeRouteMap.get(nodeId);
            if (nodeRoute != null) {
                for (int i = 0; i < nodeBoxes.size(); i++) {
                    if (nodeRoute.equals(nodeBoxes.get(i).routeId())) {
                        selectedNodeIndex = i;
                        break;
                    }
                }
            }
        }
    }

    void historyGoBack() {
        if (!historyNavigationStack.isEmpty()) {
            historyDrillDownRouteId = historyNavigationStack.pop();
            scrollY = 0;
            scrollX = 0;
        } else {
            historyReturnToTopology();
        }
    }

    void renderHistoryTopologyDiagram(Frame frame, Rect area, Line title) {
        if (topologyLayout == null) {
            return;
        }

        Block block = Block.builder()
                .borderType(BorderType.ROUNDED)
                .title(Title.from(title))
                .build();
        frame.renderWidget(block, area);

        Rect inner = block.inner(area);

        var widget = new org.apache.camel.dsl.jbang.core.commands.tui.diagram.TopologyDiagramWidget(
                topologyLayout, topologyNodeWidth, selectedNodeIndex, scrollX, scrollY,
                false, showDescription, historyRouteIds, historyFailed);

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

        var finalWidget = new org.apache.camel.dsl.jbang.core.commands.tui.diagram.TopologyDiagramWidget(
                topologyLayout, topologyNodeWidth, selectedNodeIndex, scrollX, scrollY,
                false, showDescription, historyRouteIds, historyFailed);

        List<Rect> vChunks = Layout.vertical()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(inner);

        List<Rect> hChunks = Layout.horizontal()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(vChunks.get(0));

        frame.renderWidget(finalWidget, hChunks.get(0));

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

    void renderHistoryRouteDiagram(Frame frame, Rect area, Line title, String routeId) {
        RouteDiagramLayoutEngine.LayoutRoute routeLayout = routeLayouts.get(routeId);
        if (routeLayout == null) {
            return;
        }

        Block block = Block.builder()
                .borderType(BorderType.ROUNDED)
                .title(Title.from(title))
                .build();
        frame.renderWidget(block, area);

        Rect inner = block.inner(area);
        int nw = RouteDiagramLayoutEngine.DEFAULT_BOX_WIDTH * RouteDiagramLayoutEngine.SCALE;

        // Build progressive highlight set filtered to this route
        Set<String> activeHighlight = new LinkedHashSet<>();
        String currentNodeId = historyStepIndex >= 0 && historyStepIndex < historyNodeOrder.size()
                ? historyNodeOrder.get(historyStepIndex) : null;
        for (int i = 0; i <= historyStepIndex && i < historyNodeOrder.size(); i++) {
            String nid = historyNodeOrder.get(i);
            String nRoute = historyNodeRouteMap.get(nid);
            if (routeId.equals(nRoute)) {
                activeHighlight.add(nid);
            }
        }
        // Add parent/scope nodes
        Set<String> parentIds = new LinkedHashSet<>();
        for (RouteDiagramLayoutEngine.LayoutNode ln : routeLayout.nodes) {
            if (ln.id != null && activeHighlight.contains(ln.id) && ln.parentNode != null
                    && ln.parentNode.id != null && !"route".equals(ln.parentNode.type)) {
                parentIds.add(ln.parentNode.id);
            }
        }
        activeHighlight.addAll(parentIds);

        // Find selected EIP node index within this route
        int selIdx = -1;
        if (currentNodeId != null && routeId.equals(historyNodeRouteMap.get(currentNodeId))) {
            int idx = 0;
            for (RouteDiagramLayoutEngine.LayoutNode ln : routeLayout.nodes) {
                if (!"route".equals(ln.type)) {
                    if (ln.id != null && ln.id.equals(currentNodeId)) {
                        selIdx = idx;
                        break;
                    }
                    idx++;
                }
            }
        }

        Map<String, String> linkable = computeLinkableEndpoints(routeId);
        Map<String, String> routeDescs = showDescription ? computeRouteDescriptions() : Collections.emptyMap();

        var widget = new org.apache.camel.dsl.jbang.core.commands.tui.diagram.RouteDiagramWidget(
                routeLayout, nw, selIdx, scrollX, scrollY, false, linkable,
                showDescription, routeDescs, activeHighlight, historyFailed);

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

        // Auto-scroll to keep selected node visible
        if (selIdx >= 0 && currentNodeId != null) {
            for (RouteDiagramLayoutEngine.LayoutNode ln : routeLayout.nodes) {
                if (ln.id != null && ln.id.equals(currentNodeId)) {
                    int nodeRow = ln.y / 20;
                    if (nodeRow < scrollY) {
                        scrollY = Math.max(0, nodeRow - 2);
                    } else if (nodeRow >= scrollY + visibleLines) {
                        scrollY = Math.min(maxVScroll, nodeRow - visibleLines + 4);
                    }
                    break;
                }
            }
        }

        var finalWidget = new org.apache.camel.dsl.jbang.core.commands.tui.diagram.RouteDiagramWidget(
                routeLayout, nw, selIdx, scrollX, scrollY, false, linkable,
                showDescription, routeDescs, activeHighlight, historyFailed);

        List<Rect> vChunks = Layout.vertical()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(inner);

        List<Rect> hChunks = Layout.horizontal()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(vChunks.get(0));

        frame.renderWidget(finalWidget, hChunks.get(0));

        eipNodeBoxes = new ArrayList<>(finalWidget.getNodeBoxes());

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

        renderMinimap(frame, hChunks.get(0), routeId);
        renderTreePreview(frame, hChunks.get(0), routeId);
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
            if (!showDiagram && !preloading) {
                return;
            }
            preloading = false;
            cachedPid = pid;
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
        });
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
