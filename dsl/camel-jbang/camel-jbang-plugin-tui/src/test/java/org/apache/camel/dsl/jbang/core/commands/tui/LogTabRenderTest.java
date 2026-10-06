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
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rendering tests for {@link LogTab}. These tests render the tab into a virtual terminal buffer and inspect the
 * rendered cell content.
 */
class LogTabRenderTest {

    private MonitorContext ctx;
    private IntegrationInfo info;

    @BeforeEach
    void setUp() {
        info = new IntegrationInfo();
        info.pid = "1234";
        info.name = "test-app";

        AtomicReference<List<IntegrationInfo>> data = new AtomicReference<>(List.of(info));
        AtomicReference<List<InfraInfo>> infraData = new AtomicReference<>(List.of());
        ctx = new MonitorContext(data, infraData);
        ctx.selectedPid = "1234";
    }

    @Test
    void foldsAStormOfTheSameLineIntoOneEntry() {
        List<LogEntry> entries = new ArrayList<>();
        LogTab.addFolded(entries, LogTab.parseLogLine(
                "2026-09-22 10:00:00.001  INFO 42 --- [           main] route1 : Started"));
        for (int i = 0; i < 100; i++) {
            LogTab.addFolded(entries, LogTab.parseLogLine(
                    "2026-09-22 10:01:" + String.format("%02d", i % 60)
                                                          + ".000 ERROR 42 --- [ timer://tick] route1 : Failed to call the API"));
        }
        LogTab.addFolded(entries, LogTab.parseLogLine(
                "2026-09-22 10:02:00.001  INFO 42 --- [           main] route1 : Done"));

        assertEquals(3, entries.size(), "the storm is one entry between the two others");
        assertEquals(100, entries.get(1).repeat);
        assertEquals("10:01:39.000", entries.get(1).time, "the entry keeps the newest timestamp");
        assertEquals(1, entries.get(0).repeat, "a line that happened once is not a repeat");
    }

    @Test
    void theCompactViewShowsTimeLevelLoggerAndMessage() {
        LogEntry entry = LogTab.parseLogLine("2026-10-01 17:24:30.257  INFO 85013 --- [ntloop-thread-0]"
                                             + " tform.http.vertx.VertxPlatformHttpServer : Vert.x HttpServer started on 0.0.0.0:8080");
        String text = LogTab.compactLine(entry).spans().stream().map(Span::content).reduce("", String::concat);
        assertEquals("17:24:30.257  INFO VertxPlatformHttpServer  Vert.x HttpServer started on 0.0.0.0:8080", text);
    }

    @Test
    void renderNoSelectionShowsPrompt() {
        ctx.selectedPid = null;
        LogTab tab = new LogTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 120, 20);
        assertTrue(rendered.contains("No integration selected") || rendered.contains("Select an integration"),
                "Should show selection prompt when no integration selected");
    }

    @Test
    void renderShowsBlockTitle() {
        LogTab tab = new LogTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 120, 20);
        assertTrue(rendered.contains("Log"), "Should show Log in the block title");
    }

    @Test
    void theTitleNamesTheIntegrationAndItsLogLevel() {
        info.rootLogLevel = "INFO";
        LogTab tab = new LogTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 120, 20);
        assertTrue(rendered.contains("[test-app] Log level:INFO"), rendered);
    }

    @Test
    void renderShowsLoadingOrEmpty() {
        LogTab tab = new LogTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 120, 20);
        assertTrue(rendered.contains("Loading") || rendered.contains("Log"),
                "Should show loading state or Log title");
    }

    @Test
    void wrappedLinesAreCountedByTheirRows() {
        assertEquals(1, LogTab.wrappedRows("short line", 40));
        assertEquals(2, LogTab.wrappedRows("one two three four five six seven eight nine ten", 30));
        assertEquals(3, LogTab.wrappedRows("x".repeat(70), 30));
        assertEquals(1, LogTab.wrappedRows("", 30));
    }

    @Test
    void followingWithWordWrapShowsTheNewestLine() {
        List<LogEntry> entries = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            // long continuation lines that wrap to two rows each
            entries.add(LogTab.parseLogLine("orders.camel.yaml:" + i + "   orders/to" + i + "   "
                                            + "to[direct:big-orders] ".repeat(8)));
        }
        entries.add(LogTab.parseLogLine(
                "2026-10-05 20:14:59.906 ERROR 33838 --- [mer://inventory] orders.camel.yaml:44 : The newest line"));
        LogTab tab = new LogTab(ctx);
        tab.setEntriesForTesting(entries);

        String rendered = TuiTestHelper.renderToString(tab, 120, 20);

        assertTrue(rendered.contains("The newest line"), rendered);
    }

    @Test
    void shiftF8WithDifferentErrorsOnTheScreenShowsThePickList() {
        ctx.askAiCallback = (file, line, problem, text) -> {
        };
        List<LogEntry> entries = new ArrayList<>();
        for (String line : List.of(
                "2026-10-05 20:14:57.676 ERROR 33838 --- [timer://billing] ocessor.errorhandler.DefaultErrorHandler : "
                                   + "Failed delivery for (MessageId: 4E93-1). Exhausted after delivery attempt: 1",
                "Message History",
                "orders.camel.yaml:34                     billing/throwException2        throwException[]",
                "Stacktrace",
                "java.lang.IllegalStateException: No account for customer 42",
                "\tat org.apache.camel.processor.ThrowExceptionProcessor.process(ThrowExceptionProcessor.java:68)",
                "2026-10-05 20:14:59.906 ERROR 33838 --- [mer://inventory] orders.camel.yaml:44                     : "
                                                                                                                    + "Stock is low for item 7")) {
            entries.add(LogTab.parseLogLine(line));
        }
        LogTab tab = new LogTab(ctx);
        tab.setEntriesForTesting(entries);
        TuiTestHelper.renderToString(tab, 120, 20);

        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.F8, KeyModifiers.SHIFT));
        String rendered = TuiTestHelper.renderToString(tab, 120, 20);

        assertTrue(rendered.contains("which ERROR"), rendered);
        assertTrue(rendered.contains("Stock is low for item 7"), rendered);
        assertTrue(rendered.contains("IllegalStateException: No account for customer 42"), rendered);
    }

    @Test
    void theFixWithAiHintIsWithTheFKeysWhenAnErrorIsOnTheScreen() {
        ctx.askAiCallback = (file, line, problem, text) -> {
        };
        LogTab tab = new LogTab(ctx);
        tab.setEntriesForTesting(List.of(
                LogTab.parseLogLine("2026-10-05 21:10:20.969  INFO 1 --- [ timer://orders] orders.camel.yaml:18 : Order 12"),
                LogTab.parseLogLine(
                        "2026-10-05 21:10:25.025 ERROR 1 --- [mer://inventory] orders.camel.yaml:43 : Stock is low")));
        TuiTestHelper.renderToString(tab, 120, 20);

        List<Span> spans = new ArrayList<>();
        tab.renderFKeyHints(spans);
        String hints = spans.stream().map(Span::content).reduce("", String::concat);
        assertTrue(hints.contains("Shift+F8"), hints);
    }

    @Test
    void renderFooterHints() {
        LogTab tab = new LogTab(ctx);
        List<Span> footerSpans = new ArrayList<>();
        tab.renderFooter(footerSpans);
        String footer = footerSpans.stream().map(Span::content).reduce("", String::concat);
        assertTrue(footer.contains("find") || footer.contains("/"),
                "Footer should contain find hint");
    }

}
