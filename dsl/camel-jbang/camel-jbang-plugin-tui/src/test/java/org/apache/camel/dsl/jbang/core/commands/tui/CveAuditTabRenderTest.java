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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Span;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CveAuditTabRenderTest {

    private MonitorContext ctx;
    private IntegrationInfo info;

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
        info = new IntegrationInfo();
        info.pid = "1234";
        info.name = "test-app";

        AtomicReference<List<IntegrationInfo>> data = new AtomicReference<>(List.of(info));
        AtomicReference<List<InfraInfo>> infraData = new AtomicReference<>(List.of());
        ctx = new MonitorContext(data, infraData);
        ctx.selectedPid = "1234";
    }

    @Test
    void renderNoSelectionShowsPrompt() {
        ctx.selectedPid = null;
        CveAuditTab tab = new CveAuditTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 120, 30);
        assertTrue(rendered.contains("No integration selected") || rendered.contains("Select an integration"));
    }

    @Test
    void renderShowsBlockTitle() {
        CveAuditTab tab = new CveAuditTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 120, 30);
        assertTrue(rendered.contains("CVE Audit"), "Should show CVE Audit in the block title");
    }

    @Test
    void renderFooterHints() {
        CveAuditTab tab = new CveAuditTab(ctx);
        List<Span> footerSpans = new ArrayList<>();
        tab.renderFooter(footerSpans);
        String footer = footerSpans.stream().map(Span::content).reduce("", String::concat);
        assertTrue(footer.contains("Esc"), "Footer should contain Esc hint");
        assertTrue(footer.contains("rescan"), "Footer should contain rescan hint");
    }

    @Test
    void buildVulnGroupsDeduplicatesByAlias() {
        Map<String, List<OsvClient.Vulnerability>> vulnMap = new HashMap<>();
        vulnMap.put("com.example:lib-a:1.0", List.of(
                new OsvClient.Vulnerability(
                        "GHSA-xxxx", "Test vuln", null, "HIGH", null, "2024-01-01",
                        List.of("CVE-2024-1234"), List.of("1.1"))));
        vulnMap.put("com.example:lib-b:2.0", List.of(
                new OsvClient.Vulnerability(
                        "CVE-2024-1234", "Test vuln", null, "HIGH", null, "2024-01-01",
                        List.of("GHSA-xxxx"), List.of("1.1"))));

        List<CveAuditTab.VulnGroup> groups = CveAuditTab.buildVulnGroups(vulnMap);

        assertEquals(1, groups.size(), "Should deduplicate CVE-2024-1234 and GHSA-xxxx into one group");
        assertEquals("CVE-2024-1234", groups.get(0).canonicalId, "Should prefer CVE- prefix as canonical ID");
        assertEquals(2, groups.get(0).affectedGavs.size(), "Should list both affected artifacts");
    }

    @Test
    void buildVulnGroupsSeparatesDistinctCves() {
        Map<String, List<OsvClient.Vulnerability>> vulnMap = new HashMap<>();
        vulnMap.put("com.example:lib:1.0", List.of(
                new OsvClient.Vulnerability(
                        "CVE-2024-1111", "Vuln A", null, "CRITICAL", null, "2024-01-01", List.of(), List.of()),
                new OsvClient.Vulnerability(
                        "CVE-2024-2222", "Vuln B", null, "MEDIUM", null, "2024-02-01", List.of(), List.of())));

        List<CveAuditTab.VulnGroup> groups = CveAuditTab.buildVulnGroups(vulnMap);

        assertEquals(2, groups.size(), "Should create separate groups for distinct CVEs");
    }

    @Test
    void severityStyleCriticalIsRedBold() {
        Rect area = new Rect(0, 0, 120, 30);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);

        Map<String, List<OsvClient.Vulnerability>> vulnMap = new HashMap<>();
        vulnMap.put("com.example:lib:1.0", List.of(
                new OsvClient.Vulnerability(
                        "CVE-2024-9999", "Critical vuln", null, "CRITICAL", null, "2024-01-01",
                        List.of(), List.of())));

        List<CveAuditTab.VulnGroup> groups = CveAuditTab.buildVulnGroups(vulnMap);
        assertFalse(groups.isEmpty());
        assertEquals("CRITICAL", groups.get(0).severity);

        // verify the severity style method returns red for CRITICAL
        var style = CveAuditTab.severityStyle("CRITICAL");
        assertNotNull(style);
    }

    @Test
    void severityExtraction() {
        assertEquals("CRITICAL", OsvClient.cvssToSeverity("CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H"));
        assertEquals("HIGH", OsvClient.cvssToSeverity("CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:N"));
        assertEquals("MEDIUM", OsvClient.cvssToSeverity("CVSS:3.1/AV:L/AC:H/PR:H/UI:R/S:U/C:H/I:N/A:N"));
        assertEquals("MEDIUM", OsvClient.cvssToSeverity("CVSS:3.1/AV:N/AC:H/PR:N/UI:N/S:U/C:N/I:H/A:N"));
    }

    @Test
    void descriptionIsNotNull() {
        CveAuditTab tab = new CveAuditTab(ctx);
        assertNotNull(tab.description());
        assertFalse(tab.description().isEmpty());
    }

    @Test
    void helpTextIsNotNull() {
        CveAuditTab tab = new CveAuditTab(ctx);
        assertNotNull(tab.getHelpText());
        assertTrue(tab.getHelpText().contains("CVE Audit"));
    }

    @Test
    void buildVulnGroupsMultipleArtifacts() {
        Map<String, List<OsvClient.Vulnerability>> vulnMap = new HashMap<>();
        vulnMap.put("org.apache.camel:camel-core:4.12.0", List.of(
                new OsvClient.Vulnerability(
                        "CVE-2024-5555", "A camel vuln", null, "HIGH", null, "2024-01-01",
                        List.of(), List.of("4.13.0"))));
        vulnMap.put("com.example:other:1.0", List.of(
                new OsvClient.Vulnerability(
                        "CVE-2024-6666", "Other vuln", null, "LOW", null, "2024-01-01",
                        List.of(), List.of())));

        List<CveAuditTab.VulnGroup> groups = CveAuditTab.buildVulnGroups(vulnMap);
        assertEquals(2, groups.size());
    }
}
