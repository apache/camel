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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.dsl.jbang.core.commands.LlmClient;
import org.apache.camel.dsl.jbang.core.commands.ai.IntegrationSummary;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The /overview command of the AI panel (CAMEL-25143): one plain request to the model, the answer saved as the
 * project's summary marked AI-assisted, and the AI descriptions offered to the tabs unless the setting is off.
 */
@Isolated
class ProjectOverviewAssistTest {

    private static final String ROUTES = """
            - route:
                id: orders
                from:
                  uri: kafka:orders
                  steps:
                    - to: direct:store
            - route:
                id: store
                from:
                  uri: direct:store
                  steps:
                    - to: sql:insert into orders values (:#body)
            """;

    private static final String ANSWER = """
            OVERVIEW:
            Stores orders arriving on Kafka.

            CAPABILITIES:
            - Order storage: orders, store | Keeps every order.

            DESCRIPTIONS:
            - orders: Takes orders from Kafka.
            - store: Inserts an order into the database.
            """;

    @TempDir
    Path home;
    @TempDir
    Path project;

    private String originalHome;
    private final List<String> entries = new CopyOnWriteArrayList<>();
    private final ProjectOverviewAssist.Sink sink = (role, text) -> entries.add(role + ": " + text);

    @BeforeEach
    void setUp() throws Exception {
        originalHome = CommandLineHelper.getHomeDir().toString();
        CommandLineHelper.useHomeDir(home.toString());
        IntegrationSummaryHints.resetForTesting();
        Files.writeString(project.resolve("orders.camel.yaml"), ROUTES, StandardCharsets.UTF_8);
    }

    @AfterEach
    void tearDown() {
        CommandLineHelper.useHomeDir(originalHome);
        IntegrationSummaryHints.resetForTesting();
    }

    @Test
    void explainsSavesAndShowsTheSummary() {
        AnsweringClient client = new AnsweringClient(ANSWER);
        ProjectOverviewAssist assist = new ProjectOverviewAssist();

        String started = assist.command("", project, client, null, sink);
        assertTrue(started.startsWith("Explaining 2 routes with test-model"), started);
        await().atMost(10, TimeUnit.SECONDS).until(() -> !entries.isEmpty() && !assist.isRunning());

        assertEquals(1, client.calls.get());
        assertTrue(client.system.contains("DESCRIPTIONS:"), "asks for the summary format");
        assertTrue(client.user.contains("Routes needing a description: orders, store"), client.user);
        assertTrue(entries.get(0).startsWith("ASSISTANT: ### Project overview " + IntegrationSummary.AI_MARK),
                entries.get(0));
        assertTrue(entries.get(0).contains("Stores orders arriving on Kafka."));
        assertTrue(Files.isRegularFile(project.resolve(IntegrationSummary.FILE_NAME)));

        // the tabs get the AI descriptions, marked by the caller
        assertEquals("Inserts an order into the database.", IntegrationSummaryHints.description(project, "store"));

        // up to date: shown again without asking the model
        String shown = assist.command("", project, client, null, sink);
        assertTrue(shown.contains("Keeps every order."), shown);
        assertEquals(1, client.calls.get());
        assertTrue(assist.command("show", project, client, null, sink).startsWith("<!-- camel-summary"));

        // a changed route makes it out of date: explained again
        entries.clear();
        rewrite(ROUTES.replace("direct:store", "direct:save"));
        assertTrue(assist.command("", project, client, null, sink).startsWith("Explaining"));
        await().atMost(10, TimeUnit.SECONDS).until(() -> !entries.isEmpty() && !assist.isRunning());
        assertEquals(2, client.calls.get());
    }

    @Test
    void anAnswerInTheWrongFormatSavesNothing() {
        ProjectOverviewAssist assist = new ProjectOverviewAssist();
        assist.command("refresh", project, new AnsweringClient("I cannot help with that."), null, sink);
        await().atMost(10, TimeUnit.SECONDS).until(() -> !entries.isEmpty() && !assist.isRunning());
        assertTrue(entries.get(0).startsWith("ERROR: The model's answer did not follow the format"), entries.get(0));
        assertFalse(Files.exists(project.resolve(IntegrationSummary.FILE_NAME)));
    }

    @Test
    void offModeHidesEverything() {
        ProjectOverviewAssist assist = new ProjectOverviewAssist();
        assist.command("", project, new AnsweringClient(ANSWER), null, sink);
        await().atMost(10, TimeUnit.SECONDS).until(() -> !entries.isEmpty() && !assist.isRunning());
        assertEquals("Takes orders from Kafka.", IntegrationSummaryHints.description(project, "orders"));

        TuiSettings settings = TuiSettings.load();
        settings.setAiOverview("off");
        settings.save();
        IntegrationSummaryHints.resetForTesting();

        assertTrue(assist.command("", project, new AnsweringClient(ANSWER), null, sink).contains("is off"));
        assertNull(IntegrationSummaryHints.description(project, "orders"));
        assertTrue(IntegrationSummaryHints.descriptionsIfEnabled(project).isEmpty());
    }

