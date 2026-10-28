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
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutNode;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutRoute;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.NodeInfo;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.RouteInfo;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.StatInfo;
import org.apache.camel.diagram.RouteDiagramRenderer.DiagramColors;
import org.apache.camel.diagram.TopologyErrorFrame.LineStyle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The body types a step has been reached with, as a label on the arrow into it in a route diagram image.
 */
class RouteDiagramBodyTypesTest {

    private static final FontMetrics FM;

    static {
        Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        FM = g.getFontMetrics(new Font("SansSerif", Font.PLAIN, 22));
        g.dispose();
    }

    private static NodeInfo node(String type, String code, int level, List<String> types, List<String> kinds) {
        NodeInfo n = new NodeInfo();
        n.type = type;
        n.id = type + level;
        n.code = code;
        n.level = level;
        n.bodyTypes = types;
        n.bodyKinds = kinds;
        return n;
    }

    /** A file read, a mapping that gives a map or a text, and an http call reached with both. */
    private static RouteInfo orders(boolean withBodyTypes) {
        List<String> none = List.of();
        RouteInfo r = new RouteInfo();
        r.routeId = "orders";
        r.nodes.add(node("route", "route[orders]", 0, none, none));
        r.nodes.add(node("from", "from[file:orders]", 1, none, none));
        r.nodes.add(node("setBody", "setBody[groovy{body.items}]", 2,
                withBodyTypes ? List.of("org.apache.camel.component.file.GenericFile") : none,
                withBodyTypes ? List.of("payload") : none));
        r.nodes.add(node("to", "to[http:pricing]", 2,
                withBodyTypes ? List.of("java.util.LinkedHashMap", "java.lang.String") : none,
                withBodyTypes ? List.of("data", "payload") : none));
        return r;
    }

    @Test
    void labelOfTheTypes() {
        assertThat(RouteDiagramBodyTypes.fit(List.of("java.lang.String"), List.of("payload"), FM, 1000))
                .isEqualTo("String");
        assertThat(RouteDiagramBodyTypes.fit(List.of("java.lang.String", "byte[]"), List.of("payload"), FM, 1000))
                .isEqualTo("String | byte[]");
        // more than one kind: the kinds too
        assertThat(RouteDiagramBodyTypes.fit(List.of("java.util.LinkedHashMap", "java.lang.String"),
                List.of("data", "payload"), FM, 1000)).isEqualTo("LinkedHashMap | String (data and payload)");
        assertThat(RouteDiagramBodyTypes.kinds(List.of("null", "payload", "data"))).isEqualTo("null, payload and data");
        // not reached: no label
        assertThat(RouteDiagramBodyTypes.fit(List.of(), List.of(), FM, 1000)).isEmpty();
    }

    @Test
    void labelThatDoesNotFit() {
        List<String> types = List.of("java.util.LinkedHashMap", "java.lang.String", "java.util.ArrayList");
        List<String> kinds = List.of("data", "payload", "collection");
        // the kinds go first, then the last types, then the first type is cut
        String all = "LinkedHashMap | String | ArrayList";
        assertThat(RouteDiagramBodyTypes.fit(types, kinds, FM, FM.stringWidth(all))).isEqualTo(all);
        String two = "LinkedHashMap | String +1";
        assertThat(RouteDiagramBodyTypes.fit(types, kinds, FM, FM.stringWidth(two))).isEqualTo(two);
        assertThat(RouteDiagramBodyTypes.fit(types, kinds, FM, FM.stringWidth("Linked… +2")))
                .startsWith("Linked").endsWith("… +2");
    }

