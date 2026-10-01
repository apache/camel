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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.dsl.jbang.core.commands.ai.IntegrationSummary.AiContent;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview.Overview;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A route's description is the short label a diagram box shows, its note the sentence that explains it; the AI writes
 * both, and they go into the source as description and note.
 */
class LabelAndNoteTest {

    private static final String ROUTES = """
            - route:
                id: intake
                description: Order intake
                from:
                  uri: direct:intake
                  steps:
                    - to: direct:store
            - route:
                id: store
                note: Keeps every order.
                from:
                  uri: direct:store
                  steps:
                    - to: sql:insert into orders values (:#body)
            - route:
                id: report
                from:
                  uri: timer:report
                  steps:
                    - to: direct:store
            """;

    @TempDir
    Path dir;

    private Overview overview() throws Exception {
        Files.writeString(dir.resolve("orders.camel.yaml"), ROUTES, StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("R.java"), """
                public class R extends RouteBuilder {
                    public void configure() {
                        from("direct:j").routeId("j").routeNote("Java note.").to("log:j");
                    }
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("x.camel.xml"), """
                <routes xmlns="http://camel.apache.org/schema/xml-io">
                  <route id="x" description="X label"><from uri="direct:x"/><to uri="direct:store"/></route>
                </routes>
                """, StandardCharsets.UTF_8);
        return ProjectOverview.analyze(dir, ProjectOverviewTest.CATALOG);
    }

    @Test
    void readsNotesOfEveryDsl() throws Exception {
        Overview o = overview();
        assertThat(o.route("store").note()).isEqualTo("Keeps every order.");
        assertThat(o.route("j").note()).isEqualTo("Java note.");
        assertThat(o.route("x").description()).isEqualTo("X label");
        assertThat(o.route("x").hasNote()).isFalse();
    }

    @Test
    void labelAndNoteFromTheAnswer() {
        assertThat(IntegrationSummary.labelAndNote(" **Order intake** | Takes orders over HTTP. "))
                .containsExactly("Order intake", "Takes orders over HTTP.");
        assertThat(IntegrationSummary.labelAndNote("Nightly report")).containsExactly("Nightly report", null);
        // a sentence where the label belongs is no label: the note when there is none, else dropped
        assertThat(IntegrationSummary.labelAndNote("Receive HTTP orders, validate, bill, and handle all the errors. |"))
                .containsExactly(null, "Receive HTTP orders, validate, bill, and handle all the errors.");
        assertThat(IntegrationSummary.labelAndNote(
                "Receive HTTP orders, validate, bill, and handle all the errors. | Takes orders over HTTP."))
                .containsExactly(null, "Takes orders over HTTP.");
        assertThat(IntegrationSummary.labelAndNote("Writes the orders report file on a fixed schedule for the business."))
                .containsExactly(null, "Writes the orders report file on a fixed schedule for the business.");
    }

    @Test
    void theAiFillsOnlyWhatIsMissing() throws Exception {
        Overview o = overview();
        AiContent ai = IntegrationSummary.parseAnswer("""
                DESCRIPTIONS:
                - intake: Ignored label | Accepts new orders.
                - store: Order storage | Ignored note.
                - report: Nightly report | Writes the report on a timer.
                """, o);
        // intake has a description: only its note; store has a note: only its label
        assertThat(ai.descriptions()).containsOnly(Map.entry("store", "Order storage"),
                Map.entry("report", "Nightly report"));
        assertThat(ai.notes()).containsOnly(Map.entry("intake", "Accepts new orders."),
                Map.entry("report", "Writes the report on a timer."));
        assertThat(IntegrationSummary.userPrompt(o, Map.of())).contains("note: Keeps every order.",
                "Routes needing a description: ");
    }

    @Test
    void summaryKeepsLabelsNotesAndSourceNotes() throws Exception {
        Overview o = overview();
        AiContent ai = new AiContent(
                null, List.of(), Map.of("report", "Nightly report"), List.of(),
                Map.of("report", "Writes the report | on a timer.", "intake", "Accepts new orders."));
        String text = IntegrationSummary.render(o, ai, o.fingerprint(), "m", "2026-09-29");
        assertThat(text).contains("| Route | From | Description | Note | Source |",
                "| `report` | `timer:report` | " + IntegrationSummary.AI_PREFIX + "Nightly report | "
                                                                                    + IntegrationSummary.AI_PREFIX
                                                                                    + "Writes the report \\| on a timer. |",
                "| `store` | `direct:store` |  | Keeps every order. |");
        IntegrationSummary.Summary back = IntegrationSummary.parse(text);
        assertThat(back.descriptions()).containsOnly(Map.entry("report", "Nightly report"));
        assertThat(back.notes()).containsOnly(Map.entry("report", "Writes the report | on a timer."),
                Map.entry("intake", "Accepts new orders."));
        assertThat(back.sourceNotes()).containsEntry("store", "Keeps every order.").containsEntry("j", "Java note.");
    }

    @Test
    void anOlderSummaryWithSentencesReadsThemAsNotes() {
        String older = """
                <!-- camel-summary fingerprint="f" date="2026-09-29" -->
                ## Routes

                | Route | From | Description | Source |
                |---|---|---|---|
                | `a` | `direct:a` | ✦ AI: Short label | a.yaml:1 |
                | `b` | `direct:b` | ✦ AI: A much longer sentence that explains what the route does and why. | a.yaml:9 |
                """;
        IntegrationSummary.Summary s = IntegrationSummary.parse(older);
        assertThat(s.descriptions()).containsOnly(Map.entry("a", "Short label"));
        assertThat(s.notes()).containsOnlyKeys("b");
    }

    @Test
    void labelsAndNotesGoIntoTheSources() throws Exception {
        Overview o = overview();
        RouteDescriptions.Plan plan = RouteDescriptions.plan(o,
                Map.of("report", "Nightly report", "store", "Order storage", "x", "Ignored", "j", "Java label"),
                Map.of("report", "Writes the report on a timer.", "intake", "Accepts new orders.", "x", "X note.",
                        "store", "Ignored note."));
        Map<String, String> byFile = new HashMap<>();
        plan.changes().forEach(c -> byFile.put(c.file(), c.newContent()));
        assertThat(byFile.get("orders.camel.yaml")).contains(
                "    id: intake\n    note: \"Accepts new orders.\"\n    description: Order intake",
                "    id: store\n    description: \"Order storage\"\n    note: Keeps every order.",
                "    id: report\n    description: \"Nightly report\"\n    note: \"Writes the report on a timer.\"");
        assertThat(byFile.get("x.camel.xml")).contains("<route id=\"x\" note=\"X note.\" description=\"X label\">");
        assertThat(byFile.get("R.java")).contains(".routeId(\"j\").routeDescription(\"Java label\").routeNote");

        for (RouteDescriptions.Change c : plan.changes()) {
            Files.writeString(dir.resolve(c.file()), c.newContent(), StandardCharsets.UTF_8);
        }
        Overview after = ProjectOverview.analyze(dir, ProjectOverviewTest.CATALOG);
        assertThat(after.route("report").description()).isEqualTo("Nightly report");
        assertThat(after.route("report").note()).isEqualTo("Writes the report on a timer.");
        assertThat(after.route("intake").note()).isEqualTo("Accepts new orders.");
        assertThat(after.route("store").description()).isEqualTo("Order storage");
        assertThat(after.route("x").note()).isEqualTo("X note.");
    }

    /** A route without an id has a colon in its key: the description is still its own, full key or short form. */
    @Test
    void routesWithoutIdsInTheAnswer() {
        String order = """
                public class OrderRoute extends RouteBuilder {
                    public void configure() {
                        from("file:src/main/data").to("amqp:queue:order.queue");
                    }
                }
                """;
        String widget = """
                public class WidgetGadgetRoute extends RouteBuilder {
                    public void configure() {
                        from("amqp:queue:order.queue").to("amqp:queue:widget.queue");
                    }
                }
                """;
        ProjectOverview.Overview o = ProjectOverview.analyze(Path.of("wg"), Map.of(
                "src/main/java/sample/OrderRoute.java", order, "src/main/java/sample/WidgetGadgetRoute.java", widget),
                ProjectOverviewTest.CATALOG);
        IntegrationSummary.AiContent ai = IntegrationSummary.parseAnswer("""
                DESCRIPTIONS:
                - src/main/java/sample/OrderRoute.java:3: Order intake | Reads order files and queues them.
                - `WidgetGadgetRoute.java:3`: Order classification | Sends each order to the widget or gadget queue.
                """, o);
        assertThat(ai.descriptions()).containsOnly(
                Map.entry("src/main/java/sample/OrderRoute.java:3", "Order intake"),
                Map.entry("src/main/java/sample/WidgetGadgetRoute.java:3", "Order classification"));
        assertThat(ai.notes()).containsKey("src/main/java/sample/WidgetGadgetRoute.java:3");
    }
}
