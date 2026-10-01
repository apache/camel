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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the source editor tells about a line from the rest of the project: problems marked as soon as a file opens, the
 * values of property placeholders, where a bean is declared (and a jump to it), and where an endpoint is used.
 */
class SourceEditorProjectInfoTest {

    @TempDir
    Path dir;

    private static SourceTab newTab() {
        return new SourceTab(new MonitorContext(new AtomicReference<>(List.of()), new AtomicReference<>(List.of())));
    }

    @Test
    void theProblemsOfAFileAreMarkedWhenItOpens() throws Exception {
        Path file = Files.writeString(dir.resolve("MyRoute.java"), "a\nb\nc\nd\n");
        SourceViewer viewer = new SourceViewer();
        viewer.setRouteValidator(content -> List.of("Line 2: timer: Unknown option 'peroid'", "Line 4: oops"));
        viewer.loadFile(file);

        assertThat(viewer.viewErrors()).containsOnlyKeys(1, 3);
        assertThat(viewer.viewErrors().get(1)).contains("peroid");

        // F9 goes to the next problem in the view, and around to the first
        viewer.goToLine(0);
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.F9));
        assertThat(viewer.getSelectedLine()).isEqualTo(1);
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.F9));
        assertThat(viewer.getSelectedLine()).isEqualTo(3);

        // the editor starts with them, before the first change
        viewer.enterEditMode();
        assertThat(viewer.inlineErrors()).containsOnlyKeys(1, 3);
    }

    @Test
    void aFileWithoutProblemsHasNoMarkers() throws Exception {
        Path file = Files.writeString(dir.resolve("MyRoute.java"), "a\n");
        SourceViewer viewer = new SourceViewer();
        viewer.setRouteValidator(content -> List.of());
        viewer.loadFile(file);
        assertThat(viewer.viewErrors()).isEmpty();
    }

    @Test
    void thePlaceholdersOfALineAreExplained() throws Exception {
        Files.writeString(dir.resolve("application.properties"), "orders.period = 500\n");
        SourceEditAssist assist = new SourceEditAssist(
                new MonitorContext(new AtomicReference<>(List.of()), new AtomicReference<>(List.of())));
        assist.setRootDir(dir);

        List<SourceViewer.DocEntry> docs = assist.placeholderDocs(
                "from(\"timer:orders?period={{orders.period}}&delay={{orders.delay:100}}&x={{env:HOME}}\")");
        assertThat(docs).extracting(SourceViewer.DocEntry::text).containsExactly(
                "{{orders.period}} = 500  (application.properties)",
                "{{orders.delay}} is not set in the project's properties: the default 100 is used",
                "{{env:HOME}} is read from the environment when the route starts");
        assertThat(docs.get(0).title()).isEqualTo("Placeholder");
        assertThat(assist.placeholderDocs("from(\"timer:orders\")")).isEmpty();
    }

    @Test
    void thePlaceholdersOfAMavenProjectComeFromItsResources() throws Exception {
        Files.writeString(dir.resolve("pom.xml"), "<project/>\n");
        Path resources = Files.createDirectories(dir.resolve("src/main/resources"));
        Files.writeString(resources.resolve("application.properties"), "timer.period=1s\n");
        SourceEditAssist assist = new SourceEditAssist(
                new MonitorContext(new AtomicReference<>(List.of()), new AtomicReference<>(List.of())));
        assist.setRootDir(dir);

        assertThat(assist.placeholderDocs("from(\"timer:foo?period={{timer.period}}\")"))
                .extracting(SourceViewer.DocEntry::text)
                .containsExactly("{{timer.period}} = 1s  (src/main/resources/application.properties)");
    }

    @Test
    void theBeansARouteRefersToAreFound() throws Exception {
        Files.writeString(dir.resolve("OrderService.java"), """
                package com.acme;

                import org.apache.camel.BindToRegistry;

                @BindToRegistry("orders")
                public class OrderService {
                    public String ship(String body) {
                        return body;
                    }
                }
                """);
        Files.writeString(dir.resolve("beans.camel.yaml"), """
                - beans:
                    - name: audit
                      type: "#class:com.acme.Audit"
                """);
        ProjectBeans beans = ProjectBeans.scan(List.of(dir.resolve("OrderService.java"), dir.resolve("beans.camel.yaml")));

        assertThat(beans.refOn("    .to(\"bean:orders?method=ship\")").line()).isEqualTo(4);
        assertThat(beans.refOn("    .bean(OrderService.class, \"ship\")").type()).isEqualTo("com.acme.OrderService");
        assertThat(beans.refOn("    .bean(\"orders\")").label()).isEqualTo("orders");
        assertThat(beans.refOn("            ref: audit").filePath()).endsWith("beans.camel.yaml");
        assertThat(beans.refOn("            ref: audit").type()).isEqualTo("com.acme.Audit");
        assertThat(beans.refOn("  <bean ref=\"orders\"/>").label()).isEqualTo("orders");
        assertThat(beans.refOn("    .to(\"log:info\")")).isNull();
        assertThat(beans.refOn("    .to(\"bean:unknown\")")).isNull();
        assertThat(ProjectBeans.describe(beans.refOn("    .to(\"bean:orders\")")))
                .isEqualTo("orders (com.acme.OrderService) is declared in OrderService.java:5 — Enter goes there");
    }

    @Test
    void aRouteLineThatRefersToABeanJumpsToIt() throws Exception {
        Files.writeString(dir.resolve("OrderService.java"), """
                package com.acme;

                @org.apache.camel.BindToRegistry("orders")
                public class OrderService {
                }
                """);
        Path route = Files.writeString(dir.resolve("route.camel.yaml"), """
                - route:
                    from:
                      uri: timer:tick
                      steps:
                        - to:
                            uri: bean:orders
                """);
        SourceTab tab = newTab();
        assertThat(tab.loadDirectory(dir)).isTrue();

        Map<Integer, SourceViewer.JumpLink> links = tab.computeJumpLinks(route);
        assertThat(links.get(5).routeId()).isEqualTo("orders");
        assertThat(links.get(5).filePath()).endsWith("OrderService.java");
        assertThat(links.get(5).targetLine()).isEqualTo(2);
    }

    @Test
    void theUsagesOfAnEndpointAreItsConsumersAndProducers() throws Exception {
        Files.writeString(dir.resolve("orders.camel.yaml"), """
                - route:
                    id: orders
                    from:
                      uri: timer:tick
                      steps:
                        - to:
                            uri: direct:billing
                - route:
                    id: refunds
                    from:
                      uri: timer:refund
                      steps:
                        - to:
                            uri: direct:billing
                """);
        Files.writeString(dir.resolve("billing.camel.yaml"), """
                - route:
                    id: billing
                    from:
                      uri: direct:billing
                      steps:
                        - to:
                            uri: log:billing
                """);
        SourceTab tab = newTab();
        assertThat(tab.loadDirectory(dir)).isTrue();
        tab.computeJumpLinks(dir.resolve("billing.camel.yaml"));

        assertThat(tab.usagesOf("direct:billing"))
                .extracting(i -> i.routeId() + " " + i.fromUri() + " @" + (i.fromLine() + 1))
                .containsExactlyInAnyOrder(
                        "billing from direct:billing @3",
                        "orders to direct:billing @7",
                        "refunds to direct:billing @14");
    }
}
