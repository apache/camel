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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.List;
import java.util.Map;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import org.apache.camel.diagram.TopologyLayoutEngine;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyLayoutResult;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyNodeInfo;
import org.apache.camel.dsl.jbang.core.commands.tui.diagram.TopologyDiagramWidget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A topology box with descriptions on shows the description as a label of at most two lines and the route id beneath
 * it; a cut keeps the space between words.
 */
class TopologyLabelTest {

    @Test
    void labelThenRouteId() {
        TopologyNodeInfo node = new TopologyNodeInfo();
        node.routeId = "order-intake";
        node.description = "Receives HTTP orders, validates them, triggers invoicing and handles failures";
        node.from = "platform-http:/orders";
        node.nodeType = "route";
        TopologyLayoutEngine engine = new TopologyLayoutEngine();
        TopologyLayoutResult layout = engine.layout(List.of(node), List.of());
        TopologyDiagramWidget widget
                = new TopologyDiagramWidget(layout, engine.getNodeWidth(), 0, 0, 0, false, true);
        Rect area = new Rect(0, 0, 60, 12);
        Buffer buffer = Buffer.empty(area);
        widget.render(area, buffer);
        String screen = TuiTestHelper.bufferToString(buffer);
        assertTrue(screen.contains("order-intake"), "the route id beneath the label: " + screen);
        assertTrue(screen.contains("Receives HTTP"), screen);
        assertTrue(screen.contains("..."), "cut at two lines: " + screen);
    }

    @Test
    void groupTagUnderTheRouteId() {
        TopologyNodeInfo node = new TopologyNodeInfo();
        node.routeId = "order-intake";
        node.description = "Receives HTTP orders, validates them, triggers invoicing and handles failures";
        node.from = "platform-http:/orders";
        node.nodeType = "route";
        TopologyLayoutEngine engine = new TopologyLayoutEngine();
        TopologyLayoutResult layout = engine.layout(List.of(node), List.of());
        TopologyDiagramWidget widget = new TopologyDiagramWidget(layout, engine.getNodeWidth(), 0, 0, 0, true, true)
                .withGroups(Map.of("order-intake", new TopologyDiagramWidget.NodeLine("\u25b8 Order intake", Style.EMPTY)),
                        Map.of("order-intake", Color.GREEN));
        Rect area = new Rect(0, 0, 60, 12);
        Buffer buffer = Buffer.empty(area);
        widget.render(area, buffer);
        String screen = TuiTestHelper.bufferToString(buffer);
        assertTrue(screen.contains("order-intake"), "the id stays: " + screen);
        assertTrue(screen.contains("\u25b8 Order intake"), "the group: " + screen);
    }

    @Test
    void cutKeepsTheSpace() {
        List<String> lines = TopologyDiagramWidget.wrapText(
                "Logs incoming request bodies for debugging and monitoring purposes only", 20);
        assertEquals(3, lines.size());
        assertTrue(!lines.get(2).contains("andmon") && !lines.get(2).contains("formon"), lines.toString());
    }
}
