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
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.ai.IntegrationSummary;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectCapabilities;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The capability view of the Diagram tab (CAMEL-25147): groups as boxes with their facts, AI-assisted groups marked,
 * Enter opens a group's routes and a route's diagram, Esc goes back up, utility hidden until u.
 */
@Isolated
@Timeout(60)
class ArchitectureViewTest {

    private static final String ROUTES = """
            - route:
                id: invoice
                group: billing
                from:
                  uri: direct:invoice
                  steps:
                    - to: direct:audit
            - route:
                id: order
                from:
                  uri: platform-http:/orders
                  steps:
                    - to: direct:invoice
                    - to: direct:audit
                    - doTry:
                        steps:
                          - to: kafka:orders
                        doCatch:
                          - exception:
                              - java.lang.Exception
                            steps:
                              - to: direct:dlq
            - route:
                id: audit
                from:
                  uri: direct:audit
                  steps:
                    - to: mongodb:audit
            - route:
                id: dlq
                from:
                  uri: direct:dlq
                  steps:
                    - to: kafka:dlq
            """;

    @TempDir
    Path home;
    @TempDir
    Path project;

    private String originalHome;
    private final List<ProjectCapabilities.Group> opened = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        originalHome = CommandLineHelper.getHomeDir().toString();
        CommandLineHelper.useHomeDir(home.toString());
        IntegrationSummaryHints.resetForTesting();
        Files.writeString(project.resolve("shop.camel.yaml"), ROUTES, StandardCharsets.UTF_8);
        ProjectOverview.Overview o = ProjectOverview.analyze(project, new DefaultCamelCatalog());
        IntegrationSummary.write(o, new IntegrationSummary.AiContent(
                "A shop.",
                List.of(new IntegrationSummary.Capability("Order taking", List.of("order"), "Takes orders.")),
                Map.of("order", "Takes an order over HTTP.")), o.fingerprint(), "test-model");
    }

    @AfterEach
    void tearDown() {
        CommandLineHelper.useHomeDir(originalHome);
        IntegrationSummaryHints.resetForTesting();
    }

    private ArchitectureView openView() {
        ArchitectureView view = new ArchitectureView(null, opened::add);
        view.open(project);
        await().atMost(10, TimeUnit.SECONDS).until(() -> !view.isLoadingForTesting());
        return view;
    }

    private static String render(ArchitectureView view) {
        return render(view, 40);
    }

    private static String render(ArchitectureView view, int height) {
        Rect area = new Rect(0, 0, 160, height);
        Buffer buffer = Buffer.empty(area);
        view.render(Frame.forTesting(buffer), area, "shop");
        return TuiTestHelper.bufferToString(buffer);
    }

    @Test
    void showsGroupsWithTheirFacts() {
        ArchitectureView view = openView();
        assertTrue(view.isActive());
        assertEquals("group:billing", view.selectedForTesting(), "the first group is selected");
        String screen = render(view);
        assertTrue(screen.contains("Architecture"), screen);
        assertTrue(screen.contains("billing"), screen);
        assertTrue(screen.contains(IntegrationSummaryHints.MARK + "Order taking"), "AI group is marked: " + screen);
        assertTrue(screen.contains("Shared services"), screen);
        // the business view: what the capability achieves (the technical view shows entry points and systems)
        assertTrue(screen.contains(IntegrationSummaryHints.MARK + "Takes orders."), "what it achieves: " + screen);
        assertFalse(screen.contains("Utility"), "utility hidden by default: " + screen);
        // boxes are as wide as their longest line, not wider
        String boxTop = screen.lines().filter(l -> l.contains("\u250c")).findFirst().orElseThrow();
        int width = boxTop.indexOf('\u2510', boxTop.indexOf('\u250c')) - boxTop.indexOf('\u250c') + 1;
        assertTrue(width <= "platform-http:/orders".length() + 8, "box width " + width + ": " + screen);

        view.handleKeyEvent(KeyEvent.ofChar('u'));
        assertTrue(render(view).contains("Utility"));
    }

    @Test
    void enterGoesDownToTheTopologyWithTheGroup() {
        ArchitectureView view = openView();
        view.selectForTesting("capability:Order taking");
        view.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        // the Diagram tab gets the group, to highlight its routes in the topology of all routes
        assertEquals(1, opened.size());
        assertEquals("Order taking", opened.get(0).name());
        assertEquals(List.of("order"), opened.get(0).routes());
        assertTrue(view.isSuspended(), "left for the topology");
        // u in the topology changes the setting both levels share: the groups follow it on the way back
        view.setShowUtility(true);
        view.resume();
        assertEquals("capability:Order taking", view.selectedForTesting(), "back on the group it came from");
        assertTrue(render(view).contains("Utility"), "utility shown as set in the topology");
        // the groups are the top level: Esc is the tab's, the view stays
        assertFalse(view.handleEscape());
        assertTrue(view.isActive());
    }

    @Test
    void technicalViewShowsEntryPointsAndSystems() {
        boolean[] business = { true };
        ArchitectureView view = new ArchitectureView(null, g -> {
        }, () -> business[0]);
        view.open(project);
        await().atMost(10, TimeUnit.SECONDS).until(() -> !view.isLoadingForTesting());
        assertTrue(render(view).contains(IntegrationSummaryHints.MARK + "Takes orders."));

        business[0] = false;
        view.refresh();
        String screen = render(view);
        assertTrue(screen.contains("platform-http:/orders"), "the entry point: " + screen);
        assertFalse(screen.contains("Takes orders."), screen);
    }

    @Test
    void externalSystemsAsBoxesLinkedToTheirGroups() {
        ArchitectureView view = openView();
        assertTrue(systemBoxes(render(view)).isEmpty(), "no system boxes by default");

        view.handleKeyEvent(KeyEvent.ofChar('e'));
        assertTrue(view.isShowExternal());
        // systems messages go out to are drawn below the groups
        String boxes = systemBoxes(render(view, 80));
        assertTrue(boxes.contains("Platform HTTP"), "where orders come in from: " + boxes);
        assertTrue(boxes.contains("MongoDB"), "what shared services write to: " + boxes);
        assertTrue(boxes.contains("Kafka"), boxes);

        // a system is selectable: the Info panel tells who uses it, Enter highlights its routes in the topology
        view.selectForTesting(ArchitectureView.systemId("MongoDB", false));
        String screen = render(view, 80);
        assertTrue(screen.contains("messages go out"), screen);
        assertTrue(screen.contains("Used by"), screen);
        assertTrue(screen.contains("mongodb:audit \u2192 audit"), "its endpoint and route: " + screen);
        view.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        assertEquals(List.of("audit"), opened.get(opened.size() - 1).routes());
        view.resume();

        view.handleKeyEvent(KeyEvent.ofChar('e'));
        assertTrue(systemBoxes(render(view)).isEmpty(), "off again");
    }

    /** The lines of the dashed boxes: the external systems. */
    private static String systemBoxes(String screen) {
        return screen.lines().filter(l -> l.chars().filter(c -> c == '\u2506').count() >= 2)
                .collect(Collectors.joining("\n"));
    }

    @Test
    void factsAloneWhenTheAiIsNotShown() {
        ArchitectureView view = openView();
        assertTrue(render(view).contains(IntegrationSummaryHints.MARK + "Order taking"));

        IntegrationSummaryHints.setShown(false);
        String screen = render(view);
        assertFalse(screen.contains("Order taking"), "the AI's capability is gone: " + screen);
        assertFalse(screen.contains(IntegrationSummaryHints.MARK), "nothing marked as AI-assisted: " + screen);
        assertTrue(screen.contains("Other"), "its route is not placed by the facts: " + screen);
        assertTrue(screen.contains("billing"), "the source's group stays: " + screen);

        IntegrationSummaryHints.setShown(true);
        assertTrue(render(view).contains(IntegrationSummaryHints.MARK + "Order taking"), "back");
    }

    @Test
    void followsAChangedSummaryWithoutAKey() throws Exception {
        ArchitectureView view = openView();
        assertTrue(render(view).contains("Order taking"));

        // /overview writes a new summary while the view is shown
        ProjectOverview.Overview o = ProjectOverview.analyze(project, new DefaultCamelCatalog());
        IntegrationSummary.write(o, new IntegrationSummary.AiContent(
                "A shop.",
                List.of(new IntegrationSummary.Capability("Order handling", List.of("order"), "Handles orders.")),
                Map.of()), o.fingerprint(), "test-model");
        Files.setLastModifiedTime(project.resolve(IntegrationSummary.FILE_NAME),
                FileTime.fromMillis(System.currentTimeMillis() + 5000));
        view.checkNowForTesting();
        await().atMost(10, TimeUnit.SECONDS).until(() -> render(view).contains("Order handling"));
        assertFalse(render(view).contains("Order taking"));
    }

    @Test
    void noDirectory() {
        ArchitectureView view = new ArchitectureView(null, g -> {
        });
        view.open(null);
        assertTrue(render(view).contains("No source directory"));
    }
}
