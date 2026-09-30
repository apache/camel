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
import java.util.Set;
import java.util.stream.Collectors;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.ai.IntegrationSummary;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The group of each route for the topology's group setting (CAMEL-25147): route groups from the source, the AI's
 * capabilities (while its hints are on), and no tag for routes nothing placed.
 */
@Isolated
class RouteGroupsTest {

    @TempDir
    Path home;
    @TempDir
    Path project;

    private String originalHome;

    @BeforeEach
    void setUp() throws Exception {
        originalHome = CommandLineHelper.getHomeDir().toString();
        CommandLineHelper.useHomeDir(home.toString());
        RouteGroups.resetForTesting();
        Files.writeString(project.resolve("shop.camel.yaml"), """
                - route:
                    id: invoice
                    group: billing
                    from:
                      uri: direct:invoice
                      steps:
                        - to: kafka:invoices
                - route:
                    id: order
                    from:
                      uri: platform-http:/orders
                      steps:
                        - to: direct:invoice
                - route:
                    id: stray
                    from:
                      uri: direct:stray
                      steps:
                        - setBody:
                            constant: x
                """, StandardCharsets.UTF_8);
    }

    @AfterEach
    void tearDown() {
        CommandLineHelper.useHomeDir(originalHome);
        RouteGroups.resetForTesting();
    }

    @Test
    void groupsFromTheSourceAndTheAi() throws Exception {
        Map<String, RouteGroups.Tag> tags = RouteGroups.load(project);
        assertEquals("billing", tags.get("invoice").name());
        assertFalse(tags.get("invoice").ai());
        assertFalse(tags.containsKey("order"), "not placed yet: no tag");

        ProjectOverview.Overview o = ProjectOverview.analyze(project, new DefaultCamelCatalog());
        IntegrationSummary.write(o, new IntegrationSummary.AiContent(
                null,
                List.of(new IntegrationSummary.Capability("Order intake", List.of("order"), "Takes orders.")),
                Map.of()), o.fingerprint(), "m");
        tags = RouteGroups.load(project);
        assertEquals("Order intake", tags.get("order").name());
        assertTrue(tags.get("order").ai());
        assertFalse(tags.containsKey("stray"));
        assertTrue(RouteGroups.tagLines(tags).get("order").text().contains(IntegrationSummaryHints.MARK),
                "an AI group is marked in the tag");

        // the ai view setting off: the facts alone
        IntegrationSummaryHints.setShown(false);
        try {
            tags = RouteGroups.load(project);
            assertFalse(tags.containsKey("order"), "not placed by the facts");
            assertEquals("billing", tags.get("invoice").name());
        } finally {
            IntegrationSummaryHints.setShown(true);
        }
    }

    private static String text(Line line) {
        return line.spans().stream().map(Span::content).collect(Collectors.joining());
    }

    @Test
    void utilityRoutesAndTheLegend() {
        Map<String, RouteGroups.Tag> tags = Map.of(
                "invoice", new RouteGroups.Tag("group:billing", "billing", false, 0),
                "order", new RouteGroups.Tag("capability:Orders", "Orders", true, 1),
                "dlq", new RouteGroups.Tag("utility", "Utility", false, 3));
        assertEquals(Set.of("dlq"), RouteGroups.utility(tags));

        String all = text(RouteGroups.legend(tags, Set.of()));
        assertTrue(all.indexOf("billing") < all.indexOf("Orders") && all.indexOf("Orders") < all.indexOf("Utility"),
                "in the order of the architecture: " + all);
        assertTrue(all.contains(IntegrationSummaryHints.MARK + "Orders"), all);
        String shown = text(RouteGroups.legend(tags, Set.of("dlq")));
        assertFalse(shown.contains("Utility"), "hidden utility routes are not in the legend: " + shown);
        assertNull(RouteGroups.legend(Map.of(), Set.of()));
    }
}
