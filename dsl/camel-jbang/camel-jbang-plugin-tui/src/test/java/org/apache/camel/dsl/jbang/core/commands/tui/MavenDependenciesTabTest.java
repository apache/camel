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

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.apache.camel.dsl.jbang.core.common.outdated.CamelRelease;
import org.apache.camel.tooling.model.ReleaseModel;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CAMEL-27351: the Maven Dependencies tab with the versions Camel brings, the conflicts, the newer versions and the
 * Camel release on top.
 */
class MavenDependenciesTabTest {

    private MonitorContext ctx;

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
        IntegrationInfo info = new IntegrationInfo();
        info.pid = "1234";
        info.name = "orders";
        info.camelVersion = "4.18.1";
        ctx = new MonitorContext(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));
        ctx.selectedPid = "1234";
    }

    private static ReleaseModel release(String version, String kind) {
        ReleaseModel r = new ReleaseModel();
        r.setVersion(version);
        r.setKind(kind);
        r.setEol("lts".equals(kind) ? "2027-02-10" : null);
        return r;
    }

    private MavenDependenciesTab tab() {
        MavenDependenciesTab tab = new MavenDependenciesTab(ctx);
        CamelRelease release = CamelRelease.of("4.18.1",
                List.of(release("4.18.1", "lts"), release("4.18.3", "lts"), release("4.23.0", null)),
                LocalDate.parse("2026-11-21"));
        tab.showForTesting(
                List.of(new DependencyLoader.DepEntry("org.apache.camel", "camel-timer", "4.18.1"),
                        new DependencyLoader.DepEntry("org.apache.commons", "commons-text", "1.9"),
                        new DependencyLoader.DepEntry("org.yaml", "snakeyaml", "1.33")),
                "4.18.1", Map.of("org.yaml:snakeyaml", "2.5"),
                Map.of("org.yaml:snakeyaml", Set.of("1.33", "2.5")),
                Map.of("org.apache.commons:commons-text", List.of("1.10.0", "1.15.0"),
                        "org.yaml:snakeyaml", List.of("2.5", "2.7")),
                release);
        return tab;
    }

    @Test
    void theCamelReleaseOnTopAndTheColumns() {
        String rendered = TuiTestHelper.renderToString(tab(), 160, 20);
        assertTrue(rendered.contains("Camel 4.18.1 LTS, end of life 2027-02-10 · newer on this line: 4.18.3"),
                rendered);
        assertTrue(rendered.contains("CAMEL"), rendered);
        assertTrue(rendered.contains("STATUS"), rendered);
        assertTrue(rendered.contains("NEWER"), rendered);
        assertTrue(rendered.contains("LATEST"), rendered);
        assertTrue(rendered.contains("conflict"), rendered);
        assertTrue(rendered.contains("2.7 (major)"), rendered);
        assertTrue(rendered.contains("1.15.0"), rendered);
        // the line of the selected dependency (the first: camel-timer)
        assertTrue(rendered.contains("org.apache.camel:camel-timer:4.18.1: part of Camel 4.18.1"), rendered);
    }

    @Test
    void theOutdatedScope() {
        MavenDependenciesTab tab = tab();
        // all, camel, other, outdated
        for (int i = 0; i < 3; i++) {
            tab.handleKeyEvent(KeyEvent.ofChar('f'));
        }
        JsonObject table = tab.getTableDataAsJson();
        JsonArray rows = (JsonArray) table.get("rows");
        assertEquals(2, rows.size(), "camel-timer is not outdated");
        JsonObject yaml = (JsonObject) rows.get(1);
        assertEquals("org.yaml", yaml.getString("groupId"));
        assertEquals("conflict", yaml.getString("status"));
        assertEquals("2.5", yaml.getString("camelVersion"));
        assertEquals("2.7", yaml.getString("latest"));
        assertTrue(table.getBoolean("newerVersionsChecked"));
        assertNotNull(table.get("camel"));
    }

    @Test
    void theFooterAndTheAiPrompt() {
        MavenDependenciesTab tab = tab();
        List<String> asked = new ArrayList<>();
        ctx.askAiCallback = new MonitorContext.AskAi() {
            @Override
            public void fixProblem(Path file, int line, String problem, String lineText) {
            }

            @Override
            public void ask(String question) {
                asked.add(question);
            }
        };
        List<Span> spans = new ArrayList<>();
        tab.renderFooter(spans);
        String footer = spans.stream().map(Span::content).reduce("", String::concat);
        assertTrue(footer.contains("check again"), footer);
        assertTrue(footer.contains("ask AI"), footer);

        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.F8, KeyModifiers.SHIFT));
        assertEquals(1, asked.size());
        String prompt = asked.get(0);
        assertTrue(prompt.startsWith("Review the dependencies of the running integration orders."), prompt);
        assertTrue(prompt.contains("org.yaml:snakeyaml:1.33 (declared): loaded in 1.33 and 2.5"), prompt);
        assertTrue(prompt.contains("org.apache.commons:commons-text:1.9 (declared): Camel does not bring this library;"
                                   + " latest: 1.15.0"),
                prompt);
        assertTrue(prompt.endsWith("Make no changes to the files unless I agree."), prompt);
    }
}
