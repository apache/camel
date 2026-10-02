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

import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import org.apache.camel.diagram.RouteDiagramLayoutEngine;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutRoute;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.NodeInfo;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.RouteInfo;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.StatInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A Switch is drawn as a decision table: one box, a row per case with where it sends and its own count.
 */
class SwitchTableTest {

    private static NodeInfo node(String type, String id, int level, String code, String uri, Long total) {
        NodeInfo n = new NodeInfo();
        n.type = type;
        n.id = id;
        n.level = level;
        n.code = code;
        n.uri = uri;
        if (total != null) {
            n.stat = new StatInfo();
            n.stat.exchangesTotal = total;
        }
        return n;
    }

    private static RouteInfo route(int cases) {
        RouteInfo r = new RouteInfo();
        r.routeId = "specialist-dispatch";
        r.nodes.add(node("route", "specialist-dispatch", 0, "route[specialist-dispatch]", null, null));
        r.nodes.add(node("from", "specialist-dispatch", 1, "from[direct:dispatch]", "direct:dispatch", 9L));
        r.nodes.add(node("switch", "switch1", 2, "switch[exchangeProperty{specialist}]", null, 9L));
        String[] names = { "reservation", "weather", "cost", "general" };
        for (int i = 0; i < cases; i++) {
            String value = i < names.length ? names[i] : "value" + i;
            String uri = "a2a:{{agents." + value + ".url}}?protocolBinding=JSONRPC";
            r.nodes.add(node("case", "case" + (i + 1), 3, "case[" + value + " -> " + uri + "]", uri, (long) i + 1));
        }
        r.nodes.add(node("to", "switch1-otherwise", 3, "otherwise[direct:unsupported]", "direct:unsupported", 2L));
        return r;
    }

    private static List<String> render(RouteInfo route, int selected) {
        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
        engine.setTableLayout(true);
        LayoutRoute lr = engine.layoutRoute(route, 0);
        RouteDiagramWidget widget = new RouteDiagramWidget(
                lr, engine.getNodeWidth(), selected, 0, 0, true,
                Map.of("direct:unsupported", "unsupported"), false, Map.of(), Set.of(), false);
        Rect area = new Rect(0, 0, 120, 60);
        Buffer buffer = Buffer.empty(area);
        widget.render(area, buffer);
        List<String> rows = new java.util.ArrayList<>();
        for (int y = 0; y < area.height(); y++) {
            StringBuilder sb = new StringBuilder();
            for (int x = 0; x < area.width(); x++) {
                sb.append(buffer.get(x, y).symbol());
            }
            rows.add(sb.toString().stripTrailing());
        }
        return rows;
    }

    @Test
    void aRowPerCaseWithItsCount() {
        List<String> rows = render(route(4), -1);
        String all = String.join("\n", rows);

        assertThat(all).contains("switch  exchangeProperty{specialist}");
        assertThat(rows).anyMatch(r -> r.contains("reservation") && r.contains("→ a2a:{{agents.reservation.url}}")
                && r.stripTrailing().endsWith("1 │"));
        assertThat(rows).anyMatch(r -> r.contains("general") && r.stripTrailing().endsWith("4 │"));
        // the otherwise sends to a route of the integration: a jump marker before its count
        assertThat(rows).anyMatch(r -> r.contains("otherwise") && r.contains("↵ 2"));
        // the options of an endpoint are left out, and the cases are not boxes of their own
        assertThat(all).doesNotContain("protocolBinding").doesNotContain("case[");
    }

    @Test
    void aLongTableShowsAWindowThatFollowsTheSelection() {
        // the boxes: from (0), the table (1), then its rows: row 20 is box 2 + 20
        List<String> rows = render(route(30), 2 + 20);
        String all = String.join("\n", rows);
        assertThat(all).contains("value20").doesNotContain("reservation ").doesNotContain("value21 ");
        assertThat(all).contains("↑ 31 cases, 14-21 shown ↓");
        // without a selection in it, the table shows its first rows
        assertThat(String.join("\n", render(route(30), -1))).contains("reservation").contains("31 cases, 1-8 shown ↓");
    }
}
