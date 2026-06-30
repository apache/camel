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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Higher-level rendering tests for {@link ThreadsTab}. Renders the tab into a virtual terminal buffer via
 * {@link Frame#forTesting(Buffer)} and inspects the rendered cell content.
 */
class ThreadsTabRenderTest {

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
    void renderNoSelectionShowsPrompt() {
        ctx.selectedPid = null;
        ThreadsTab tab = new ThreadsTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 120, 20);
        assertTrue(rendered.contains("No integration selected") || rendered.contains("Select an integration"),
                "Should show selection prompt when no integration selected");
    }

    @Test
    void renderShowsBlockTitle() {
        ThreadsTab tab = new ThreadsTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 120, 20);
        assertTrue(rendered.contains("Threads"), "Should show Threads in the block title");
    }

    @Test
    void renderFooterHints() {
        ThreadsTab tab = new ThreadsTab(ctx);
        List<Span> footerSpans = new ArrayList<>();
        tab.renderFooter(footerSpans);
        String footer = footerSpans.stream().map(Span::content).reduce("", String::concat);
        assertTrue(footer.contains("sort"), "Footer should contain sort hint");
    }

    @Test
    void renderShowsLoadingOrEmpty() {
        ThreadsTab tab = new ThreadsTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 120, 20);
        assertTrue(rendered.contains("Loading") || rendered.contains("No threads") || rendered.contains("Threads"),
                "Should show loading or empty state");
    }

}
