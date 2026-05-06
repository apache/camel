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
import java.awt.image.BufferedImage;
import java.util.List;

import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutNode;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutRoute;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.NodeInfo;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.RouteInfo;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.TreeNode;
import org.apache.camel.diagram.RouteDiagramRenderer.DiagramColors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteDiagramTest {

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

        RouteDiagramRenderer renderer = new RouteDiagramRenderer();
        List<String> lines = renderer.printTextDiagram(List.of(route));

        String output = String.join("\n", lines);
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

        RouteDiagramRenderer renderer = new RouteDiagramRenderer();
        List<String> lines = renderer.printTextDiagram(List.of(route));

        String output = String.join("\n", lines);
        assertTrue(output.contains("[choice]"));
        assertTrue(output.contains("[when]"));
        assertTrue(output.contains("[otherwise]"));
        assertTrue(output.contains("│"), "Should contain tree continuation characters");
    }

    @Test
    void testTextDiagramEmptyRoute() {
        RouteInfo route = new RouteInfo();
        route.routeId = "empty";

        RouteDiagramRenderer renderer = new RouteDiagramRenderer();
        List<String> lines = renderer.printTextDiagram(List.of(route));

        String output = String.join("\n", lines);
        assertTrue(output.contains("Route: empty"));
    }

    @Test
    void testChoiceInsideDoTryNoSpuriousMergeConnection() {
        RouteInfo route = new RouteInfo();
        route.routeId = "route1";
        route.nodes.add(node("from", "timer:tryChoiceInside", 0));
        route.nodes.add(node("setHeader", "setHeader[type]", 1));
        route.nodes.add(node("doTry", "doTry", 1));
        route.nodes.add(node("choice", "choice()", 2));
        route.nodes.add(node("when", "when(header(type) == A)", 3));
        route.nodes.add(node("log", "log[Type A]", 4));
        route.nodes.add(node("otherwise", "otherwise()", 3));
        route.nodes.add(node("log", "log[Other type]", 4));
        route.nodes.add(node("throwException", "throwException[java.lang.Exception]", 4));
        route.nodes.add(node("doCatch", "doCatch[java.lang.Exception]", 2));
        route.nodes.add(node("log", "log[Err: ${exception.message}]", 3));
        route.nodes.add(node("log", "log[Do other processing...]", 1));

        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
        LayoutRoute lr = engine.layoutRoute(route, RouteDiagramLayoutEngine.PADDING);

        LayoutNode logAfter = lr.nodes.get(lr.nodes.size() - 1);
        assertEquals("log[Do other processing...]", String.join("", logAfter.wrappedLines));
        assertTrue(logAfter.connectFromMerge, "Should connect from doTry merge point");

        LayoutNode choiceNode = lr.nodes.stream()
                .filter(n -> "choice".equals(n.type))
                .findFirst().orElseThrow();
        TreeNode choiceTn = choiceNode.treeNode;
        assertTrue(RouteDiagramLayoutEngine.isBranchingEip(choiceTn.parent.info.type),
                "Choice parent (doTry) should be a branching EIP");
    }

    @Test
    void testFilterAsScopeEip() {
        RouteInfo route = new RouteInfo();
        route.routeId = "route1";
        route.nodes.add(node("from", "direct:start", 0));
        route.nodes.add(node("setBody", "setBody[simple{Hello}]", 1));
        route.nodes.add(node("filter", "filter[{header(x) == value}]", 1));
        route.nodes.add(node("removeHeader", "removeHeader[x-another-header]", 2));
        route.nodes.add(node("to", "to[log:after]", 1));

        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
        LayoutRoute lr = engine.layoutRoute(route, RouteDiagramLayoutEngine.PADDING);

        assertEquals(5, lr.nodes.size());

        LayoutNode toNode = lr.nodes.get(4);
        assertEquals("to[log:after]", String.join("", toNode.wrappedLines));
        assertTrue(toNode.connectFromMerge, "Node after filter should connect from merge point");

        LayoutNode filterNode = lr.nodes.get(2);
        assertTrue(RouteDiagramLayoutEngine.hasScope(filterNode.treeNode));

        LayoutNode removeNode = lr.nodes.get(3);
        assertEquals(filterNode.x, removeNode.x, "Filter child should be in same column (sequential)");
    }

    @Test
    void testSplitAsScopeEip() {
        RouteInfo route = new RouteInfo();
        route.routeId = "route1";
        route.nodes.add(node("from", "direct:start", 0));
        route.nodes.add(node("split", "split[body()]", 1));
        route.nodes.add(node("log", "log[${body}]", 2));
        route.nodes.add(node("to", "to[mock:end]", 1));

        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
        LayoutRoute lr = engine.layoutRoute(route, RouteDiagramLayoutEngine.PADDING);

        LayoutNode toNode = lr.nodes.get(3);
        assertTrue(toNode.connectFromMerge, "Node after split should connect from merge point");
    }

    @Test
    void testFilterLastChildNoMerge() {
        RouteInfo route = new RouteInfo();
        route.routeId = "route1";
        route.nodes.add(node("from", "direct:start", 0));
        route.nodes.add(node("filter", "filter[{header(x)}]", 1));
        route.nodes.add(node("log", "log[filtered]", 2));

        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
        LayoutRoute lr = engine.layoutRoute(route, RouteDiagramLayoutEngine.PADDING);

        assertEquals(3, lr.nodes.size());
        LayoutNode logNode = lr.nodes.get(2);
        assertNull(logNode.parentNode == null ? null : (logNode.connectFromMerge ? "merge" : null),
                "Filter as last child should not produce merge connection on its children");
    }

    @Test
    void testCustomBoxWidthLayout() {
        RouteInfo route = new RouteInfo();
        route.routeId = "route1";
        route.nodes.add(node("from", "timer:tick", 0));
        route.nodes.add(node("to", "log:a", 1));

        RouteDiagramLayoutEngine defaultEngine = new RouteDiagramLayoutEngine();
        LayoutRoute defaultLr = defaultEngine.layoutRoute(route, RouteDiagramLayoutEngine.PADDING);

        RouteDiagramLayoutEngine wideEngine = new RouteDiagramLayoutEngine(250, 12);
        LayoutRoute wideLr = wideEngine.layoutRoute(route, RouteDiagramLayoutEngine.PADDING);

        assertTrue(wideLr.maxX > defaultLr.maxX, "Wider box should produce wider layout");
        assertEquals(250 * RouteDiagramLayoutEngine.SCALE, wideEngine.getNodeWidth());
    }

    @Test
    void testCustomFontSizeLayout() {
        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine(180, 16);
        assertTrue(engine.getBaseNodeHeight() > new RouteDiagramLayoutEngine().getBaseNodeHeight(),
                "Larger font should produce taller base node height");
    }

    @Test
    void testTextWrappingShortLabel() {
        RouteInfo route = new RouteInfo();
        route.routeId = "route1";
        route.nodes.add(node("from", "timer:tick", 0));

        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
        LayoutRoute lr = engine.layoutRoute(route, RouteDiagramLayoutEngine.PADDING);

        LayoutNode fromNode = lr.nodes.get(0);
        assertEquals(1, fromNode.wrappedLines.size());
        assertEquals(engine.getBaseNodeHeight(), fromNode.height);
    }

    @Test
    void testTextWrappingLongLabel() {
        RouteInfo route = new RouteInfo();
        route.routeId = "route1";
        route.nodes
                .add(node("from", "kafka:my-topic?brokers=localhost:9092&groupId=myConsumerGroup&autoOffsetReset=earliest", 0));

        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
        LayoutRoute lr = engine.layoutRoute(route, RouteDiagramLayoutEngine.PADDING);

        LayoutNode fromNode = lr.nodes.get(0);
        assertTrue(fromNode.wrappedLines.size() > 1, "Long label should wrap to multiple lines");
        assertTrue(fromNode.height > engine.getBaseNodeHeight(), "Wrapped node should be taller");
    }

    @Test
    void testPerNodeHeightAffectsNextNodePosition() {
        RouteInfo route = new RouteInfo();
        route.routeId = "route1";
        route.nodes
                .add(node("from", "kafka:my-topic?brokers=localhost:9092&groupId=myConsumerGroup&autoOffsetReset=earliest", 0));
        route.nodes.add(node("to", "log:a", 1));

        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
        LayoutRoute lr = engine.layoutRoute(route, RouteDiagramLayoutEngine.PADDING);

        LayoutNode fromNode = lr.nodes.get(0);
        LayoutNode toNode = lr.nodes.get(1);
        assertEquals(fromNode.y + fromNode.height + RouteDiagramLayoutEngine.V_GAP, toNode.y,
                "Next node Y should account for wrapped node height");
    }

    @Test
    void testRenderDiagramWithWrappedNodes() {
        System.setProperty("java.awt.headless", "true");

        RouteInfo route = new RouteInfo();
        route.routeId = "route1";
        route.nodes
                .add(node("from", "kafka:my-topic?brokers=localhost:9092&groupId=myConsumerGroup&autoOffsetReset=earliest", 0));
        route.nodes.add(node("to", "log:a", 1));

        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
        LayoutRoute lr = engine.layoutRoute(route, RouteDiagramLayoutEngine.PADDING);

        RouteDiagramRenderer renderer = new RouteDiagramRenderer();
        DiagramColors colors = DiagramColors.parse("dark");
        BufferedImage image = renderer.renderDiagram(List.of(lr), lr.maxY + RouteDiagramLayoutEngine.V_GAP, colors);

        assertNotNull(image);
        assertTrue(image.getWidth() > 0);
        assertTrue(image.getHeight() > 0);
    }

    @Test
    void testRenderDiagramWithCustomDimensions() {
        System.setProperty("java.awt.headless", "true");

        RouteInfo route = new RouteInfo();
        route.routeId = "route1";
        route.nodes.add(node("from", "timer:tick", 0));
        route.nodes.add(node("to", "log:a", 1));

        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine(250, 16);
        LayoutRoute lr = engine.layoutRoute(route, RouteDiagramLayoutEngine.PADDING);

        RouteDiagramRenderer renderer = new RouteDiagramRenderer(engine.getNodeWidth(), 16 * RouteDiagramLayoutEngine.SCALE);
        DiagramColors colors = DiagramColors.parse("dark");
        BufferedImage image = renderer.renderDiagram(List.of(lr), lr.maxY + RouteDiagramLayoutEngine.V_GAP, colors);

        assertNotNull(image);
        assertTrue(image.getWidth() > 0);
        assertTrue(image.getHeight() > 0);
    }

    @Test
    void testNodeLabelModeDescription() {
        RouteInfo route = new RouteInfo();
        route.routeId = "route1";
        route.nodes.add(node("from", "timer:tick?period=1000", 0, "Poll every second"));
        route.nodes.add(node("to", "log:a", 1, null));

        RouteDiagramLayoutEngine engine
                = new RouteDiagramLayoutEngine(180, 12, RouteDiagramLayoutEngine.NodeLabelMode.DESCRIPTION);
        LayoutRoute lr = engine.layoutRoute(route, RouteDiagramLayoutEngine.PADDING);

        assertEquals("Poll every second", String.join("", lr.nodes.get(0).wrappedLines));
        assertEquals("log:a", String.join("", lr.nodes.get(1).wrappedLines));
    }

    @Test
    void testNodeLabelModeDescriptionFallsBackToCode() {
        RouteInfo route = new RouteInfo();
        route.routeId = "route1";
        route.nodes.add(node("from", "timer:tick", 0, null));

        RouteDiagramLayoutEngine engine
                = new RouteDiagramLayoutEngine(180, 12, RouteDiagramLayoutEngine.NodeLabelMode.DESCRIPTION);
        LayoutRoute lr = engine.layoutRoute(route, RouteDiagramLayoutEngine.PADDING);

        assertEquals("timer:tick", String.join("", lr.nodes.get(0).wrappedLines));
    }

    @Test
    void testNodeLabelModeBoth() {
        RouteInfo route = new RouteInfo();
        route.routeId = "route1";
        route.nodes.add(node("from", "timer:tick?period=1000", 0, "Poll every second"));
        route.nodes.add(node("to", "log:a", 1, null));

        RouteDiagramLayoutEngine engine
                = new RouteDiagramLayoutEngine(180, 12, RouteDiagramLayoutEngine.NodeLabelMode.BOTH);
        LayoutRoute lr = engine.layoutRoute(route, RouteDiagramLayoutEngine.PADDING);

        List<String> fromLines = lr.nodes.get(0).wrappedLines;
        assertTrue(fromLines.size() >= 2, "Both mode should produce at least 2 lines when description differs from code");
        String allFromText = String.join("", fromLines);
        assertTrue(allFromText.contains("Poll every second"));
        assertTrue(allFromText.contains("timer:tick"));

        assertEquals("log:a", String.join("", lr.nodes.get(1).wrappedLines));
    }

    @Test
    void testNodeLabelModeCodeDefault() {
        RouteInfo route = new RouteInfo();
        route.routeId = "route1";
        route.nodes.add(node("from", "timer:tick", 0, "Poll every second"));

        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
        LayoutRoute lr = engine.layoutRoute(route, RouteDiagramLayoutEngine.PADDING);

        assertEquals("timer:tick", String.join("", lr.nodes.get(0).wrappedLines));
    }

    @Test
    void testTextDiagramWithDescriptionMode() {
        RouteInfo route = new RouteInfo();
        route.routeId = "route1";
        route.nodes.add(node("from", "timer:tick?period=1000", 0, "Poll every second"));
        route.nodes.add(node("to", "log:a", 1, null));

        RouteDiagramRenderer renderer = new RouteDiagramRenderer();
        List<String> lines
                = renderer.printTextDiagram(List.of(route), RouteDiagramLayoutEngine.NodeLabelMode.DESCRIPTION);

        String output = String.join("\n", lines);
        assertTrue(output.contains("Poll every second"));
        assertTrue(output.contains("log:a"));
    }

    @Test
    void testTextDiagramWithBothMode() {
        RouteInfo route = new RouteInfo();
        route.routeId = "route1";
        route.nodes.add(node("from", "timer:tick", 0, "Poll every second"));
        route.nodes.add(node("to", "log:a", 1, null));

        RouteDiagramRenderer renderer = new RouteDiagramRenderer();
        List<String> lines = renderer.printTextDiagram(List.of(route), RouteDiagramLayoutEngine.NodeLabelMode.BOTH);

        String output = String.join("\n", lines);
        assertTrue(output.contains("Poll every second"));
        assertTrue(output.contains("timer:tick"));
        assertTrue(output.contains("log:a"));
    }

    @Test
    void testRenderDiagramWithDescriptionMode() {
        System.setProperty("java.awt.headless", "true");

        RouteInfo route = new RouteInfo();
        route.routeId = "route1";
        route.nodes.add(node("from", "timer:tick?period=1000", 0, "Poll every second"));
        route.nodes.add(node("to", "log:a", 1, null));

        RouteDiagramLayoutEngine engine
                = new RouteDiagramLayoutEngine(180, 12, RouteDiagramLayoutEngine.NodeLabelMode.DESCRIPTION);
        LayoutRoute lr = engine.layoutRoute(route, RouteDiagramLayoutEngine.PADDING);

        RouteDiagramRenderer renderer = new RouteDiagramRenderer();
        DiagramColors colors = DiagramColors.parse("dark");
        BufferedImage image = renderer.renderDiagram(List.of(lr), lr.maxY + RouteDiagramLayoutEngine.V_GAP, colors);

        assertNotNull(image);
        assertTrue(image.getWidth() > 0);
        assertTrue(image.getHeight() > 0);
    }

    @Test
    void testWrapLabelWithEmoji() {
        RouteInfo route = new RouteInfo();
        route.routeId = "route1";
        route.nodes.add(node("to", "Send order 📦 to warehouse processing system queue", 0));

        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
        LayoutRoute lr = engine.layoutRoute(route, RouteDiagramLayoutEngine.PADDING);

        LayoutNode n = lr.nodes.get(0);
        String rejoined = String.join("", n.wrappedLines);
        assertTrue(rejoined.contains("📦"), "Emoji must not be split across lines");
        for (String line : n.wrappedLines) {
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (Character.isHighSurrogate(c)) {
                    assertTrue(i + 1 < line.length() && Character.isLowSurrogate(line.charAt(i + 1)),
                            "High surrogate at " + i + " must be followed by low surrogate in: " + line);
                } else if (Character.isLowSurrogate(c)) {
                    assertTrue(i > 0 && Character.isHighSurrogate(line.charAt(i - 1)),
                            "Low surrogate at " + i + " must be preceded by high surrogate in: " + line);
                }
            }
        }
    }

    @Test
    void testWrapLabelWithCombiningMarks() {
        RouteInfo route = new RouteInfo();
        route.routeId = "route1";
        route.nodes.add(node("to", "Résumé café order processing endpoint", 0));

        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
        LayoutRoute lr = engine.layoutRoute(route, RouteDiagramLayoutEngine.PADDING);

        LayoutNode n = lr.nodes.get(0);
        String rejoined = String.join("", n.wrappedLines);
        assertTrue(rejoined.contains("é"), "Combining accent must stay attached to base character");
    }

    @Test
    void testWrapLabelWithZwjEmoji() {
        RouteInfo route = new RouteInfo();
        route.routeId = "route1";
        route.nodes.add(node("to", "Family 👨‍👩‍👧‍👦 order processing", 0));

        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
        LayoutRoute lr = engine.layoutRoute(route, RouteDiagramLayoutEngine.PADDING);

        LayoutNode n = lr.nodes.get(0);
        String rejoined = String.join("", n.wrappedLines);
        assertTrue(rejoined.contains("👨"), "ZWJ emoji must not be split");
    }

    @Test
    void testMinimumNodeWidthClamped() {
        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine(50, 12);
        assertEquals(80 * RouteDiagramLayoutEngine.SCALE, engine.getNodeWidth(),
                "Node width below 80 should be clamped to 80");
    }

    @Test
    void testBranchGapScalesWithNodeWidth() {
        RouteInfo route = new RouteInfo();
        route.routeId = "route1";
        route.nodes.add(node("from", "timer:tick", 0));
        route.nodes.add(node("choice", "choice()", 1));
        route.nodes.add(node("when", "when(a)", 2));
        route.nodes.add(node("to", "log:a", 3));
        route.nodes.add(node("otherwise", "otherwise()", 2));
        route.nodes.add(node("to", "log:b", 3));

        RouteDiagramLayoutEngine defaultEngine = new RouteDiagramLayoutEngine();
        LayoutRoute defaultLr = defaultEngine.layoutRoute(route, RouteDiagramLayoutEngine.PADDING);

        RouteDiagramLayoutEngine wideEngine = new RouteDiagramLayoutEngine(300, 12);
        LayoutRoute wideLr = wideEngine.layoutRoute(route, RouteDiagramLayoutEngine.PADDING);

        LayoutNode defaultWhen = defaultLr.nodes.get(2);
        LayoutNode defaultOtherwise = defaultLr.nodes.get(4);
        int defaultGap = defaultOtherwise.x - (defaultWhen.x + defaultEngine.getNodeWidth());

        LayoutNode wideWhen = wideLr.nodes.get(2);
        LayoutNode wideOtherwise = wideLr.nodes.get(4);
        int wideGap = wideOtherwise.x - (wideWhen.x + wideEngine.getNodeWidth());

        assertTrue(wideGap > defaultGap,
                "Branch gap should increase with wider nodes (default=" + defaultGap + ", wide=" + wideGap + ")");
    }

    @Test
    void testNodeTextPaddingScalesWithNodeWidth() {
        RouteDiagramLayoutEngine defaultEngine = new RouteDiagramLayoutEngine();
        RouteDiagramLayoutEngine wideEngine = new RouteDiagramLayoutEngine(300, 12);

        assertTrue(wideEngine.getNodeTextPadding() > defaultEngine.getNodeTextPadding(),
                "Text padding should increase with wider nodes");
    }

    @Test
    void testExtractSourceNameWithScheme() {
        assertEquals("my-route.yaml", RouteDiagramHelper.extractSourceName("file:/path/to/my-route.yaml"));
    }

    @Test
    void testExtractSourceNameClasspath() {
        assertEquals("my-route.yaml", RouteDiagramHelper.extractSourceName("classpath:my-route.yaml"));
    }

    @Test
    void testExtractSourceNameWithLineNumber() {
        assertEquals("cheese.java:9", RouteDiagramHelper.extractSourceName("cheese.java:9"));
    }

    @Test
    void testExtractSourceNameSchemeAndLineNumber() {
        assertEquals("cheese.java:9", RouteDiagramHelper.extractSourceName("file:/path/to/cheese.java:9"));
    }

    @Test
    void testExtractSourceNamePlainFilename() {
        assertEquals("my-route.yaml", RouteDiagramHelper.extractSourceName("my-route.yaml"));
    }

    @Test
    void testExtractSourceNameWindowsPath() {
        assertEquals("route.yaml", RouteDiagramHelper.extractSourceName("C:\\Users\\test\\route.yaml"));
    }

    @Test
    void testExtractSourceNameNull() {
        assertNull(RouteDiagramHelper.extractSourceName(null));
    }

    @Test
    void testExtractSourceNameBlank() {
        assertNull(RouteDiagramHelper.extractSourceName(""));
    }

    private static NodeInfo node(String type, String code, int level) {
        return node(type, code, level, null);
    }

    private static NodeInfo node(String type, String code, int level, String description) {
        NodeInfo n = new NodeInfo();
        n.type = type;
        n.code = code;
        n.description = description;
        n.level = level;
        return n;
    }
}
