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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import org.apache.camel.diagram.RouteDiagramLayoutEngine;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutRoute;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.NodeInfo;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.RouteInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The densities of the route diagram: the compact and the bare ones put the same steps on fewer rows.
 */
class RouteDensityTest {

    static NodeInfo node(String type, String id, int level, String code) {
        NodeInfo n = new NodeInfo();
        n.type = type;
        n.id = id;
        n.level = level;
        n.code = code;
        return n;
    }

    static RouteInfo route() {
        RouteInfo r = new RouteInfo();
        r.routeId = "orders";
        r.nodes.add(node("route", "orders", 0, "route[orders]"));
        r.nodes.add(node("from", "from1", 1, "from[kafka:orders]"));
        r.nodes.add(node("log", "log1", 2, "log[Received ${body}]"));
        r.nodes.add(node("choice", "choice1", 2, "choice"));
        r.nodes.add(node("when", "when1", 3, "when[${header.priority} == 'high']"));
        r.nodes.add(node("to", "to1", 4, "to[jms:queue:priority]"));
        r.nodes.add(node("log", "log2", 4, "log[Priority order]"));
        r.nodes.add(node("otherwise", "otherwise1", 3, "otherwise"));
        r.nodes.add(node("to", "to2", 4, "to[jms:queue:normal]"));
        r.nodes.add(node("split", "split1", 2, "split[${body.items}]"));
        r.nodes.add(node("to", "to3", 3, "to[http:inventory/reserve]"));
        r.nodes.add(node("log", "log3", 3, "log[Reserved ${body}]"));
        r.nodes.add(node("to", "to4", 2, "to[kafka:orders-done]"));
        return r;
    }

    static List<String> render(LayoutRoute lr, int nodeWidth, RouteDensity density, Map<String, Integer> folded) {
        RouteDiagramWidget widget = new RouteDiagramWidget(
                lr, nodeWidth, -1, 0, 0, false, Map.of(), false, Map.of(), Set.of(), false)
                .withDensity(density).withFoldedSteps(folded);
        Rect area = new Rect(0, 0, 160, 120);
        Buffer buffer = Buffer.empty(area);
        widget.render(area, buffer);
        List<String> rows = new ArrayList<>();
        for (int y = 0; y < area.height(); y++) {
            StringBuilder sb = new StringBuilder();
            for (int x = 0; x < area.width(); x++) {
                sb.append(buffer.get(x, y).symbol());
            }
            rows.add(sb.toString().stripTrailing());
        }
        while (!rows.isEmpty() && rows.get(rows.size() - 1).isEmpty()) {
            rows.remove(rows.size() - 1);
        }
        return rows;
    }

    private static List<String> render(RouteDensity density) {
        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
        engine.setTableLayout(true);
        LayoutRoute lr = engine.layoutRoute(route(), 0);
        density.apply(lr);
        List<String> rows = render(lr, engine.getNodeWidth(), density, Map.of());
        if (Boolean.getBoolean("routeDensity.print")) {
            System.out.println("---- " + density + " (" + rows.size() + " rows)");
            rows.forEach(System.out::println);
        }
        return rows;
    }

    @Test
    void theStepsTakeFewerRowsAsTheDiagramZoomsOut() {
        List<String> detailed = render(RouteDensity.DETAILED);
        List<String> compact = render(RouteDensity.COMPACT);
        List<String> bare = render(RouteDensity.BARE);

        assertThat(compact.size()).isLessThan(detailed.size() * 3 / 4);
        assertThat(bare.size()).isLessThan(compact.size() * 3 / 4);
        // every step is there at each density
        for (List<String> rows : List.of(detailed, compact, bare)) {
            String all = String.join("\n", rows);
            assertThat(all).contains("from[kafka:orders]").contains("choice").contains("otherwise").contains("split");
            assertThat(all.split("to\\[", -1)).hasSize(5);
        }
    }

    @Test
    void aCompactStepIsABoxWithOneLine() {
        String all = String.join("\n", render(RouteDensity.COMPACT));
        assertThat(all).contains("┌").contains("▼");
        // the code of a step is not wrapped onto a second line
        assertThat(all).doesNotContain("priority] ");
    }

    @Test
    void aBareStepIsAMarkAndItsText() {
        List<String> rows = render(RouteDensity.BARE);
        String all = String.join("\n", rows);
        assertThat(all).doesNotContain("└").doesNotContain("▼").doesNotContain("╌");
        assertThat(rows).anyMatch(r -> r.contains("▶ from[kafka:orders]"));
        assertThat(rows).anyMatch(r -> r.contains("◆ choice"));
        assertThat(rows).anyMatch(r -> r.contains("◇ when"));
        assertThat(rows).anyMatch(r -> r.contains("● to[kafka:orders-done]"));
    }

    @Test
    void aStepTheBlockIsFoldedIntoSaysHowManyStepsAreFolded() {
        RouteInfo r = route();
        // the choice and its branches left out, as the zoom does with a folded block
        r.nodes.removeIf(n -> n.level > 2 && List.of("when1", "to1", "log2", "otherwise1", "to2").contains(n.id));
        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
        engine.setTableLayout(true);
        LayoutRoute lr = engine.layoutRoute(r, 0);
        String all = String.join("\n", render(lr, engine.getNodeWidth(), RouteDensity.DETAILED, Map.of("choice1", 5)));
        assertThat(all).contains("▸ 5 steps folded").doesNotContain("jms:queue:priority");
    }

    private static List<String> renderMulticast(RouteDensity density) {
        RouteInfo r = new RouteInfo();
        r.routeId = "fanout";
        r.nodes.add(node("route", "fanout", 0, "route[fanout]"));
        r.nodes.add(node("from", "from1", 1, "from[direct:in]"));
        r.nodes.add(node("multicast", "multicast1", 2, "multicast"));
        for (int i = 1; i <= 4; i++) {
            r.nodes.add(node("to", "to" + i, 3, "to[direct:out" + i + "]"));
        }
        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
        LayoutRoute lr = engine.layoutRoute(r, 0);
        density.apply(lr);
        return render(lr, engine.getNodeWidth(), density, Map.of());
    }

    private static long count(String row, char c) {
        return row.chars().filter(ch -> ch == c).count();
    }

    @Test
    void theArrowsOfManyBranchesJoinOnTheirRow() {
        // bare: one line from the first branch to the last, a junction at each branch between
        String bare = renderMulticast(RouteDensity.BARE).stream().filter(row -> row.contains("┴")).findFirst()
                .orElseThrow();
        assertThat(count(bare, '┌')).isEqualTo(1);
        assertThat(count(bare, '┐')).isEqualTo(1);
        assertThat(count(bare, '┬')).isEqualTo(2);
        // compact: the heads of all the branches are on the row of the line
        String compact = renderMulticast(RouteDensity.COMPACT).stream().filter(row -> row.contains("┴")).findFirst()
                .orElseThrow();
        assertThat(count(compact, '▼')).isEqualTo(4);
    }

    @Test
    void thePositionCountsUpAsTheDiagramZoomsIn() {
        assertThat(RouteDensity.BARE.position()).isEqualTo("1/3");
        assertThat(RouteDensity.COMPACT.position()).isEqualTo("2/3");
        assertThat(RouteDensity.DETAILED.position()).isEqualTo("3/3");
    }
}
