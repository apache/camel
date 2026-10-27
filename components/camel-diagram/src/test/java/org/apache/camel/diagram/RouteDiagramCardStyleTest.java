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

import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutNode;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutRoute;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.NodeInfo;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.NodeLabelMode;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.RouteInfo;
import org.apache.camel.diagram.RouteDiagramRenderer.DiagramColors;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * The card style of a route diagram: the steps as cards with an icon, a title and the detail below it.
 */
class RouteDiagramCardStyleTest {

    private static NodeInfo node(String type, String code, int level) {
        NodeInfo n = new NodeInfo();
        n.type = type;
        n.id = type + level;
        n.code = code;
        n.level = level;
        return n;
    }

    /** The route of the payload mode design: a file read, a groovy mapping, an http call and a JMS send. */
    private static RouteInfo orders() {
        RouteInfo r = new RouteInfo();
        r.routeId = "orders";
        r.source = "orders.camel.yaml";
        r.nodes.add(node("route", "route[orders]", 0));
        r.nodes.add(node("from", "from[file:orders]", 1));
        r.nodes.add(node("setBody", "setBody[groovy{body.items}]", 2));
        r.nodes.add(node("to", "to[http:pricing]", 2));
        r.nodes.add(node("setHeader", "setHeader[skus]", 2));
        r.nodes.add(node("to", "to[jms:priced]", 2));
        return r;
    }

    @Test
    void cardPresetsDrawCards() {
        assertThat(DiagramColors.parse("card").isCard()).isTrue();
        assertThat(DiagramColors.parse("card").getCardBg()).isEqualTo(Color.WHITE);
        assertThat(DiagramColors.parse("card-dark").isCard()).isTrue();
        assertThat(DiagramColors.parse("card-dark").getCardBg()).isNotEqualTo(Color.WHITE);
        assertThat(DiagramColors.parse("dark").isCard()).isFalse();
        assertThat(DiagramColors.parse("light").isCard()).isFalse();
        // a custom spec turns the card style on with style=card
        DiagramColors custom = DiagramColors.parse("style=card:bg=#ffffff:card=#fafafa");
        assertThat(custom.isCard()).isTrue();
        assertThat(custom.getCardBg()).isEqualTo(new Color(0xfafafa));
    }

    @Test
    void cardLabelSplitsKindAndDetail() {
        assertThat(RouteDiagramLayoutEngine.cardLabel(node("setBody", "setBody[groovy{body.items}]", 1),
                NodeLabelMode.CODE)).containsExactly("setBody", "groovy: body.items");
        assertThat(RouteDiagramLayoutEngine.cardLabel(node("from", "from[file:orders]", 1), NodeLabelMode.CODE))
                .containsExactly("from", "file:orders");
        assertThat(RouteDiagramLayoutEngine.cardLabel(node("setHeader", "setHeader[skus]", 1), NodeLabelMode.CODE))
                .containsExactly("setHeader", "skus");
        // a step without detail has only its kind
        assertThat(RouteDiagramLayoutEngine.cardLabel(node("choice", "choice", 1), NodeLabelMode.CODE))
                .containsExactly("choice");
        // a predicate keeps its text, only the language moves in front
        assertThat(RouteDiagramLayoutEngine.cardLabel(node("when", "when[simple{${header.kind} == 0}]", 1),
                NodeLabelMode.CODE)).containsExactly("when", "simple: ${header.kind} == 0");
    }

    @Test
    void cardLabelByMode() {
        NodeInfo n = node("to", "to[kafka:orders]", 1);
        n.description = "Publish the order";
        assertThat(RouteDiagramLayoutEngine.cardLabel(n, NodeLabelMode.CODE)).containsExactly("to", "kafka:orders");
        assertThat(RouteDiagramLayoutEngine.cardLabel(n, NodeLabelMode.DESCRIPTION))
                .containsExactly("to", "Publish the order");
        assertThat(RouteDiagramLayoutEngine.cardLabel(n, NodeLabelMode.BOTH))
                .containsExactly("to", "Publish the order", "kafka:orders");

        // without a description the detail is shown in every mode
        NodeInfo plain = node("to", "to[kafka:orders]", 1);
        assertThat(RouteDiagramLayoutEngine.cardLabel(plain, NodeLabelMode.DESCRIPTION))
                .containsExactly("to", "kafka:orders");
        assertThat(RouteDiagramLayoutEngine.cardLabel(plain, NodeLabelMode.BOTH))
                .containsExactly("to", "kafka:orders");
    }

    @Test
    void cardLayoutGivesTitleAndDetail() {
        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
        engine.setCardStyle(true);
        LayoutRoute lr = engine.layoutRoute(orders(), RouteDiagramLayoutEngine.PADDING);

        LayoutNode setBody = lr.nodes.stream().filter(n -> "setBody".equals(n.type)).findFirst().orElseThrow();
        assertThat(setBody.wrappedLines).containsExactly("setBody", "groovy: body.items");
        // a card with a detail is taller than one with only its title
        LayoutNode route = lr.nodes.get(0);
        assertThat(route.wrappedLines).containsExactly("route", "orders");

        RouteDiagramLayoutEngine plain = new RouteDiagramLayoutEngine();
        LayoutRoute plainRoute = plain.layoutRoute(orders(), RouteDiagramLayoutEngine.PADDING);
        assertThat(plainRoute.nodes.get(2).wrappedLines).containsExactly("setBody[groovy{body.items}]");
        assertThat(setBody.height).isGreaterThan(plainRoute.nodes.get(2).height);
    }

