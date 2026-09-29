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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyNodeInfo;
import org.apache.camel.dsl.jbang.core.commands.ai.IntegrationSummary;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A topology box shows the start of a route's description; the Info panel beside it shows all of it, word-wrapped, the
 * AI-assisted one marked (CAMEL-25143).
 */
@Isolated
class RouteInfoPanelDescriptionTest {

    private static final String AI_TEXT
            = "Receives an order over HTTP, validates, invoices and audits it, and parks it as failed when anything goes wrong.";

    @TempDir
    Path home;
    @TempDir
    Path project;

    private String originalHome;
    private MonitorContext ctx;
    private IntegrationInfo info;

    @BeforeEach
    void setUp() throws Exception {
        originalHome = CommandLineHelper.getHomeDir().toString();
        CommandLineHelper.useHomeDir(home.toString());
        IntegrationSummaryHints.resetForTesting();
        Files.writeString(project.resolve("orders.camel.yaml"), """
                - route:
                    id: order-intake
                    from:
                      uri: direct:orders
                      steps:
                        - log: "${body}"
                        - to: direct:next
                """, StandardCharsets.UTF_8);
        ProjectOverview.Overview o = ProjectOverview.analyze(project, new DefaultCamelCatalog());
        IntegrationSummary.write(o, new IntegrationSummary.AiContent(
                null, List.of(), Map.of("order-intake", AI_TEXT),
                List.of(), Map.of("order-intake", "Checked before billing starts.")), o.fingerprint(), "test-model");

        info = new IntegrationInfo();
        info.pid = "1";
        info.name = "orders";
        info.directory = project.toString();
        RouteInfo route = new RouteInfo();
        route.routeId = "order-intake";
        route.from = "direct://orders";
        route.state = "Started";
        info.routes.add(route);
        ctx = new MonitorContext(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));
        ctx.selectedPid = "1";
    }

    @AfterEach
    void tearDown() {
        CommandLineHelper.useHomeDir(originalHome);
        IntegrationSummaryHints.resetForTesting();
    }

    private String renderPanel() {
        Rect area = new Rect(0, 0, 30, 30);
        Buffer buffer = Buffer.empty(area);
        new DiagramDetailSupport(ctx, new DiagramSupport()).renderRouteInfoPanel(Frame.forTesting(buffer), area, info,
                "order-intake");
        return TuiTestHelper.bufferToString(buffer);
    }

    @Test
    void aiDescriptionIsShownInFull() {
        String panel = renderPanel();
        String flat = panel.replaceAll("[\\s│]+", " ");
        assertTrue(flat.contains(IntegrationSummaryHints.MARK + "Receives an order over HTTP,"), panel);
        assertTrue(flat.contains("parks it as failed when anything goes wrong."), "the whole text: " + panel);
        assertTrue(flat.contains(IntegrationSummaryHints.MARK + "Checked before billing starts."), "the note: " + panel);
    }

    @Test
    void routeDescriptionWinsTheAiNoteStays() {
        info.routes.get(0).description = "Takes orders.";
        String panel = renderPanel();
        assertTrue(panel.contains(" Takes orders."), "the route's own description, not marked: " + panel);
        assertTrue(!panel.contains(IntegrationSummaryHints.MARK + "Receives"), panel);
        // the route has no note of its own: the AI's is still shown, marked
        assertTrue(panel.contains(IntegrationSummaryHints.MARK + "Checked before billing"), panel);
    }

    @Test
    void topologyTitleIsMarkedWhenABoxShowsAnAiDescription() {
        DiagramSupport diagram = new DiagramSupport();
        TopologyNodeInfo node = new TopologyNodeInfo();
        node.routeId = "order-intake";
        node.from = "direct:orders";
        node.nodeType = "route";
        diagram.showCustomTopology(List.of(node), List.of(), Map.of(), null);
        diagram.setAiSourceDirectory(project);
        Line title = Line.from(Span.raw(" Topology "));

        assertTrue(!renderTopology(diagram, title).contains(IntegrationSummary.AI_MARK), "descriptions off");
        diagram.setShowDescription(true);
        assertTrue(renderTopology(diagram, title).contains(IntegrationSummary.AI_MARK), "an AI description is shown");
    }

    private static String renderTopology(DiagramSupport diagram, Line title) {
        Rect area = new Rect(0, 0, 100, 20);
        Buffer buffer = Buffer.empty(area);
        diagram.renderNativeDiagram(Frame.forTesting(buffer), area, title, false);
        return TuiTestHelper.bufferToString(buffer).lines().findFirst().orElse("");
    }

    @Test
    void wrapWords() {
        assertEquals(List.of("one two", "three", "averyverylongword"),
                DiagramDetailSupport.wrapWords("one two three averyverylongword", 8));
    }
}