    @Test
    void longerArrowsForTheLabels() {
        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
        engine.setCardStyle(true);
        LayoutRoute plain = engine.layoutRoute(orders(true), RouteDiagramLayoutEngine.PADDING);
        assertThat(plain.bodyTypes).isFalse();

        engine.setBodyTypes(true);
        LayoutRoute labels = engine.layoutRoute(orders(true), RouteDiagramLayoutEngine.PADDING);
        assertThat(labels.bodyTypes).isTrue();
        LayoutNode from = labels.nodes.get(1);
        LayoutNode setBody = labels.nodes.get(2);
        assertThat(engine.getBodyTypesGap()).isGreaterThanOrEqualTo(RouteDiagramLayoutEngine.BODY_TYPES_GAP);
        assertThat(setBody.y - (from.y + from.height))
                .isEqualTo(RouteDiagramLayoutEngine.V_GAP + engine.getBodyTypesGap());
        // and room on both sides for a label wider than a step
        int overhang = (RouteDiagramLayoutEngine.bodyTypesLabelWidth(engine.getNodeWidth()) - engine.getNodeWidth()) / 2;
        assertThat(from.x).isEqualTo(RouteDiagramLayoutEngine.PADDING + overhang);
        assertThat(labels.maxX).isEqualTo(from.x + engine.getNodeWidth() + overhang);

        // a route without body types (not run, or no message history) does not change
        LayoutRoute none = engine.layoutRoute(orders(false), RouteDiagramLayoutEngine.PADDING);
        assertThat(none.bodyTypes).isFalse();
        assertThat(none.maxY).isEqualTo(plain.maxY);
    }

    @Test
    void drawTheLabels() {
        DiagramColors colors = DiagramColors.parse("dark");
        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
        engine.setCardStyle(true);
        engine.setBodyTypes(true);
        LayoutRoute lr = engine.layoutRoute(orders(true), RouteDiagramLayoutEngine.PADDING);
        BufferedImage image = new RouteDiagramRenderer().renderDiagram(List.of(lr), lr.maxY, colors);

        // the label of the setBody covers the arrow: the background of a label where the line would be
        LayoutNode from = lr.nodes.get(1);
        int cx = from.x + engine.getNodeWidth() / 2;
        int top = from.y + from.height + RouteDiagramLayoutEngine.V_GAP / 2 + 4 * RouteDiagramLayoutEngine.SCALE;
        assertThat(new Color(image.getRGB(cx, top + 3))).isEqualTo(colors.getCardBg());

        // the http call was reached with a map and a text: a warning label
        LayoutNode setBody = lr.nodes.get(2);
        int warnTop = setBody.y + setBody.height + RouteDiagramLayoutEngine.V_GAP / 2
                      + 4 * RouteDiagramLayoutEngine.SCALE;
        assertThat(new Color(image.getRGB(cx, warnTop + 3))).isEqualTo(colors.getWarningBg());
    }

    @Test
    void labelsOfTheErrorHandling() {
        // only the onException clause has been reached with a body
        List<String> none = List.of();
        RouteInfo r = new RouteInfo();
        r.routeId = "checkout";
        r.nodes.add(node("route", "route[checkout]", 0, none, none));
        r.nodes.add(node("from", "from[direct:checkout]", 1, none, none));
        r.nodes.add(node("to", "to[direct:charge]", 2, none, none));
        r.nodes.add(node("onException", "onException[IOException]", 2, none, none));
        r.nodes.add(node("log", "log[failed]", 3, List.of("java.lang.String"), List.of("payload")));

        DiagramColors colors = DiagramColors.parse("dark");
        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
        engine.setCardStyle(true);
        engine.setBodyTypes(true);
        LayoutRoute lr = RouteErrorFrame.layout(engine, r, RouteDiagramLayoutEngine.PADDING, true, null, List.of(),
                LineStyle.TEXT, RouteDiagramRenderer.rowHeight(RouteDiagramLayoutEngine.DEFAULT_FONT_SIZE));
        assertThat(lr.bodyTypes).isTrue();
        BufferedImage image = new RouteDiagramRenderer().renderDiagram(List.of(lr), lr.maxY, colors);

        // the label on the arrow into the log of the onException clause
        LayoutNode onException = lr.nodes.stream().filter(n -> "onException".equals(n.type)).findFirst().orElseThrow();
        int cx = onException.x + engine.getNodeWidth() / 2;
        int top = onException.y + onException.height + RouteDiagramLayoutEngine.bodyTypesLabelOffset();
        assertThat(new Color(image.getRGB(cx, top + 3))).isEqualTo(colors.getCardBg());
    }

