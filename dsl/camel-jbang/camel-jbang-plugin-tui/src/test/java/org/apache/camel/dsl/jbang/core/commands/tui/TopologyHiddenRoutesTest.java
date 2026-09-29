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

import dev.tamboui.style.Style;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyEdgeInfo;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyNodeInfo;
import org.apache.camel.dsl.jbang.core.commands.tui.diagram.TopologyDiagramWidget.NodeLine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The topology leaves out the utility routes when they are hidden, and widens its boxes so labels and group tags fit
 * (CAMEL-25147).
 */
class TopologyHiddenRoutesTest {

    private static TopologyNodeInfo node(String id, String type) {
        TopologyNodeInfo n = new TopologyNodeInfo();
        n.routeId = id;
        n.nodeType = type;
        return n;
    }

    private static TopologyEdgeInfo edge(String from, String to) {
        TopologyEdgeInfo e = new TopologyEdgeInfo();
        e.fromRouteId = from;
        e.toRouteId = to;
        return e;
    }

    @Test
    void hiddenRoutesTheirLinksAndTheirSystemsAreLeftOut() {
        List<TopologyNodeInfo> nodes = new ArrayList<>(
                List.of(
                        node("order", "route"), node("dlq", "route"), node("trace", "route"),
                        node("ext-kafka-dlq", "external-out"), node("ext-http", "external-in")));
        List<TopologyEdgeInfo> edges = new ArrayList<>(
                List.of(
                        edge("order", "dlq"), edge("order", "trace"), edge("dlq", "ext-kafka-dlq"),
                        edge("ext-http", "order")));

        DiagramSupport.removeRoutes(nodes, edges, Set.of("dlq", "trace"));

        assertThat(nodes).extracting(n -> n.routeId).containsExactly("order", "ext-http");
        assertThat(edges).extracting(e -> e.fromRouteId + ">" + e.toRouteId).containsExactly("ext-http>order");
    }

    @Test
    void boxesWidenForTheGroupTagWithinReason() {
        DiagramSupport support = new DiagramSupport();
        assertThat(support.wantedBoxColumns()).isEqualTo(DiagramSupport.DEFAULT_BOX_COLUMNS);

        String tag = "▸ ✦ Operational Visibility";
        support.setGroups(Map.of("status", new NodeLine(tag, Style.EMPTY)), Map.of());
        assertThat(support.wantedBoxColumns()).isEqualTo(tag.length() + 4);
        assertThat(support.isTopologyStale()).as("laid out for the default width").isTrue();

        support.setHiddenRoutes(Set.of("status"));
        assertThat(support.wantedBoxColumns()).as("a hidden route does not widen the boxes")
                .isEqualTo(DiagramSupport.DEFAULT_BOX_COLUMNS);

        support.setHiddenRoutes(Set.of());
        support.setGroups(Map.of("x", new NodeLine(
                "▸ " + "a very long capability name indeed".repeat(2),
                Style.EMPTY)), Map.of());
        assertThat(support.wantedBoxColumns()).isEqualTo(DiagramSupport.MAX_BOX_COLUMNS);
    }
}
