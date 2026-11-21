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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-27328: the CVE Audit tab by CVE and by artifact (g), its / filter, and what tui_get_table returns.
 */
class CveAuditTabViewsTest {

    private static final String SNAPPY = "org.xerial.snappy:snappy-java:1.1.10.1";
    private static final String JSON_SMART = "net.minidev:json-smart:2.4.8";

    private CveAuditTab tab;

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
        IntegrationInfo info = new IntegrationInfo();
        info.pid = "1234";
        info.name = "kafka-app";
        MonitorContext ctx = new MonitorContext(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));
        ctx.selectedPid = "1234";
        tab = new CveAuditTab(ctx);

        List<CveAuditTab.VulnGroup> groups = CveScan.buildVulnGroups(Map.of(
                SNAPPY, List.of(vuln("CVE-2024-1001", "HIGH", "org.xerial.snappy:snappy-java", "1.1.10.4"),
                        vuln("CVE-2024-1002", "MEDIUM", "org.xerial.snappy:snappy-java", "1.1.10.6")),
                JSON_SMART, List.of(vuln("CVE-2024-2001", "CRITICAL", "net.minidev:json-smart", "2.4.9"))),
                Map.of());
        Map<String, List<String>> chains = Map.of(
                "org.xerial.snappy:snappy-java",
                List.of("org.apache.camel:camel-kafka", "org.apache.kafka:kafka-clients"),
                "net.minidev:json-smart", List.of("org.apache.camel:camel-jsonpath"));
        CveScan.attachChains(groups, chains, Map.of(), Set.of());
        CveScan.markRoutesUsage(groups, chains, Set.of("kafka"),
                Map.of("camel-kafka", Set.of("kafka"), "camel-jsonpath", Set.of()));
        tab.showForTesting(new ArrayList<>(groups));
    }

    private static OsvClient.Vulnerability vuln(String id, String severity, String pkg, String fixed) {
        return new OsvClient.Vulnerability(
                id, "summary of " + id, null, severity, null, "2024-01-01", List.of(),
                List.of(fixed), List.of(new OsvClient.AffectedRange(pkg, null, fixed, null)));
    }

    @Test
    void byCveTheFixTheChainAndTheRoutes() {
        // the worst first
        assertThat(tab.visibleGroups()).extracting(g -> g.canonicalId)
                .containsExactly("CVE-2024-2001", "CVE-2024-1001", "CVE-2024-1002");
        String screen = TuiTestHelper.renderToString(tab, 200, 40);
        assertThat(screen).contains("FIX", "USED", "CVE-2024-1001", "1.1.10.4", "camel-kafka > kafka-clients")
                .contains("Used by the routes: no, the routes have no endpoint of camel-jsonpath");

        tab.navigateDown();
        screen = TuiTestHelper.renderToString(tab, 200, 40);
        assertThat(screen).contains("fixed in 1.1.10.4", "Used by the routes: yes, the routes have endpoints of camel-kafka");
    }

    @Test
    void gGroupsByArtifactWithTheVersionThatFixesAll() {
        assertThat(tab.handleKeyEvent(KeyEvent.ofChar('g'))).isTrue();

        String screen = TuiTestHelper.renderToString(tab, 200, 40);
        assertThat(screen).contains("FIXES ALL", "CVES", SNAPPY, "1.1.10.6");
        assertThat(tab.selectedRow()).isNotNull();
        assertThat(tab.selectedGroup()).isNull();
        assertThat(footer()).contains("by CVE");

        tab.handleKeyEvent(KeyEvent.ofChar('g'));
        assertThat(footer()).contains("by artifact");
    }

    @Test
    void theFilterNarrowsTheRowsAndEscClearsIt() {
        assertThat(footer()).contains("/").contains("filter");
        tab.handleKeyEvent(KeyEvent.ofChar('/'));
        assertThat(tab.isTextInputActive()).isTrue();
        for (char c : "snappy".toCharArray()) {
            tab.handleKeyEvent(KeyEvent.ofChar(c));
        }
        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER));
        assertThat(tab.isTextInputActive()).isFalse();
        assertThat(tab.visibleGroups()).extracting(g -> g.canonicalId)
                .containsExactly("CVE-2024-1001", "CVE-2024-1002");

        // the filter matches the chain too
        tab.setFilter("jsonpath");
        assertThat(tab.visibleGroups()).extracting(g -> g.canonicalId).containsExactly("CVE-2024-2001");
        tab.handleKeyEvent(KeyEvent.ofChar('g'));
        assertThat(tab.visibleRows()).extracting(r -> r.gav).containsExactly(JSON_SMART);

        assertThat(tab.handleEscape()).isTrue();
        assertThat(tab.visibleRows()).hasSize(2);
        // with no filter, Esc goes back
        assertThat(tab.handleEscape()).isFalse();
    }

    @Test
    void tuiGetTableHasTheFixesAndTheView() {
        JsonObject table = tab.getTableDataAsJson();
        assertThat(table.getString("view")).isEqualTo("cve");
        JsonObject first = (JsonObject) ((JsonArray) table.get("rows")).get(1);
        assertThat(first.getString("cveId")).isEqualTo("CVE-2024-1001");
        assertThat(first.getBoolean("usedByRoutes")).isTrue();
        JsonObject art = (JsonObject) ((JsonArray) first.get("affectedArtifacts")).get(0);
        assertThat(art.getString("fixedIn")).isEqualTo("1.1.10.4");
        assertThat(art.getString("via")).isEqualTo("camel-kafka > kafka-clients");

        tab.handleKeyEvent(KeyEvent.ofChar('g'));
        table = tab.getTableDataAsJson();
        assertThat(table.getString("view")).isEqualTo("artifact");
        JsonObject row = (JsonObject) ((JsonArray) table.get("rows")).get(0);
        assertThat(row.getString("artifact")).isEqualTo(JSON_SMART);
        assertThat(row.getString("fixesAll")).isEqualTo("2.4.9");
        assertThat(row.getBoolean("usedByRoutes")).isFalse();
    }

    private String footer() {
        List<Span> spans = new ArrayList<>();
        tab.renderFooter(spans);
        StringBuilder sb = new StringBuilder();
        spans.forEach(s -> sb.append(s.content()));
        return sb.toString();
    }
}
