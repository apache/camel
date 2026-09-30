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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.camel.dsl.jbang.core.commands.ai.IntegrationSummary.AiContent;
import org.apache.camel.dsl.jbang.core.commands.ai.IntegrationSummary.Capability;
import org.apache.camel.dsl.jbang.core.commands.ai.IntegrationSummary.Summary;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview.Overview;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The integration summary file (CAMEL-25143): AI content fenced and marked apart from the facts, read back by tools,
 * and the model's answer parsed tolerantly.
 */
class IntegrationSummaryTest {

    private static final String ANSWER = """
            Here is the summary.

            **OVERVIEW:**
            The project takes orders over HTTP and processes them
            through Kafka.

            ## Capabilities
            1. **Order intake**: intake, audit | Accepts and records new orders.
            2. Processing: `process-order`, urgent, made-up | Routes orders by priority.

            DESCRIPTIONS:
            - `audit`: Writes each order to the audit log.
            - intake: Should be dropped, intake has a description.
            - made-up: Should be dropped, no such route.
            - nightly: Starts the nightly report.
            """;

    @Test
    void parsesTheAnswerTolerantly() {
        Overview o = ProjectOverviewTest.overview();
        AiContent ai = IntegrationSummary.parseAnswer(ANSWER, o);
        assertThat(ai.overview()).isEqualTo("The project takes orders over HTTP and processes them through Kafka.");
        assertThat(ai.capabilities()).containsExactly(
                new Capability("Order intake", List.of("intake", "audit"), "Accepts and records new orders."),
                // process-order has group orders in the source: the AI does not place it
                new Capability("Processing", List.of("urgent"), "Routes orders by priority."));
        assertThat(ai.descriptions()).containsOnly(Map.entry("audit", "Writes each order to the audit log."),
                Map.entry("nightly", "Starts the nightly report."));
    }

    @Test
    void sentencesStartingWithASectionNameStayInTheOverview() {
        AiContent ai = IntegrationSummary.parseAnswer("OVERVIEW:\nCapabilities of this project are few.\n",
                ProjectOverviewTest.overview());
        assertThat(ai.overview()).isEqualTo("Capabilities of this project are few.");
    }

    @Test
    void renderAndReadBack() {
        Overview o = ProjectOverviewTest.overview();
        AiContent ai = IntegrationSummary.parseAnswer(ANSWER, o);
        String text = IntegrationSummary.render(o, ai, o.fingerprint(), "llama3.2", "2026-09-29");

        assertThat(text).startsWith("<!-- camel-summary fingerprint=\"" + o.fingerprint()
                                    + "\" model=\"llama3.2\" date=\"2026-09-29\" -->");
        assertThat(text).contains("## Overview " + IntegrationSummary.AI_MARK, "## Capabilities " + IntegrationSummary.AI_MARK,
                IntegrationSummary.AI_BEGIN, "| `audit` | `seda:audit` | " + IntegrationSummary.AI_PREFIX
                                             + "Writes each order to the audit log. |",
                "| `intake` | `direct:intake` | Accepts new orders over HTTP |",
                "- HTTP: `POST /api/orders` → `intake`",
                "- `intake` hands off to `audit` over `seda:audit`",
                "  - Kafka `kafka:orders`, read by `process-order`, written by `intake`",
                "## Findings");
        // facts sections are not marked as AI
        assertThat(text).doesNotContain("## Routes " + IntegrationSummary.AI_MARK);

        Summary back = IntegrationSummary.parse(text);
        assertThat(back.fingerprint()).isEqualTo(o.fingerprint());
        assertThat(back.model()).isEqualTo("llama3.2");
        assertThat(back.date()).isEqualTo("2026-09-29");
        assertThat(back.overview()).isEqualTo(ai.overview());
        // audit only logs: the map has it under Utility, so the AI's capability lists intake alone
        assertThat(back.capabilities()).containsExactly(
                new Capability("Order intake", List.of("intake"), "Accepts and records new orders."),
                new Capability("Processing", List.of("urgent"), "Routes orders by priority."));
        assertThat(back.descriptions()).isEqualTo(ai.descriptions());
    }

    @Test
    void factsOnlySummary() {
        Overview o = ProjectOverviewTest.overview();
        String text = IntegrationSummary.render(o, null, null, null, "2026-09-29");
        assertThat(text).contains("No AI-assisted sections yet").doesNotContain(IntegrationSummary.AI_MARK + "\n");
        assertThat(IntegrationSummary.parse(text).ai().isEmpty()).isTrue();
    }

    @Test
    void staleAiContentIsFlagged() {
        Overview o = ProjectOverviewTest.overview();
        AiContent ai = IntegrationSummary.parseAnswer(ANSWER, o);
        String text = IntegrationSummary.render(o, ai, "0000000000000000", "m", "2026-09-29");
        assertThat(text).contains("The routes changed after the AI sections were written");
        assertThat(IntegrationSummary.parse(text).fingerprint()).isEqualTo("0000000000000000");
    }

