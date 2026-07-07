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

import java.util.List;

import dev.tamboui.text.Line;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutNode;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutRoute;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.NodeInfo;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.TreeNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteTreePreviewTest {

    @Test
    void testSmallTreeFitsWithoutScrolling() {
        LayoutRoute layout = buildContentBasedRouterLayout();
        TreeNode root = layout.nodes.get(0).treeNode;

        // with enough lines, all nodes are shown
        List<Line> lines = RouteTreePreview.buildTree(layout, 20, 40, null);
        assertTrue(lines.size() > 5, "Tree should have multiple lines");
    }

    @Test
    void testTreeScrollsToSelectedNode() {
        LayoutRoute layout = buildContentBasedRouterLayout();
        TreeNode root = layout.nodes.get(0).treeNode;

        // find the last node deep in the tree (last child of "otherwise")
        TreeNode choice = root.children.get(2); // choice
        TreeNode otherwise = choice.children.get(2); // otherwise
        TreeNode lastLog = otherwise.children.get(1); // log (last node)

        // build full tree to know total size
        List<Line> fullTree = RouteTreePreview.buildTree(layout, 100, 40, null);
        int totalLines = fullTree.size();

        // with a small window, the selected node at the bottom must be visible
        int maxLines = 6;
        assertTrue(totalLines > maxLines, "Tree must be taller than window for this test");

        List<Line> windowedTree = RouteTreePreview.buildTree(layout, maxLines, 40, lastLog);
        assertEquals(maxLines, windowedTree.size());

        // the selected node's line should be present in the windowed output
        String lastLineText = lineToPlainText(windowedTree);
        assertTrue(lastLineText.contains("log"), "Selected 'log' node should be visible in the scrolled tree");
    }

    @Test
    void testTreeScrollsToSelectedNodeInMiddle() {
        LayoutRoute layout = buildContentBasedRouterLayout();
        TreeNode root = layout.nodes.get(0).treeNode;

        // select a node in the middle of the tree
        TreeNode choice = root.children.get(2); // choice
        TreeNode when2 = choice.children.get(1); // second when

        int maxLines = 6;
        List<Line> windowedTree = RouteTreePreview.buildTree(layout, maxLines, 40, when2);
        assertEquals(maxLines, windowedTree.size());

        String allText = lineToPlainText(windowedTree);
        assertTrue(allText.contains("when"), "Selected 'when' node should be visible");
    }

    @Test
    void testNoSelectedNodeShowsFromTop() {
        LayoutRoute layout = buildContentBasedRouterLayout();

        int maxLines = 6;
        List<Line> lines = RouteTreePreview.buildTree(layout, maxLines, 40, null);
        assertEquals(maxLines, lines.size());

        // first line should be the root (from)
        String firstLine = lines.get(0).rawContent();
        assertTrue(firstLine.contains("from"), "Without selection, tree should start from the root");
    }

    /**
     * Builds a layout matching the content-based-router example: from(timer) -> setBody -> convertBodyTo -> choice when
     * -> setHeader -> log when -> setHeader -> log otherwise -> setHeader -> log
     */
    private LayoutRoute buildContentBasedRouterLayout() {
        LayoutRoute layout = new LayoutRoute();
        layout.routeId = "sensor";

        // build tree structure
        TreeNode root = node("from", "timer:sensor");
        TreeNode setBody = node("setBody", "${random(0,40)}");
        TreeNode convertBody = node("convertBodyTo", "int");
        TreeNode choice = node("choice", null);

        TreeNode when1 = node("when", "${body} >= 30");
        TreeNode setHeader1 = node("setHeader", "level=hot");
        TreeNode log1 = node("log", "Hot alert");

        TreeNode when2 = node("when", "${body} >= 15");
        TreeNode setHeader2 = node("setHeader", "level=normal");
        TreeNode log2 = node("log", "Normal");

        TreeNode otherwise = node("otherwise", null);
        TreeNode setHeader3 = node("setHeader", "level=cold");
        TreeNode log3 = node("log", "Cold alert");

        // wire parent-child relationships
        addChild(root, setBody);
        addChild(root, convertBody);
        addChild(root, choice);
        addChild(choice, when1);
        addChild(when1, setHeader1);
        addChild(when1, log1);
        addChild(choice, when2);
        addChild(when2, setHeader2);
        addChild(when2, log2);
        addChild(choice, otherwise);
        addChild(otherwise, setHeader3);
        addChild(otherwise, log3);

        // create layout nodes and link to tree nodes
        addLayoutNode(layout, root);
        addLayoutNode(layout, setBody);
        addLayoutNode(layout, convertBody);
        addLayoutNode(layout, choice);
        addLayoutNode(layout, when1);
        addLayoutNode(layout, setHeader1);
        addLayoutNode(layout, log1);
        addLayoutNode(layout, when2);
        addLayoutNode(layout, setHeader2);
        addLayoutNode(layout, log2);
        addLayoutNode(layout, otherwise);
        addLayoutNode(layout, setHeader3);
        addLayoutNode(layout, log3);

        return layout;
    }

    private TreeNode node(String type, String code) {
        NodeInfo info = new NodeInfo();
        info.type = type;
        info.code = code;
        return new TreeNode(info);
    }

    private void addChild(TreeNode parent, TreeNode child) {
        child.parent = parent;
        parent.children.add(child);
    }

    private void addLayoutNode(LayoutRoute layout, TreeNode treeNode) {
        LayoutNode ln = new LayoutNode();
        ln.type = treeNode.info.type;
        ln.treeNode = treeNode;
        treeNode.layoutNode = ln;
        layout.nodes.add(ln);
    }

    private String lineToPlainText(List<Line> lines) {
        StringBuilder sb = new StringBuilder();
        for (Line line : lines) {
            sb.append(line.rawContent()).append("\n");
        }
        return sb.toString();
    }
}
