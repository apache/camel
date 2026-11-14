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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.buffer.Cell;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import org.apache.camel.diagram.RouteDiagramLayoutEngine;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutRoute;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.NodeInfo;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.RouteInfo;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.StatInfo;
import org.apache.camel.dsl.jbang.core.commands.tui.Theme;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The live flow in the route diagram: dots on the arrows the messages go along, the stuck step marked in its border,
 * and none of it with the flow off or the metrics off.
 */
class FlowDotsTest {

    private final AtomicLong clock = new AtomicLong(50_000);
    private final FlowAnimation flow = new FlowAnimation(clock::get);
    private final RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();

    @Test
    void dotsMoveOnTheArrowsTheMessagesGoAlong() {
        int dots = 0;
        boolean moved = false;
        String before = null;
        for (int reload = 0; reload < 3; reload++) {
            LayoutRoute layout = layout(reload * 40L, 0, 0);
            for (int frame = 0; frame < 12; frame++) {
                Buffer buffer = render(layout, true);
                String screen = screen(buffer);
                dots += count(screen, '●');
                if (before != null && reload > 0 && !screen.equals(before)) {
                    moved = true;
                }
                before = screen;
                clock.addAndGet(FlowAnimation.FRAME_MS);
            }
            clock.addAndGet(1000 - 12 * FlowAnimation.FRAME_MS);
        }
        assertThat(dots).isPositive();
        assertThat(moved).as("the dots move from frame to frame").isTrue();
        assertThat(flow.isAnimating()).isTrue();
    }

    @Test
    void theBusyBoxesKeepTheirFrameAndColour() {
        Buffer idle = render(layout(0, 0, 0), false);
        Color colour = borderColor(idle);

        Buffer busy = null;
        for (int reload = 0; reload < 4; reload++) {
            LayoutRoute layout = layout(reload * 100L, 0, 0);
            for (int frame = 0; frame < 12; frame++) {
                busy = render(layout, true);
                clock.addAndGet(FlowAnimation.FRAME_MS);
            }
            clock.addAndGet(1000 - 12 * FlowAnimation.FRAME_MS);
        }

        assertThat(corner(busy)).isEqualTo(corner(idle)).isEqualTo("┌");
        assertThat(borderColor(busy)).as("the colour of its kind").isEqualTo(colour);
    }

    @Test
    void bareLightsUpTheLinesBetweenTheSteps() {
        int lit = 0;
        for (int reload = 0; reload < 3; reload++) {
            LayoutRoute layout = layout(reload * 40L, 0, 0);
            RouteDensity.BARE.apply(layout);
            for (int frame = 0; frame < 12; frame++) {
                Buffer buffer = render(layout, true, RouteDensity.BARE);
                assertThat(screen(buffer)).as("no dot: it would look like the mark of a step").doesNotContain("•");
                lit += litLines(buffer);
                clock.addAndGet(FlowAnimation.FRAME_MS);
            }
            clock.addAndGet(1000 - 12 * FlowAnimation.FRAME_MS);
        }
        assertThat(lit).isPositive();
    }

    /** The cells of the lines in the colour of the foreground, not the muted colour of a line. */
    private static int litLines(Buffer buffer) {
        int lit = 0;
        for (int y = 0; y < buffer.height(); y++) {
            for (int x = 0; x < buffer.width(); x++) {
                Cell cell = buffer.get(x, y);
                if ("│".equals(cell.symbol()) && cell.style().fg().filter(Theme.baseFg()::equals).isPresent()) {
                    lit++;
                }
            }
        }
        return lit;
    }

    @Test
    void theOverviewHasALightPassingOverTheSteps() {
        LayoutRoute quiet = layout(0, 0, 0);
        RouteDensity.OVERVIEW.apply(quiet);
        Color colour = markColor(render(quiet, false, RouteDensity.OVERVIEW));

        Set<Color> colours = new HashSet<>();
        for (int reload = 0; reload < 3; reload++) {
            LayoutRoute layout = layout(reload * 40L, 0, 0);
            RouteDensity.OVERVIEW.apply(layout);
            for (int frame = 0; frame < 12; frame++) {
                colours.add(markColor(render(layout, true, RouteDensity.OVERVIEW)));
                clock.addAndGet(FlowAnimation.FRAME_MS);
            }
            clock.addAndGet(1000 - 12 * FlowAnimation.FRAME_MS);
        }
        assertThat(colours).as("lit up as the light passes, and back").contains(colour).hasSizeGreaterThan(1);
    }

    /** The colour of the mark of the last step in the overview. */
    private static Color markColor(Buffer buffer) {
        for (int y = buffer.height() - 1; y >= 0; y--) {
            int x = row(buffer, y).indexOf('■');
            if (x >= 0) {
                return buffer.get(x, y).style().fg().orElse(Color.RESET);
            }
        }
        throw new AssertionError("no step");
    }

    @Test
    void aStuckStepHasASpinnerInItsBorder() {
        render(layout(10, 0, 0), true);
        for (int reload = 1; reload <= 3; reload++) {
            clock.addAndGet(1000);
            render(layout(10, 0, 2), true);
        }
        String screen = screen(render(layout(10, 0, 2), true));

        assertThat(screen).contains("2 inflight · 3s");
    }

