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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutNode;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutRoute;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.NodeInfo;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.NodeLabelMode;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.RouteInfo;
import org.apache.camel.dsl.jbang.core.commands.tui.diagram.RouteDensity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RouteDiagramZoomTest {

    private static NodeInfo node(String type, String id, int level) {
        NodeInfo n = new NodeInfo();
        n.type = type;
        n.id = id;
        n.level = level;
        n.code = type + "[" + id + "]";
        return n;
    }

    private static RouteInfo route() {
        RouteInfo r = new RouteInfo();
        r.routeId = "orders";
        r.nodes.add(node("route", "orders", 0));
        r.nodes.add(node("from", "from1", 1));
        r.nodes.add(node("choice", "choice1", 2));
        r.nodes.add(node("when", "when1", 3));
        r.nodes.add(node("to", "to1", 4));
        r.nodes.add(node("log", "log1", 4));
        r.nodes.add(node("otherwise", "otherwise1", 3));
        r.nodes.add(node("to", "to2", 4));
        r.nodes.add(node("split", "split1", 2));
        r.nodes.add(node("to", "to3", 3));
        r.nodes.add(node("to", "to4", 2));
        return r;
    }

    private static LayoutRoute layout(RouteDiagramZoom zoom, int visibleRows) {
        return zoom.layoutRoutes(List.of(route()), NodeLabelMode.CODE, false, List.of(), new HashMap<>(), visibleRows)
                .get("orders");
    }

    private static LayoutNode find(LayoutRoute lr, String id) {
        return lr.nodes.stream().filter(n -> id.equals(n.id)).findFirst().orElseThrow();
    }

    private static List<String> ids(LayoutRoute lr) {
        return lr.nodes.stream().map(n -> n.id).toList();
    }

    @Test
    void spaceOnAStepFoldsTheBlockItIsIn() {
        RouteDiagramZoom zoom = new RouteDiagramZoom();
        LayoutRoute lr = layout(zoom, 0);

        assertThat(zoom.toggleFold("orders", find(lr, "log1"))).isEqualTo("when1");
        lr = layout(zoom, 0);
        assertThat(ids(lr)).contains("when1", "otherwise1", "to2").doesNotContain("to1", "log1");
        assertThat(zoom.foldedStepsOf(lr)).isEqualTo(Map.of("when1", 2));

        // the choice folds its branches, and the branch folded in it stays folded when the choice opens again
        assertThat(zoom.toggleFold("orders", find(lr, "choice1"))).isEqualTo("choice1");
        lr = layout(zoom, 0);
        assertThat(ids(lr)).contains("choice1", "split1", "to4").doesNotContain("when1", "to2");
        assertThat(zoom.foldedStepsOf(lr)).isEqualTo(Map.of("choice1", 5));
        zoom.toggleFold("orders", find(lr, "choice1"));
        assertThat(zoom.foldedStepsOf(layout(zoom, 0))).isEqualTo(Map.of("when1", 2));
    }

    @Test
    void aStepOutsideAnyBlockHasNothingToFold() {
        RouteDiagramZoom zoom = new RouteDiagramZoom();
        LayoutRoute lr = layout(zoom, 0);
        assertThat(zoom.toggleFold("orders", find(lr, "to4"))).isNull();
        assertThat(zoom.toggleFold("orders", find(lr, "from1"))).isNull();
    }

    @Test
    void foldAllFoldsTheOutermostBlocksThenUnfoldsAll() {
        RouteDiagramZoom zoom = new RouteDiagramZoom();
        zoom.toggleFoldAll("orders", layout(zoom, 0));
        LayoutRoute lr = layout(zoom, 0);
        assertThat(zoom.foldedStepsOf(lr)).isEqualTo(Map.of("choice1", 5, "split1", 1));

        zoom.toggleFoldAll("orders", lr);
        assertThat(zoom.foldedStepsOf(layout(zoom, 0))).isEmpty();
    }

    @Test
    void zoomOutAndFit() {
        RouteDiagramZoom zoom = new RouteDiagramZoom();
        int detailed = RouteDensity.rows(layout(zoom, 0));
        zoom.zoomOut();
        LayoutRoute compact = layout(zoom, 0);
        assertThat(zoom.densityOf(compact)).isEqualTo(RouteDensity.COMPACT);
        assertThat(RouteDensity.rows(compact)).isLessThan(detailed);
        zoom.zoomOut();
        zoom.zoomOut();
        assertThat(zoom.density()).isEqualTo(RouteDensity.BARE);
        int bare = RouteDensity.rows(layout(zoom, 0));

        // fit: the most detail that fits the rows
        zoom.toggleFit();
        assertThat(zoom.densityOf(layout(zoom, detailed + 5))).isEqualTo(RouteDensity.DETAILED);
        assertThat(zoom.densityOf(layout(zoom, RouteDensity.rows(compact)))).isEqualTo(RouteDensity.COMPACT);
        LayoutRoute fitted = layout(zoom, bare);
        assertThat(zoom.densityOf(fitted)).isEqualTo(RouteDensity.BARE);
        // the route starts at the top, as without fit
        assertThat(find(fitted, "from1").y).isEqualTo(40);
        assertThat(zoom.label(layout(zoom, bare))).isEqualTo("fit bare");
        // a zoom key leaves fit
        zoom.zoomIn();
        assertThat(zoom.isFit()).isFalse();
        assertThat(zoom.density()).isEqualTo(RouteDensity.COMPACT);
    }

    @Test
    void theSelectedStepIsSelectedAgainInTheNewLayout() {
        RouteDiagramZoom zoom = new RouteDiagramZoom();
        LayoutRoute before = layout(zoom, 0);
        zoom.anchor(before, "split1", 7);
        // not the layout it was anchored on yet
        assertThat(zoom.restore(before)).isNull();

        zoom.zoomOut();
        LayoutRoute after = layout(zoom, 0);
        RouteDiagramZoom.Restore restore = zoom.restore(after);
        assertThat(restore).isNotNull();
        // the boxes: from, choice, when, to1, log1, otherwise, to2, split
        assertThat(restore.index()).isEqualTo(7);
        assertThat(restore.row()).isEqualTo(find(after, "split1").y / RouteDensity.ROW);
        assertThat(restore.screenRow()).isEqualTo(7);
        // once
        assertThat(zoom.restore(after)).isNull();
    }

    @Test
    void theDensityOrFitIsKeptAsASetting() {
        RouteDiagramZoom zoom = new RouteDiagramZoom();
        assertThat(zoom.setting()).isNull();
        zoom.zoomOut();
        assertThat(zoom.setting()).isEqualTo("compact");
        zoom.toggleFit();
        assertThat(zoom.setting()).isEqualTo("fit");

        RouteDiagramZoom again = new RouteDiagramZoom();
        again.setting("bare");
        assertThat(again.density()).isEqualTo(RouteDensity.BARE);
        again.setting("fit");
        assertThat(again.isFit()).isTrue();
        again.setting("nonsense");
        assertThat(again.density()).isEqualTo(RouteDensity.DETAILED);
        assertThat(again.isFit()).isFalse();
    }
}
