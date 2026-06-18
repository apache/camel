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
package org.apache.camel.diagram;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies computeSubtreeWidth and assignPositions behaviour through the public layoutRoute() API. The same scenarios
 * are mirrored in the browser tests in integration-test.html.
 *
 * Java constants (default constructor, SCALE=2): nodeWidth = 360 (DEFAULT_BOX_WIDTH * SCALE) hGap = 180 (nodeWidth / 2)
 * V_GAP = 80 (40 * SCALE) PADDING = 60 (30 * SCALE)
 */
class RouteDiagramLayoutEngineTest {

    private static final RouteDiagramLayoutEngine ENGINE = new RouteDiagramLayoutEngine();
    private static final int NODE_W = ENGINE.getNodeWidth();           // 360
    private static final int H_GAP = NODE_W / 2;                      // 180
    private static final int PADDING = RouteDiagramLayoutEngine.PADDING; // 60

    // ─── helpers ─────────────────────────────────────────────────────────────

    private static RouteDiagramLayoutEngine.NodeInfo node(String type, String id, int level) {
        RouteDiagramLayoutEngine.NodeInfo n = new RouteDiagramLayoutEngine.NodeInfo();
        n.type = type;
        n.id = id;
        n.level = level;
        n.code = type;
        return n;
    }

    private static RouteDiagramLayoutEngine.RouteInfo route(RouteDiagramLayoutEngine.NodeInfo... nodes) {
        RouteDiagramLayoutEngine.RouteInfo r = new RouteDiagramLayoutEngine.RouteInfo();
        r.routeId = "test";
        r.nodes.addAll(List.of(nodes));
        return r;
    }