    @Test
    void aStuckStepSaysWhenItLastFinishedAnExchange() {
        render(layout(10, 0, 0), true);
        for (int reload = 1; reload <= 3; reload++) {
            clock.addAndGet(1000);
            LayoutRoute layout = layout(10, 0, 2);
            lastStep(layout).lastCompletedExchangeTimestamp = System.currentTimeMillis() - 5 * 60_000;
            render(layout, true);
        }
        LayoutRoute layout = layout(10, 0, 2);
        lastStep(layout).lastCompletedExchangeTimestamp = System.currentTimeMillis() - 5 * 60_000;

        assertThat(screen(render(layout, true))).contains("2 inflight · 5m");
    }

    @Test
    void aStuckStepThatNeverFinishedOneSaysSo() {
        render(layout(0, 0, 0), true);
        for (int reload = 1; reload <= 3; reload++) {
            clock.addAndGet(1000);
            render(layout(0, 0, 4), true);
        }

        assertThat(screen(render(layout(0, 0, 4), true))).contains("4 · none done");
    }

    private static StatInfo lastStep(LayoutRoute layout) {
        return layout.nodes.stream().filter(n -> "to1".equals(n.id)).findFirst().orElseThrow().treeNode.info.stat;
    }

    @Test
    void offThereAreNoDots() {
        flow.setEnabled(false);
        for (int reload = 0; reload < 3; reload++) {
            LayoutRoute layout = layout(reload * 40L, 0, 0);
            for (int frame = 0; frame < 12; frame++) {
                assertThat(screen(render(layout, true))).doesNotContain("●");
                clock.addAndGet(FlowAnimation.FRAME_MS);
            }
            clock.addAndGet(1000 - 12 * FlowAnimation.FRAME_MS);
        }
        assertThat(flow.isAnimating()).isFalse();
    }

    @Test
    void withoutTheMetricsThereIsNoFlow() {
        for (int reload = 0; reload < 3; reload++) {
            LayoutRoute layout = layout(reload * 40L, 0, 0);
            RouteDiagramWidget widget = new RouteDiagramWidget(layout, engine.getNodeWidth(), -1, 0, 0, false, Map.of())
                    .withFlow(flow);
            Rect area = new Rect(0, 0, 120, 60);
            Buffer buffer = Buffer.empty(area);
            widget.render(area, buffer);
            assertThat(screen(buffer)).doesNotContain("●");
            clock.addAndGet(1000);
        }
        assertThat(flow.isAnimating()).isFalse();
    }

    private Buffer render(LayoutRoute layout, boolean withFlow) {
        return render(layout, withFlow, RouteDensity.DETAILED);
    }

    private Buffer render(LayoutRoute layout, boolean withFlow, RouteDensity density) {
        RouteDiagramWidget widget = new RouteDiagramWidget(layout, engine.getNodeWidth(), -1, 0, 0, true, Map.of())
                .withDensity(density).withFlow(withFlow ? flow : null);
        Rect area = new Rect(0, 0, 120, 60);
        Buffer buffer = Buffer.empty(area);
        widget.render(area, buffer);
        return buffer;
    }

    /** A route of a few steps in a row, each reached by the same messages; the last step has the inflight. */
    private LayoutRoute layout(long total, long failed, long inflight) {
        RouteInfo route = new RouteInfo();
        route.routeId = "orders";
        NodeInfo r = node("route", "orders", 0, 0, 0, 0);
        r.code = "route[orders]";
        route.nodes.add(r);
        route.nodes.add(node("from", "from1", 0, total, 0, 0));
        route.nodes.add(node("unmarshal", "unmarshal1", 1, total, 0, 0));
        route.nodes.add(node("bean", "bean1", 1, total, 0, 0));
        route.nodes.add(node("to", "to1", 1, total, failed, inflight));
        return engine.layoutRoute(route, 0);
    }

    private static NodeInfo node(String type, String id, int level, long total, long failed, long inflight) {
        NodeInfo n = new NodeInfo();
        n.type = type;
        n.id = id;
        n.code = type;
        n.level = level;
        n.stat = new StatInfo();
        n.stat.exchangesTotal = total;
        n.stat.exchangesFailed = failed;
        n.stat.exchangesInflight = inflight;
        return n;
    }

    /** The colour of the top left corner of the box of the unmarshal step. */
    private static Color borderColor(Buffer buffer) {
        int[] at = cornerOf(buffer);
        return buffer.get(at[0], at[1]).style().fg().orElse(Color.RESET);
    }

    /** The top left corner of the box of the unmarshal step. */
    private static String corner(Buffer buffer) {
        int[] at = cornerOf(buffer);
        return buffer.get(at[0], at[1]).symbol();
    }

    private static int[] cornerOf(Buffer buffer) {
        for (int y = 0; y < buffer.height(); y++) {
            String row = row(buffer, y);
            int x = row.indexOf("unmarshal");
            if (x >= 0) {
                int left = Math.max(row.lastIndexOf('│', x), row.lastIndexOf('┃', x));
                return new int[] { left, y - 1 };
            }
        }
        throw new AssertionError("no unmarshal box");
    }

    private static String screen(Buffer buffer) {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < buffer.height(); y++) {
            sb.append(row(buffer, y)).append('\n');
        }
        return sb.toString();
    }

    private static String row(Buffer buffer, int y) {
        StringBuilder sb = new StringBuilder();
        for (int x = 0; x < buffer.width(); x++) {
            sb.append(buffer.get(x, y).symbol());
        }
        return sb.toString();
    }

    private static int count(String s, char c) {
        return (int) s.chars().filter(ch -> ch == c).count();
    }
}