    @Test
    void longDetailWrapsToThreeLines() {
        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
        engine.setCardStyle(true);
        RouteInfo r = new RouteInfo();
        r.routeId = "long";
        r.nodes.add(node("route", "route[long]", 0));
        r.nodes.add(node("to", "to[https:www.example.com/a/very/long/path/that/does/not/fit/on/one/line/of/a/card"
                               + "?with=many&query=parameters&and=more&of=them&until=it&takes=several&lines=to&show]",
                1));
        LayoutRoute lr = engine.layoutRoute(r, RouteDiagramLayoutEngine.PADDING);
        List<String> lines = lr.nodes.get(1).wrappedLines;
        assertThat(lines.get(0)).isEqualTo("to");
        // the title and at most three lines of detail; the renderer cuts the last one
        assertThat(lines).hasSize(4);
    }

    @Test
    void everyIconHasAnOutlineOnTheGrid() {
        assertThat(RouteDiagramIcons.names()).hasSizeGreaterThan(15);
        for (String name : RouteDiagramIcons.names()) {
            Rectangle2D bounds = RouteDiagramIcons.icon(name).getBounds2D();
            assertThat(bounds.getWidth()).as(name).isPositive();
            assertThat(bounds.getHeight()).as(name).isPositive();
            assertThat(bounds.getMinX()).as(name).isGreaterThanOrEqualTo(0.5);
            assertThat(bounds.getMinY()).as(name).isGreaterThanOrEqualTo(0.5);
            assertThat(bounds.getMaxX()).as(name).isLessThanOrEqualTo(RouteDiagramIcons.GRID - 0.5);
            assertThat(bounds.getMaxY()).as(name).isLessThanOrEqualTo(RouteDiagramIcons.GRID - 0.5);
        }
    }

    @Test
    void iconOfEachKindOfStep() {
        assertThat(RouteDiagramIcons.iconName("from")).isEqualTo("log-in");
        assertThat(RouteDiagramIcons.iconName("to")).isEqualTo("log-out");
        assertThat(RouteDiagramIcons.iconName("setBody")).isEqualTo("braces");
        assertThat(RouteDiagramIcons.iconName("setHeader")).isEqualTo("heading");
        assertThat(RouteDiagramIcons.iconName("choice")).isEqualTo("git-branch");
        assertThat(RouteDiagramIcons.iconName("someNewEip")).isEqualTo("box");
        assertThat(RouteDiagramIcons.iconName(null)).isEqualTo("box");
    }

    @Test
    void pathDataAsLucideWritesIt() {
        // numbers run together (1.1.9 is 1.1 and .9) and an arc repeated by more numbers
        Shape braces = RouteDiagramIcons.path("M8 3H7a2 2 0 0 0-2 2v5a2 2 0 0 1-2 2 2 2 0 0 1 2 2v5c0 1.1.9 2 2 2h1");
        Rectangle2D b = braces.getBounds2D();
        assertThat(b.getMinX()).isEqualTo(3.0, within(0.01));
        assertThat(b.getMaxX()).isEqualTo(8.0, within(0.01));
        assertThat(b.getMinY()).isEqualTo(3.0, within(0.01));
        assertThat(b.getMaxY()).isEqualTo(21.0, within(0.01));

        // a half circle with the sweep flag bulges to the right of its chord
        Rectangle2D arc = RouteDiagramIcons.path("M12 2a10 10 0 0 1 0 20").getBounds2D();
        assertThat(arc.getMaxX()).isEqualTo(22.0, within(0.01));
        assertThat(arc.getMinX()).isEqualTo(12.0, within(0.01));

        assertThatThrownBy(() -> RouteDiagramIcons.path("12 2")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RouteDiagramIcons.path("M12 2Q1 1 2 2")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void renderCards() {
        for (String theme : List.of("card", "card-dark")) {
            DiagramColors colors = DiagramColors.parse(theme);
            RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
            engine.setCardStyle(true);
            LayoutRoute lr = engine.layoutRoute(orders(), RouteDiagramLayoutEngine.PADDING);
            BufferedImage image = new RouteDiagramRenderer().renderDiagram(List.of(lr), lr.maxY, colors);

            // the card is filled with the card color, right of its text
            LayoutNode setBody = lr.nodes.get(2);
            int x = setBody.x + engine.getNodeWidth() - RouteDiagramLayoutEngine.CARD_PAD / 2;
            int y = setBody.y + setBody.height / 2;
            assertThat(new Color(image.getRGB(x, y))).as(theme).isEqualTo(colors.getCardBg());
            // the circle of the icon has the color of the kind of step (a transform)
            int cx = setBody.x + RouteDiagramLayoutEngine.CARD_PAD + 2 * RouteDiagramLayoutEngine.SCALE;
            assertThat(new Color(image.getRGB(cx, y))).as(theme).isEqualTo(colors.getNodeTransform());
        }
    }
}