    @Test
    void pipesInDescriptionsSurvive() {
        Overview o = ProjectOverviewTest.overview();
        AiContent ai = new AiContent(null, List.of(), Map.of("audit", "Logs a | b"));
        String text = IntegrationSummary.render(o, ai, o.fingerprint(), null, "2026-09-29");
        assertThat(IntegrationSummary.parse(text).descriptions()).containsEntry("audit", "Logs a | b");
    }

    @Test
    void mergeKeepsWhatIsNotReplaced() {
        Overview o = ProjectOverviewTest.overview();
        AiContent previous = new AiContent(
                "Old overview.", List.of(new Capability("A", List.of("audit"), "x")),
                Map.of("audit", "Old audit.", "intake", "Dropped: intake has one in the source", "gone", "Dropped"));
        AiContent fresh = new AiContent(null, List.of(), Map.of("nightly", "New nightly."));
        AiContent merged = IntegrationSummary.merge(o, previous, fresh);
        assertThat(merged.overview()).isEqualTo("Old overview.");
        assertThat(merged.capabilities()).hasSize(1);
        assertThat(merged.descriptions()).containsOnlyKeys("audit", "nightly");
    }

    @Test
    void promptCarriesFactsAndMaskedExcerpts() {
        Overview o = ProjectOverviewTest.overview();
        String prompt = IntegrationSummary.userPrompt(o, ProjectOverviewTest.sources());
        assertThat(prompt).contains("Project: orders-app", "- intake from direct:intake to kafka:orders, seda:audit",
                "described as: Accepts new orders over HTTP", "Routes needing a description: ", "audit (intake.camel.yaml):");
        assertThat(prompt).doesNotContain("saslJaasConfig=secret", "apiKey=abc");
        assertThat(IntegrationSummary.systemPrompt()).contains("OVERVIEW:", "CAPABILITIES:", "DESCRIPTIONS:");
    }

    @Test
    void maskLine() {
        assertThat(IntegrationSummary.maskLine("      password: s3cret")).isEqualTo("      password: xxxxxx");
        assertThat(IntegrationSummary.maskLine("      password: \"{{db.password}}\""))
                .isEqualTo("      password: \"{{db.password}}\"");
        assertThat(IntegrationSummary.maskLine("<to uri=\"x\" accessKey=\"AKIA\"/>")).isEqualTo(
                "<to uri=\"x\" accessKey=\"xxxxxx\"/>");
        assertThat(IntegrationSummary.maskLine("uri: kafka:t?brokers=b&password=p")).doesNotContain("=p");
        assertThat(IntegrationSummary.maskLine("  message: hello")).isEqualTo("  message: hello");
    }

    @Test
    void saveToolWritesAndMerges(@TempDir Path dir) throws Exception {
        for (Map.Entry<String, String> e : ProjectOverviewTest.sources().entrySet()) {
            Files.writeString(dir.resolve(e.getKey()), e.getValue(), StandardCharsets.UTF_8);
        }
        ToolContext ctx = new ToolContext();
        ToolRegistry.execute("camel_save_project_summary", ctx, Map.of("directory", dir.toString(),
                "overview", "Handles orders.", "descriptions", "audit: Audits orders.\nnope: x", "model", "test-model"));
        ToolRegistry.execute("camel_save_project_summary", ctx, Map.of("directory", dir.toString(),
                "descriptions", "- nightly: Nightly report.", "utility", "audit, nope"));

        Summary summary = IntegrationSummary.read(dir);
        assertThat(summary.overview()).isEqualTo("Handles orders.");
        assertThat(summary.descriptions()).containsOnlyKeys("audit", "nightly");
        assertThat(summary.utility()).containsExactly("audit");

        String json = ToolRegistry.execute("camel_project_overview", ctx, Map.of("directory", dir.toString())).toString();
        assertThat(json).contains("\"upToDate\":true", "\"aiDescription\":\"Audits orders.\"")
                .doesNotContain("\"hint\"");
        // the summary file is not a route file, so writing it does not make the summary out of date
        assertThat(json).contains(
                "\"routeFiles\":[\"intake.camel.yaml\",\"processing.camel.yaml\",\"Report.java\",\"urgent.camel.xml\"]");
        // the model of the first save is kept when the second does not name one
        assertThat(summary.model()).isEqualTo("test-model");

        assertThatThrownBy(() -> ToolRegistry.execute("camel_save_project_summary", ctx,
                Map.of("directory", dir.toString(), "descriptions", "nope: x")))
                .isInstanceOf(ToolExecutionException.class).hasMessageContaining("Nothing to save");
    }
}
