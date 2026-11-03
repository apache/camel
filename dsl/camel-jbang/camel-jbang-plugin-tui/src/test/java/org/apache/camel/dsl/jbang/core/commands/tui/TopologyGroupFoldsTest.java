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
import java.util.Map;
import java.util.Set;

import org.apache.camel.diagram.TopologyLayoutEngine.TopologyEdgeInfo;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyNodeInfo;
import org.apache.camel.dsl.jbang.core.commands.tui.diagram.TopologyDiagramWidget.NodeLine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A group of routes folded in the topology is one box: the links to its routes go to the box, those between them are
 * left out.
 */
class TopologyGroupFoldsTest {

    private static TopologyNodeInfo route(String id, long total, long failed) {
        TopologyNodeInfo n = new TopologyNodeInfo();
        n.routeId = id;
        n.from = "direct:" + id;
        n.nodeType = "route";
        n.exchangesTotal = total;
        n.exchangesFailed = failed;
        return n;
    }

    private static TopologyEdgeInfo call(String from, String to) {
        TopologyEdgeInfo e = new TopologyEdgeInfo();
        e.fromRouteId = from;
        e.toRouteId = to;
        return e;
    }

    private final Map<String, RouteGroups.Tag> tags = Map.of(
            "audit", new RouteGroups.Tag("utility", "Utility", false, 1),
            "archive", new RouteGroups.Tag("utility", "Utility", false, 1),
            "orders", new RouteGroups.Tag("orders", "Orders", false, 0));

    @Test
    void theRoutesOfAFoldedGroupAreOneBox() {
        List<TopologyNodeInfo> nodes = new ArrayList<>(
                List.of(route("orders", 10, 0), route("audit", 8, 1), route("archive", 9, 2)));
        List<TopologyEdgeInfo> edges = new ArrayList<>(
                List.of(call("orders", "audit"), call("orders", "archive"), call("audit", "archive")));

        Map<String, List<NodeLine>> lines = TopologyGroupFolds.fold(nodes, edges, tags, Set.of("utility"));

        assertThat(nodes).extracting(n -> n.routeId).containsExactly("orders", "group:utility");
        TopologyNodeInfo box = nodes.get(1);
        // the busiest route's count, the failures of all; its routes named for the Info panel
        assertThat(box.exchangesTotal).isEqualTo(9);
        assertThat(box.exchangesFailed).isEqualTo(3);
        assertThat(box.from).isEqualTo("audit, archive");
        assertThat(lines.get("group:utility")).extracting(NodeLine::text)
                .containsExactly("▸ Utility", "2 routes folded");
        // one link from orders to the box; the link between the routes of the group is left out
        assertThat(edges).extracting(e -> e.fromRouteId + ">" + e.toRouteId).containsExactly("orders>group:utility");
        assertThat(TopologyGroupFolds.groupOf("group:utility")).isEqualTo("utility");
        assertThat(TopologyGroupFolds.groupOf("orders")).isNull();
    }

    @Test
    void nothingFoldedLeavesTheTopologyAsItIs() {
        List<TopologyNodeInfo> nodes = new ArrayList<>(List.of(route("orders", 10, 0), route("audit", 8, 1)));
        List<TopologyEdgeInfo> edges = new ArrayList<>(List.of(call("orders", "audit")));
        assertThat(TopologyGroupFolds.fold(nodes, edges, tags, Set.of())).isEmpty();
        assertThat(nodes).hasSize(2);
        assertThat(edges).hasSize(1);
    }
}
