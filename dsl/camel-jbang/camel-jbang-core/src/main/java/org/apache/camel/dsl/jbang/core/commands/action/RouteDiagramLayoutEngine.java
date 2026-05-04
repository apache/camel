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
package org.apache.camel.dsl.jbang.core.commands.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class RouteDiagramLayoutEngine {

    static final int SCALE = 2;
    static final int NODE_WIDTH = 180 * SCALE;
    static final int NODE_HEIGHT = 32 * SCALE;
    static final int H_GAP = 30 * SCALE;
    static final int V_GAP = 40 * SCALE;
    static final int PADDING = 30 * SCALE;
    static final int SCOPE_BOX_PAD = 14 * SCALE;
    static final int LABEL_OFFSET = 24 * SCALE;

    private static final Set<String> BRANCHING_EIPS = Set.of(
            "choice", "multicast", "doTry", "loadBalance", "recipientList", "circuitBreaker");

    private static final Set<String> BRANCH_CHILD_TYPES = Set.of(
            "when", "otherwise", "doCatch", "doFinally", "onFallback");

    private static final Set<String> STRUCTURAL_TYPES = Set.of(
            "route", "from");

    static class NodeInfo {
        String type;
        String code;
        int level;
    }

    static class RouteInfo {
        String routeId;
        String source;
        List<NodeInfo> nodes = new ArrayList<>();
    }

    static class TreeNode {
        final NodeInfo info;
        TreeNode parent;
        List<TreeNode> children = new ArrayList<>();
        int subtreeWidth;
        LayoutNode layoutNode;

        TreeNode(NodeInfo info) {
            this.info = info;
        }
    }

    static class LayoutNode {
        String label;
        String type;
        int x;
        int y;
        LayoutNode parentNode;
        TreeNode treeNode;
        boolean connectFromMerge;
        int mergeY;
        int mergeCx;
    }

    static class LayoutRoute {
        String routeId;
        String source;
        int labelY;
        int maxX;
        int maxY;
        List<LayoutNode> nodes = new ArrayList<>();
    }

    static TreeNode buildTree(List<NodeInfo> nodes) {
        if (nodes.isEmpty()) {
            return null;
        }
        TreeNode root = new TreeNode(nodes.get(0));
        TreeNode current = root;

        for (int i = 1; i < nodes.size(); i++) {
            NodeInfo ni = nodes.get(i);
            TreeNode tn = new TreeNode(ni);

            if (ni.level > current.info.level) {
                current.children.add(tn);
                tn.parent = current;
            } else if (ni.level == current.info.level) {
                TreeNode parent = current.parent;
                if (parent != null) {
                    parent.children.add(tn);
                    tn.parent = parent;
                } else {
                    root.children.add(tn);
                    tn.parent = root;
                }
            } else {
                TreeNode ancestor = current.parent;
                while (ancestor != null && ancestor.info.level >= ni.level) {
                    ancestor = ancestor.parent;
                }
                if (ancestor != null) {
                    ancestor.children.add(tn);
                    tn.parent = ancestor;
                } else {
                    root.children.add(tn);
                    tn.parent = root;
                }
            }
            current = tn;
        }
        return root;
    }

    static String cleanLabel(String code) {
        if (code == null) {
            return "";
        }
        return code.replaceFirst("^\\.", "");
    }

    LayoutRoute layoutRoute(RouteInfo route, int startY) {
        LayoutRoute lr = new LayoutRoute();
        lr.routeId = route.routeId;
        lr.source = route.source;
        lr.labelY = startY;

        TreeNode tree = buildTree(route.nodes);
        if (tree == null) {
            lr.maxX = PADDING + NODE_WIDTH;
            lr.maxY = startY + LABEL_OFFSET;
            return lr;
        }

        computeSubtreeWidth(tree);
        assignPositions(tree, PADDING, startY + LABEL_OFFSET, tree.subtreeWidth, lr);

        int[] extent = {
                tree.layoutNode.x, tree.layoutNode.y,
                tree.layoutNode.x + NODE_WIDTH, tree.layoutNode.y + NODE_HEIGHT };
        for (TreeNode child : tree.children) {
            expandBoundsForBox(child, extent);
        }

        if (extent[0] < PADDING) {
            int shift = PADDING - extent[0];
            for (LayoutNode ln : lr.nodes) {
                ln.x += shift;
                if (ln.connectFromMerge) {
                    ln.mergeCx += shift;
                }
            }
            extent[2] += shift;
        }

        lr.maxX = extent[2];
        lr.maxY = Math.max(lr.maxY, extent[3]);

        return lr;
    }

    private int computeSubtreeWidth(TreeNode node) {
        if (node.children.isEmpty()) {
            node.subtreeWidth = NODE_WIDTH;
            return node.subtreeWidth;
        }

        if (isBranchingEip(node.info.type)) {
            int totalWidth = 0;
            for (int i = 0; i < node.children.size(); i++) {
                if (i > 0) {
                    totalWidth += H_GAP;
                }
                totalWidth += computeSubtreeWidth(node.children.get(i));
            }
            node.subtreeWidth = Math.max(NODE_WIDTH, totalWidth);
        } else {
            int maxChildWidth = NODE_WIDTH;
            for (TreeNode child : node.children) {
                maxChildWidth = Math.max(maxChildWidth, computeSubtreeWidth(child));
            }
            node.subtreeWidth = maxChildWidth;
        }
        return node.subtreeWidth;
    }

    private void assignPositions(TreeNode node, int x, int y, int parentWidth, LayoutRoute lr) {
        int availableWidth = Math.max(node.subtreeWidth, parentWidth);
        int nodeX = x + (availableWidth - NODE_WIDTH) / 2;

        LayoutNode ln = new LayoutNode();
        ln.label = cleanLabel(node.info.code);
        ln.type = node.info.type;
        ln.x = nodeX;
        ln.y = y;
        ln.treeNode = node;
        node.layoutNode = ln;
        lr.nodes.add(ln);

        if (node.parent != null && node.parent.layoutNode != null) {
            TreeNode parentNode = node.parent;
            if (!isBranchingEip(parentNode.info.type)) {
                int myIndex = parentNode.children.indexOf(node);
                if (myIndex > 0) {
                    TreeNode prevSibling = parentNode.children.get(myIndex - 1);
                    if (hasScope(prevSibling)) {
                        ln.connectFromMerge = true;
                        int[] boxBounds = {
                                prevSibling.layoutNode.x, prevSibling.layoutNode.y,
                                prevSibling.layoutNode.x + NODE_WIDTH,
                                prevSibling.layoutNode.y + NODE_HEIGHT };
                        for (TreeNode c : prevSibling.children) {
                            expandBoundsForBox(c, boxBounds);
                        }
                        ln.mergeY = boxBounds[3] + SCOPE_BOX_PAD;
                        ln.mergeCx = prevSibling.layoutNode.x + NODE_WIDTH / 2;
                        ln.parentNode = prevSibling.layoutNode;
                    } else {
                        ln.parentNode = findLastLayoutNode(prevSibling);
                    }
                } else {
                    ln.parentNode = parentNode.layoutNode;
                }
            } else {
                ln.parentNode = parentNode.layoutNode;
            }
        }

        lr.maxY = Math.max(lr.maxY, y + NODE_HEIGHT);

        if (node.children.isEmpty()) {
            return;
        }

        int childY = y + NODE_HEIGHT + V_GAP;

        if (isBranchingEip(node.info.type)) {
            int childX = x + (availableWidth - node.subtreeWidth) / 2;
            for (TreeNode child : node.children) {
                int adjustedY = childY;
                if (!child.children.isEmpty() && !BRANCH_CHILD_TYPES.contains(child.info.type)) {
                    adjustedY += SCOPE_BOX_PAD;
                }
                assignPositions(child, childX, adjustedY, child.subtreeWidth, lr);
                childX += child.subtreeWidth + H_GAP;
            }
        } else {
            int curY = childY;
            for (int i = 0; i < node.children.size(); i++) {
                TreeNode child = node.children.get(i);
                int adjustedY = hasScope(child) ? curY + SCOPE_BOX_PAD : curY;
                assignPositions(child, x, adjustedY, availableWidth, lr);
                if (hasScope(child)) {
                    int[] cb = {
                            child.layoutNode.x, child.layoutNode.y,
                            child.layoutNode.x + NODE_WIDTH, child.layoutNode.y + NODE_HEIGHT };
                    for (TreeNode c : child.children) {
                        expandBoundsForBox(c, cb);
                    }
                    curY = cb[3] + SCOPE_BOX_PAD + V_GAP;
                } else {
                    curY = findMaxY(child) + V_GAP;
                }
            }
        }
    }

    static LayoutNode findLastLayoutNode(TreeNode node) {
        if (node.children.isEmpty()) {
            return node.layoutNode;
        }
        if (isBranchingEip(node.info.type)) {
            return node.layoutNode;
        }
        return findLastLayoutNode(node.children.get(node.children.size() - 1));
    }

    static int findMaxY(TreeNode node) {
        int maxY = node.layoutNode != null ? node.layoutNode.y + NODE_HEIGHT : 0;
        for (TreeNode child : node.children) {
            maxY = Math.max(maxY, findMaxY(child));
        }
        return maxY;
    }

    static boolean isBranchingEip(String type) {
        return type != null && BRANCHING_EIPS.contains(type);
    }

    static boolean hasScope(TreeNode node) {
        return node.parent != null
                && !node.children.isEmpty()
                && !BRANCH_CHILD_TYPES.contains(node.info.type)
                && !STRUCTURAL_TYPES.contains(node.info.type);
    }

    static void expandBoundsForBox(TreeNode node, int[] bounds) {
        boolean hasOwnBox = hasScope(node);

        if (hasOwnBox) {
            int[] inner = {
                    node.layoutNode.x, node.layoutNode.y,
                    node.layoutNode.x + NODE_WIDTH, node.layoutNode.y + NODE_HEIGHT };
            for (TreeNode child : node.children) {
                expandBoundsForBox(child, inner);
            }
            bounds[0] = Math.min(bounds[0], inner[0] - SCOPE_BOX_PAD);
            bounds[1] = Math.min(bounds[1], inner[1] - SCOPE_BOX_PAD);
            bounds[2] = Math.max(bounds[2], inner[2] + SCOPE_BOX_PAD);
            bounds[3] = Math.max(bounds[3], inner[3] + SCOPE_BOX_PAD);
        } else {
            if (node.layoutNode != null) {
                bounds[0] = Math.min(bounds[0], node.layoutNode.x);
                bounds[1] = Math.min(bounds[1], node.layoutNode.y);
                bounds[2] = Math.max(bounds[2], node.layoutNode.x + NODE_WIDTH);
                bounds[3] = Math.max(bounds[3], node.layoutNode.y + NODE_HEIGHT);
            }
            for (TreeNode child : node.children) {
                expandBoundsForBox(child, bounds);
            }
        }
    }
}
