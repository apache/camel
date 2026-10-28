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
package org.apache.camel.dsl.jbang.core.commands.tui.diagram;

import java.util.Map;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import org.apache.camel.diagram.RouteDiagramLayoutEngine;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutRoute;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.NodeInfo;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.RouteInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ↵ beside a box that calls another route names that route; an emoji of several code points in the name is drawn as
 * one character.
 */
class RouteLinkMarkerTest {

    // a family: three emoji joined by ZWJs, eight chars that take two columns
    private static final String FAMILY = "👨\u200D👩\u200D👧";

    @Test
    void theMarkerDrawsAnEmojiOfSeveralCodePointsInOneCell() {
        RouteInfo route = new RouteInfo();
        route.routeId = "orders";
        route.nodes.add(node("route", null, 0));
        route.nodes.add(node("from", "timer:tick", 1));
        route.nodes.add(node("to", "direct:family", 2));
        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
        LayoutRoute layout = engine.layoutRoute(route, 0);
        RouteDiagramWidget widget = new RouteDiagramWidget(
                layout, engine.getNodeWidth(), 0, 0, 0, false, Map.of("direct:family", FAMILY + "x"));
        Rect area = new Rect(0, 0, 120, 30);
        Buffer buffer = Buffer.empty(area);
        widget.render(area, buffer);

        // the ↵, a space, the family in one cell of two columns, then the x
        int[] arrow = find(buffer, "↵");
        assertThat(arrow).isNotNull();
        assertThat(buffer.get(arrow[0] + 2, arrow[1]).symbol()).isEqualTo(FAMILY);
        assertThat(buffer.get(arrow[0] + 4, arrow[1]).symbol()).isEqualTo("x");
    }

    private static NodeInfo node(String type, String uri, int level) {
        NodeInfo n = new NodeInfo();
        n.type = type;
        n.id = uri != null ? type + "1" : "orders";
        n.uri = uri;
        n.code = uri != null ? type + "[" + uri + "]" : "route[orders]";
        n.level = level;
        return n;
    }

    private static int[] find(Buffer buffer, String symbol) {
        for (int y = 0; y < buffer.height(); y++) {
            for (int x = 0; x < buffer.width(); x++) {
                if (symbol.equals(buffer.get(x, y).symbol())) {
                    return new int[] { x, y };
                }
            }
        }
        return null;
    }
}