    @Test
    void autoModeExplainsOncePerProjectState() {
        TuiSettings settings = TuiSettings.load();
        settings.setAiOverview("auto");
        settings.save();
        AnsweringClient client = new AnsweringClient(ANSWER);
        ProjectOverviewAssist assist = new ProjectOverviewAssist();

        assertTrue(assist.autoExplain(project, client, null, sink));
        await().atMost(10, TimeUnit.SECONDS).until(() -> entries.size() >= 2 && !assist.isRunning());
        assertTrue(entries.get(0).contains("(AI Overview: auto)"), entries.get(0));
        // up to date now, and tried already
        assertFalse(assist.autoExplain(project, client, null, sink));
        assertEquals(1, client.calls.get());

        // manual mode never explains by itself
        settings.setAiOverview(null);
        settings.save();
        rewrite(ROUTES.replace("kafka:orders", "kafka:orders2"));
        assertFalse(assist.autoExplain(project, client, null, sink));
    }

    @Test
    void theSummaryWaitsUntilThePanelIsIdle() throws Exception {
        TuiSettings settings = TuiSettings.load();
        settings.setAiOverview("auto");
        settings.save();
        AnsweringClient client = new AnsweringClient(ANSWER);
        ProjectOverviewAssist assist = new ProjectOverviewAssist();
        AtomicBoolean idle = new AtomicBoolean();

        // the user asks a question right after opening the panel: the summary must not go first
        Thread check = new Thread(() -> assist.autoExplain(project, client, null, sink, idle::get));
        check.start();
        check.join(1000);
        assertTrue(check.isAlive(), "waits while the panel is busy");
        assertEquals(0, client.calls.get());
        assertTrue(entries.isEmpty());

        idle.set(true);
        await().atMost(10, TimeUnit.SECONDS).until(() -> client.calls.get() == 1 && !assist.isRunning());
        assertTrue(entries.get(0).contains("(AI Overview: auto)"), entries.get(0));
    }

    @Test
    void aStoppedSummaryLeavesNoError() {
        TuiSettings settings = TuiSettings.load();
        settings.setAiOverview("auto");
        settings.save();
        CountDownLatch asked = new CountDownLatch(1);
        LlmClient slow = new AnsweringClient(ANSWER) {
            @Override
            public ChatResponse chatWithTools(String systemPrompt, List<Message> messages, List<ToolDef> tools) {
                asked.countDown();
                try {
                    // a local model writing a long summary
                    new CountDownLatch(1).await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted", e);
                }
                return null;
            }
        };
        ProjectOverviewAssist assist = new ProjectOverviewAssist();
        assertFalse(assist.stop(), "nothing to stop");

        assertTrue(assist.autoExplain(project, slow, null, sink));
        await().atMost(10, TimeUnit.SECONDS).until(() -> asked.getCount() == 0);
        assertTrue(assist.stop());
        await().atMost(10, TimeUnit.SECONDS).until(() -> !assist.isRunning());
        assertEquals(1, entries.size(), "only the start was said: " + entries);
        assertFalse(Files.exists(project.resolve(IntegrationSummary.FILE_NAME)));
    }

    @Test
    void applyNeedsTheSelectedIntegration() {
        ProjectOverviewAssist assist = new ProjectOverviewAssist();
        assertTrue(assist.command("apply", project, null, null, sink).startsWith("No suggested route descriptions"));
        assist.command("", project, new AnsweringClient(ANSWER), null, sink);
        await().atMost(10, TimeUnit.SECONDS).until(() -> !entries.isEmpty() && !assist.isRunning());
        assertTrue(assist.command("apply", project, null, null, sink).startsWith("Select the integration"));
        assertTrue(assist.command("bogus", project, null, null, sink).startsWith("Usage: /overview"));
    }

    @Test
    void noClientNoRoutes() throws Exception {
        ProjectOverviewAssist assist = new ProjectOverviewAssist();
        assertTrue(assist.command("", project, null, null, sink).startsWith("No LLM client available"));
        Files.delete(project.resolve("orders.camel.yaml"));
        assertTrue(assist.command("", project, null, null, sink).startsWith("No routes found"));
    }

    private void rewrite(String content) {
        try {
            Files.writeString(project.resolve("orders.camel.yaml"), content, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static class AnsweringClient extends LlmClient {

        private final String answer;
        private final AtomicInteger calls = new AtomicInteger();
        private volatile String system;
        private volatile String user;

        AnsweringClient(String answer) {
            this.answer = answer;
            withModel("test-model");
            withApiType(ApiType.openai);
        }

        @Override
        public ChatResponse chatWithTools(String systemPrompt, List<Message> messages, List<ToolDef> tools) {
            calls.incrementAndGet();
            system = systemPrompt;
            user = messages.get(0).content();
            assertTrue(tools.isEmpty(), "the overview needs no tools");
            return new ChatResponse(answer, null, "end_turn", false, TokenUsage.EMPTY);
        }
    }
}
