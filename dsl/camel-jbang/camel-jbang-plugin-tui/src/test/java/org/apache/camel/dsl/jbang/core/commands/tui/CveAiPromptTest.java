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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.apache.camel.dsl.jbang.core.common.cve.CamelAdvisory;
import org.apache.camel.dsl.jbang.core.common.cve.CveArtifact;
import org.apache.camel.dsl.jbang.core.common.cve.CveFinding;
import org.apache.camel.dsl.jbang.core.common.cve.CveScan;
import org.apache.camel.dsl.jbang.core.common.cve.CveUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25362: explain with AI (Shift+F8) on the CVE Audit tab, for the selected vulnerability.
 */
class CveAiPromptTest {

    private final List<String> asked = new ArrayList<>();
    private IntegrationInfo info;
    private MonitorContext ctx;

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
        info = new IntegrationInfo();
        info.pid = "1234";
        info.name = "xml-orders";
        ctx = new MonitorContext(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));
        ctx.selectedPid = "1234";
        ctx.askAiCallback = new MonitorContext.AskAi() {
            @Override
            public void fixProblem(Path file, int line, String problem, String lineText) {
                asked.add("fix: " + problem);
            }

            @Override
            public void ask(String question) {
                asked.add(question);
            }
        };
    }

    @Test
    void theQuestionHasTheSeverityArtifactsFixedVersionsAndDescription() {
        String question = CveAiPrompt.of(info, vulnerability("x".repeat(CveAiPrompt.MAX_DETAILS + 100)));

        assertThat(question)
                .startsWith("Explain the vulnerability CVE-2026-1234 (HIGH 9.8,"
                            + " CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H) in the dependencies of"
                            + " the running integration xml-orders: XML external entity expansion.\n")
                .contains("It affects: com.example:xml-parser:2.1.0, fixed in 2.1.4 (via camel-jacksonxml).\n")
                .contains("Used by the routes: yes, the routes have endpoints of camel-jacksonxml.\n")
                .contains("Fixed versions of all lines: 2.1.4, 2.2.0.\n")
                .contains("Also known as: GHSA-abcd-1234.\n")
                .contains("Published: 2026-11-01.\n")
                .contains("x".repeat(CveAiPrompt.MAX_DETAILS) + "...\n")
                .doesNotContain("x".repeat(CveAiPrompt.MAX_DETAILS + 1))
                // CAMEL-27328: the change that fixes it, for the AI to offer
                .contains("Suggested changes:\n- com.example:xml-parser: declare com.example:xml-parser:2.1.4 in the"
                          + " project (camel.jbang.dependencies or //DEPS) to use the fixed version instead of the one"
                          + " camel-jacksonxml brings in")
                .endsWith("Make no changes to the files unless I agree.");
    }

    @Test
    void aDeclaredDependencyIsFixedWhereTheProjectDeclaresIt() {
        CveFinding group = vulnerability("details");
        group.gavVia().clear();
        group.declared().add("com.example:xml-parser:2.1.0");

        assertThat(CveAiPrompt.of(info, group))
                .contains("It affects: com.example:xml-parser:2.1.0, fixed in 2.1.4 (declared by the project).\n")
                .contains("- com.example:xml-parser: use com.example:xml-parser:2.1.4 where the project declares it");
    }

    @Test
    void aCamelAdvisoryIsFixedByUpgradingCamel() {
        CveFinding group = new CveFinding(
                "CVE-2026-49098", "HIGH", null, "Kafka header injection", null, "2026-05-01",
                new ArrayList<>(List.of("org.apache.camel:camel-kafka:4.18.1")), new HashMap<>(), new ArrayList<>(),
                new ArrayList<>());
        group.fixFor().put("org.apache.camel:camel-kafka:4.18.1", "4.18.3");
        group.setCamelAdvisory(new CamelAdvisory(
                "4.14.8, 4.18.3 and 4.21.0", "4.18.3", "Upgrade.", "https://camel.apache.org/security/CVE-2026-49098.html"));

        assertThat(CveAiPrompt.of(info, group))
                .contains("Apache Camel advisory: upgrade to Camel 4.18.3; fixed in 4.14.8, 4.18.3 and 4.21.0."
                          + " Mitigation: Upgrade.\n")
                .contains("- org.apache.camel:camel-kafka: upgrade Camel to 4.18.3");
    }

    @Test
    void aVulnerabilityWithoutAFixSaysSo() {
        CveFinding group = new CveFinding(
                "CVE-2026-9", "LOW", null, null, null, null,
                List.of("com.example:lib:1.0"), Map.of(), List.of("CVE-2026-9"), List.of());

        assertThat(CveAiPrompt.of(info, group))
                .contains("It affects: com.example:lib:1.0, no fixed version known.\n")
                .contains("No fixed version is known.\n")
                .contains("- com.example:lib: no fixed version is known")
                .doesNotContain("Also known as");
    }

    @Test
    void theQuestionOfAnArtifactListsItsCvesAndTheVersionThatFixesAll() {
        CveFinding other = new CveFinding(
                "CVE-2026-5678", "MEDIUM", null, "Denial of service", null, null,
                new ArrayList<>(List.of("com.example:xml-parser:2.1.0")), new HashMap<>(), new ArrayList<>(),
                new ArrayList<>());
        other.fixFor().put("com.example:xml-parser:2.1.0", "2.1.6");
        List<CveArtifact> rows = CveScan.artifactRows(List.of(vulnerability("details"), other));

        assertThat(CveAiPrompt.ofArtifact(info, rows.get(0)))
                .startsWith("Explain the vulnerabilities of com.example:xml-parser:2.1.0, a dependency of the running"
                            + " integration xml-orders: 2 CVEs, the worst HIGH 9.8.\n")
                .contains("- CVE-2026-1234 (HIGH 9.8): XML external entity expansion, fixed in 2.1.4.\n")
                .contains("- CVE-2026-5678 (MEDIUM): Denial of service, fixed in 2.1.6.\n")
                .contains("It comes in via camel-jacksonxml.\n")
                .contains("Version 2.1.6 fixes all of them.\n")
                .contains("declare com.example:xml-parser:2.1.6 in the project");
    }

    @Test
    void shiftF8AsksAboutTheSelectedVulnerability() {
        CveAuditTab tab = new CveAuditTab(ctx);
        assertThat(fKeyHints(tab)).isEmpty();
        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.F8, KeyModifiers.SHIFT));
        assertThat(asked).isEmpty();

        tab.showForTesting(List.of(vulnerability("details")));
        assertThat(fKeyHints(tab)).contains("Shift+F8").contains("explain with AI");
        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.F8, KeyModifiers.SHIFT));
        assertThat(asked).singleElement().asString().startsWith("Explain the vulnerability CVE-2026-1234 (HIGH");

        // grouped by artifact (g), it asks about the selected JAR and all its CVEs
        tab.handleKeyEvent(KeyEvent.ofChar('g'));
        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.F8, KeyModifiers.SHIFT));
        assertThat(asked).hasSize(2).last().asString()
                .startsWith("Explain the vulnerabilities of com.example:xml-parser:2.1.0");
    }

    private static CveFinding vulnerability(String details) {
        CveFinding group = new CveFinding(
                "CVE-2026-1234", "HIGH", "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H", "XML external entity expansion",
                details, "2026-11-01", new ArrayList<>(List.of("com.example:xml-parser:2.1.0")),
                new HashMap<>(Map.of("com.example:xml-parser:2.1.0", "camel-jacksonxml")),
                new ArrayList<>(List.of("CVE-2026-1234", "GHSA-abcd-1234")), new ArrayList<>(List.of("2.1.4", "2.2.0")));
        group.fixFor().put("com.example:xml-parser:2.1.0", "2.1.4");
        group.usedBy().put("com.example:xml-parser:2.1.0", new CveUsage("camel-jacksonxml", true));
        group.setUsedByRoutes(true);
        return group;
    }

    private static String fKeyHints(MonitorTab tab) {
        List<Span> spans = new ArrayList<>();
        tab.renderFKeyHints(spans);
        StringBuilder sb = new StringBuilder();
        spans.forEach(s -> sb.append(s.content()));
        return sb.toString();
    }
}