    private static RouteDiagramLayoutEngine.LayoutNode findNode(
            RouteDiagramLayoutEngine.LayoutRoute lr, String id) {
        return lr.nodes.stream()
                .filter(n -> id.equals(n.id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No layout node with id: " + id));
    }

    // ─── computeSubtreeWidth (verified through node positions) ───────────────

    @Test
    void leafNodeSubtreeWidthEqualsNodeWidth() {
        // A single leaf node fills exactly one node-width slot.
        // nodeX = PADDING + (subtreeWidth - NODE_W) / 2; if subtreeWidth == NODE_W, nodeX == PADDING.
        RouteDiagramLayoutEngine.LayoutRoute lr = ENGINE.layoutRoute(
                route(node("log", "l1", 0)), 0);

        RouteDiagramLayoutEngine.LayoutNode l1 = findNode(lr, "l1");
        assertThat(l1.x).as("leaf node must be placed at PADDING (subtreeWidth == nodeWidth)").isEqualTo(PADDING);
    }

    @Test
    void branchingEipSubtreeWidthIsSumOfBranchWidthsPlusGaps() {
        // choice -> [when, otherwise], both leaves.
        // subtreeWidth(choice) = NODE_W + H_GAP + NODE_W = NODE_W*2 + H_GAP
        // when.x  = PADDING  (leftmost child)
        // ow.x    = PADDING + NODE_W + H_GAP
        // gap between children = NODE_W + H_GAP
        RouteDiagramLayoutEngine.LayoutRoute lr = ENGINE.layoutRoute(
                route(node("choice", "ch", 0),
                        node("when", "w1", 1),
                        node("otherwise", "ow", 1)),
                0);

        RouteDiagramLayoutEngine.LayoutNode w1 = findNode(lr, "w1");
        RouteDiagramLayoutEngine.LayoutNode ow = findNode(lr, "ow");
        assertThat(ow.x - w1.x)
                .as("gap between two leaf branches of a branching EIP must equal NODE_W + H_GAP")
                .isEqualTo(NODE_W + H_GAP);
    }

    @Test
    void nonBranchingNodeSubtreeWidthIsMaxOfChildWidths() {
        // route -> [from, to] (linear siblings, both leaves, equal widths).
        // subtreeWidth(route) = max(NODE_W, NODE_W) = NODE_W
        // Both children should be placed at x == PADDING.
        RouteDiagramLayoutEngine.LayoutRoute lr = ENGINE.layoutRoute(
                route(node("route", "r1", 0),
                        node("from", "f1", 1),
                        node("to", "t1", 1)),
                0);

        RouteDiagramLayoutEngine.LayoutNode f1 = findNode(lr, "f1");
        RouteDiagramLayoutEngine.LayoutNode t1 = findNode(lr, "t1");
        assertThat(f1.x).as("first linear child x must equal PADDING").isEqualTo(PADDING);
        assertThat(t1.x).as("second linear child x must equal PADDING").isEqualTo(PADDING);
    }

    // ─── assignPositions ─────────────────────────────────────────────────────

    @Test
    void linearChainEachNodeConnectsToItsVisualPredecessor() {
        // route -> from -> log -> to (flat level-1 siblings processed linearly).
        // f1.parentNode == r1, l1.parentNode == f1, t1.parentNode == l1.
        RouteDiagramLayoutEngine.LayoutRoute lr = ENGINE.layoutRoute(
                route(node("route", "r1", 0),
                        node("from", "f1", 1),
                        node("log", "l1", 1),
                        node("to", "t1", 1)),
                0);

        RouteDiagramLayoutEngine.LayoutNode r1 = findNode(lr, "r1");
        RouteDiagramLayoutEngine.LayoutNode f1 = findNode(lr, "f1");
        RouteDiagramLayoutEngine.LayoutNode l1 = findNode(lr, "l1");
        RouteDiagramLayoutEngine.LayoutNode t1 = findNode(lr, "t1");

        assertThat(r1.parentNode).as("root must have no parent").isNull();
        assertThat(f1.parentNode).as("f1 must connect from r1").isSameAs(r1);
        assertThat(l1.parentNode).as("l1 must connect from f1, not r1").isSameAs(f1);
        assertThat(t1.parentNode).as("t1 must connect from l1, not r1").isSameAs(l1);
    }

    @Test
    void singleChainRouteAssignsStrictlyIncreasingYValues() {
        RouteDiagramLayoutEngine.LayoutRoute lr = ENGINE.layoutRoute(
                route(node("route", "r1", 0),
                        node("from", "f1", 1),
                        node("log", "l1", 1),
                        node("to", "t1", 1)),
                0);

        int yr1 = findNode(lr, "r1").y;
        int yf1 = findNode(lr, "f1").y;
        int yl1 = findNode(lr, "l1").y;
        int yt1 = findNode(lr, "t1").y;

        assertThat(yf1).as("f1.y must be below r1").isGreaterThan(yr1);
        assertThat(yl1).as("l1.y must be below f1").isGreaterThan(yf1);
        assertThat(yt1).as("t1.y must be below l1").isGreaterThan(yl1);
    }

    @Test
    void branchingEipChildrenAreLaidOutSideBySide() {
        // choice -> [when, otherwise]: children must share the same y, different x.
        RouteDiagramLayoutEngine.LayoutRoute lr = ENGINE.layoutRoute(
                route(node("choice", "ch", 0),
                        node("when", "w1", 1),
                        node("otherwise", "ow", 1)),
                0);

        RouteDiagramLayoutEngine.LayoutNode w1 = findNode(lr, "w1");
        RouteDiagramLayoutEngine.LayoutNode ow = findNode(lr, "ow");

        assertThat(w1.x).as("when must be to the left of otherwise").isLessThan(ow.x);
        assertThat(w1.y).as("both branches must start at the same y").isEqualTo(ow.y);
    }

    @Test
    void nextSiblingIsPlacedBelowDeepestDescendantOfPreviousSibling() {
        // route -> choice -> [when -> log_a, otherwise -> log_b], log_after
        // log_after must be below BOTH log_a and log_b.
        RouteDiagramLayoutEngine.LayoutRoute lr = ENGINE.layoutRoute(
                route(node("route", "r1", 0),
                        node("choice", "ch", 1),
                        node("when", "wh", 2),
                        node("log", "la", 3),
                        node("otherwise", "ow", 2),
                        node("log", "lb", 3),
                        node("log", "lafter", 1)),
                0);

        RouteDiagramLayoutEngine.LayoutNode la = findNode(lr, "la");
        RouteDiagramLayoutEngine.LayoutNode lb = findNode(lr, "lb");
        RouteDiagramLayoutEngine.LayoutNode lafter = findNode(lr, "lafter");

        assertThat(lafter.y)
                .as("lafter must be below la")
                .isGreaterThan(la.y + la.height);
        assertThat(lafter.y)
                .as("lafter must be below lb")
                .isGreaterThan(lb.y + lb.height);
    }

    @Test
    void linearChainAfterBranchingEipConnectsFromBranchingEip() {
        // route -> choice -> [when, otherwise], log_after
        // log_after.parentNode must be the choice node, not when or otherwise.
        RouteDiagramLayoutEngine.LayoutRoute lr = ENGINE.layoutRoute(
                route(node("route", "r1", 0),
                        node("choice", "ch", 1),
                        node("when", "wh", 2),
                        node("otherwise", "ow", 2),
                        node("log", "lafter", 1)),
                0);

        RouteDiagramLayoutEngine.LayoutNode ch = findNode(lr, "ch");
        RouteDiagramLayoutEngine.LayoutNode lafter = findNode(lr, "lafter");

        assertThat(lafter.parentNode)
                .as("node after a branching EIP must connect from the branching EIP itself")
                .isSameAs(ch);
    }

    @Test
    void layoutRouteMaxYEqualsDeepestNodeBottom() {
        // route -> from -> log: maxY must equal log.y + log.height.
        RouteDiagramLayoutEngine.LayoutRoute lr = ENGINE.layoutRoute(
                route(node("route", "r1", 0),
                        node("from", "f1", 1),
                        node("log", "l1", 1)),
                0);

        RouteDiagramLayoutEngine.LayoutNode l1 = findNode(lr, "l1");
        assertThat(lr.maxY)
                .as("maxY must equal the bottom of the deepest node")
                .isEqualTo(l1.y + l1.height);
    }
}
