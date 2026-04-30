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
package org.apache.camel.dsl.jbang.core.commands.action;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.action.RouteDiagramLayoutEngine.LayoutNode;
import org.apache.camel.dsl.jbang.core.commands.action.RouteDiagramLayoutEngine.LayoutRoute;
import org.apache.camel.dsl.jbang.core.commands.action.RouteDiagramLayoutEngine.NodeInfo;
import org.apache.camel.dsl.jbang.core.commands.action.RouteDiagramLayoutEngine.RouteInfo;
import org.apache.camel.dsl.jbang.core.commands.action.RouteDiagramLayoutEngine.TreeNode;
import org.apache.camel.dsl.jbang.core.commands.action.RouteDiagramRenderer.DiagramColors;
import org.apache.camel.dsl.jbang.core.common.Printer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CamelRouteDiagramActionTest {

    @Test
    void testBuildTreeEmpty() {
        assertNull(RouteDiagramLayoutEngine.buildTree(List.of()));
    }

    @Test
    void testBuildTreeSingleNode() {
        List<NodeInfo> nodes = List.of(node("from", "timer:tick", 0));
        TreeNode root = RouteDiagramLayoutEngine.buildTree(nodes);

        assertNotNull(root);
        assertEquals("from", root.info.type);
        assertTrue(root.children.isEmpty());
    }

    @Test
    void testBuildTreeSequential() {
        List<NodeInfo> nodes = List.of(
                node("from", "timer:tick", 0),
                node("to", "log:a", 1),
                node("to", "log:b", 1));
        TreeNode root = RouteDiagramLayoutEngine.buildTree(nodes);

        assertNotNull(root);
        assertEquals(2, root.children.size());
        assertEquals("log:a", root.children.get(0).info.code);
        assertEquals("log:b", root.children.get(1).info.code);
    }

    @Test
    void testBuildTreeBranching() {
        List<NodeInfo> nodes = List.of(
                node("from", "timer:tick", 0),
                node("choice", "choice()", 1),
                node("when", "when(simple(...))", 2),
                node("to", "log:a", 3),
                node("otherwise", "otherwise()", 2),
                node("to", "log:b", 3));
        TreeNode root = RouteDiagramLayoutEngine.buildTree(nodes);

        assertNotNull(root);
        assertEquals(1, root.children.size());
        TreeNode choice = root.children.get(0);
        assertEquals("choice", choice.info.type);
        assertEquals(2, choice.children.size());
        assertEquals("when", choice.children.get(0).info.type);
        assertEquals("otherwise", choice.children.get(1).info.type);
        assertEquals(1, choice.children.get(0).children.size());
        assertEquals(1, choice.children.get(1).children.size());
    }

    @Test
    void testBuildTreeWalkUpMultipleLevels() {
        List<NodeInfo> nodes = List.of(
                node("from", "timer:tick", 0),
                node("choice", "choice()", 1),
                node("when", "when(...)", 2),
                node("to", "log:deep", 3),
                node("to", "log:after-choice", 1));
        TreeNode root = RouteDiagramLayoutEngine.buildTree(nodes);

        assertNotNull(root);
        assertEquals(2, root.children.size());
        assertEquals("choice", root.children.get(0).info.type);
        assertEquals("log:after-choice", root.children.get(1).info.code);
    }

    @Test
    void testBuildTreeDeeplyNested() {
        List<NodeInfo> nodes = List.of(
                node("from", "timer:tick", 0),
                node("choice", "choice()", 1),
                node("when", "when(a)", 2),
                node("choice", "choice()", 3),
                node("when", "when(b)", 4),
                node("to", "log:deep", 5),
                node("to", "log:end", 1));
        TreeNode root = RouteDiagramLayoutEngine.buildTree(nodes);

        assertNotNull(root);
        assertEquals(2, root.children.size());
        TreeNode outerChoice = root.children.get(0);
        assertEquals("choice", outerChoice.info.type);
        TreeNode when = outerChoice.children.get(0);
        assertEquals(1, when.children.size());
        TreeNode innerChoice = when.children.get(0);
        assertEquals("choice", innerChoice.info.type);
        assertEquals("log:end", root.children.get(1).info.code);
    }

    @Test
    void testBuildTreeFromOnly() {
        List<NodeInfo> nodes = List.of(node("from", "timer:tick", 0));
        TreeNode root = RouteDiagramLayoutEngine.buildTree(nodes);

        assertNotNull(root);
        assertEquals("from", root.info.type);
        assertTrue(root.children.isEmpty());
    }

    @Test
    void testColorPresetDark() {
        DiagramColors colors = DiagramColors.parse("dark");
        assertNotNull(colors.getBg());
        assertNotNull(colors.getText());
        assertNotNull(colors.getNodeFrom());
        assertEquals(new Color(0x0d1117), colors.getBg());
        assertEquals(new Color(0xf0f6fc), colors.getText());
    }

    @Test
    void testColorPresetLight() {
        DiagramColors colors = DiagramColors.parse("light");
        assertEquals(new Color(0xf6f8fa), colors.getBg());
        assertEquals(new Color(0xf0f6fc), colors.getText());
    }

    @Test
    void testColorPresetTransparent() {
        DiagramColors colors = DiagramColors.parse("transparent");
        assertNull(colors.getBg());
        assertNotNull(colors.getText());
    }

    @Test
    void testColorCustomOverride() {
        DiagramColors colors = DiagramColors.parse("bg=#ff0000:from=#00ff00");
        assertEquals(new Color(0xff0000), colors.getBg());
        assertEquals(new Color(0x00ff00), colors.getNodeFrom());
        assertNotNull(colors.getText());
        assertNotNull(colors.getNodeTo());
    }

    @Test
    void testColorInvalidHexFallsBack() {
        DiagramColors colors = DiagramColors.parse("bg=notacolor");
        assertNull(colors.getBg());
    }

    @Test
    void testColorNewCategories() {
        DiagramColors colors = DiagramColors.parse("dark");
        assertNotNull(colors.getNodeTransform());
        assertNotNull(colors.getNodeProcessor());
        assertEquals(new Color(0x1b7c83), colors.getNodeTransform());
        assertEquals(new Color(0xbf4b8a), colors.getNodeProcessor());
    }

    @Test
    void testColorCustomTransformProcessor() {
        DiagramColors colors = DiagramColors.parse("transform=#aabbcc:processor=#ddeeff");
        assertEquals(new Color(0xaabbcc), colors.getNodeTransform());
        assertEquals(new Color(0xddeeff), colors.getNodeProcessor());
    }

    @Test
    void testColorByName() {
        DiagramColors colors = DiagramColors.parse("from=green:to=blue");
        assertNotNull(colors.getNodeFrom());
        assertNotNull(colors.getNodeTo());
    }

    @Test
    void testColorInvalidNameFallsBack() {
        DiagramColors colors = DiagramColors.parse("bg=nosuchcolor");
        assertNull(colors.getBg());
    }

    @Test
    void testCleanLabelNull() {
        assertEquals("", RouteDiagramLayoutEngine.cleanLabel(null));
    }

    @Test
    void testCleanLabelShort() {
        assertEquals("log:hello", RouteDiagramLayoutEngine.cleanLabel("log:hello"));
    }

    @Test
    void testCleanLabelLeadingDot() {
        assertEquals("to(\"log:a\")", RouteDiagramLayoutEngine.cleanLabel(".to(\"log:a\")"));
    }

    @Test
    void testCleanLabelPreservesLong() {
        String longLabel = "to(\"http://very-long-endpoint-url-that-exceeds-forty-characters\")";
        String result = RouteDiagramLayoutEngine.cleanLabel(longLabel);
        assertEquals(longLabel, result);
    }

    @Test
    void testLayoutSingleRoute() {
        RouteInfo route = new RouteInfo();
        route.routeId = "route1";
        route.nodes.add(node("from", "timer:tick", 0));
        route.nodes.add(node("to", "log:a", 1));

        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
        LayoutRoute lr = engine.layoutRoute(route, RouteDiagramLayoutEngine.PADDING);

        assertEquals(2, lr.nodes.size());
        assertTrue(lr.maxX > 0);
        assertTrue(lr.maxY > 0);

        LayoutNode fromNode = lr.nodes.get(0);
        LayoutNode toNode = lr.nodes.get(1);
        assertEquals("from", fromNode.type);
        assertEquals("to", toNode.type);
        assertEquals(fromNode.x, toNode.x);
        assertTrue(toNode.y > fromNode.y);
    }

    @Test
    void testLayoutBranchingRoute() {
        RouteInfo route = new RouteInfo();
        route.routeId = "route1";
        route.nodes.add(node("from", "timer:tick", 0));
        route.nodes.add(node("choice", "choice()", 1));
        route.nodes.add(node("when", "when(...)", 2));
        route.nodes.add(node("to", "log:a", 3));
        route.nodes.add(node("otherwise", "otherwise()", 2));
        route.nodes.add(node("to", "log:b", 3));

        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
        LayoutRoute lr = engine.layoutRoute(route, RouteDiagramLayoutEngine.PADDING);

        assertEquals(6, lr.nodes.size());

        LayoutNode whenNode = lr.nodes.get(2);
        LayoutNode otherwiseNode = lr.nodes.get(4);
        assertTrue(whenNode.x != otherwiseNode.x, "Branches should be at different x positions");
    }

    @Test
    void testLayoutEmptyRoute() {
        RouteInfo route = new RouteInfo();
        route.routeId = "empty";

        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
        LayoutRoute lr = engine.layoutRoute(route, 0);

        assertTrue(lr.nodes.isEmpty());
        assertTrue(lr.maxX > 0);
    }

    @Test
    void testRenderDiagramProducesImage() {
        System.setProperty("java.awt.headless", "true");

        RouteInfo route = new RouteInfo();
        route.routeId = "route1";
        route.nodes.add(node("from", "timer:tick", 0));
        route.nodes.add(node("to", "log:a", 1));

        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
        LayoutRoute lr = engine.layoutRoute(route, RouteDiagramLayoutEngine.PADDING);

        RouteDiagramRenderer renderer = new RouteDiagramRenderer();
        DiagramColors colors = DiagramColors.parse("dark");
        BufferedImage image = renderer.renderDiagram(List.of(lr), lr.maxY + RouteDiagramLayoutEngine.V_GAP, colors);

        assertNotNull(image);
        assertTrue(image.getWidth() > 0);
        assertTrue(image.getHeight() > 0);
        assertEquals(BufferedImage.TYPE_INT_RGB, image.getType());
    }

    @Test
    void testRenderDiagramTransparentBackground() {
        System.setProperty("java.awt.headless", "true");

        RouteInfo route = new RouteInfo();
        route.routeId = "route1";
        route.nodes.add(node("from", "timer:tick", 0));

        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
        LayoutRoute lr = engine.layoutRoute(route, RouteDiagramLayoutEngine.PADDING);

        RouteDiagramRenderer renderer = new RouteDiagramRenderer();
        DiagramColors colors = DiagramColors.parse("transparent");
        BufferedImage image = renderer.renderDiagram(List.of(lr), lr.maxY + RouteDiagramLayoutEngine.V_GAP, colors);

        assertEquals(BufferedImage.TYPE_INT_ARGB, image.getType());
    }

    @Test
    void testRenderDiagramExceedingMaxDimensionThrows() {
        System.setProperty("java.awt.headless", "true");

        LayoutRoute lr = new LayoutRoute();
        lr.routeId = "huge";
        lr.maxX = RouteDiagramRenderer.MAX_IMAGE_DIMENSION + 100;
        lr.maxY = 100;

        RouteDiagramRenderer renderer = new RouteDiagramRenderer();
        DiagramColors colors = DiagramColors.parse("dark");
        assertThrows(IllegalStateException.class,
                () -> renderer.renderDiagram(List.of(lr), 100, colors));
    }

    @Test
    void testTextDiagramSequential() {
        RouteInfo route = new RouteInfo();
        route.routeId = "route1";
        route.source = "test.yaml";
        route.nodes.add(node("from", "timer:tick", 0));
        route.nodes.add(node("to", "log:a", 1));
        route.nodes.add(node("to", "log:b", 1));

        StringBuilder sb = new StringBuilder();
        Printer printer = new CapturingPrinter(sb);

        RouteDiagramRenderer renderer = new RouteDiagramRenderer();
        renderer.printTextDiagram(List.of(route), printer);

        String output = sb.toString();
        assertTrue(output.contains("Route: route1 (test.yaml)"));
        assertTrue(output.contains("[from] timer:tick"));
        assertTrue(output.contains("[to] log:a"));
        assertTrue(output.contains("[to] log:b"));
        assertTrue(output.contains("├──") || output.contains("└──"));
    }

    @Test
    void testTextDiagramBranching() {
        RouteInfo route = new RouteInfo();
        route.routeId = "route1";
        route.nodes.add(node("from", "timer:tick", 0));
        route.nodes.add(node("choice", "choice()", 1));
        route.nodes.add(node("when", "when(...)", 2));
        route.nodes.add(node("to", "log:a", 3));
        route.nodes.add(node("otherwise", "otherwise()", 2));
        route.nodes.add(node("to", "log:b", 3));

        StringBuilder sb = new StringBuilder();
        Printer printer = new CapturingPrinter(sb);

        RouteDiagramRenderer renderer = new RouteDiagramRenderer();
        renderer.printTextDiagram(List.of(route), printer);

        String output = sb.toString();
        assertTrue(output.contains("[choice]"));
        assertTrue(output.contains("[when]"));
        assertTrue(output.contains("[otherwise]"));
        assertTrue(output.contains("│"), "Should contain tree continuation characters");
    }

    @Test
    void testTextDiagramEmptyRoute() {
        RouteInfo route = new RouteInfo();
        route.routeId = "empty";

        StringBuilder sb = new StringBuilder();
        Printer printer = new CapturingPrinter(sb);

        RouteDiagramRenderer renderer = new RouteDiagramRenderer();
        renderer.printTextDiagram(List.of(route), printer);

        String output = sb.toString();
        assertTrue(output.contains("Route: empty"));
    }

    @Test
    void testParseRoutesEmpty() {
        org.apache.camel.util.json.JsonObject jo = new org.apache.camel.util.json.JsonObject();
        CamelRouteDiagramAction action = new CamelRouteDiagramAction(null);
        List<RouteInfo> routes = action.parseRoutes(jo);
        assertTrue(routes.isEmpty());
    }

    @Test
    void testParseRoutesWithData() {
        org.apache.camel.util.json.JsonObject line1 = new org.apache.camel.util.json.JsonObject();
        line1.put("type", "from");
        line1.put("code", "timer:tick");
        line1.put("level", 0);

        org.apache.camel.util.json.JsonObject line2 = new org.apache.camel.util.json.JsonObject();
        line2.put("type", "to");
        line2.put("code", "log:a");
        line2.put("level", 1);

        org.apache.camel.util.json.JsonArray code = new org.apache.camel.util.json.JsonArray();
        code.add(line1);
        code.add(line2);

        org.apache.camel.util.json.JsonObject routeObj = new org.apache.camel.util.json.JsonObject();
        routeObj.put("routeId", "route1");
        routeObj.put("code", code);

        org.apache.camel.util.json.JsonArray routesArr = new org.apache.camel.util.json.JsonArray();
        routesArr.add(routeObj);

        org.apache.camel.util.json.JsonObject jo = new org.apache.camel.util.json.JsonObject();
        jo.put("routes", routesArr);

        CamelRouteDiagramAction action = new CamelRouteDiagramAction(null);
        List<RouteInfo> routes = action.parseRoutes(jo);

        assertEquals(1, routes.size());
        assertEquals("route1", routes.get(0).routeId);
        assertEquals(2, routes.get(0).nodes.size());
        assertEquals("from", routes.get(0).nodes.get(0).type);
        assertEquals("timer:tick", routes.get(0).nodes.get(0).code);
    }

    private static NodeInfo node(String type, String code, int level) {
        NodeInfo n = new NodeInfo();
        n.type = type;
        n.code = code;
        n.level = level;
        return n;
    }

    private static class CapturingPrinter implements Printer {
        private final StringBuilder sb;

        CapturingPrinter(StringBuilder sb) {
            this.sb = sb;
        }

        @Override
        public void println() {
            sb.append('\n');
        }

        @Override
        public void println(String line) {
            sb.append(line).append('\n');
        }

        @Override
        public void print(String output) {
            sb.append(output);
        }

        @Override
        public void printf(String format, Object... args) {
            sb.append(String.format(format, args));
        }
    }
}
