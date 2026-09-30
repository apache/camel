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
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview.Overview;
import org.apache.camel.dsl.jbang.core.commands.ai.RouteDescriptions.Change;
import org.apache.camel.dsl.jbang.core.commands.ai.RouteDescriptions.Plan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Descriptions put into the route sources as reviewable changes (CAMEL-25143), and the changed sources still read as
 * the same routes.
 */
class RouteDescriptionsTest {

    @TempDir
    Path dir;

    private Overview project() throws Exception {
        for (Map.Entry<String, String> e : ProjectOverviewTest.sources().entrySet()) {
            Files.writeString(dir.resolve(e.getKey()), e.getValue(), StandardCharsets.UTF_8);
        }
        Files.writeString(dir.resolve("bare.camel.yaml"), """
                - from:
                    uri: timer:bare
                    steps:
                      - to: direct:report
                """, StandardCharsets.UTF_8);
        return ProjectOverview.analyze(dir, ProjectOverviewTest.CATALOG);
    }

    @Test
    void addsDescriptionsInEveryDsl() throws Exception {
        Overview o = project();
        Map<String, String> descriptions = new LinkedHashMap<>();
        descriptions.put("audit", "Writes each order to the \"audit\" log.");
        descriptions.put("process-order", "Routes orders by priority.");
        descriptions.put("nightly", "Starts the nightly report.");
        descriptions.put("urgent", "Asks OpenAI about urgent orders & <escalates>.");
        descriptions.put("report", "Writes the report file.");
        descriptions.put("intake", "Ignored: has one.");
        String bare = o.flows().stream().filter(r -> r.file().equals("bare.camel.yaml")).findFirst().orElseThrow().key();
        descriptions.put(bare, "Cannot be placed.");

        Plan plan = RouteDescriptions.plan(o, descriptions);
        assertThat(plan.skipped()).extracting(RouteDescriptions.Skipped::route).containsExactlyInAnyOrder("intake", bare);

        Map<String, Change> byFile = new LinkedHashMap<>();
        plan.changes().forEach(c -> byFile.put(c.file(), c));
        assertThat(byFile.get("intake.camel.yaml").newContent()).contains("""
                - route:
                    id: audit
                    description: "Writes each order to the \\"audit\\" log."
                    from:""");
        assertThat(byFile.get("processing.camel.yaml").routes()).containsExactly("process-order", "nightly");
        assertThat(byFile.get("processing.camel.yaml").newContent()).contains(
                "    id: process-order\n    description: \"Routes orders by priority.\"\n    group: orders\n",
                "    id: nightly\n    description: \"Starts the nightly report.\"\n    from:\n");
        assertThat(byFile.get("urgent.camel.xml").newContent()).contains(
                "<route id=\"urgent\" description=\"Asks OpenAI about urgent orders &amp; &lt;escalates&gt;.\">");
        assertThat(byFile.get("Report.java").newContent())
                .contains(".routeId(\"report\").routeDescription(\"Writes the report file.\")");

        // written back, the routes read with their new descriptions
        for (Change c : plan.changes()) {
            Files.writeString(dir.resolve(c.file()), c.newContent(), StandardCharsets.UTF_8);
        }
        Overview after = ProjectOverview.analyze(dir, ProjectOverviewTest.CATALOG);
        assertThat(after.route("audit").description()).isEqualTo("Writes each order to the \"audit\" log.");
        assertThat(after.route("nightly").description()).isEqualTo("Starts the nightly report.");
        assertThat(after.route("urgent").description())
                .isEqualTo("Asks OpenAI about urgent orders & <escalates>.");
        assertThat(after.route("report").description()).isEqualTo("Writes the report file.");
        assertThat(after.links()).isEqualTo(o.links());
    }

    @Test
    void routeWithoutIdGetsItFirst() throws Exception {
        Files.writeString(dir.resolve("noid.camel.yaml"), """
                - route:
                    from:
                      uri: timer:x
                      steps:
                        - to: log:x
                """, StandardCharsets.UTF_8);
        Overview o = ProjectOverview.analyze(dir, ProjectOverviewTest.CATALOG);
        String key = o.flows().get(0).key();
        assertThat(key).isEqualTo("noid.camel.yaml:1");
        Plan plan = RouteDescriptions.plan(o, Map.of(key, "Logs every tick."));
        assertThat(plan.changes().get(0).newContent()).startsWith("""
                - route:
                    description: "Logs every tick."
                    from:""");
    }
}
