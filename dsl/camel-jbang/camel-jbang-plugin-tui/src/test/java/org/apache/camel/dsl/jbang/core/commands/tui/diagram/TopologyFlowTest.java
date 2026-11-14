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
import java.util.concurrent.atomic.AtomicLong;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.buffer.Cell;
import dev.tamboui.layout.Rect;
import org.apache.camel.diagram.TopologyLayoutEngine;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyEdgeInfo;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyLayoutResult;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyNodeInfo;
import org.apache.camel.dsl.jbang.core.commands.tui.Theme;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The live flow in the topology: dots on the edges into the routes that get messages, from the routes that send them,
 * and none from a route that sends none.
 */
class TopologyFlowTest {

    private final AtomicLong clock = new AtomicLong(50_000);
    private final FlowAnimation flow = new FlowAnimation(clock::get);
    private final TopologyLayoutEngine engine = new TopologyLayoutEngine();

    @Test
    void theEdgeFromABusyRouteHasDots() {
        int dots = 0;
        for (int reload = 0; reload < 3; reload++) {
            TopologyLayoutResult layout = layout(reload * 40L, 0, reload * 40L);
            for (int frame = 0; frame < 12; frame++) {
                dots += count(render(layout, RouteDensity.DETAILED), '●');
                clock.addAndGet(FlowAnimation.FRAME_MS);
            }
            clock.addAndGet(1000 - 12 * FlowAnimation.FRAME_MS);
        }
        assertThat(dots).isPositive();
        assertThat(flow.isAnimating()).isTrue();
    }

    @Test
    void theEdgeFromARouteThatSendsNothingHasNone() {
        // b gets its messages from somewhere else: the caller is idle
        for (int reload = 0; reload < 3; reload++) {
            TopologyLayoutResult layout = layout(0, 0, reload * 40L);
            for (int frame = 0; frame < 12; frame++) {
                assertThat(render(layout, RouteDensity.DETAILED)).doesNotContain("●");
                clock.addAndGet(FlowAnimation.FRAME_MS);
            }
            clock.addAndGet(1000 - 12 * FlowAnimation.FRAME_MS);
        }
    }

    @Test
    void bareLightsUpTheLines() {
        int lit = 0;
        for (int reload = 0; reload < 3; reload++) {
            TopologyLayoutResult layout = layout(reload * 40L, 0, reload * 40L);
            TopologyLayoutResult bare = TopologyZoom.apply(RouteDensity.BARE, layout, null).layout();
            for (int frame = 0; frame < 12; frame++) {
                Buffer buffer = buffer(bare, RouteDensity.BARE);
                assertThat(screen(buffer)).as("no dot: it would look like the mark of a route").doesNotContain("•");
                lit += litLines(buffer);
                clock.addAndGet(FlowAnimation.FRAME_MS);
            }
            clock.addAndGet(1000 - 12 * FlowAnimation.FRAME_MS);
        }
        assertThat(lit).isPositive();
    }

    @Test
    void withoutTheMetricsThereIsNoFlow() {
        for (int reload = 0; reload < 3; reload++) {
            TopologyLayoutResult layout = layout(reload * 40L, 0, reload * 40L);
            TopologyDiagramWidget widget
                    = new TopologyDiagramWidget(layout, engine.getNodeWidth(), -1, 0, 0, false, false).withFlow(flow);
            Rect area = new Rect(0, 0, 120, 40);
            Buffer buffer = Buffer.empty(area);
            widget.render(area, buffer);
            assertThat(screen(buffer)).doesNotContain("●");
            clock.addAndGet(1000);
        }
        assertThat(flow.isAnimating()).isFalse();
    }

    /** Route a calls b; a reload lays it out anew, with new nodes. */
    private TopologyLayoutResult layout(long totalA, long failedA, long totalB) {
        List<TopologyNodeInfo> nodes = new ArrayList<>();
        nodes.add(node("a", "timer:a", totalA, failedA));
        nodes.add(node("b", "direct:b", totalB, 0));
        TopologyEdgeInfo edge = new TopologyEdgeInfo();
        edge.fromRouteId = "a";
        edge.toRouteId = "b";
        edge.endpoint = "direct:b";
        edge.connectionType = "internal";
        return engine.layout(nodes, List.of(edge));
    }

    private static TopologyNodeInfo node(String id, String from, long total, long failed) {
        TopologyNodeInfo n = new TopologyNodeInfo();
        n.routeId = id;
        n.from = from;
        n.fromScheme = from.substring(0, from.indexOf(':'));
        n.nodeType = "route";
        n.exchangesTotal = total;
        n.exchangesFailed = failed;
        return n;
    }

    private String render(TopologyLayoutResult layout, RouteDensity density) {
        return screen(buffer(layout, density));
    }

    private Buffer buffer(TopologyLayoutResult layout, RouteDensity density) {
        TopologyDiagramWidget widget = new TopologyDiagramWidget(layout, engine.getNodeWidth(), -1, 0, 0, true, false)
                .withDensity(density).withFlow(flow);
        Rect area = new Rect(0, 0, 120, 40);
        Buffer buffer = Buffer.empty(area);
        widget.render(area, buffer);
        return buffer;
    }

    /** The cells of the lines in the colour of the foreground, not the colour of a line. */
    private static int litLines(Buffer buffer) {
        int lit = 0;
        for (int y = 0; y < buffer.height(); y++) {
            for (int x = 0; x < buffer.width(); x++) {
                Cell cell = buffer.get(x, y);
                if ("│▼".contains(cell.symbol()) && cell.style().fg().filter(Theme.baseFg()::equals).isPresent()) {
                    lit++;
                }
            }
        }
        return lit;
    }

    private static String screen(Buffer buffer) {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < buffer.height(); y++) {
            for (int x = 0; x < buffer.width(); x++) {
                sb.append(buffer.get(x, y).symbol());
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static int count(String s, char c) {
        return (int) s.chars().filter(ch -> ch == c).count();
    }
}
