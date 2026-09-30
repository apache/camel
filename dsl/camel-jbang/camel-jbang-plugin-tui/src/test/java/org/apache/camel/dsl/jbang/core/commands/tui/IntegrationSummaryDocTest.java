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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.ai.IntegrationSummary;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The integration summary opened like a README in the markdown viewer (CAMEL-25143): tool comments left out, the AI
 * marking kept, out of date said in the title, and the facts when nothing is saved yet.
 */
class IntegrationSummaryDocTest {

    private static final String ROUTES = """
            - route:
                id: orders
                from:
                  uri: direct:orders
                  steps:
                    - to: direct:store
            - route:
                id: store
                from:
                  uri: direct:store
                  steps:
                    - to: log:store
            """;

    @TempDir
    Path project;

    private ProjectOverview.Overview write(String routes) throws Exception {
        Files.writeString(project.resolve("orders.camel.yaml"), routes, StandardCharsets.UTF_8);
        return ProjectOverview.analyze(project, new DefaultCamelCatalog());
    }

    @Test
    void savedSummaryWithoutToolComments() throws Exception {
        ProjectOverview.Overview o = write(ROUTES);
        IntegrationSummary.write(o, new IntegrationSummary.AiContent(
                "Takes orders.", List.of(),
                Map.of("store", "Order storage"), List.of(), Map.of("store", "Keeps every order | also old ones.")),
                o.fingerprint(), "m");
        IntegrationSummaryDoc.Doc doc = IntegrationSummaryDoc.load(project);
        assertTrue(doc.title().startsWith(IntegrationSummary.FILE_NAME), doc.title());
        assertFalse(doc.title().contains("out of date"), doc.title());
        assertFalse(doc.markdown().contains("<!--"), doc.markdown());
        assertTrue(doc.markdown().contains("## Overview " + IntegrationSummary.AI_MARK), "the marking stays");
        assertTrue(doc.markdown().contains("Takes orders."));
        // the routes table as a list, so a long note is not cut off in the viewer
        assertFalse(doc.markdown().contains("| Route | From |"), doc.markdown());
        assertTrue(doc.markdown().contains("- **store** \u2014 " + IntegrationSummaryHints.MARK + "Order storage: "
                                           + IntegrationSummaryHints.MARK + "Keeps every order \\| also old ones."),
                doc.markdown());
        assertTrue(doc.markdown().contains("- **orders** *(`direct:orders`, orders.camel.yaml:1)*"), doc.markdown());

        write(ROUTES.replace("log:store", "log:stored"));
        assertTrue(IntegrationSummaryDoc.load(project).title().contains("out of date: /overview refresh"));
    }

    @Test
    void factsWhenNothingIsSaved() throws Exception {
        write(ROUTES);
        IntegrationSummaryDoc.Doc doc = IntegrationSummaryDoc.load(project);
        assertTrue(doc.title().endsWith("(not saved)"), doc.title());
        assertTrue(doc.markdown().contains("Not saved yet: /overview"), doc.markdown());
        assertTrue(doc.markdown().contains("## Routes"));
        assertNull(IntegrationSummaryDoc.load(null));
    }

    @Test
    void opensInTheViewerAtTheSection() throws Exception {
        write(ROUTES);
        MonitorContext ctx = new MonitorContext(new AtomicReference<>(List.of()), new AtomicReference<>(List.of()));
        List<String> opened = new ArrayList<>();
        ctx.openMarkdownAtCallback = (title, markdown, heading) -> opened.add(heading);
        assertNull(IntegrationSummaryDoc.open(ctx, project, IntegrationSummaryDoc.ARCHITECTURE));
        assertEquals(List.of(IntegrationSummaryDoc.ARCHITECTURE), opened);
        assertTrue(IntegrationSummaryDoc.open(ctx, null, null).startsWith("No source directory"));
    }

    @Test
    void viewerScrollsToThePendingHeading() {
        StringBuilder md = new StringBuilder("# Top\n\n");
        for (int i = 0; i < 60; i++) {
            md.append("line ").append(i).append("\n\n");
        }
        md.append("## Architecture\n\nthe map\n");
        DocViewerPopup viewer = new DocViewerPopup();
        viewer.openMarkdownAt("t", md.toString(), IntegrationSummaryDoc.ARCHITECTURE);
        Rect area = new Rect(0, 0, 80, 20);
        viewer.render(Frame.forTesting(Buffer.empty(area)), area);
        Buffer buffer = Buffer.empty(area);
        viewer.render(Frame.forTesting(buffer), area);
        String screen = TuiTestHelper.bufferToString(buffer);
        assertTrue(screen.contains("Architecture"), screen);
        assertFalse(screen.contains("# Top") || screen.contains("line 0 "), screen);
    }
}
