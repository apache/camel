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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.ai.IntegrationSummary;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The AI panel's readme tool when there is no README: the facts derived from the route sources, so a question about
 * what the integration does starts from them instead of reading every file (CAMEL-25143), and the project's summary,
 * partly written by an AI, only when asked for and saying so, as a model takes back what an AI wrote there as facts
 * (CAMEL-26946).
 */
class ReadmeToolSummaryTest {

    @TempDir
    Path project;

    private TuiToolRegistry registry() {
        IntegrationInfo info = new IntegrationInfo();
        info.name = "shop";
        info.pid = "1";
        info.directory = project.toString();
        McpFacade facade = new McpFacade(
                null, new AtomicReference<>(List.of(info)), null, null, null, null, null, null, null, null, null,
                null, null);
        return new TuiToolRegistry(facade);
    }

    private void route(String uri) throws Exception {
        Files.writeString(project.resolve("orders.camel.yaml"), """
                - route:
                    id: orders
                    from:
                      uri: %s
                      steps:
                        - to: log:orders
                """.formatted(uri), StandardCharsets.UTF_8);
    }

    private void summary(String overview) throws Exception {
        ProjectOverview.Overview o = ProjectOverview.analyze(project, new DefaultCamelCatalog());
        IntegrationSummary.write(o, new IntegrationSummary.AiContent(overview, List.of(), Map.of()), o.fingerprint(), "m");
    }

    private String readme(boolean aiSummary) throws Exception {
        Map<String, Object> args = new HashMap<>(Map.of("name", "shop"));
        if (aiSummary) {
            args.put("aiSummary", true);
        }
        return registry().execute("tui_get_readme", args);
    }

    private JsonObject readmeJson(boolean aiSummary) throws Exception {
        return (JsonObject) Jsoner.deserialize(readme(aiSummary));
    }

    @Test
    void factsFromTheSourcesWhenThereIsNoReadme() throws Exception {
        route("timer:tick");

        JsonObject answer = readmeJson(false);
        assertThat(answer.getBoolean("aiWritten")).as(answer.toJson()).isFalse();
        assertThat(answer.getString("content")).contains("Derived from the route sources", "timer:tick");
    }

    @Test
    void theAiSummaryOnlyWhenAskedForAndSayingSo() throws Exception {
        route("timer:tick");
        summary("Ticks orders.");

        JsonObject facts = readmeJson(false);
        assertThat(facts.getBoolean("aiWritten")).as(facts.toJson()).isFalse();
        assertThat(facts.getString("content")).as("what the AI wrote is left out")
                .contains("timer:tick").doesNotContain("Ticks orders.");

        JsonObject ai = readmeJson(true);
        assertThat(ai.getBoolean("aiWritten")).as(ai.toJson()).isTrue();
        assertThat(ai.getString("file")).isEqualTo(IntegrationSummary.FILE_NAME);
        assertThat(ai.getString("model")).isEqualTo("m");
        assertThat(ai.getString("note")).contains("were written by an AI");
        assertThat(ai.getString("content")).as("tool comments left out")
                .contains("Ticks orders.").doesNotContain("ai:begin");
        assertThat(ai).doesNotContainKey("upToDate");
    }

    @Test
    void theFactsFollowTheSourcesAfterTheSummaryWasWritten() throws Exception {
        route("timer:tick");
        summary("Ticks orders.");
        route("timer:clock");

        JsonObject facts = readmeJson(false);
        assertThat(facts.getString("content")).contains("timer:clock").doesNotContain("timer:tick");

        JsonObject ai = readmeJson(true);
        assertThat(ai.getBoolean("upToDate")).as("the AI wrote its part for the routes before").isFalse();
    }

    @Test
    void askingForTheAiSummaryWhenThereIsNone() throws Exception {
        route("timer:tick");

        JsonObject answer = readmeJson(true);
        assertThat(answer.getBoolean("aiWritten")).as(answer.toJson()).isFalse();
        assertThat(answer.getString("note")).contains("There is no " + IntegrationSummary.FILE_NAME);
        assertThat(answer.getString("content")).contains("timer:tick");
    }

    @Test
    void noReadmeAndNoRoutes() throws Exception {
        assertThat(readme(false)).startsWith("No README found");
        assertThat(readme(true)).startsWith("No README found");
    }
}
