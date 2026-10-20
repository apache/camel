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

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import org.apache.camel.diagram.RouteDiagramLayoutEngine;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutRoute;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.NodeInfo;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.RouteInfo;
import org.apache.camel.dsl.jbang.core.commands.tui.BodyTypes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The body types each step has been reached with, beside the arrow into it (CAMEL-25892): on the shaft above the head,
 * cut where the arrows of a branch are side by side, and a ⚠ on a step reached with bodies of more than one kind.
 */
class BodyTypesOnArrowsTest {

    private static final String STREAM = "org.apache.camel.converter.stream.ByteArrayInputStreamCache";

    @Test
    void theTypesAreOnTheShaftAboveTheHeadOfTheArrow() {
        RouteInfo route = route();
        route.nodes.add(node("unmarshal", "unmarshal1", 1, List.of(STREAM), List.of("payload")));
        route.nodes.add(node("to", "to1", 1, List.of("byte[]", "java.util.LinkedHashMap"), List.of("payload", "data")));

        Buffer buffer = render(route, true);

        int[] stream = find(buffer, "ByteArrayInputStreamCache");
        assertThat(stream).isNotNull();
        // the shaft is two columns left of it, and the head of the arrow right below
        assertThat(symbol(buffer, stream[0] - 2, stream[1])).isEqualTo("│");
        assertThat(symbol(buffer, stream[0] - 2, stream[1] + 1)).isEqualTo("▼");
        // with more than one kind: the kinds as well, as there is room
        int[] mixed = find(buffer, BodyTypes.MARK + " byte[] | LinkedHashMap (payload and data)");
        assertThat(mixed).isNotNull();
        assertThat(symbol(buffer, mixed[0] - 2, mixed[1] + 1)).isEqualTo("▼");
        // and its box has a ⚠ in its top border
        assertThat(row(buffer, mixed[1] + 2)).contains(BodyTypes.MARK);
    }

    @Test
    void theArrowsOfABranchSideBySideCutTheTypes() {
        RouteInfo route = route();
        route.nodes.add(node("multicast", "multicast1", 1, List.of(), List.of()));
        route.nodes.add(node("to", "to1", 2,
                List.of(STREAM, "java.util.LinkedHashMap", "java.util.ArrayList"), List.of("payload", "data", "collection")));
        route.nodes.add(node("to", "to2", 2, List.of("java.lang.String"), List.of("payload")));

        Buffer buffer = render(route, true);

        // the first one stops before the arrow of the second: the first type, and how many more
        int[] left = find(buffer, BodyTypes.MARK + " ByteArrayInputStreamCache +2 ");
        assertThat(left).isNotNull();
        assertThat(row(buffer, left[1])).doesNotContain("LinkedHashMap").contains("▼ String");
    }

    @Test
    void offTheStepsNeedingAttentionStillHaveTheirMark() {
        RouteInfo route = route();
        route.nodes.add(node("to", "to1", 1, List.of("byte[]", "java.util.LinkedHashMap"), List.of("payload", "data")));

        Buffer buffer = render(route, false);

        assertThat(find(buffer, "LinkedHashMap")).isNull();
        assertThat(find(buffer, BodyTypes.MARK)).isNotNull();
    }

    private static Buffer render(RouteInfo route, boolean bodyTypes) {
        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
        LayoutRoute layout = engine.layoutRoute(route, 0);
        RouteDiagramWidget widget = new RouteDiagramWidget(layout, engine.getNodeWidth(), -1, 0, 0, false, Map.of())
                .withBodyTypes(bodyTypes).withBodyKindMarks(true);
        Rect area = new Rect(0, 0, 160, 50);
        Buffer buffer = Buffer.empty(area);
        widget.render(area, buffer);
        return buffer;
    }

    private static RouteInfo route() {
        RouteInfo route = new RouteInfo();
        route.routeId = "orders";
        NodeInfo r = node("route", "orders", 0, List.of(), List.of());
        r.code = "route[orders]";
        route.nodes.add(r);
        route.nodes.add(node("from", "from1", 0, List.of(), List.of()));
        return route;
    }

    private static NodeInfo node(String type, String id, int level, List<String> bodyTypes, List<String> bodyKinds) {
        NodeInfo n = new NodeInfo();
        n.type = type;
        n.id = id;
        n.code = type;
        n.level = level;
        n.bodyTypes = bodyTypes;
        n.bodyKinds = bodyKinds;
        return n;
    }

    private static String symbol(Buffer buffer, int x, int y) {
        return buffer.get(x, y).symbol();
    }

    private static String row(Buffer buffer, int y) {
        StringBuilder sb = new StringBuilder();
        for (int x = 0; x < buffer.width(); x++) {
            sb.append(buffer.get(x, y).symbol());
        }
        return sb.toString();
    }

    private static int[] find(Buffer buffer, String text) {
        for (int y = 0; y < buffer.height(); y++) {
            int x = row(buffer, y).indexOf(text);
            if (x >= 0) {
                return new int[] { x, y };
            }
        }
        return null;
    }
}