    @Test
    void labelsDoNotHideTheCounters() {
        DiagramColors colors = DiagramColors.parse("dark");
        for (int fontSize : new int[] { 12, 16, 20 }) {
            RouteDiagramLayoutEngine engine
                    = new RouteDiagramLayoutEngine(RouteDiagramLayoutEngine.DEFAULT_BOX_WIDTH, fontSize);
            engine.setCardStyle(true);
            engine.setBodyTypes(true);
            RouteInfo r = orders(true);
            for (NodeInfo n : r.nodes) {
                n.stat = new StatInfo();
                n.stat.exchangesTotal = 120;
                n.stat.exchangesFailed = 3;
            }
            LayoutRoute lr = engine.layoutRoute(r, RouteDiagramLayoutEngine.PADDING);
            RouteDiagramRenderer renderer = new RouteDiagramRenderer(
                    engine.getNodeWidth(), fontSize * RouteDiagramLayoutEngine.SCALE, true);
            BufferedImage labels = renderer.renderDiagram(List.of(lr), lr.maxY, colors);
            // the same layout without the labels
            lr.bodyTypes = false;
            BufferedImage plain = renderer.renderDiagram(List.of(lr), lr.maxY, colors);

            assertThat(count(labels, colors.getCounter())).as("font size %d", fontSize)
                    .isPositive().isEqualTo(count(plain, colors.getCounter()));
            assertThat(count(labels, colors.getCounterFail())).as("font size %d", fontSize)
                    .isPositive().isEqualTo(count(plain, colors.getCounterFail()));
        }
    }

    @Test
    void labelInsideTheBoxOfTheSplit() {
        List<String> none = List.of();
        RouteInfo r = new RouteInfo();
        r.routeId = "split";
        r.nodes.add(node("route", "route[split]", 0, none, none));
        r.nodes.add(node("from", "from[file:orders]", 1, none, none));
        r.nodes.add(node("split", "split[jsonpath{$.items}]", 2, none, none));
        r.nodes.add(node("to", "to[http:pricing]", 3,
                List.of("java.util.LinkedHashMap", "java.lang.String"), List.of("data", "payload")));

        DiagramColors colors = DiagramColors.parse("dark");
        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
        engine.setCardStyle(true);
        engine.setBodyTypes(true);
        LayoutRoute lr = engine.layoutRoute(r, RouteDiagramLayoutEngine.PADDING);
        BufferedImage image = new RouteDiagramRenderer().renderDiagram(List.of(lr), lr.maxY, colors);

        LayoutNode split = lr.nodes.get(2);
        int labelY = split.y + split.height + RouteDiagramLayoutEngine.bodyTypesLabelOffset() + 3;
        int cx = split.x + engine.getNodeWidth() / 2;
        assertThat(new Color(image.getRGB(cx, labelY))).isEqualTo(colors.getWarningBg());
        // nothing of the label outside the dashed box, on either side
        int boxLeft = split.x - RouteDiagramLayoutEngine.SCOPE_BOX_PAD;
        int boxRight = split.x + engine.getNodeWidth() + RouteDiagramLayoutEngine.SCOPE_BOX_PAD;
        for (int x = 0; x < boxLeft - RouteDiagramLayoutEngine.SCALE; x++) {
            assertThat(new Color(image.getRGB(x, labelY))).as("x=%d", x).isEqualTo(colors.getBg());
        }
        for (int x = boxRight + RouteDiagramLayoutEngine.SCALE; x < image.getWidth(); x++) {
            assertThat(new Color(image.getRGB(x, labelY))).as("x=%d", x).isEqualTo(colors.getBg());
        }
    }

    private static int count(BufferedImage image, Color color) {
        int n = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) == color.getRGB()) {
                    n++;
                }
            }
        }
        return n;
    }
}
