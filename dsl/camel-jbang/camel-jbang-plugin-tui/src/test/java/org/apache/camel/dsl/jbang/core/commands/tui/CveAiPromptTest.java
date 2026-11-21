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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
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
                .startsWith("Explain the vulnerability CVE-2026-1234 (HIGH, CVSS:3.1/AV:N/AC:L) in the dependencies of"
                            + " the running integration xml-orders: XML external entity expansion.\n")
                .contains("It affects: com.example:xml-parser:2.1.0 (via org.apache.camel:camel-jacksonxml:4.24.0).\n")
                .contains("Fixed in: 2.1.4, 2.2.0.\n")
                .contains("Also known as: GHSA-abcd-1234.\n")
                .contains("Published: 2026-11-01.\n")
                .contains("x".repeat(CveAiPrompt.MAX_DETAILS) + "...\n")
                .doesNotContain("x".repeat(CveAiPrompt.MAX_DETAILS + 1))
                .endsWith("Make no changes to the files unless I agree.");
    }

    @Test
    void aVulnerabilityWithoutAFixSaysSo() {
        CveAuditTab.VulnGroup group = new CveAuditTab.VulnGroup(
                "CVE-2026-9", "LOW", null, null, null, null,
                List.of("com.example:lib:1.0"), Map.of(), List.of("CVE-2026-9"), List.of());

        assertThat(CveAiPrompt.of(info, group))
                .contains("It affects: com.example:lib:1.0.\n")
                .contains("No fixed version is known.\n")
                .doesNotContain("Also known as");
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
    }

    private static CveAuditTab.VulnGroup vulnerability(String details) {
        return new CveAuditTab.VulnGroup(
                "CVE-2026-1234", "HIGH", "CVSS:3.1/AV:N/AC:L", "XML external entity expansion", details, "2026-11-01",
                List.of("com.example:xml-parser:2.1.0"),
                Map.of("com.example:xml-parser:2.1.0", "org.apache.camel:camel-jacksonxml:4.24.0"),
                List.of("CVE-2026-1234", "GHSA-abcd-1234"), List.of("2.1.4", "2.2.0"));
    }

    private static String fKeyHints(MonitorTab tab) {
        List<Span> spans = new ArrayList<>();
        tab.renderFKeyHints(spans);
        StringBuilder sb = new StringBuilder();
        spans.forEach(s -> sb.append(s.content()));
        return sb.toString();
    }
}
